package com.github.andreyasadchy.xtra.model.chat

class GuestStarChannelSlot (
    val id: Int,
    val isLive: Boolean = false,
    val channelId: String? = null,
    val channelLogin: String? = null,
    val channelName: String? = null,
    var profileImageUrl: String? = null
)