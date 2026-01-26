// ChatData.kt
package com.jeremy.test

import android.graphics.Bitmap
import com.google.gson.annotations.SerializedName
import java.util.*

data class ChatNotification(
    val id: String = UUID.randomUUID().toString(),
    val chatId: String,                    // Unique ID for the chat/group
    val senderId: String,                  // Who sent the message
    val senderName: String,                // Display name
    val message: String,                   // Message text
    val timestamp: Long = System.currentTimeMillis(),
    
    // Chat/Group info
    val chatName: String = "",             // Group name or contact name
    val isGroup: Boolean = false,          // Is this a group chat?
    val participantCount: Int = 1,         // Number of participants
    val participants: List<String> = emptyList(), // Participant names
    
    // Message type
    val messageType: String = TYPE_TEXT,
    val mediaUrl: String? = null,          // URL for images/videos
    val mediaThumbnail: String? = null,    // URL for thumbnail
    val mediaDuration: Int? = null,        // Duration in seconds for audio/video
    
    // Message status
    val isForwarded: Boolean = false,
    val isReply: Boolean = false,
    val replyToMessage: String? = null,
    val replyToSender: String? = null,
    
    // Quick actions
    val quickReactions: List<String> = listOf("👍", "❤️", "😂", "😮", "😢", "👏"),
    
    // Display settings
    val duration: Long = 15,               // How long to show notification
    val priority: Int = 1,                 // 0=low, 1=normal, 2=high
    val showAvatar: Boolean = true,
    val showChatName: Boolean = true,
    val collapseAfter: Long = 5000,        // Collapse to summary after 5s
) {
    companion object {
        // Message types
        const val TYPE_TEXT = "text"
        const val TYPE_IMAGE = "image"
        const val TYPE_VIDEO = "video"
        const val TYPE_AUDIO = "audio"
        const val TYPE_VOICE = "voice"
        const val TYPE_DOCUMENT = "document"
        const val TYPE_LOCATION = "location"
        const val TYPE_CONTACT = "contact"
        const val TYPE_STICKER = "sticker"
        const val TYPE_GIF = "gif"
        
        // App identifiers
        const val APP_WHATSAPP = "whatsapp"
        const val APP_TELEGRAM = "telegram"
        const val APP_SIGNAL = "signal"
        const val APP_MESSENGER = "messenger"
        const val APP_DISCORD = "discord"
        const val APP_SLACK = "slack"
    }
    
    fun getDisplayName(): String {
        return if (isGroup) "$senderName → $chatName" else senderName
    }
    
    fun isMediaMessage(): Boolean {
        return messageType in listOf(TYPE_IMAGE, TYPE_VIDEO, TYPE_GIF, TYPE_STICKER)
    }
}

// Chat manager for grouping
class ChatManager {
    private val activeChats = mutableMapOf<String, ChatThread>()
    private val MAX_MESSAGES_PER_CHAT = 20
    private val MAX_ACTIVE_CHATS = 5
    
    data class ChatThread(
        val chatId: String,
        val chatName: String,
        val isGroup: Boolean,
        val participants: List<String>,
        val messages: LinkedHashSet<ChatNotification> = LinkedHashSet(),
        var lastActivity: Long = System.currentTimeMillis(),
        var unreadCount: Int = 0
    )
    
    fun addMessage(notification: ChatNotification) {
        val thread = activeChats.getOrPut(notification.chatId) {
            ChatThread(
                chatId = notification.chatId,
                chatName = notification.chatName,
                isGroup = notification.isGroup,
                participants = notification.participants
            )
        }
        
        thread.messages.add(notification)
        thread.lastActivity = notification.timestamp
        thread.unreadCount++
        
        // Limit messages per chat
        if (thread.messages.size > MAX_MESSAGES_PER_CHAT) {
            val oldest = thread.messages.minByOrNull { it.timestamp }
            oldest?.let { thread.messages.remove(it) }
        }
        
        // Limit active chats
        if (activeChats.size > MAX_ACTIVE_CHATS) {
            val oldestChat = activeChats.values.minByOrNull { it.lastActivity }
            oldestChat?.let { activeChats.remove(it.chatId) }
        }
    }
    
    fun markChatRead(chatId: String) {
        activeChats[chatId]?.unreadCount = 0
    }
    
    fun getChatThread(chatId: String): ChatThread? {
        return activeChats[chatId]
    }
    
    fun getActiveChats(): List<ChatThread> {
        return activeChats.values.sortedByDescending { it.lastActivity }
    }
    
    fun getUnreadCount(chatId: String): Int {
        return activeChats[chatId]?.unreadCount ?: 0
    }
    
    fun clearChat(chatId: String) {
        activeChats.remove(chatId)
    }
    
    fun clearAll() {
        activeChats.clear()
    }
}