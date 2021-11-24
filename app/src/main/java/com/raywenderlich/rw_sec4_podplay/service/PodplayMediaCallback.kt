package com.raywenderlich.rw_sec4_podplay.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat

class PodplayMediaCallback(
    val context: Context,
    val mediaSession: MediaSessionCompat,
    var mediaPlayer: MediaPlayer? = null
) : MediaSessionCompat.Callback() {
    private var mediaUri: Uri? = null
    private var newMedia: Boolean = false
    private var mediaExtras: Bundle? = null
    private var focusRequest: AudioFocusRequest? = null
    var listener: PodplayMediaListener? = null
    private var mediaNeedsPrepare: Boolean = false

    companion object {
        const val CMD_CHANGESPEED = "change_speed"
        const val CMD_EXTRA_SPEED = "speed"
    }

    override fun onPlayFromUri(uri: Uri?, extras: Bundle?) {
        super.onPlayFromUri(uri, extras)
        println("Playing ${uri.toString()}")

        /*
        If the uri passed in is the same as before, then the newMedia flag is set to false, and mediaExtras is set to null.
        There is no need to set the new media or mediaExtras if a new media item is not being set.
        If the uri is new, then the media extras are stored and setNewMedia() is called.
        * */
        if (mediaUri == uri) {
            newMedia = false
            mediaExtras = null
        } else {
            mediaExtras = extras
            setNewMedia(uri)
        }
        onPlay()
        /*mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(
                    MediaMetadataCompat.METADATA_KEY_MEDIA_URI,
                    uri.toString()
                )
                .build()
        )*/
    }

    override fun onPlay() {
        super.onPlay()
        println("onPlay called")
        if (ensureAudioFocus()) {
            mediaSession.isActive = true
            initializeMediaPlayer()
            prepareMedia()
            startPlaying()
        }
    }

    override fun onStop() {
        super.onStop()
        println("onStop called")
        stopPlaying()
    }

    override fun onPause() {
        super.onPause()
        println("onPause called")
        pausePlaying()
    }

    private fun prepareMedia() {
        if (newMedia) {
            newMedia = false
            mediaPlayer?.let { mediaPlayer ->
                mediaUri?.let { mediaUri ->
                    if (mediaNeedsPrepare) {
                        mediaPlayer.reset()
                        mediaPlayer.setDataSource(context, mediaUri)
                        mediaPlayer.prepare()
                    }
                    mediaExtras?.let { mediaExtras ->
                        mediaSession.setMetadata(
                            MediaMetadataCompat.Builder()
                                .putString(
                                    MediaMetadataCompat.METADATA_KEY_TITLE,
                                    mediaExtras.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
                                )
                                .putString(
                                    MediaMetadataCompat.METADATA_KEY_ARTIST,
                                    mediaExtras.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
                                )
                                .putString(
                                    MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
                                    mediaExtras.getString(
                                        MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI
                                    )
                                )
                                .putLong(
                                    MediaMetadataCompat.METADATA_KEY_DURATION,
                                    mediaPlayer.duration.toLong()
                                )
                                .build()
                        )
                    }
                }
            }
        }
    }

    /*
    If it’s a new media item and the media player and media URI are valid,
    the media player state is reset, and the data source is set to the media item.
    Once the data source is set, then prepare is called.
    prepare() puts the MediaPlayer in an initialized state ready to play the media provided as the data source.
    **/
    private fun prepareMedia_old() {
        if (newMedia) {
            newMedia = false
            mediaPlayer?.let { mediaPlayer ->
                mediaUri?.let { mediaUri ->
                    mediaPlayer.reset()
                    mediaPlayer.setDataSource(context, mediaUri)
                    mediaPlayer.prepare()
                    mediaSession.setMetadata(
                        MediaMetadataCompat.Builder()
                            .putString(
                                MediaMetadataCompat.METADATA_KEY_MEDIA_URI,
                                mediaUri.toString()
                            )
                            .build()
                    )
                }
            }
        }
    }

    private fun initializeMediaPlayer() {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer()
            mediaPlayer?.setOnCompletionListener {
                setState(PlaybackStateCompat.STATE_PAUSED)
            }

            /*
            This sets mediaNeedsPrepare to true only if the mediaPlayer is created by PodplayMediaCallback.
            When playing back videos, the mediaPlayer is created by the EpisodePlayerFragment and passed into PodplayMediaCallback, so mediaNeedsPrepare will not be set to true.
            **/
            mediaNeedsPrepare = true
        }
    }

    private fun startPlaying() {
        mediaPlayer?.let { mediaPlayer ->
            if (!mediaPlayer.isPlaying) {
                mediaPlayer.start()
                setState(PlaybackStateCompat.STATE_PLAYING)
            }
        }
    }

    private fun pausePlaying() {
        removeAudioFocus()
        mediaPlayer?.let { mediaPlayer ->
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                setState(PlaybackStateCompat.STATE_PAUSED)
            }
        }
        listener?.onPausePlaying()
    }

    private fun stopPlaying() {
        removeAudioFocus()
        mediaSession.isActive = false
        mediaPlayer?.let { mediaPlayer ->
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
                setState(PlaybackStateCompat.STATE_STOPPED)
            }
        }
        listener?.onStopPlaying()
    }

    override fun onCommand(command: String?, extras: Bundle?, cb: ResultReceiver?) {
        super.onCommand(command, extras, cb)
        when (command) {
            CMD_CHANGESPEED -> extras?.let { changeSpeed(it) }
        }
    }

    /*
    When the speed is changed, you want to make sure the playback state (playing or paused) doesn’t change.
    This is accomplished by taking the current playback state and passing it into setState().
    playbackState is set to the current playback state if it is valid.
    If not, playbackState is set to the default state of STATE_PAUSED.
    You call setState() with playbackState and the new playback speed.
    * */
    private fun changeSpeed(extras: Bundle) {
        var playbackState = PlaybackStateCompat.STATE_PAUSED
        if (mediaSession.controller.playbackState != null) {
            playbackState = mediaSession.controller.playbackState.state
        }
        setState(playbackState, extras.getFloat(CMD_EXTRA_SPEED))
    }

    /*
    This is a helper method to set the current state of the media session.
    The media session state is configured with a PlaybackState object that provides a Builder to set all of the options.
    This takes a simple playback state such as STATE_PLAYING and uses it to construct the more complex PlaybackState object.
    setActions() specifies what states the media session will allow.
    * */
    private fun setState(state: Int, newSpeed: Float? = null) {
        var position: Long = -1
        mediaPlayer?.let {
            position = it.currentPosition.toLong()
        }

        var speed = 1.0f
        /*
        The MediaPlayer gained the ability to change the playback speed beginning with Android 6.0 (Marshmallow).
        If the version supports speed control, then the code block is executed.
        * */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (newSpeed == null) {
                speed = mediaPlayer?.getPlaybackParams()?.speed ?: 1.0f
            } else {
                speed = newSpeed
            }
            mediaPlayer?.let { mediaPlayer ->
                try {
                    mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(speed)
                } catch (e: Exception) {
                    mediaPlayer.reset()
                    mediaUri?.let { mediaUri ->
                        mediaPlayer.setDataSource(context, mediaUri)
                    }
                    mediaPlayer.prepare()
                    mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(speed)
                    mediaPlayer.seekTo(position.toInt())
                    if (state == PlaybackStateCompat.STATE_PLAYING) {
                        mediaPlayer.start()
                    }
                }
            }
        }

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_PAUSE
            )
            .setState(state, position, speed)
            .build()
        mediaSession.setPlaybackState(playbackState)
        if (state == PlaybackStateCompat.STATE_PAUSED ||
            state == PlaybackStateCompat.STATE_PLAYING
        ) {
            listener?.onStateChanged()
        }
    }

    private fun ensureAudioFocus(): Boolean {
        val audioManager = this.context.getSystemService(
            Context.AUDIO_SERVICE
        ) as AudioManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            /*
            If the version of Android is 8 (Android O) or newer, then an AudioFocusRequest object is generated using the AudioFocusRequest builder and stored in a local variable.
            The builder requires a single focusGain parameter, which is set to AUDIOFOCUS_GAIN.
            This tells Android that you want to gain audio focus and are about to start playing audio.
            A set of audio attributes are defined on the focus request to indicate that you are using media (USAGE_MEDIA) and the content type is music (CONTENT_TYPE_MUSIC).
            Other types of usage and content types can be set for different scenarios.
            * */
            val focusRequest =
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .run {
                        setAudioAttributes(AudioAttributes.Builder().run {
                            setUsage(AudioAttributes.USAGE_MEDIA)
                            setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            build()
                        })
                        build()
                    }
            this.focusRequest = focusRequest
            val result = audioManager.requestAudioFocus(focusRequest)
            return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            val result = audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    /*
    You’ll call this method any time you pause or stop audio playback.
    * */
    private fun removeAudioFocus() {
        val audioManager = this.context.getSystemService(
            Context.AUDIO_SERVICE
        ) as AudioManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
            }
        } else {
            audioManager.abandonAudioFocus(null)
        }
    }

    override fun onSeekTo(pos: Long) {
        super.onSeekTo(pos)
        mediaPlayer?.seekTo(pos.toInt())
        val playbackState: PlaybackStateCompat? =
            mediaSession.controller.playbackState
        if (playbackState != null) {
            setState(playbackState.state)
        } else {
            setState(PlaybackStateCompat.STATE_PAUSED)
        }
    }


    private fun setNewMedia(uri: Uri?) {
        newMedia = true
        mediaUri = uri
    }

    interface PodplayMediaListener {
        fun onStateChanged()
        fun onStopPlaying()
        fun onPausePlaying()
    }


}

