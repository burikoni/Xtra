package com.github.andreyasadchy.xtra.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.ui.ChannelViewerCountList
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.helper.StreamTogetherHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewerCountViewModel @Inject constructor(
    private val graphQLRepository: GraphQLRepository,
) : ViewModel() {

    val integrity = MutableStateFlow<String?>(null)

    private val _viewerCount = MutableStateFlow<ChannelViewerCountList?>(null)
    val viewerCount: StateFlow<ChannelViewerCountList?> = _viewerCount
    private var isLoading = false

    fun loadViewerCount(channelId: String?, channelLogin: String?, collaborationGuestsCount: Int?, networkLibrary: String?, gqlHeaders: Map<String, String>, enableIntegrity: Boolean) {
        if (_viewerCount.value == null && !isLoading) {
            isLoading = true
            viewModelScope.launch {
                try {
                    val collaborationGuests = collaborationGuestsCount?.let {
                        if (it > 0) {

                            val streams = listOf(
                                Stream(
                                    channelId = channelId
                                )
                            )
                            val collaborationSession = StreamTogetherHelper.getStreamsWithCollaborations(
                                gqlHeaders,
                                graphQLRepository,
                                enableIntegrity,
                                networkLibrary,
                                streams
                            ).firstOrNull()
                            collaborationSession?.collaborationGuests
                            //listOf(collaborationSession?.user!!)
                        } else null
                    }

                    val response = graphQLRepository.loadQueryCostreamDetails(networkLibrary, gqlHeaders, channelId, channelLogin)
                    if (enableIntegrity && integrity.value == null) {
                        response.errors?.find { it.message == "failed integrity check" }?.let {
                            integrity.value = "refresh"
                            isLoading = false
                            return@launch
                        }
                    }
                    _viewerCount.value = response.data?.user?.stream?.costreamDetails?.let { response ->
                        ChannelViewerCountList(
                            costreamersCount = response.costreamersCount,
                            organizer = response.organizer?.let { user ->
                                User(
                                    channelId= user.id,
                                    channelLogin =  user.login,
                                    channelName = user.displayName,
                                    profileImageUrl = user.profileImageURL,
                                    stream = user.stream?.let { stream ->
                                        Stream(
                                            id = stream.id,
                                            viewerCount = stream.viewersCount
                                        )
                                    }
                                )
                            },
                            topCostreamers = if (response.role == "ORGANIZER") response.topCostreamers?.map { user ->
                                User(
                                    channelId= user.id,
                                    channelLogin =  user.login,
                                    channelName = user.displayName,
                                    profileImageUrl = user.profileImageURL,
                                    stream = user.stream?.let { stream ->
                                        Stream(
                                            id = stream.id,
                                            viewerCount = stream.viewersCount
                                        )
                                    }
                                )
                            } ?: emptyList() else emptyList(),
                            collaborationGuests = if (!collaborationGuests.isNullOrEmpty()) collaborationGuests else emptyList()
                        )
                    } ?: ChannelViewerCountList(
                        costreamersCount = null,
                        organizer = null,
                        topCostreamers = emptyList(),
                        collaborationGuests = if (!collaborationGuests.isNullOrEmpty()) collaborationGuests else emptyList()
                    )
                } catch (e: Exception) {

                } finally {
                    isLoading = false
                }
            }
        }
    }
}