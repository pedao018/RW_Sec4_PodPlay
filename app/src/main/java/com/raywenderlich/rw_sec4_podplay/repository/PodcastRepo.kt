package com.raywenderlich.rw_sec4_podplay.repository

import androidx.lifecycle.LiveData
import com.raywenderlich.rw_sec4_podplay.db.PodcastDao
import com.raywenderlich.rw_sec4_podplay.model.Episode
import com.raywenderlich.rw_sec4_podplay.model.Podcast
import com.raywenderlich.rw_sec4_podplay.service.RssFeedResponse
import com.raywenderlich.rw_sec4_podplay.service.RssFeedService
import com.raywenderlich.rw_sec4_podplay.util.DateUtils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class PodcastRepo(
    private var feedService: RssFeedService,
    private var podcastDao: PodcastDao
) {
    suspend fun getPodcast(feedUrl: String): Podcast? {
        var podcast: Podcast?

        //Load from local
        podcast = podcastDao.loadPodcast(feedUrl)
        if (podcast != null) {
            podcast.id?.let {
                podcast!!.episodes = podcastDao.loadEpisodes(it)
                return podcast
            }
        }

        //If local is null -> load from internet
        val rssFeedResponse = feedService.getFeed(feedUrl)
        if (rssFeedResponse != null) {
            podcast = rssResponseToPodcast(
                feedUrl = feedUrl,
                imageUrl = "No image",
                rssResponse = rssFeedResponse
            )
        }
        return podcast
    }

    fun getAll(): LiveData<List<Podcast>> {
        return podcastDao.loadPodcasts()
    }

    suspend fun updatePodcastEpisodes(): MutableList<PodcastUpdateInfo> {
        val updatedPodcasts: MutableList<PodcastUpdateInfo> = mutableListOf()
        val podcasts = podcastDao.loadPodcastsStatic()
        for (podcast in podcasts) {
            val newEpisodes = getNewEpisodes(podcast)
            if (newEpisodes.count() > 0) {
                podcast.id?.let {
                    saveNewEpisodes(it, newEpisodes)
                    updatedPodcasts.add(
                        PodcastUpdateInfo(
                            podcast.feedUrl, podcast.feedTitle, newEpisodes.count()
                        )
                    )
                }
            }
        }
        return updatedPodcasts
    }

    private suspend fun getNewEpisodes(localPodcast: Podcast): List<Episode> {
        val response = feedService.getFeed(localPodcast.feedUrl)
        if (response != null) {
            val remotePodcast =
                rssResponseToPodcast(localPodcast.feedUrl, localPodcast.imageUrl, response)
            remotePodcast?.let {
                val localEpisodes = podcastDao.loadEpisodes(localPodcast.id!!)
                return remotePodcast.episodes.filter { episode ->
                    localEpisodes.find { episode.guid == it.guid } == null
                }
            }
        }
        return listOf()
    }

    fun save(podcast: Podcast) {
        GlobalScope.launch {
            val podcastId = podcastDao.insertPodcast(podcast)
            for (episode in podcast.episodes) {
                episode.podcastId = podcastId
                podcastDao.insertEpisode(episode)
            }
        }
    }

    fun delete(podcast: Podcast) {
        GlobalScope.launch {
            podcastDao.deletePodcast(podcast)
        }
    }

    private fun saveNewEpisodes(podcastId: Long, episodes: List<Episode>) {
        GlobalScope.launch {
            for (episode in episodes) {
                episode.podcastId = podcastId
                podcastDao.insertEpisode(episode)
            }
        }
    }

    private fun rssResponseToPodcast(
        feedUrl: String, imageUrl: String, rssResponse: RssFeedResponse
    ): Podcast? {
        val items = rssResponse.episodes ?: return null
        val description = if (rssResponse.description == "")
            rssResponse.summary else rssResponse.description
        return Podcast(
            null,
            feedUrl, rssResponse.title, description, imageUrl,
            rssResponse.lastUpdated, episodes = rssItemsToEpisodes(items)
        )
    }

    private fun rssItemsToEpisodes(
        episodeResponses: List<RssFeedResponse.EpisodeResponse>
    ): List<Episode> {
        return episodeResponses.map {
            Episode(
                it.guid ?: "",
                null,
                it.title ?: "",
                it.description ?: "",
                it.url ?: "",
                it.type ?: "",
                DateUtils.xmlDateToDate(it.pubDate),
                it.duration ?: ""
            )
        }
    }

    class PodcastUpdateInfo(
        val feedUrl: String,
        val name: String,
        val newCount: Int
    )

}
