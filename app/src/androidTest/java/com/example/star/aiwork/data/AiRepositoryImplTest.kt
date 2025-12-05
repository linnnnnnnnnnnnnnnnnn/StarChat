package com.example.star.aiwork.data

import com.example.star.aiwork.data.repository.AiRepositoryImpl
import com.example.star.aiwork.domain.TextGenerationParams
import com.example.star.aiwork.domain.model.ChatDataItem
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.domain.model.ProviderSetting
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 针对 [AiRepositoryImpl.streamChat] 的简单单元测试，
 * 使用 [FakeStreamingChatRemoteDataSource] 手动控制上游流。
 */
/*
class AiRepositoryImplTest {

    @Test
    fun streamChat_normalizesSmallChunksIntoSingleEmission() = runBlocking {
        val fakeDataSource = FakeStreamingChatRemoteDataSource().apply {
            // 手动控制上游流的输出（在真正收集之前先写入 fake）
            emit("hello")
            emit("world")
        }
        val repository = AiRepositoryImpl(
            remoteChatDataSource = fakeDataSource,
            okHttpClient = OkHttpClient()
        )

        val history: List<ChatDataItem> = emptyList()
        val providerSetting: ProviderSetting =
            ProviderSetting.Ollama(models = emptyList())
        val params = TextGenerationParams(model = Model())
        val taskId = "test-task"

        // 直接将 Flow 收集为 List，同步等待上游和归一化逻辑跑完
        val received = repository
            .streamChat(history, providerSetting, params, taskId)
            .toList()

        // 由于 AiRepositoryImpl 会对 chunk 做 32 字符的归一化切分，
        // "hello" + "world" 长度不足 32，因此会合并成一次发射。
        assertEquals(listOf("helloworld"), received)
    }
}
*/

