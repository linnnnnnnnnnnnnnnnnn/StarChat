package com.example.star.aiwork.ai

import android.text.TextUtils

/**
 * 聊天服务单例，用于管理和创建聊天会话。
 *
 * 此服务作为中心点，负责协调所有的 ChatSession 实例，包括创建、获取和销毁会话。
 * 它支持管理不同类型的会话（目前主要是基于 Transformer 的 LLM 会话）。
 */
class ChatService {
    // 存储活跃会话的 Map，Key 为 sessionId
    private val transformerSessionMap: MutableMap<String, ChatSession> = HashMap()

    /**
     * 创建任意类型模型会话的统一方法。
     *
     * @param modelId 模型 ID。
     * @param modelName 模型名称（用于类型检测，例如是否包含 "omni"）。
     * @param sessionIdParam 可选的会话 ID，如果为空则自动生成。
     * @param historyList 初始聊天历史记录。
     * @param configPath LLM 模型的配置文件路径，或扩散模型的目录。
     * @param useNewConfig 如果为 true，则忽略现有配置并使用提供的 configPath。如果为 false，可能会重用现有会话配置。
     * @return 创建的 ChatSession 实例。
     */
    @Synchronized
    fun createSession(
        modelId: String,
        modelName: String,
        sessionIdParam: String?,
        historyList: List<ChatDataItem>?,
        configPath: String?,
        useNewConfig: Boolean = false
    ): ChatSession {
        val sessionId = if (TextUtils.isEmpty(sessionIdParam)) {
            System.currentTimeMillis().toString()
        } else {
            sessionIdParam!!
        }

        // 创建 LlmSession 实例
        val session = LlmSession(modelId, sessionId, configPath!!, historyList)
        // 根据模型名称判断是否支持 Omni 功能
        session.supportOmni = modelName.lowercase().contains("omni")

        // 存储在 Map 中
        transformerSessionMap[sessionId] = session

        return session
    }

    /**
     * 专门用于创建 LLM 会话的方法。
     *
     * @param modelId 模型 ID。
     * @param modelDir 模型目录路径。
     * @param sessionIdParam 可选的会话 ID。
     * @param chatDataItemList 初始聊天历史数据项列表。
     * @param supportOmni 是否支持 Omni 功能。
     * @return 创建的 LlmSession 实例。
     */
    @Synchronized
    fun createLlmSession(
        modelId: String?,
        modelDir: String?,
        sessionIdParam: String?,
        chatDataItemList: List<ChatDataItem>?,
        supportOmni: Boolean
    ): LlmSession {
        var sessionId: String = if (TextUtils.isEmpty(sessionIdParam)) {
            System.currentTimeMillis().toString()
        } else {
            sessionIdParam!!
        }
        val session = LlmSession(modelId!!, sessionId, modelDir!!, chatDataItemList)
        session.supportOmni = supportOmni
        transformerSessionMap[sessionId] = session
        return session
    }

    /**
     * 获取现有的会话。
     *
     * @param sessionId 会话 ID。
     * @return 对应的 ChatSession 实例，如果未找到则返回 null。
     */
    @Synchronized
    fun getSession(sessionId: String): ChatSession? {
        return transformerSessionMap[sessionId]
    }

    /**
     * 移除并销毁会话。
     *
     * @param sessionId 要移除的会话 ID。
     */
    @Synchronized
    fun removeSession(sessionId: String) {
        transformerSessionMap.remove(sessionId)
    }

    companion object {
        private var instance: ChatService? = null

        /**
         * 获取 ChatService 的单例实例。
         *
         * @return ChatService 实例。
         */
        @JvmStatic
        @Synchronized
        fun provide(): ChatService {
            if (instance == null) {
                instance = ChatService()
            }
            return instance!!
        }
    }
}
