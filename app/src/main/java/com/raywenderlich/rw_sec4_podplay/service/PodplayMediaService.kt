package com.raywenderlich.rw_sec4_podplay.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.raywenderlich.rw_sec4_podplay.R
import com.raywenderlich.rw_sec4_podplay.ui.PodcastActivity
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.net.URL

class PodplayMediaService : MediaBrowserServiceCompat(),
    PodplayMediaCallback.PodplayMediaListener {

    private lateinit var mediaSession: MediaSessionCompat

    companion object {
        private const val PODPLAY_EMPTY_ROOT_MEDIA_ID =
            "podplay_empty_root_media_id"
        private const val PLAYER_CHANNEL_ID = "podplay_player_channel"
        private const val NOTIFICATION_ID = 1

    }

    override fun onCreate() {
        super.onCreate()
        createMediaSession()
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        if (parentId.equals(PODPLAY_EMPTY_ROOT_MEDIA_ID)) {
            result.sendResult(null)
        }
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int, rootHints: Bundle?
    ): BrowserRoot? {
        return BrowserRoot(
            PODPLAY_EMPTY_ROOT_MEDIA_ID, null
        )
    }

    private fun createMediaSession() {
        mediaSession = MediaSessionCompat(this, "PodplayMediaServiceAhihi")
        setSessionToken(mediaSession.sessionToken)
        val callback = PodplayMediaCallback(this, mediaSession)
        callback.listener = this
        mediaSession.setCallback(callback)

    }

    override fun onStateChanged() {
        displayNotification()
    }

    override fun onStopPlaying() {
        stopSelf()
        stopForeground(true)
    }

    override fun onPausePlaying() {
        stopForeground(false)
    }

    /*
    Stop the playback if the user dismisses the app from the recent applications list
    onTaskRemoved() is called if the user swipes away the app in the recent apps list.
    This stops the playback and removes the service.
    This is all you would need if running on API 21 or higher.
    For versions before API 21, you have to use a built-in broadcast receiver to get button events from the notification
    * */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        mediaSession.controller.transportControls.stop()
    }

    private fun displayNotification() {
        if (mediaSession.controller.metadata == null) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
        val mediaDescription =
            mediaSession.controller.metadata.description
        GlobalScope.launch {
            val iconUrl = URL(mediaDescription.iconUri.toString())
            val bitmap = BitmapFactory.decodeStream(iconUrl.openStream())
            val notification = createNotification(mediaDescription, bitmap)
            ContextCompat.startForegroundService(
                this@PodplayMediaService,
                Intent(
                    this@PodplayMediaService,
                    PodplayMediaService::class.java
                )
            )
            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }


    private fun createNotification(
        mediaDescription: MediaDescriptionCompat,
        bitmap: Bitmap?
    ): Notification {
        val notificationIntent = getNotificationIntent()
        val (pauseAction, playAction) = getPausePlayActions()
        val notification = NotificationCompat.Builder(
            this@PodplayMediaService, PLAYER_CHANNEL_ID
        )

        /*
        The builder is used to create the details of the notification.
        setContentTitle: sets the main title on the notification from the media description title.
        setContentText: sets the content text on the notification from the media description subtitle.
        setLargeIcon: sets the icon (album art) to display on the notification.
        setContentIntent: set the content Intent, so PodPlay is launched when the notification is tapped.
        setDeleteIntent: send an ACTION_STOP command to the service if the user swipes away the notification.
        setVisibility: make sure the transport controls are visible on the lock screen.
        setSmallIcon: set the icon to display in the status bar.
        addAction: add either the play or pause action based on the current playback state.

        setStyle: uses the special MediaStyle to create a style that is designed to display up to five transport control buttons in the expanded view.
        The following items are used to control how the MediaStyle behaves:
        setStyle.setMediaSession: indicates that this is an active media session. The system uses this as a flag to activate special features such as showing album artwork and playback controls on the lock screen.
        setStyle.setShowActionsInCompactView: Indicates which action buttons to display in compact view mode. This takes up to three index numbers to specify the order of the controls.
        setStyle.setShowCancelButton: Displays a cancel button on versions of Android before Lollipop (API 21).
        setStyle.setCancelButtonIntent(): Pending Intent to use when the cancel button is tapped.
        * */
        notification
            .setContentTitle(mediaDescription.title)
            .setContentText(mediaDescription.subtitle)
            .setLargeIcon(bitmap)
            .setContentIntent(notificationIntent)
            .setDeleteIntent(
                MediaButtonReceiver.buildMediaButtonPendingIntent
                    (this, PlaybackStateCompat.ACTION_STOP)
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSmallIcon(R.drawable.ic_episode_icon)
            .addAction(if (isPlaying()) pauseAction else playAction)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                            this, PlaybackStateCompat.ACTION_STOP
                        )
                    )
            )
        return notification.build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.getNotificationChannel
                (PLAYER_CHANNEL_ID) == null
        ) {
            val channel = NotificationChannel(
                PLAYER_CHANNEL_ID,
                "Player", NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getNotificationIntent(): PendingIntent {
        val openActivityIntent = Intent(this, PodcastActivity::class.java)
        openActivityIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        return PendingIntent.getActivity(
            this@PodplayMediaService, 0, openActivityIntent,
            PendingIntent.FLAG_CANCEL_CURRENT
        )
    }

    private fun getPausePlayActions():
            Pair<NotificationCompat.Action, NotificationCompat.Action> {
        val pauseAction = NotificationCompat.Action(
            R.drawable.ic_pause_white, getString(R.string.pause),
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                this,
                PlaybackStateCompat.ACTION_PAUSE
            )
        )

        val playAction = NotificationCompat.Action(
            R.drawable.ic_play_arrow_white, getString(R.string.play),
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                this,
                PlaybackStateCompat.ACTION_PLAY
            )
        )
        return Pair(pauseAction, playAction)
    }

    private fun isPlaying() =
        mediaSession.controller.playbackState != null &&
                mediaSession.controller.playbackState.state ==
                PlaybackStateCompat.STATE_PLAYING
}
