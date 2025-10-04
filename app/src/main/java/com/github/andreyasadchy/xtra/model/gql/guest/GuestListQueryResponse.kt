package com.github.andreyasadchy.xtra.model.gql.guest

import com.github.andreyasadchy.xtra.model.gql.Error
import com.github.andreyasadchy.xtra.model.gql.followed.FollowedStreamsResponse.Stream
import kotlinx.serialization.Serializable

@Serializable
class GuestListQueryResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    class Data(
        val channel: Channel,
    )

    @Serializable
    class Channel(
        val id: String?,
        val guestStarSessionCall: GuestStarSessionCall
    )

    @Serializable
    class GuestStarSessionCall(
        val id: String?,
        val guests: List<Guests>
    )

    @Serializable
    class Guests(
        val id: String?,
        val user: User
    )

    @Serializable
    class User(
        val id: String,
        val login: String,
        val displayName: String,
        val profileImageURL: String
    )
}