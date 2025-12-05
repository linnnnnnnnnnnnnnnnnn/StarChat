package com.example.star.aiwork.ui.conversation.logic

import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.domain.model.ProviderSetting
import com.example.star.aiwork.domain.usecase.SendMessageUseCase
import com.example.star.aiwork.ui.conversation.ConversationUiState
import com.example.star.aiwork.ui.conversation.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.util.UUID

class AutoLoopHandler(
    private val uiState: ConversationUiState,
    private val sendMessageUseCase: SendMessageUseCase,
    private val getProviderSettings: () -> List<ProviderSetting>,
    private val timeNow: String
) {
    suspend fun handleAutoLoop(
        fullResponse: String,
        loopCount: Int,
        currentProviderSetting: ProviderSetting,
        currentModel: Model,
        retrieveKnowledge: suspend (String) -> String,
        onProcessMessage: suspend (String, ProviderSetting, Model, Boolean, Int, suspend (String) -> String) -> Unit
    ) {
        val plannerProviderId = uiState.autoLoopProviderId
        val plannerModelId = uiState.autoLoopModelId

        var plannerProviderSetting: ProviderSetting? = currentProviderSetting
        var plannerModel: Model? = currentModel

        if (plannerProviderId != null) {
             val foundProvider = getProviderSettings().find { it.id == plannerProviderId }
             if (foundProvider != null) {
                 plannerProviderSetting = foundProvider
                 if (plannerModelId != null) {
                     val foundModel = foundProvider.models.find { it.modelId == plannerModelId }
                     if (foundModel != null) {
                         plannerModel = foundModel
                     } else {
                         plannerModel = foundProvider.models.firstOrNull()
                     }
                 } else {
                     plannerModel = foundProvider.models.firstOrNull()
                 }
             }
        }

        if (plannerProviderSetting != null && plannerModel != null) {
            val plannerSystemPrompt = """
                                You are a task planner agent.
                                Analyze the previous AI response and generate a short, specific instruction for the next step to deepen the task or solve remaining issues.
                                If the task appears complete or no further meaningful steps are needed, reply with exactly "STOP".
                                Output ONLY the instruction or "STOP".
                            """.trimIndent()

            val plannerUserMessage = ChatDataItem(
                role = "user",
                content = "Previous Response:\n$fullResponse"
            )

            val plannerHistory = listOf<ChatDataItem>(
                ChatDataItem(role="system", content=plannerSystemPrompt)
            )

            try {
                val plannerResult = sendMessageUseCase(
                    sessionId = UUID.randomUUID().toString(),
                    userMessage = plannerUserMessage,
                    history = plannerHistory,
                    providerSetting = plannerProviderSetting,
                    params = TextGenerationParams(
                        model = plannerModel,
                        temperature = 0.3f, // Lower temperature for more deterministic instructions
                        maxTokens = 100
                    )
                )

                var nextInstruction = ""
                plannerResult.stream.collect { delta ->
                    nextInstruction += delta
                }
                nextInstruction = nextInstruction.trim()

                if (nextInstruction != "STOP" && nextInstruction.isNotEmpty()) {
                    // Recursive call using the callback
                    onProcessMessage(
                        nextInstruction,
                        currentProviderSetting,
                        currentModel,
                        true,
                        loopCount + 1,
                        retrieveKnowledge
                    )
                }
            } catch (e: Exception) {
                 withContext(Dispatchers.Main) {
                    uiState.addMessage(Message("System", "Auto-loop planner failed: ${e.message}", timeNow))
                }
            }
        } else {
             withContext(Dispatchers.Main) {
                uiState.addMessage(Message("System", "Auto-loop planner configuration invalid or provider not found.", timeNow))
            }
        }
    }
}
