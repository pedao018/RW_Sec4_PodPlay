package com.raywenderlich.rw_sec4_podplay.ui

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.raywenderlich.rw_sec4_podplay.R
import com.raywenderlich.rw_sec4_podplay.adapter.EpisodeListAdapter
import com.raywenderlich.rw_sec4_podplay.databinding.FragmentPodcastDetailsBinding
import com.raywenderlich.rw_sec4_podplay.viewmodel.PodcastViewModel

class PodcastDetailsFragment : Fragment() {
    private lateinit var databinding: FragmentPodcastDetailsBinding
    private lateinit var episodeListAdapter: EpisodeListAdapter

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
                    episodeListAdapter = EpisodeListAdapter(viewData.episodes)
                    databinding.episodeRecyclerView.adapter = episodeListAdapter
                }
            })

    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_details, menu)
    }
}
