package com.raywenderlich.rw_sec4_podplay.ui

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.method.ScrollingMovementMethod
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.raywenderlich.rw_sec4_podplay.R
import com.raywenderlich.rw_sec4_podplay.adapter.EpisodeListAdapter
import com.raywenderlich.rw_sec4_podplay.databinding.FragmentPodcastDetailsBinding
import com.raywenderlich.rw_sec4_podplay.service.PodplayMediaService
import com.raywenderlich.rw_sec4_podplay.viewmodel.PodcastViewModel
import java.lang.RuntimeException

class PodcastDetailsFragment : Fragment(), EpisodeListAdapter.EpisodeListAdapterListener {
    private lateinit var databinding: FragmentPodcastDetailsBinding
    private lateinit var episodeListAdapter: EpisodeListAdapter
    private var listener: OnPodcastDetailsListener? = null

    /*
    activitytViewModels() is an extension function that allows the fragment to access and share view models from the fragment’s parent activity.
    In previous chapters, you used different techniques to communicate between Activities and Fragments.
    Using activitytViewModels() provides a convenient means to use shared view model data as the communication mechanism between a Fragment and its host Activity.
    activityViewModels() provides the same instance of the PodcastViewModel that was created in PodcastActivity.
    When the fragment is attached to the activity it will automatically assign podcastViewModel to the already initialized parent activity’s podcastViewModel.

    Note: The usage here illustrates a key benefit of using view models.
    You can seamlessly share view models with any Fragments managed by the Activity.
    View models can also survive configuration changes, so you don’t need to create them again when the screen rotates.
    * */
    private val podcastViewModel: PodcastViewModel by activityViewModels()

    companion object {
        fun newInstance(): PodcastDetailsFragment {
            return PodcastDetailsFragment()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        databinding = FragmentPodcastDetailsBinding.inflate(inflater, container, false)
        return databinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        podcastViewModel.podcastLiveData.observe(viewLifecycleOwner,
            { viewData ->
                if (viewData != null) {
                    databinding.feedTitleTextView.text = viewData.feedTitle
                    databinding.feedDescTextView.text = viewData.feedDesc
                    activity?.let { activity ->
                        Glide.with(activity).load(viewData.imageUrl).into(databinding.feedImageView)
                    }

                    //This allows the feed title to scroll if it gets too long for its container.
                    databinding.feedDescTextView.movementMethod = ScrollingMovementMethod()

                    databinding.episodeRecyclerView.setHasFixedSize(true)
                    val layoutManager = LinearLayoutManager(activity)
                    databinding.episodeRecyclerView.layoutManager = layoutManager
                    val dividerItemDecoration = DividerItemDecoration(
                        databinding.episodeRecyclerView.context, layoutManager.orientation
                    )
                    databinding.episodeRecyclerView.addItemDecoration(dividerItemDecoration)
                    episodeListAdapter = EpisodeListAdapter(viewData.episodes, this)
                    databinding.episodeRecyclerView.adapter = episodeListAdapter

                    activity?.invalidateOptionsMenu()
                }
            })
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_details, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        podcastViewModel.podcastLiveData.observe(viewLifecycleOwner,
            { podcastViewData ->
                if (podcastViewData != null) {
                    menu.findItem(R.id.menu_feed_action).title =
                        if (podcastViewData.subscribed)
                            getString(R.string.unsubscribe)
                        else
                            getString(R.string.subscribe)
                }
            })
        super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_feed_action -> {
                podcastViewModel.podcastLiveData.value?.feedUrl?.let {
                    if (item.title == getString(R.string.subscribe))
                        listener?.onSubscribe()
                    else
                        listener?.onUnSubsribe()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /*
    The property holds a reference to the listener. onAttach() is called by the FragmentManager when the fragment is attached to its parent activity.
    The context argument is a reference to the parent Activity.
    If the Activity implements the OnPodcastDetailsListener interface, then you assign the listener property to it.
    If it doesn’t implement the interface, then an exception is thrown.
    * */
    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnPodcastDetailsListener) {
            listener = context
        } else {
            throw RuntimeException(
                context.toString() +
                        " must implement OnPodcastDetailsListener"
            )
        }
    }

    interface OnPodcastDetailsListener {
        fun onSubscribe()
        fun onUnSubsribe()
        fun onShowEpisodePlayer(episodeViewData: PodcastViewModel.EpisodeViewData)
    }

    override fun onStart() {
        super.onStart()

    }

    override fun onStop() {
        super.onStop()

    }

    override fun onSelectedEpisode(episodeViewData: PodcastViewModel.EpisodeViewData) {
        listener?.onShowEpisodePlayer(episodeViewData)

        /*val fragmentActivity = activity as FragmentActivity
        val controller = MediaControllerCompat.getMediaController(fragmentActivity)
        if (controller.playbackState != null) {
            if (controller.playbackState.state == PlaybackStateCompat.STATE_PLAYING) {
                controller.transportControls.pause()
            } else {
                startPlaying(episodeViewData)
            }
        } else {
            startPlaying(episodeViewData)
        }*/
    }




}
