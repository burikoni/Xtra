package com.github.andreyasadchy.xtra.model.ui

class ChannelViewerCountList(
    val costreamersCount: Int?,
    val organizer: User? = null,
    val topCostreamers: List<User>,
    val collaborationGuests: List<User>
)