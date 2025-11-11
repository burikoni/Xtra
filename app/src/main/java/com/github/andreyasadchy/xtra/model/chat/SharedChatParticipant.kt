package com.github.andreyasadchy.xtra.model.chat

class SharedChatParticipant (
    val id: String,
    val channelId: String,
    val status: String? = null,
    val channelLogin: String,
    val channelName: String,
    val profileImageUrl: String,
    val channelPrimaryColorHex: String? = null,
)