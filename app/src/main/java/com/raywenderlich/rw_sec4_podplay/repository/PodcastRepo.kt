package com.raywenderlich.rw_sec4_podplay.repository

import com.raywenderlich.rw_sec4_podplay.model.Episode
import com.raywenderlich.rw_sec4_podplay.model.Podcast
import com.raywenderlich.rw_sec4_podplay.service.RssFeedResponse
import com.raywenderlich.rw_sec4_podplay.service.RssFeedService
import com.raywenderlich.rw_sec4_podplay.util.DateUtils

class PodcastRepo(private var feedService: RssFeedService) {
    suspend fun getPodcast(feedUrl: String): Podcast? {
        var podcast: Podcast? = null
        val rssFeedResponse = feedService.getFeed(feedUrl)
        if (rssFeedResponse != null) {
            podcast = rssResponseToPodcast(
                feedUrl = feedUrl,
                imageUrl = "No image",
                rssResponse = rssFeedResponse
            )
        }
        return podcast
        //return Podcast(feedUrl, "No Name", "No description", "No image")
    }

    private fun rssResponseToPodcast(
        feedUrl: String, imageUrl: String, rssResponse: RssFeedResponse
    ): Podcast? {
        val items = rssResponse.episodes ?: return null
        val description = if (rssResponse.description == "")
            rssResponse.summary else rssResponse.description
        return Podcast(
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
                it.title ?: "",
                it.description ?: "",
                it.url ?: "",
                it.type ?: "",
                DateUtils.xmlDateToDate(it.pubDate),
                it.duration ?: ""
            )
        }
    }

}
