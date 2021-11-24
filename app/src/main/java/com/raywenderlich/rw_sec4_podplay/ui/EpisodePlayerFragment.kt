package com.raywenderlich.rw_sec4_podplay.ui

import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.format.DateUtils
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import com.bumptech.glide.Glide
import com.raywenderlich.rw_sec4_podplay.databinding.FragmentEpisodePlayerBinding
import com.raywenderlich.rw_sec4_podplay.service.PodplayMediaCallback
import com.raywenderlich.rw_sec4_podplay.service.PodplayMediaCallback.Companion.CMD_CHANGESPEED
import com.raywenderlich.rw_sec4_podplay.service.PodplayMediaCallback.Companion.CMD_EXTRA_SPEED
import com.raywenderlich.rw_sec4_podplay.service.PodplayMediaService
import com.raywenderlich.rw_sec4_podplay.util.HtmlUtils
import com.raywenderlich.rw_sec4_podplay.viewmodel.PodcastViewModel

class EpisodePlayerFragment : Fragment() {

    private lateinit var databinding: FragmentEpisodePlayerBinding
    private val podcastViewModel: PodcastViewModel by activityViewModels()

    private lateinit var mediaBrowser: MediaBrowserCompat
    private var mediaControllerCallback: MediaControllerCallback? = null
    private var playerSpeed: Float = 1.0f
    private var episodeDuration: Long = 0
    private var draggingScrubber: Boolean = false
    private var progressAnimator: ValueAnimator? = null
    private var mediaSession: MediaSessionCompat? = null
    private var mediaPlayer: MediaPlayer? = null
    private var playOnPrepare: Boolean = false
    private var isVideo: Boolean = false

    companion object {
        fun newInstance(): EpisodePlayerFragment {
            return EpisodePlayerFragment()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isVideo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            podcastViewModel.activeEpisodeViewData?.isVideo ?: false
        } else {
            false
        }
        if (!isVideo)
            initMediaBrowser()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        databinding = FragmentEpisodePlayerBinding.inflate(inflater, container, false)
        return databinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupControls()
        if (isVideo) {
            initMediaSession()
            initVideoPlayer()
        }
        updateControls()
    }

    override fun onStart() {
        super.onStart()
        /*
        The media browser connection logic is only implemented if the media is not a video.
        The media browser should be connected when the Activity or Fragment is started
        First, check to see if the media browser is already connected.
        This happens when a configuration change occurs, such as a screen rotation.
        If it’s connected, then all that’s needed is to register the media controller.
        If it’s not connected, then you call connect() and delay the media controller registration until the connection is complete.
        * */
        if (!isVideo) {
            if (mediaBrowser.isConnected) {
                val fragmentActivity = activity as FragmentActivity
                if (MediaControllerCompat.getMediaController(fragmentActivity) == null) {
                    registerMediaController(mediaBrowser.sessionToken)
                }
                updateControlsFromController()
            } else {
                mediaBrowser.connect()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        progressAnimator?.cancel()

        /*
        The media controller callbacks should be unregistered when the Activity or Fragment is stopped.
        If the media controller is available and the mediaControllerCallback is not null, the media controller callbacks object is unregistered.
        * */
        val fragmentActivity = activity as FragmentActivity
        if (MediaControllerCompat.getMediaController(fragmentActivity) != null) {
            mediaControllerCallback?.let {
                MediaControllerCompat.getMediaController(fragmentActivity)
                    .unregisterCallback(it)
            }
        }

        /*
        Clearing the display surface is required on some versions of Android to prevent issues when the screen is rotated.
        * */
        if (isVideo)
            mediaPlayer?.setDisplay(null)
        /*
        You need to manually stop the playback when the fragment is exited
        If the Fragment is not stopping due to a configuration change, then stop the playback and release the media player.
        If the Fragment is stopped during a configuration change, such as a screen rotation, then the media player is not recreated.
        * */
        if (!fragmentActivity.isChangingConfigurations) {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    private fun updateControls() {
        databinding.episodeTitleTextView.text = podcastViewModel.activeEpisodeViewData?.title
        val htmlDesc = podcastViewModel.activeEpisodeViewData?.description ?: ""
        val descSpan = HtmlUtils.htmlToSpannable(htmlDesc)
        databinding.episodeDescTextView.text = descSpan
        databinding.episodeDescTextView.movementMethod = ScrollingMovementMethod()
        val fragmentActivity = activity as FragmentActivity
        Glide.with(fragmentActivity)
            .load(podcastViewModel.podcastLiveData.value?.imageUrl)
            .into(databinding.episodeImageView)

        val speedButtonText = "${playerSpeed}x"
        databinding.speedButton.text = speedButtonText
        mediaPlayer?.let {
            updateControlsFromController()
        }
        
        /*
        There’s one more change required to handle the playback controls properly when the screen is rotated
        If mediaPlayer is not null, then the controls are updated from the media controller state.
        * */
        mediaPlayer?.let {
            updateControlsFromController()
        }
    }

    //This method calls handleStateChange and updateControlsFromMetadata to make sure the controls match the playback state after a screen rotation.
    private fun updateControlsFromController() {
        val fragmentActivity = activity as FragmentActivity
        val controller = MediaControllerCompat.getMediaController(fragmentActivity)
        if (controller != null) {
            val metadata = controller.metadata
            if (metadata != null) {
                handleStateChange(
                    controller.playbackState.state,
                    controller.playbackState.position, playerSpeed
                )
                updateControlsFromMetadata(controller.metadata)
            }
        }
    }

    private fun setupControls() {
        databinding.playToggleButton.setOnClickListener {
            togglePlayPause()
        }
        databinding.forwardButton.setOnClickListener {
            seekBy(30)
        }
        databinding.replayButton.setOnClickListener {
            seekBy(-10)
        }

        databinding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                databinding.currentTimeTextView.text =
                    DateUtils.formatElapsedTime((progress / 1000).toLong())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                draggingScrubber = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                draggingScrubber = false
                val fragmentActivity = activity as FragmentActivity
                val controller = MediaControllerCompat.getMediaController(fragmentActivity)
                if (controller.playbackState != null) {
                    controller.transportControls.seekTo(seekBar.progress.toLong())
                } else {
                    seekBar.progress = 0
                }
            }
        })
    }

    /*
    This hides everything on the screen except the video controls.
    It sets the player controls background color to a 50% transparency level.
    * */
    private fun setupVideoUI() {
        databinding.episodeDescTextView.visibility = View.INVISIBLE
        databinding.headerView.visibility = View.INVISIBLE
        val activity = activity as AppCompatActivity
        activity.supportActionBar?.hide()
        databinding.playerControls.setBackgroundColor(Color.argb(255 / 2, 0, 0, 0))
    }

    private fun handleStateChange(state: Int, position: Long, speed: Float) {
        progressAnimator?.let {
            it.cancel()
            progressAnimator = null
        }

        val isPlaying = state == PlaybackStateCompat.STATE_PLAYING
        databinding.playToggleButton.isActivated = isPlaying

        /*
        This first checks to see if the device supports the speed setting.
        If it does, the onClickListener is set on the speed button.
        The listener calls changeSpeed() when the user taps the speed button.
        If the device does not support speed control, then the speed button is hidden.
        * */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            databinding.speedButton.setOnClickListener {
                changeSpeed()
            }
        } else {
            databinding.speedButton.visibility = View.INVISIBLE
        }

        val progress = position.toInt()
        databinding.seekBar.progress = progress
        val speedButtonText = "${playerSpeed}x"
        databinding.speedButton.text = speedButtonText

        if (isPlaying) {
            if (isVideo)
                setupVideoUI()
            animateScrubber(progress, speed)
        }
    }

    private fun togglePlayPause() {
        playOnPrepare = true
        val fragmentActivity = activity as FragmentActivity
        val controller = MediaControllerCompat.getMediaController(fragmentActivity)
        if (controller.playbackState != null) {
            if (controller.playbackState.state ==
                PlaybackStateCompat.STATE_PLAYING
            ) {
                controller.transportControls.pause()
            } else {
                podcastViewModel.activeEpisodeViewData?.let { startPlaying(it) }
            }
        } else {
            podcastViewModel.activeEpisodeViewData?.let { startPlaying(it) }
        }
    }

    private fun startPlaying(episodeViewData: PodcastViewModel.EpisodeViewData) {
        val fragmentActivity = activity as FragmentActivity
        val controller = MediaControllerCompat.getMediaController(fragmentActivity)

        /*controller.transportControls.playFromUri(
            Uri.parse(episodeViewData.mediaUrl), null
        )*/
        val viewData = podcastViewModel.podcastLiveData ?: return
        val bundle = Bundle()
        bundle.putString(
            MediaMetadataCompat.METADATA_KEY_TITLE,
            episodeViewData.title
        )
        bundle.putString(
            MediaMetadataCompat.METADATA_KEY_ARTIST,
            viewData.value?.feedTitle
        )
        bundle.putString(
            MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
            viewData.value?.imageUrl
        )
        controller.transportControls.playFromUri(
            Uri.parse(episodeViewData.mediaUrl), bundle
        )

    }

    private fun changeSpeed() {
        playerSpeed += 0.25f
        if (playerSpeed > 2.0f) {
            playerSpeed = 0.75f
        }

        val bundle = Bundle()
        bundle.putFloat(CMD_EXTRA_SPEED, playerSpeed)
        val fragmentActivity = activity as FragmentActivity
        val controller = MediaControllerCompat.getMediaController(fragmentActivity)
        controller.sendCommand(CMD_CHANGESPEED, bundle, null)
        val speedButtonText = "${playerSpeed}x"
        databinding.speedButton.text = speedButtonText
    }

    private fun seekBy(seconds: Int) {
        val fragmentActivity = activity as FragmentActivity
        val controller = MediaControllerCompat.getMediaController(fragmentActivity)
        val newPosition = controller.playbackState.position + seconds * 1000
        controller.transportControls.seekTo(newPosition)
    }

    private fun updateControlsFromMetadata(metadata: MediaMetadataCompat) {
        episodeDuration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
        //DateUtils.formatElapsedTime() takes the time in seconds and returns a formatted time string as hours:minutes:seconds.
        databinding.endTimeTextView.text = DateUtils.formatElapsedTime((episodeDuration / 1000))

        /*
        This sets the range of the scrubber seekBar to match the episode duration.
        This lets you set the progress value on the seekBar directly to the playback position in milliseconds, and it places the progress indicator at the correct position.
        * */
        databinding.seekBar.max = episodeDuration.toInt()
    }

    private fun animateScrubber(progress: Int, speed: Float) {
        val timeRemaining = ((episodeDuration - progress) / speed).toInt()
        if (timeRemaining < 0) {
            return;
        }
        progressAnimator = ValueAnimator.ofInt(
            progress, episodeDuration.toInt()
        )
        progressAnimator?.let { animator ->
            animator.duration = timeRemaining.toLong()
            animator.interpolator = LinearInterpolator()
            animator.addUpdateListener {
                if (draggingScrubber) {
                    animator.cancel()
                } else {
                    databinding.seekBar.progress = animator.animatedValue as Int
                }
            }
            // 10
            animator.start()
        }
    }

    private fun initMediaBrowser() {
        val fragmentActivity = activity as FragmentActivity
        mediaBrowser = MediaBrowserCompat(
            fragmentActivity, //context: the current Activity hosting the Fragment.
            ComponentName(
                fragmentActivity,
                PodplayMediaService::class.java
            ), //serviceComponent: this tells the media browser that it should connect to the PodplayMediaService service.
            MediaBrowserCallBacks(), //callback: the callback object to receive connection events.
            null //rootHints: optional service-specific hints to pass along as a Bundle object.
        )
    }

    private fun initVideoPlayer() {
        databinding.videoSurfaceView.visibility = View.VISIBLE
        val surfaceHolder = databinding.videoSurfaceView.holder
        surfaceHolder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                initMediaPlayer()
                mediaPlayer?.setDisplay(holder)
            }

            override fun surfaceChanged(
                var1: SurfaceHolder, var2: Int,
                var3: Int, var4: Int
            ) {
            }

            override fun surfaceDestroyed(var1: SurfaceHolder) {
            }
        })
    }


    private fun initMediaPlayer() {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer()
            mediaPlayer?.let { mediaPlayer ->
                /*
                AudioAttributes define the behavior of audio playback.
                setUsage defines what is the sound you are playing is used for, i.e the reason why you are playing it.
                For example, another usage type is USAGE_ALARM.
                setContentType defines what you are playing.
                The content-type expresses the general category of the content.
                This information is optional.
                But in case it is known (for instance CONTENT_TYPE_MOVIE for a movie streaming service or CONTENT_TYPE_MUSIC for a music playback application) this information might be used by the audio framework to selectively configure some audio post-processing blocks.
                There is a third optional attribute type, flags, which can affect how the playback is affected by the system.
                * */
                mediaPlayer.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                mediaPlayer.setDataSource(podcastViewModel.activeEpisodeViewData?.mediaUrl)
                mediaPlayer.setOnPreparedListener {
                    /*
                    Once the media is ready, the PodplayMediaCallback object is created and assigned as the callback on the current media session.
                    * */
                    val fragmentActivity = activity as FragmentActivity
                    mediaSession?.let { mediaSession ->
                        val episodeMediaCallback =
                            PodplayMediaCallback(fragmentActivity, mediaSession, it)
                        mediaSession.setCallback(episodeMediaCallback)
                    }
                    setSurfaceSize()

                    /*
                    If playOnPrepare is true, indicating that the user has already tapped the play button, then the video is started.
                    * */
                    if (playOnPrepare) {
                        togglePlayPause()
                    }
                }
                mediaPlayer.prepareAsync()
            }
        } else {
            /*
            If the media player is not null, then you only need to set the video surface size.
            This happens if there’s a configuration change, such as a screen rotation.
            * */
            setSurfaceSize()
        }
    }


    private fun initMediaSession() {
        if (mediaSession == null) {
            //Create a media session if it does not already exist.
            mediaSession = MediaSessionCompat(
                activity as Context,
                "EpisodePlayerFragment"
            )
            //Set the media button receiver to null so that media buttons are ignored if the app is not in the foreground.
            mediaSession?.setMediaButtonReceiver(null)
        }
        mediaSession?.let {
            registerMediaController(it.sessionToken)
        }
    }

    private fun setSurfaceSize() {
        val mediaPlayer = mediaPlayer ?: return
        val videoWidth = mediaPlayer.videoWidth
        val videoHeight = mediaPlayer.videoHeight
        val parent = databinding.videoSurfaceView.parent as View
        val containerWidth = parent.width
        val containerHeight = parent.height
        val layoutAspectRatio = containerWidth.toFloat() /
                containerHeight
        val videoAspectRatio = videoWidth.toFloat() / videoHeight
        val layoutParams = databinding.videoSurfaceView.layoutParams

        /*
         If the video ratio is larger than the surface view layout ratio, then the surface view layout width is retained
         , and the height is shrunk to keep the video aspect ratio.
         If the video ratio is smaller than the surface view layout ratio, then the surface view layout height is retained
         , and the width is shrunk to keep the video aspect ratio.
        * */
        if (videoAspectRatio > layoutAspectRatio) {
            layoutParams.height =
                (containerWidth / videoAspectRatio).toInt()
        } else {
            layoutParams.width =
                (containerHeight * videoAspectRatio).toInt()
        }
        databinding.videoSurfaceView.layoutParams = layoutParams
    }

    inner class MediaBrowserCallBacks : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            super.onConnected()
            registerMediaController(mediaBrowser.sessionToken)
            updateControlsFromController()
            println("onConnected")
        }

        override fun onConnectionSuspended() {
            super.onConnectionSuspended()
            println("onConnectionSuspended")
            // Disable transport controls
        }

        override fun onConnectionFailed() {
            super.onConnectionFailed()
            println("onConnectionFailed")
            // Fatal error handling
        }
    }

    private fun registerMediaController(token: MediaSessionCompat.Token) {
        val fragmentActivity = activity as FragmentActivity
        val mediaController = MediaControllerCompat(fragmentActivity, token)
        MediaControllerCompat.setMediaController(fragmentActivity, mediaController)
        mediaControllerCallback = MediaControllerCallback()
        mediaController.registerCallback(mediaControllerCallback!!)
    }

    inner class MediaControllerCallback : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            super.onMetadataChanged(metadata)
            println(
                "metadata changed to ${
                    metadata?.getString(
                        MediaMetadataCompat.METADATA_KEY_MEDIA_URI
                    )
                }"
            )
            metadata?.let { updateControlsFromMetadata(it) }
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            super.onPlaybackStateChanged(state)
            println("state changed to $state")
            val state = state ?: return
            handleStateChange(state.state, state.position, state.playbackSpeed)
        }
    }
}
