package com.raywenderlich.rw_sec4_podplay.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.raywenderlich.rw_sec4_podplay.R
import com.raywenderlich.rw_sec4_podplay.repository.ItunesRepo
import com.raywenderlich.rw_sec4_podplay.service.ItunesService
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class PodcastActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_podcast)

        val ituneService = ItunesService.instance
        val repo = ItunesRepo(ituneService)
        GlobalScope.launch {
            val results = repo.searchByTerm("Android")
            Log.e("haha", "Result = ${results.body()}")
        }
    }
}