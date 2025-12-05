package com.example.star.aiwork.ui.conversation.logic

import android.content.Context
import android.net.Uri
import com.example.star.aiwork.domain.model.Agent
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.MessageRole
import com.example.star.aiwork.infra.util.toBase64
import com.example.star.aiwork.ui.ai.UIMessage
import com.example.star.aiwork.ui.ai.UIMessagePart
import com.example.star.aiwork.ui.conversation.ConversationUiState
import com.example.star.aiwork.ui.conversation.Message

object MessageConstructionHelper {

    suspend fun constructMessagesToSend(
        uiState: ConversationUiState,
        authorMe: String,
        inputContent: String,
        isAutoTriggered: Boolean,
        activeAgent: Agent?,
        retrieveKnowledge: suspend (String) -> String,
        context: Context
    ): List<UIMessage> {
        return constructMessages(
            uiState,
            authorMe,
            inputContent,
            isAutoTriggered,
            activeAgent,
            null, // We handle knowledge retrieval result separately if needed, or pass context here.
                  // But based on original logic, retrieval happens inside construct logic or before.
                  // Let's adapt: Original logic did retrieval inside processMessage.
                  // To keep this helper synchronous or suspend, let's assume the knowledge is already retrieved or we pass the function.
            retrieveKnowledge,
            context
        )
    }
    
    suspend fun constructMessages(
        uiState: ConversationUiState,
        authorMe: String,
        inputContent: String,
        isAutoTriggered: Boolean,
        activeAgent: Agent?,
        knowledgeContext: String? = null, // If passed directly
        retrieveKnowledge: (suspend (String) -> String)? = null, // Or function to retrieve
        context: Context
    ): List<UIMessage> {

        // RAG Retrieval
        val finalKnowledgeContext = knowledgeContext ?: if (!isAutoTriggered && retrieveKnowledge != null) {
            retrieveKnowledge(inputContent)
        } else {
            ""
        }

        val augmentedInput = if (finalKnowledgeContext.isNotBlank()) {
            """
            [Context from Knowledge Base]
            $finalKnowledgeContext
            
            [User Question]
            $inputContent
            """.trimIndent()
        } else {
            inputContent
        }

        val finalUserContent = if (activeAgent != null && !isAutoTriggered) {
            activeAgent.messageTemplate.replace("{{ message }}", augmentedInput)
        } else {
            augmentedInput
        }

        val contextMessages = uiState.messages.asReversed()
            .filter { it.author != "System" }
            .map { msg ->
                val role = if (msg.author == authorMe) MessageRole.USER else MessageRole.ASSISTANT
                val parts = mutableListOf<UIMessagePart>()
                if (msg.content.isNotEmpty()) {
                    parts.add(UIMessagePart.Text(msg.content))
                }
                UIMessage(role = role, parts = parts)
            }.takeLast(10).toMutableList()

        val messagesToSend = mutableListOf<UIMessage>()

        if (activeAgent != null && activeAgent.systemPrompt.isNotEmpty()) {
            messagesToSend.add(UIMessage(
                role = MessageRole.SYSTEM,
                parts = listOf(UIMessagePart.Text(activeAgent.systemPrompt))
            ))
        }

        if (activeAgent != null) {
            activeAgent.presetMessages.forEach { preset ->
                messagesToSend.add(UIMessage(
                    role = preset.role,
                    parts = listOf(UIMessagePart.Text(preset.content))
                ))
            }
        }

        messagesToSend.addAll(contextMessages)

        // Remove duplication if last message in history is the current user input (raw)
        if (messagesToSend.isNotEmpty() && messagesToSend.last().role == MessageRole.USER) {
            messagesToSend.removeAt(messagesToSend.lastIndex)
        }

        val currentMessageParts = mutableListOf<UIMessagePart>()
        currentMessageParts.add(UIMessagePart.Text(finalUserContent))

        if (!isAutoTriggered) {
            val lastUserMsg = uiState.messages.firstOrNull { it.author == authorMe && it.author != "System" }
            if (lastUserMsg?.imageUrl != null) {
                try {
                    val imageUri = Uri.parse(lastUserMsg.imageUrl)
                    val base64Image = context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                        inputStream.readBytes().toBase64()
                    }
                    if (base64Image != null) {
                        val mimeType = context.contentResolver.getType(imageUri) ?: "image/jpeg"
                        currentMessageParts.add(UIMessagePart.Image(url = "data:$mimeType;base64,$base64Image"))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        messagesToSend.add(UIMessage(
            role = MessageRole.USER,
            parts = currentMessageParts
        ))
        
        return messagesToSend
    }

    fun toChatDataItem(message: UIMessage): ChatDataItem {
        val builder = StringBuilder()
        message.parts.forEach { part ->
            when (part) {
                is UIMessagePart.Text -> builder.append(part.text)
                is UIMessagePart.Image -> builder.append("\n[image:${part.url}]")
                else -> {}
            }
        }
        return ChatDataItem(
            role = message.role.name.lowercase(),
            content = builder.toString()
        )
    }
}
