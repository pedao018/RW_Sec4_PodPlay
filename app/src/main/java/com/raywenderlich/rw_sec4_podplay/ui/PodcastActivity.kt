package com.raywenderlich.rw_sec4_podplay.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.raywenderlich.rw_sec4_podplay.R

class PodcastActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_podcast)
    }
}