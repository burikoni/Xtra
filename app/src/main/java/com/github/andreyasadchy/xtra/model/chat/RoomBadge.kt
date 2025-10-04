package com.github.andreyasadchy.xtra.model.chat

class RoomBadge (
    val id: String,
    val channelLogin: String,
    val channelName: String,
    private val profileImageUrl: String
) {
    // Available dimensions: 28x28, 70x70, 150x150, 300x300
    val url1x: String
        get() = profileImageUrl.replace("profile_image-70x70", "profile_image-28x28")
    val url2x: String
        get() = profileImageUrl
    val url3x: String
        get() = profileImageUrl
    val url4x: String
        get() = profileImageUrl.replace("profile_image-70x70", "profile_image-150x150")
}