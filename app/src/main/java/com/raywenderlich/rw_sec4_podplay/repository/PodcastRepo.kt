package com.raywenderlich.rw_sec4_podplay.repository

import com.raywenderlich.rw_sec4_podplay.model.Podcast

class PodcastRepo {
    fun getPodcast(feedUrl: String): Podcast? {
        return Podcast(feedUrl, "No Name","No description", "No image")
    }
}
