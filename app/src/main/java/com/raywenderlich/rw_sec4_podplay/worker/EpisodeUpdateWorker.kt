package com.raywenderlich.rw_sec4_podplay.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raywenderlich.rw_sec4_podplay.R
import com.raywenderlich.rw_sec4_podplay.db.PodPlayDatabase
import com.raywenderlich.rw_sec4_podplay.repository.PodcastRepo
import com.raywenderlich.rw_sec4_podplay.service.RssFeedService
import com.raywenderlich.rw_sec4_podplay.ui.PodcastActivity
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class EpisodeUpdateWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    companion object {
        const val EPISODE_CHANNEL_ID = "podplay_episodes_channel"
        const val EXTRA_FEED_URL = "PodcastFeedUrl"

    }

    /*
    doWork(): This is where you perform the episode updating logic.
    WorkManager calls this method when it’s time for you to perform your work.
    Upon completion of your job logic, you must call Result.success(), Result.failure(), or Result.retry() to indicate that the job is finished.
    You should call success() if the task completes without issue, failure() if it could not be completed, and retry() if it should be retried.
    Note that this is a suspending function, meaning that it can be called from inside a coroutine to suspend execution, and it can also call other suspending functions.
    Behind the scenes, WorkManager will call doWork() from inside a coroutine.
    The code above is the minimum required to satisfy WorkManager.
    You’ll implement the actual update logic next and the doWork() function will be explained in more detail.
    * */
    override suspend fun doWork(): Result = coroutineScope {
        val job = async {
            val db = PodPlayDatabase.getInstance(applicationContext, this)
            val repo = PodcastRepo(RssFeedService.instance, db.podcastDao())
            val podcastUpdates = repo.updatePodcastEpisodes()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel()
            }
            podcastUpdates.forEachIndexed { index, podcastUpdate ->
                displayNotification(podcastUpdate, index)
            }
        }
        job.await()
        Result.success()
    }

    private fun displayNotification(podcastInfo: PodcastRepo.PodcastUpdateInfo, requestCode: Int) {
        /*
        The notification manager needs to know what content to display when the user taps the notification.
        You do this by providing a PendingIntent that points to the PodcastActivity.
        When the user taps the notification, the system uses the intent within the PendingIntent to launch the PodcastActivity.
        The podcast feedUrl is set as an extra on the intent, and you’ll use this information to display the podcast details screen.
        * */
        val contentIntent = Intent(applicationContext, PodcastActivity::class.java)
        contentIntent.putExtra(EXTRA_FEED_URL, podcastInfo.feedUrl)
        val pendingContentIntent =
            PendingIntent.getActivity(
                applicationContext, requestCode,
                contentIntent, PendingIntent.FLAG_UPDATE_CURRENT
            )

        /*
        The Notification is created with the following options:
        setSmallIcon(): Set to the PodPlay episode icon.
        setContentTitle(): This is the main title shown above the detailed text.
        setContentText(): This is the detailed text. It lets the user know the name of the podcast and the number of new episodes available.
        setNumber(): This tells Android the number of new items associated with this notification. In some cases, this number is shown to the right of the notification.
        setAutoCancel(): Setting this to true tells Android to clear the notification once the user taps on it.
        setContentIntent(): Sets the pending intent that was defined earlier.
        * */
        val notification =
            NotificationCompat
                .Builder(applicationContext, EPISODE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_episode_icon)
                .setContentTitle(
                    applicationContext.getString(
                        R.string.episode_notification_title
                    )
                )
                .setContentText(
                    applicationContext.getString(
                        R.string.episode_notification_text,
                        podcastInfo.newCount, podcastInfo.name
                    )
                )
                .setNumber(podcastInfo.newCount)
                .setAutoCancel(true)
                .setContentIntent(pendingContentIntent)
                .build()

        //The notification manager is retrieved using getSystemService
        val notificationManager = applicationContext
            .getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        /*
        The notification manager is instructed to notify the user with the notification object created by the builder.
        The first parameter defines a tag, and the second parameter is an id number.
        These two items combine to create a unique name for the notification.
        In this case, the podcast name is unique enough, so the id number is always 0.
        If notify() is called multiple times with the same tag and ID then it will replace any existing notification with the same tag and id.
        * */
        notificationManager.notify(podcastInfo.name, 0, notification)
    }


    /*
    Since notification channels are only supported in API 26 or newer,
    the RequiresApi annotation is used to notify the compiler that this method should only be called when running on API 26 or newer (in this case API 26 is the letter ‘O’ for ‘Oreo’, and therefore we use Build.VERSION_CODES.O).
    * */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        /*
        The notification manager is retrieved using applicationContext.getSystemService().
        You should never create the notification manager directly.
        * */
        val notificationManager = applicationContext
            .getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        //The notification manager is used to check if the channel already exists.
        if (notificationManager.getNotificationChannel(EPISODE_CHANNEL_ID) == null) {

            /*
            If the channel doesn’t exist, then a new NotificationChannel object is created with the name “Episodes”.
            The notification manager is instructed to create the channel.
            * */
            val channel = NotificationChannel(
                EPISODE_CHANNEL_ID,
                "Episodes", NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
    }
}
