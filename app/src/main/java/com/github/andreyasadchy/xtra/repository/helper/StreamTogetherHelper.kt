package com.github.andreyasadchy.xtra.repository.helper

import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingSource.LoadResult
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.util.C
import kotlin.text.isNullOrBlank

object StreamTogetherHelper {

    suspend fun loadCollaborationsFromApi(
        gqlHeaders: Map<String, String>,
        graphQLRepository: GraphQLRepository,
        enableIntegrity: Boolean,
        networkLibrary: String?,
        streams: List<Stream>,
        api: String?
    ) {
        when (api) {
            C.GQL -> gqlQueryLoad(gqlHeaders, graphQLRepository, enableIntegrity, networkLibrary, streams)
            C.GQL_PERSISTED_QUERY -> gqlLoad(gqlHeaders, graphQLRepository, enableIntegrity, networkLibrary, streams)
            else -> try {
                gqlQueryLoad(gqlHeaders, graphQLRepository, enableIntegrity, networkLibrary, streams)
            } catch (e: Exception) {
                try {
                    gqlLoad(gqlHeaders, graphQLRepository, enableIntegrity, networkLibrary, streams)
                } catch (e: Exception) {
                    return
                }
            }
        }
    }

    private suspend fun gqlQueryLoad(gqlHeaders: Map<String, String>, graphQLRepository: GraphQLRepository, enableIntegrity: Boolean, networkLibrary: String?, streams: List<Stream>)
    {
        val collaborationResponse = graphQLRepository.loadQueryGuestStarBatchCollaboration(
            networkLibrary,
            gqlHeaders,
            streams.mapNotNull { it.channelId }
        )

        if (enableIntegrity) {
            collaborationResponse.errors?.find { it.message == "failed integrity check" }?.let { return }
        }

        val collaborations = collaborationResponse
            .data!!
            .guestStarCollaborationStatuses!!
            .channelCollabs!!
            .filter { (it?.session?.guests?.size ?: 0) > 1 }

        val streamsByChannel = streams.associateBy { it.channelId }

        collaborations.forEach { collab ->
            val guests = collab?.session?.guests
            val stream = streamsByChannel[collab?.id]

            if (stream != null && !guests.isNullOrEmpty()) {

                val guestsById = guests.associateBy { it?.user?.id }
                val broadcaster = guestsById[stream.channelId]

                stream.apply {
                    collaborationViewersCount =
                        broadcaster?.user?.stream?.collaborationViewersCount
                    collaborationGuests = guests.mapNotNull { guest ->
                        guest?.user?.takeIf { it.id != broadcaster?.user?.id }?.let { user ->
                            User(
                                channelId = user.id,
                                channelLogin = user.login,
                                channelName = user.displayName,
                                profileImageUrl = user.profileImageURL
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun gqlLoad(gqlHeaders: Map<String, String>, graphQLRepository: GraphQLRepository, enableIntegrity: Boolean, networkLibrary: String?, streams: List<Stream>)
    {
        val collaborationResponse = graphQLRepository.guestStarBatchCollaboration(
            networkLibrary,
            gqlHeaders,
            streams.mapNotNull { it.channelId }
        )

        if (enableIntegrity) {
            collaborationResponse.errors?.find { it.message == "failed integrity check" }
                ?.let { return }
        }

        val collaborations = collaborationResponse
            .data!!
            .guestStarCollaborationStatuses
            .channelCollabs
            .filter { (it.session?.guests?.size ?: 0) > 1 }

        val streamsByChannel = streams.associateBy { it.channelId }

        collaborations.forEach { collab ->
            val guests = collab.session?.guests
            val stream = streamsByChannel[collab.id]

            if (stream != null && !guests.isNullOrEmpty()) {

                val guestsById = guests.associateBy { it.user?.id }
                val broadcaster = guestsById[stream.channelId]

                stream.apply {
                    collaborationViewersCount =
                        broadcaster?.user?.stream?.collaborationViewersCount
                    collaborationGuests = guests.mapNotNull { guest ->
                        guest.user.takeIf { it.id != broadcaster?.user?.id }?.let { user ->
                            User(
                                channelId = user.id,
                                channelLogin = user.login,
                                channelName = user.displayName,
                                profileImageUrl = user.profileImageURL
                            )
                        }
                    }
                }
            }
        }
    }
    suspend fun getStreamsWithCollaborations(
        gqlHeaders: Map<String, String>,
        graphQLRepository: GraphQLRepository,
        enableIntegrity: Boolean,
        networkLibrary: String?,
        streams: List<Stream>
    ): List<Stream> {
        return try {
            val response = graphQLRepository.loadQueryGuestStarBatchCollaboration(
                networkLibrary,
                gqlHeaders,
                streams.mapNotNull { it.channelId })
            if (enableIntegrity) {
                response.errors?.find { it.message == "failed integrity check" }
                    ?.let { return streams }
            }

            val data = response.data!!.guestStarCollaborationStatuses!!.channelCollabs!!

            val streamsAssociateIds = streams.associateBy { it.channelId }

            val collaborations = data.filter { (it?.session?.guests?.size ?: 0) > 1 }

            collaborations.forEach { collaboration ->
                collaboration?.session?.guests?.let { guests ->
                    streamsAssociateIds[collaboration.id]?.let { stream ->
                        val broadcaster =
                            guests.first { guest -> guest?.user?.id == stream.channelId }
                        stream.collaborationViewersCount =
                            broadcaster?.user?.stream?.collaborationViewersCount
                        stream.collaborationGuests = guests.mapNotNull { guest ->
                            if (guest?.user?.id != broadcaster?.user?.id) {
                                User(
                                    channelId = guest?.user?.id,
                                    channelLogin = guest?.user?.login,
                                    channelName = guest?.user?.displayName,
                                    profileImageUrl = guest?.user?.profileImageURL
                                )
                            } else null
                        }
                    }
                }
            }
            streams
        } catch (e: Exception) {
            try {
                val response = graphQLRepository.guestStarBatchCollaboration(
                    networkLibrary,
                    gqlHeaders,
                    streams.mapNotNull { it.channelId })
                if (enableIntegrity) {
                    response.errors?.find { it.message == "failed integrity check" }
                        ?.let { return streams }
                }

                val data = response.data!!.guestStarCollaborationStatuses.channelCollabs

                val streamsAssociateIds = streams.associateBy { it.channelId }

                val collaborations = data.filter { (it.session?.guests?.size ?: 0) > 1 }

                collaborations.forEach { collaboration ->
                    collaboration.session?.guests?.let { guests ->
                        streamsAssociateIds[collaboration.id]?.let { stream ->
                            val broadcaster =
                                guests.first { guest -> guest.user.id == stream.channelId }
                            stream.collaborationViewersCount =
                                broadcaster.user.stream?.collaborationViewersCount
                            stream.collaborationGuests = guests.mapNotNull { guest ->
                                if (guest.user.id != broadcaster.user.id) {
                                    User(
                                        channelId = guest.user.id,
                                        channelLogin = guest.user.login,
                                        channelName = guest.user.displayName,
                                        profileImageUrl = guest.user.profileImageURL
                                    )
                                } else null
                            }
                        }
                    }
                }
                streams
            } catch (e: Exception) {
                streams
            }
        }
    }
}