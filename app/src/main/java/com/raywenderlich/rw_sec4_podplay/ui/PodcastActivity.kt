package com.raywenderlich.rw_sec4_podplay.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.*
import com.raywenderlich.rw_sec4_podplay.R
import com.raywenderlich.rw_sec4_podplay.adapter.PodcastListAdapter
import com.raywenderlich.rw_sec4_podplay.databinding.ActivityPodcastBinding
import com.raywenderlich.rw_sec4_podplay.db.PodPlayDatabase
import com.raywenderlich.rw_sec4_podplay.repository.ItunesRepo
import com.raywenderlich.rw_sec4_podplay.repository.PodcastRepo
import com.raywenderlich.rw_sec4_podplay.service.ItunesService
import com.raywenderlich.rw_sec4_podplay.service.RssFeedService
import com.raywenderlich.rw_sec4_podplay.viewmodel.PodcastViewModel
import com.raywenderlich.rw_sec4_podplay.viewmodel.SearchViewModel
import com.raywenderlich.rw_sec4_podplay.worker.EpisodeUpdateWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class PodcastActivity : AppCompatActivity(), PodcastListAdapter.PodcastListAdapterListener,
    PodcastDetailsFragment.OnPodcastDetailsListener {
    private lateinit var binding: ActivityPodcastBinding
    private val searchViewModel by viewModels<SearchViewModel>()
    private lateinit var podcastListAdapter: PodcastListAdapter
    private lateinit var searchMenuItem: MenuItem
    private val podcastViewModel by viewModels<PodcastViewModel>()

    companion object {
        private const val TAG_DETAILS_FRAGMENT = "DetailsFragment"
        private const val TAG_EPISODE_UPDATE_JOB = "com.raywenderlich.podplay.episodes"
        private const val TAG_PLAYER_FRAGMENT = "PlayerFragment"

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPodcastBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar()
        setupViewModels()
        updateControls()
        setupPodcastListView()

        //This gets the saved Intent and passes it to the existing handleIntent() method
        //For not to lost data when rotate screen
        handleIntent(intent)

        addBackStackListener()
        scheduleJobs()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
    }

    private fun setupViewModels() {
        val service = ItunesService.instance
        val rssFeedService = RssFeedService.instance
        searchViewModel.iTunesRepo = ItunesRepo(service)
        podcastViewModel.podcastRepo = PodcastRepo(rssFeedService, podcastViewModel.podcastDao)
        createSubscription()
    }

    private fun updateControls() {
        binding.podcastRecyclerView.setHasFixedSize(true)
        val layoutManager = LinearLayoutManager(this)
        binding.podcastRecyclerView.layoutManager = layoutManager

        val dividerItemDecoration = DividerItemDecoration(
            binding.podcastRecyclerView.context, layoutManager.orientation
        )
        binding.podcastRecyclerView.addItemDecoration(dividerItemDecoration)

        podcastListAdapter = PodcastListAdapter(null, this, this)
        binding.podcastRecyclerView.adapter = podcastListAdapter
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_search, menu)

        searchMenuItem = menu.findItem(R.id.search_item)
        val searchView = searchMenuItem.actionView as SearchView
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName))
        if (supportFragmentManager.backStackEntryCount > 0)
            binding.podcastRecyclerView.visibility = View.INVISIBLE
        /*
        This ensures that the searchMenuItem remains hidden if podcastRecyclerView is not visible.
        You may be asking, “Why is this added to onCreateOptionsMenu()”?
        Great question! onCreateOptionsMenu() is called a second time when the Fragment is added.
        Even though you hid the searchMenuItem in showDetailsFragment(), it gets shown again when the menu is recreated.
        This is because you requested that the Fragment adds to the options menu, so Android recreates the menu from scratch when adding the Fragment.
        * */
        if (binding.podcastRecyclerView.visibility == View.INVISIBLE) {
            searchMenuItem.isVisible = false
        }

        //Để auto generate interface này: gõ object: MenuItem.OnActionExpandListener{}
        // -> trỏ chuột vào giữa {} vào chọn Code -> implement method
        searchMenuItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(p0: MenuItem?): Boolean {
                return true
            }

            override fun onMenuItemActionCollapse(p0: MenuItem?): Boolean {
                showSubscribedPodcasts()
                return true
            }
        })
        return true
    }

    override fun onShowDetails(podcastSummaryViewData: SearchViewModel.PodcastSummaryViewData) {
        podcastSummaryViewData.feedUrl?.let {
            showProgressBar()
            podcastViewModel.getPodcast(podcastSummaryViewData)
        }

        //Another way without subsribe live-data
        // , nhưng mình nghĩ nó chưa ổn, nó vẫn chạy hideProgressbar trước khi getPodcast load data xong
        /*
        In PodcastViewModel.kt, simply add the suspend keyword in front of the getPodcast() function.
        Then in PodcastActivity.kt update the body of onShowDetails() as follows:
        * */
        /*podcastSummaryViewData.feedUrl ?: return
        showProgressBar()
        podcastViewModel.viewModelScope.launch(context = Dispatchers.Main) {
            podcastViewModel.getPodcast(podcastSummaryViewData)
            hideProgressBar()
            showDetailsFragment()
        }*/
    }

    private fun createSubscription() {
        podcastViewModel.podcastLiveData.observe(this, {
            hideProgressBar()
            if (it != null) {
                showDetailsFragment()
            } else {
                showError("Error loading feed")
            }
        })
    }

    private fun showDetailsFragment() {
        var podcastDetailsFragment = supportFragmentManager
            .findFragmentByTag(TAG_DETAILS_FRAGMENT) as PodcastDetailsFragment?
        if (podcastDetailsFragment == null) {
            podcastDetailsFragment = PodcastDetailsFragment.newInstance()
            /*
            The fragment is added to the supportFragmentManager.
            The TAG_DETAILS_FRAGMENT constant you defined earlier is used to identify the fragment.
            addToBackStack() is used to make sure the back button works to close the fragment
            Adding the Fragment to the back stack is essential for proper app navigation.
            If you don’t add the call to addToBackStack(), then pressing the back button while the Fragment is displayed closes the app.
            * */
            supportFragmentManager.beginTransaction().add(
                R.id.podcastDetailsContainer,
                podcastDetailsFragment, TAG_DETAILS_FRAGMENT
            )
                .addToBackStack(TAG_DETAILS_FRAGMENT).commit()
        } else {
            //Replace là replace tất cả fragment hiện tại
            supportFragmentManager.beginTransaction().replace(
                R.id.podcastDetailsContainer,
                podcastDetailsFragment, TAG_DETAILS_FRAGMENT
            ).commit()
        }
    }

    private fun createPodcastDetailsFragment(): PodcastDetailsFragment {
        var podcastDetailsFragment = supportFragmentManager
            .findFragmentByTag(TAG_DETAILS_FRAGMENT) as PodcastDetailsFragment?

        if (podcastDetailsFragment == null) {
            podcastDetailsFragment = PodcastDetailsFragment.newInstance()
        }

        return podcastDetailsFragment
    }

    /*
    This method is called when the Intent is sent from the search widget.
    It calls setIntent() to make sure the new Intent is saved with the Activity.
    handleIntent() is called to perform the search.
    * */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)//For handleIntent(intent) in onCreate(), for not to lost data when rotate screen
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (Intent.ACTION_SEARCH == intent.action) {
            val query = intent.getStringExtra(SearchManager.QUERY) ?: return
            performSearch(query)
        }

        //Dành cho Notify
        val podcastFeedUrl = intent.getStringExtra(EpisodeUpdateWorker.EXTRA_FEED_URL)
        if (podcastFeedUrl != null) {
            podcastViewModel.viewModelScope.launch {
                podcastViewModel.setActivePodcast(podcastFeedUrl)
                //podcastSummaryViewData?.let { podcastSummaryView -> onShowDetails(podcastSummaryView) }
            }
        }
    }

    private fun performSearch(term: String) {
        showProgressBar()

        //Background thread
        GlobalScope.launch {
            val listPodcast = searchViewModel.searchPodcasts(term)
            //Main thread
            withContext(Dispatchers.Main) {
                podcastListAdapter.setSearchData(listPodcast)
                binding.toolbar.title = term
                hideProgressBar()
            }
        }
    }

    private fun addBackStackListener() {
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                binding.podcastRecyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun setupPodcastListView() {
        podcastViewModel.getPodcasts()?.observe(this, {
            if (it != null) {
                showSubscribedPodcasts()
            }
        })
    }

    private fun showSubscribedPodcasts() {
        val podcasts = podcastViewModel.getPodcasts()?.value
        if (podcasts != null) {
            binding.toolbar.title = getString(R.string.subscribed_podcasts)
            podcastListAdapter.setSearchData(podcasts)
        }
    }

    override fun onSubscribe() {
        podcastViewModel.saveActivePodcast()
        supportFragmentManager.popBackStack()
    }

    override fun onUnSubsribe() {
        podcastViewModel.deleteActivePodcast()
        supportFragmentManager.popBackStack()
    }

    //For Workmanager
    private fun scheduleJobs() {
        /*
        Create a list of constraints for the worker to run under.
        WorkManager will not execute your worker until the constraints are met.
        Constraints are constructed using the Constraints.Builder() function.
        In this case, the following constraints are used.
        setRequiredNetworkType(NetworkType.CONNECTED): Only execute the worker when the device is connected to a network.
        Other network types include UNMETERED, METERED, and NOT_REQUIRED.
        UNMETERED is useful if you don’t want the work to execute when connected to a cellular network.
        Note: Be aware that if you are experimenting with different options, setting this to NetworkType.
        UNMETERED may cause the work not to run on the emulator.
        * */
        val constraints: Constraints = Constraints.Builder().apply {
            setRequiredNetworkType(NetworkType.CONNECTED)
            /*
            setRequiresCharging(): Only execute the worker when the device is plugged into a power source.
            This will prevent the worker from draining battery life.
            * */
            setRequiresCharging(true)
        }.build()

        /*
        Create a new work request using PeriodicWorkRequestBuilder().
        This is one of two primary options for building work requests.
        The other option is OneTimeWorkRequestBuilder() and it is intended for one-time work requests.
        PeriodicWorkRequestBuilder() is for work that you want to be repeated at set intervals.
        There are several constructor variants for PeriodicWorkRequestBuilder().
        You are using a version that takes two parameters: The repeat interval and a time unit.
        WorkManager will run the work request once during the interval you specify.
        It can run at any time during the interval as long as the constraints are met.
        In this case, you are telling it to run once every hour.
        Many additional settings can be applied to PeriodicWorkRequestBuilder, such as an initial delay interval, and input data for the worker.
        The only setting applied in this case is setConstraints, which applies the constraints you defined in step 1.
        * */
        val request = PeriodicWorkRequestBuilder<EpisodeUpdateWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        /*
        Use enqueueUniquePeriodicWork() on an instance of the WorkManager to schedule the work request.
        The first parameter is a unique name for the work request.
        Only one work request will run at a time using the name you provide.
        The second parameter, ExistingPeriodicWorkPolicy.REPLACE, specifies that this should replace any existing work with the same name.
        The other option is ExistingPeriodicWorkPolicy.KEEP and it will allow an existing work request to keep running if there is already one with the same name.
        Using the REPLACE options is safer when testing different options as it will guarantee that your new options are applied.
        The last parameter is the work request to schedule.
        * */
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            TAG_EPISODE_UPDATE_JOB,
            ExistingPeriodicWorkPolicy.REPLACE, request
        )
    }

    override fun onShowEpisodePlayer(episodeViewData: PodcastViewModel.EpisodeViewData) {
        podcastViewModel.activeEpisodeViewData = episodeViewData
        showPlayerFragment()
    }

    private fun showPlayerFragment() {
        var episodePlayerFragment = supportFragmentManager
            .findFragmentByTag(TAG_PLAYER_FRAGMENT) as EpisodePlayerFragment?
        if (episodePlayerFragment == null) {
            episodePlayerFragment = EpisodePlayerFragment.newInstance()
            supportFragmentManager.beginTransaction().add(
                R.id.podcastDetailsContainer,
                episodePlayerFragment, TAG_PLAYER_FRAGMENT
            )
                .addToBackStack(TAG_PLAYER_FRAGMENT).commit()
        } else {
            //Replace là replace tất cả fragment hiện tại
            supportFragmentManager.beginTransaction().replace(
                R.id.podcastDetailsContainer,
                episodePlayerFragment, TAG_PLAYER_FRAGMENT
            ).commit()
        }
        binding.podcastRecyclerView.visibility = View.INVISIBLE
        searchMenuItem.isVisible = false
    }


    private fun createEpisodePlayerFragment(): EpisodePlayerFragment {
        var episodePlayerFragment =
            supportFragmentManager.findFragmentByTag(TAG_PLAYER_FRAGMENT) as
                    EpisodePlayerFragment?

        if (episodePlayerFragment == null) {
            episodePlayerFragment = EpisodePlayerFragment.newInstance()
        }

        return episodePlayerFragment
    }


    private fun showProgressBar() {
        binding.progressBar.visibility = View.VISIBLE
        disableUserInteraction()
    }

    private fun hideProgressBar() {
        binding.progressBar.visibility = View.INVISIBLE
        enableUserInteraction()
    }

    //Disable user touching the main window
    private fun disableUserInteraction() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )
    }

    //Enable user touching the main window
    private fun enableUserInteraction() {
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )
    }

    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(getString(R.string.ok_button), null)
            .create()
            .show()
    }

}