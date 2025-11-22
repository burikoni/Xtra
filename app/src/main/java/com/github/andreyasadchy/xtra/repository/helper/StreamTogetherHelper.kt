package com.github.andreyasadchy.xtra.repository.helper

import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.repository.GraphQLRepository

object StreamTogetherHelper {
    suspend fun getStreamsWithCollaborations(
        gqlHeaders: Map<String, String>,
        graphQLRepository: GraphQLRepository,
        enableIntegrity: Boolean,
        networkLibrary: String?,
        streams: List<Stream>
    ): List<Stream> {
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
                    val broadcaster = guests.first { guest -> guest.user.id == stream.channelId }
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
        return streams
    }
}