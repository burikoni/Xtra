package com.github.andreyasadchy.xtra.model.gql.together

import com.github.andreyasadchy.xtra.model.gql.Error
import kotlinx.serialization.Serializable

@Serializable
class GuestStartBatchCollaborationResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    class Data(
        val guestStarCollaborationStatuses: CollaborationStatuses,
    )

    @Serializable
    class CollaborationStatuses(
        val channelCollabs: List<ChannelCollaboration>
    )

    @Serializable
    class ChannelCollaboration(
        val id: String?,
        val session: CollaborationSession? = null
    )

    @Serializable
    class CollaborationSession(
        val id: String?,
        val host: Host?,
        val guests: List<Guest>? = null
    )

    @Serializable
    class Host(
        val id: String?
    )
    @Serializable
    class Guest(
        val id: String?,
        val user: User
    )

    @Serializable
    class User (
        val id: String?,
        val login: String?,
        val displayName: String?,
        val profileImageURL: String?,
        val stream: Stream?
    )

    @Serializable
    class Stream(
        val id: String?,
        val collaborationViewersCount: Int? = null,
        val viewersCount: Int? = null,
    )
}