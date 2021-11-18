package com.raywenderlich.rw_sec4_podplay.ui

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.raywenderlich.rw_sec4_podplay.R
import com.raywenderlich.rw_sec4_podplay.adapter.PodcastListAdapter
import com.raywenderlich.rw_sec4_podplay.databinding.ActivityPodcastBinding
import com.raywenderlich.rw_sec4_podplay.repository.ItunesRepo
import com.raywenderlich.rw_sec4_podplay.repository.PodcastRepo
import com.raywenderlich.rw_sec4_podplay.service.ItunesService
import com.raywenderlich.rw_sec4_podplay.viewmodel.PodcastViewModel
import com.raywenderlich.rw_sec4_podplay.viewmodel.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PodcastActivity : AppCompatActivity(), PodcastListAdapter.PodcastListAdapterListener {
    private lateinit var binding: ActivityPodcastBinding
    private val searchViewModel by viewModels<SearchViewModel>()
    private lateinit var podcastListAdapter: PodcastListAdapter
    private lateinit var searchMenuItem: MenuItem
    private val podcastViewModel by viewModels<PodcastViewModel>()

    companion object {
        private const val TAG_DETAILS_FRAGMENT = "DetailsFragment"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPodcastBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar()
        setupViewModels()
        updateControls()

        //This gets the saved Intent and passes it to the existing handleIntent() method
        //For not to lost data when rotate screen
        handleIntent(intent)

        addBackStackListener()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
    }

    private fun setupViewModels() {
        val service = ItunesService.instance
        searchViewModel.iTunesRepo = ItunesRepo(service)
        podcastViewModel.podcastRepo = PodcastRepo()
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
        return true
    }

    override fun onShowDetails(podcastSummaryViewData: SearchViewModel.PodcastSummaryViewData) {
        val feedUrl = podcastSummaryViewData.feedUrl ?: return

        showProgressBar()
        val podcast = podcastViewModel.getPodcast(podcastSummaryViewData)
        hideProgressBar()

        if (podcast != null) {
            showDetailsFragment()
        } else {
            showError("Error loading feed $feedUrl")
        }
    }

    private fun showDetailsFragment() {
        val podcastDetailsFragment = createPodcastDetailsFragment()

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
            .addToBackStack("DetailsFragment").commit()
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
    }

    private fun performSearch(term: String) {
        showProgressBar()
        GlobalScope.launch {
            val listPodcast = searchViewModel.searchPodcasts(term)
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

    private fun showProgressBar() {
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun hideProgressBar() {
        binding.progressBar.visibility = View.INVISIBLE
    }

    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(getString(R.string.ok_button), null)
            .create()
            .show()
    }


}