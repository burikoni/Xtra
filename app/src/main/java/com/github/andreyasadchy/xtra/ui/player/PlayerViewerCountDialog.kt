package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.target
import coil3.request.transformations
import coil3.transform.CircleCropTransformation
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentStreamsListItemCompactBinding
import com.github.andreyasadchy.xtra.databinding.FragmentViewerCountBinding
import com.github.andreyasadchy.xtra.model.ui.ChannelViewerCountList
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.ui.common.IntegrityDialog
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.gone
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.visible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


@AndroidEntryPoint
class PlayerViewerCountDialog : BottomSheetDialogFragment(), IntegrityDialog.CallbackListener {

    companion object {

        private const val KEY_CHANNEL_ID = "channel_id"
        private const val KEY_CHANNEL_LOGIN = "channel_login"
        private const val KEY_COLLABORATION_GUESTS_COUNT = "collaborationGuestsCount"

        fun newInstance(id: String, login: String, collaborationGuestsCount: Int): PlayerViewerCountDialog {
            return PlayerViewerCountDialog().apply {
                arguments = bundleOf(
                    KEY_CHANNEL_ID to id,
                    KEY_CHANNEL_LOGIN to login,
                    KEY_COLLABORATION_GUESTS_COUNT to collaborationGuestsCount
                )
            }
        }
    }

    private var _binding: FragmentViewerCountBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PlayerViewerCountViewModel by viewModels()

    private val topCostreamersListItems = mutableListOf<User>()
    private var topCostreamersListOffset = 0

    private val collaborationGuestsListItems = mutableListOf<User>()
    private var collaborationGuestsListOffset = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentViewerCountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val behavior = BottomSheetBehavior.from(view.parent as View)
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        with(binding) {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.integrity.collectLatest {
                        if (it != null &&
                            it != "done" &&
                            requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false) &&
                            requireContext().prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true)
                        ) {
                            IntegrityDialog.show(childFragmentManager, it)
                            viewModel.integrity.value = "done"
                        }
                    }
                }
            }
            viewModel.loadViewerCount(
                requireArguments().getString(KEY_CHANNEL_ID),
                requireArguments().getString(KEY_CHANNEL_LOGIN),
                requireArguments().getInt(KEY_COLLABORATION_GUESTS_COUNT),
                requireContext().prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                TwitchApiHelper.getGQLHeaders(requireContext()),
                requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
            )
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.viewerCount.collectLatest { fullList ->
                        if (fullList != null) {
                            if (fullList.organizer != null) {
                                costreamingOrganizerText.visible()
                                costreamingOrganizerList.visible()
                                costreamingOrganizerList.adapter = Adapter(context, listOf(fullList.organizer))
                            } else {
                                costreamingOrganizerText.gone()
                                costreamingOrganizerList.gone()
                            }
                            if (fullList.topCostreamers.isNotEmpty()) {
                                costreamingCostreamerText.visible()
                                costreamingCostreamerList.apply {
                                    visible()
                                    adapter = Adapter(context, fullList.topCostreamers)
                                }
                                loadItems(fullList, costreamingCostreamerList)
                            } else {
                                costreamingCostreamerText.gone()
                                costreamingCostreamerList.gone()
                            }
                            if (fullList.collaborationGuests.isNotEmpty()) {
                                collaborationGuestsText.visible()
                                collaborationGuestsList.apply {
                                    visible()
                                    adapter = Adapter(context, fullList.collaborationGuests)
                                }
                                loadItems(fullList, collaborationGuestsList)
                            } else {
                                collaborationGuestsText.gone()
                                collaborationGuestsList.gone()
                            }
                            if (fullList.costreamersCount != null) {
                                userCount.visible()
                                userCount.text = requireContext().getString(R.string.user_count, TwitchApiHelper.formatCount(fullList.costreamersCount, requireContext().prefs().getBoolean(C.UI_TRUNCATEVIEWCOUNT, true)))
                            } else {
                                userCount.gone()
                            }
                            scrollView.viewTreeObserver.addOnScrollChangedListener {
                                if (!scrollView.canScrollVertically(1)) {
                                    when {
                                        topCostreamersListOffset != fullList.topCostreamers.size -> loadItems(fullList, costreamingCostreamerList)
                                        collaborationGuestsListOffset != fullList.collaborationGuests.size -> loadItems(fullList, collaborationGuestsList)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadItems(fullList: ChannelViewerCountList, recyclerView: RecyclerView) {
        with(binding) {
            when (recyclerView) {
                costreamingCostreamerList -> {
                    val remaining = fullList.topCostreamers.size - topCostreamersListOffset
                    val add = if (remaining > 100) { 100 } else { remaining }
                    topCostreamersListItems.addAll(fullList.topCostreamers.subList(topCostreamersListOffset, topCostreamersListOffset + add))
                    topCostreamersListOffset += add
                    costreamingCostreamerList.adapter?.let { it.notifyItemRangeChanged(it.itemCount - add, add) }
                }
                collaborationGuestsList -> {
                    val remaining = fullList.topCostreamers.size - collaborationGuestsListOffset
                    val add = if (remaining > 100) { 100 } else { remaining }
                    collaborationGuestsListItems.addAll(fullList.topCostreamers.subList(collaborationGuestsListOffset, collaborationGuestsListOffset + add))
                    collaborationGuestsListOffset += add
                    collaborationGuestsList.adapter?.let { it.notifyItemRangeChanged(it.itemCount - add, add) }
                }
                else -> {}
            }
        }
    }

    override fun onIntegrityDialogCallback(callback: String?) {
        if (callback == "refresh") {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.loadViewerCount(
                        requireArguments().getString(KEY_CHANNEL_ID),
                        requireArguments().getString(KEY_CHANNEL_LOGIN),
                        requireArguments().getInt(KEY_COLLABORATION_GUESTS_COUNT),
                        requireContext().prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                        TwitchApiHelper.getGQLHeaders(requireContext()),
                        requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class Adapter internal constructor(context: Context?, data: List<User>) : RecyclerView.Adapter<Adapter.ViewHolder>() {
        private val mData: List<User> = data
        private val mInflater: LayoutInflater = LayoutInflater.from(context)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = FragmentStreamsListItemCompactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = mData[position]
            holder.bind(item)
        }

        override fun getItemCount(): Int {
            return mData.size
        }

        inner class ViewHolder(
            private val binding: FragmentStreamsListItemCompactBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            val context: Context = binding.root.context

            fun bind(item: User?) {
                with(binding) {
                    if (item != null) {
                        if (item.channelLogo != null) {
                            userImage.visible()
                           context.imageLoader.enqueue(
                                ImageRequest.Builder(context).apply {
                                    data(item.channelLogo)
                                    if (context.prefs().getBoolean(C.UI_ROUNDUSERIMAGE, true)) {
                                        transformations(CircleCropTransformation())
                                    }
                                    crossfade(true)
                                    target(userImage)
                                }.build()
                            )
                        } else {
                            userImage.gone()
                        }
                        if (item.channelName != null) {
                            username.visible()
                            username.text = if (item.channelLogin != null && !item.channelLogin.equals(item.channelName, true)) {
                                when (context.prefs().getString(C.UI_NAME_DISPLAY, "0")) {
                                    "0" -> "${item.channelName}(${item.channelLogin})"
                                    "1" -> item.channelName
                                    else -> item.channelLogin
                                }
                            } else {
                                item.channelName
                            }
                        } else {
                            username.gone()
                        }
                        item.stream?.let { stream ->
                            if (stream.viewerCount != null) {
                                viewers.visible()
                                viewers.text = TwitchApiHelper.formatViewersCount(
                                    context,
                                    stream.viewerCount ?: 0,
                                    context.prefs().getBoolean(C.UI_TRUNCATEVIEWCOUNT, true)
                                )
                            } else {
                                viewers.gone()
                            }
                        }
                    }
                }
            }
        }
    }
}
