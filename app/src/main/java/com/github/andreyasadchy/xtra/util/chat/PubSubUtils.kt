package com.github.andreyasadchy.xtra.util.chat

import android.util.Log
import com.github.andreyasadchy.xtra.model.chat.ChannelPointReward
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.GuestStarChannelSlot
import com.github.andreyasadchy.xtra.model.chat.Poll
import com.github.andreyasadchy.xtra.model.chat.Prediction
import com.github.andreyasadchy.xtra.model.chat.Raid
import com.github.andreyasadchy.xtra.model.chat.SharedChatParticipant
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import org.json.JSONObject

object PubSubUtils {
    fun parsePlaybackMessage(message: JSONObject): PlaybackMessage? {
        val messageType = message.optString("type")
        return when {
            messageType.startsWith("viewcount") -> PlaybackMessage(
                viewers = if (!message.isNull("viewers")) message.optInt("viewers") else null,
                collaborationViewers = if (!message.isNull("collaboration_viewers")
                    && message.optInt("collaboration_viewers") > 0)
                    message.optInt("collaboration_viewers") else null,
                costreamViewers = if(!message.isNull("costream_viewers")
                    && message.optInt("costream_viewers") > 0)
                    message.optInt("costream_viewers") else null,
                costreamStatus = if(!message.isNull("costream_status")) message.optString("costream_status") else null,
            )
            messageType.startsWith("stream-up") -> PlaybackMessage(true, if (!message.isNull("server_time")) message.optLong("server_time").takeIf { it > 0 } else null)
            messageType.startsWith("stream-down") -> PlaybackMessage(false)
            else -> null
        }
    }

    fun parseStreamInfo(message: JSONObject): StreamInfo {
        return StreamInfo(
            title = if (!message.isNull("status")) message.optString("status").takeIf { it.isNotBlank() } else null,
            gameId = if (!message.isNull("game_id")) message.optInt("game_id").takeIf { it > 0 }?.toString() else null,
            gameName = if (!message.isNull("game")) message.optString("game").takeIf { it.isNotBlank() } else null,
        )
    }

    fun parseRewardMessage(message: JSONObject): ChatMessage {
        val messageData = message.optJSONObject("data")
        val redemption = messageData?.optJSONObject("redemption")
        val user = redemption?.optJSONObject("user")
        val reward = redemption?.optJSONObject("reward")
        val rewardImage = reward?.optJSONObject("image")
        val defaultImage = reward?.optJSONObject("default_image")
        val input = if (redemption?.isNull("user_input") == false) redemption.optString("user_input").takeIf { it.isNotBlank() } else null
        return ChatMessage(
            userId = if (user?.isNull("id") == false) user.optString("id").takeIf { it.isNotBlank() } else null,
            userLogin = if (user?.isNull("login") == false) user.optString("login").takeIf { it.isNotBlank() } else null,
            userName = if (user?.isNull("display_name") == false) user.optString("display_name").takeIf { it.isNotBlank() } else null,
            message = input,
            reward = ChannelPointReward(
                id = if (reward?.isNull("id") == false) reward.optString("id").takeIf { it.isNotBlank() } else null,
                title = if (reward?.isNull("title") == false) reward.optString("title").takeIf { it.isNotBlank() } else null,
                cost = if (reward?.isNull("cost") == false) reward.optInt("cost") else null,
                url1x = if (rewardImage?.isNull("url_1x") == false) { rewardImage.optString("url_1x").takeIf { it.isNotBlank() } } else { null }
                    ?: if (defaultImage?.isNull("url_1x") == false) defaultImage.optString("url_1x").takeIf { it.isNotBlank() } else null,
                url2x = if (rewardImage?.isNull("url_2x") == false) { rewardImage.optString("url_2x").takeIf { it.isNotBlank() } } else { null }
                    ?: if (defaultImage?.isNull("url_2x") == false) defaultImage.optString("url_2x").takeIf { it.isNotBlank() } else null,
                url4x = if (rewardImage?.isNull("url_4x") == false) { rewardImage.optString("url_4x").takeIf { it.isNotBlank() } } else { null }
                    ?: if (defaultImage?.isNull("url_4x") == false) defaultImage.optString("url_4x").takeIf { it.isNotBlank() } else null,
            ),
            timestamp = if (messageData?.isNull("timestamp") == false) messageData.optString("timestamp").takeIf { it.isNotBlank() }?.let { TwitchApiHelper.parseIso8601DateUTC(it) } else null,
            fullMsg = message.toString(),
        )
    }

    fun parsePointsEarned(message: JSONObject): PointsEarned {
        val messageData = message.optJSONObject("data")
        val pointGain = messageData?.optJSONObject("point_gain")
        return PointsEarned(
            pointsGained = pointGain?.optInt("total_points"),
            timestamp = if (messageData?.isNull("timestamp") == false) messageData.optString("timestamp").takeIf { it.isNotBlank() }?.let { TwitchApiHelper.parseIso8601DateUTC(it) } else null,
            fullMsg = message.toString()
        )
    }

    fun onRaidUpdate(message: JSONObject, openStream: Boolean): Raid? {
        val raid = message.optJSONObject("raid")
        return if (raid != null) {
            Raid(
                raidId = if (!raid.isNull("id")) raid.optString("id").takeIf { it.isNotBlank() } else null,
                targetId = if (!raid.isNull("target_id")) raid.optString("target_id").takeIf { it.isNotBlank() } else null,
                targetLogin = if (!raid.isNull("target_login")) raid.optString("target_login").takeIf { it.isNotBlank() } else null,
                targetName = if (!raid.isNull("target_display_name")) raid.optString("target_display_name").takeIf { it.isNotBlank() } else null,
                targetProfileImage = if (!raid.isNull("target_profile_image")) raid.optString("target_profile_image").takeIf { it.isNotBlank() }?.replace("profile_image-%s", "profile_image-300x300") else null,
                viewerCount = raid.optInt("viewer_count"),
                openStream = openStream
            )
        } else null
    }

    fun onPollUpdate(message: JSONObject): Poll? {
        val messageData = message.optJSONObject("data")
        val poll = messageData?.optJSONObject("poll")
        val choicesList = mutableListOf<Poll.PollChoice>()
        val choices = poll?.optJSONArray("choices")
        if (choices != null) {
            for (i in 0 until choices.length()) {
                val choice = choices.get(i) as? JSONObject
                val title = if (choice?.isNull("title") == false) choice.optString("title").takeIf { it.isNotBlank() } else null
                if (!title.isNullOrBlank()) {
                    choicesList.add(
                        Poll.PollChoice(
                            title = title,
                            totalVotes = choice?.optJSONObject("votes")?.let { votes -> if (!votes.isNull("total")) votes.optInt("total") else null },
                        )
                    )
                }
            }
        }
        return if (poll != null) {
            Poll(
                id = if (!poll.isNull("poll_id")) poll.optString("poll_id").takeIf { it.isNotBlank() } else null,
                title = if (!poll.isNull("title")) poll.optString("title").takeIf { it.isNotBlank() } else null,
                status = if (!poll.isNull("status")) poll.optString("status").takeIf { it.isNotBlank() } else null,
                choices = choicesList,
                totalVotes = poll.optJSONObject("votes")?.let { votes -> if (!votes.isNull("total")) votes.optInt("total") else null },
                remainingMilliseconds = if (!poll.isNull("remaining_duration_milliseconds")) poll.optInt("remaining_duration_milliseconds") else null,
            )
        } else null
    }

    fun onPredictionUpdate(message: JSONObject): Prediction? {
        val messageData = message.optJSONObject("data")
        val prediction = messageData?.optJSONObject("event")
        val outcomesList = mutableListOf<Prediction.PredictionOutcome>()
        val outcomes = prediction?.optJSONArray("outcomes")
        if (outcomes != null) {
            for (i in 0 until outcomes.length()) {
                val outcome = outcomes.get(i) as? JSONObject
                val title = if (outcome?.isNull("title") == false) outcome.optString("title").takeIf { it.isNotBlank() } else null
                if (!title.isNullOrBlank()) {
                    outcomesList.add(
                        Prediction.PredictionOutcome(
                            id = if (outcome?.isNull("id") == false) outcome.optString("id").takeIf { it.isNotBlank() } else null,
                            title = title,
                            totalPoints = if (outcome?.isNull("total_points") == false) outcome.optInt("total_points") else null,
                            totalUsers = if (outcome?.isNull("total_users") == false) outcome.optInt("total_users") else null,
                        )
                    )
                }
            }
        }
        return if (prediction != null) {
            Prediction(
                id = if (!prediction.isNull("id")) prediction.optString("id").takeIf { it.isNotBlank() } else null,
                createdAt = if (!prediction.isNull("created_at")) prediction.optString("created_at").takeIf { it.isNotBlank() }?.let { TwitchApiHelper.parseIso8601DateUTC(it) } else null,
                outcomes = outcomesList,
                predictionWindowSeconds = if (!prediction.isNull("prediction_window_seconds")) prediction.optInt("prediction_window_seconds") else null,
                status = if (!prediction.isNull("status")) prediction.optString("status").takeIf { it.isNotBlank() } else null,
                title = if (!prediction.isNull("title")) prediction.optString("title").takeIf { it.isNotBlank() } else null,
                winningOutcomeId = if (!prediction.isNull("winning_outcome_id")) prediction.optString("winning_outcome_id").takeIf { it.isNotBlank() } else null,
            )
        } else null
    }

    fun onCollaborationUpdate(message: JSONObject) : List<GuestStarChannelSlot>? {
        val messageType = message.optString("type")
        return when {
            messageType.startsWith("group-disbanded") -> emptyList()
            messageType.startsWith("group-updated") -> {
                val slots = mutableListOf<GuestStarChannelSlot>()
                val messageData = message.optJSONObject("data")

                val users = messageData?.optJSONArray("users")
                if (users != null) {
                    for (i in 0 until users.length()) {
                        val user = users.get(i) as JSONObject
                        val status = user.optString("status")
                        if (status != "ACTIVE")
                            continue
                        val channelId = user.optString("user_id")
                        val channelLogin = user.optString("login")
                        val channelName = user.optString("display_name")
                        val profileImageUrl =
                            user.optJSONObject("profile_image_urls")?.optString("70x70")
                        slots.add(
                            GuestStarChannelSlot(
                                id = i,
                                isLive = false,
                                channelId = channelId,
                                channelLogin = channelLogin,
                                channelName = channelName,
                                profileImageUrl = profileImageUrl
                            )
                        )
                    }
                }
                slots
            }
            else -> null
        }
    }

    fun onStarGuestUpdate(message: JSONObject) : List<GuestStarChannelSlot>? {
        val messageType = message.optString("type")
        return when {
            // messageType.startsWith("slot-assignments-changed") -> slotAssignmentsChanged(message, slots)
            messageType.startsWith("slot-subscribers-deleted") || messageType.startsWith("call-ended") -> emptyList()
            else -> null
        }
    }

    fun onSharedChatSessionUpdate(message: JSONObject) : List<SharedChatParticipant> {
        val messageType = message.optString("type")
        val messageData = message.optJSONObject("data")
        if (!messageType.startsWith("session-started") && !messageType.startsWith("session-updated")) {
            return emptyList()
        }

        val participants = messageData?.optJSONObject("session")?.optJSONArray("participants")
            ?: return emptyList()

        val sharedChatParticipants = mutableListOf<SharedChatParticipant>()

        for (i in 0 until participants.length()) {
            val participant = participants.get(i) as JSONObject
            val status = participant.optString("status")
            if (status != "ACTIVE")
                continue
            val participantId = participant.optString("participant_id")
            val channelId = participant.optString("channel_id")
            val channelLogin = participant.optString("channel_login")
            val channelName = participant.optString("channel_display_name")
            val profileImageUrl = participant.optString("channel_profile_image_url")
                .replace("profile_image-%s", "profile_image-70x70")
            val sharedChatParticipant = SharedChatParticipant(
                id = participantId,
                channelId = channelId,
                channelLogin = channelLogin,
                channelName = channelName,
                profileImageUrl = profileImageUrl
            )
            sharedChatParticipants.add(sharedChatParticipant)
        }

        return sharedChatParticipants
    }

    class PlaybackMessage(
        val live: Boolean? = null,
        val serverTime: Long? = null,
        val viewers: Int? = null,
        val collaborationViewers: Int? = null,
        val costreamViewers: Int? = null,
        val costreamStatus: String? = null,
    )

    class StreamInfo(
        val title: String? = null,
        val gameId: String? = null,
        val gameName: String? = null,
    )

    class PointsEarned(
        val pointsGained: Int? = null,
        val timestamp: Long? = null,
        val fullMsg: String? = null,
    )
}
