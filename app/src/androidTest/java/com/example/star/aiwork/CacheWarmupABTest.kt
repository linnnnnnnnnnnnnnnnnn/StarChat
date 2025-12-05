/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.star.aiwork

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.star.aiwork.data.local.DatabaseProvider
import com.example.star.aiwork.data.local.datasource.DraftLocalDataSourceImpl
import com.example.star.aiwork.data.local.datasource.MessageLocalDataSourceImpl
import com.example.star.aiwork.data.local.datasource.SessionLocalDataSourceImpl
import com.example.star.aiwork.domain.model.SessionEntity
import com.example.star.aiwork.domain.model.SessionMetadata
import com.example.star.aiwork.domain.usecase.draft.GetDraftUseCase
import com.example.star.aiwork.domain.usecase.draft.UpdateDraftUseCase
import com.example.star.aiwork.domain.usecase.message.ObserveMessagesUseCase
import com.example.star.aiwork.domain.usecase.message.RollbackMessageUseCase
import com.example.star.aiwork.domain.usecase.message.SendMessageUseCase
import com.example.star.aiwork.domain.usecase.session.*
import com.example.star.aiwork.ui.conversation.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * A/B 测试：比较预热与未预热情况下开机时间的差异，
 * 以及从缓存加载会话与从数据库直接加载会话的时间差距。
 */
@RunWith(AndroidJUnit4::class)
class CacheWarmupABTest {

    private lateinit var context: Context
    private lateinit var application: Application
    private lateinit var sessionLocalDataSource: SessionLocalDataSourceImpl
    private lateinit var messageLocalDataSource: MessageLocalDataSourceImpl
    private lateinit var draftLocalDataSource: DraftLocalDataSourceImpl

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        application = context.applicationContext as Application
        
        // 使用测试数据库
        sessionLocalDataSource = SessionLocalDataSourceImpl(context)
        messageLocalDataSource = MessageLocalDataSourceImpl(context)
        draftLocalDataSource = DraftLocalDataSourceImpl(context)
        
        // 清理测试数据
        runBlocking {
            clearTestData()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            clearTestData()
        }
    }

    /**
     * 清理测试数据
     */
    private suspend fun clearTestData() {
        val deleteAllSessionsUseCase = DeleteAllSessionsUseCase(sessionLocalDataSource)
        deleteAllSessionsUseCase()
    }

    /**
     * 创建测试会话数据
     * 确保这些会话是数据库中 updatedAt 最大的（最新的）
     */
    private suspend fun createTestSessions(count: Int): List<SessionEntity> {
        val sessions = mutableListOf<SessionEntity>()
        val currentTime = System.currentTimeMillis()
        
        // 确保会话按 updatedAt DESC 排序（最新的在前）
        // 使用递增的时间戳，确保最后创建的会话 updatedAt 最大
        for (i in 1..count) {
            val session = SessionEntity(
                id = UUID.randomUUID().toString(),
                name = "测试会话 $i",
                createdAt = currentTime + i * 1000L, // 递增的时间戳
                updatedAt = currentTime + i * 1000L, // 最新的会话 updatedAt 最大
                pinned = false,
                archived = false,
                metadata = SessionMetadata()
            )
            sessions.add(session)
            sessionLocalDataSource.upsertSession(session)
        }
        
        // 等待数据库写入完成
        kotlinx.coroutines.delay(300)
        
        // 验证会话已保存，并确认它们是前N个
        val savedSessions = sessionLocalDataSource.getTopSessions(count + 5) // 多取一些，确保包含我们的测试会话
        println("创建了 ${sessions.size} 个测试会话，数据库中实际有 ${savedSessions.size} 个会话")
        savedSessions.take(10).forEach { println("  - 会话: ${it.id}, 名称: ${it.name}, updatedAt: ${it.updatedAt}") }
        
        // 返回按 updatedAt DESC 排序的会话（最新的在前）
        return sessions.sortedByDescending { it.updatedAt }
    }

    /**
     * 创建 ChatViewModel（不预热缓存）
     */
    private fun createViewModelWithoutWarmup(): ChatViewModel {
        val getSessionListUseCase = GetSessionListUseCase(sessionLocalDataSource)
        val createSessionUseCase = CreateSessionUseCase(sessionLocalDataSource)
        val renameSessionUseCase = RenameSessionUseCase(sessionLocalDataSource)
        val deleteSessionUseCase = DeleteSessionUseCase(sessionLocalDataSource, messageLocalDataSource, draftLocalDataSource)
        val pinSessionUseCase = PinSessionUseCase(sessionLocalDataSource)
        val archiveSessionUseCase = ArchiveSessionUseCase(sessionLocalDataSource)
        val searchSessionsUseCase = SearchSessionsUseCase(sessionLocalDataSource)
        val sendMessageUseCase = SendMessageUseCase(messageLocalDataSource, sessionLocalDataSource)
        val rollbackMessageUseCase = RollbackMessageUseCase(messageLocalDataSource)
        val observeMessagesUseCase = ObserveMessagesUseCase(messageLocalDataSource)
        val getDraftUseCase = GetDraftUseCase(draftLocalDataSource)
        val updateDraftUseCase = UpdateDraftUseCase(draftLocalDataSource)
        val getTopSessionsUseCase = GetTopSessionsUseCase(sessionLocalDataSource)

        return ChatViewModel(
            getSessionListUseCase,
            createSessionUseCase,
            renameSessionUseCase,
            deleteSessionUseCase,
            pinSessionUseCase,
            archiveSessionUseCase,
            sendMessageUseCase,
            rollbackMessageUseCase,
            observeMessagesUseCase,
            getDraftUseCase,
            updateDraftUseCase,
            searchSessionsUseCase,
            getTopSessionsUseCase
        )
    }

    /**
     * 测试 1: 预热 vs 未预热的启动时间差异
     */
    @Test
    fun testWarmupVsNoWarmup_StartupTime() = runBlocking {
        // 准备测试数据：创建 10 个会话
        val testSessions = createTestSessions(10)
        assertTrue("应该创建 10 个测试会话", testSessions.size == 10)

        // 测试 A: 未预热情况下的启动时间
        val startTimeNoWarmup = System.currentTimeMillis()
        val viewModelNoWarmup = createViewModelWithoutWarmup()
        // 等待 ViewModel 初始化完成（loadSessions 完成）
        viewModelNoWarmup.sessions.first { it.isNotEmpty() }
        val endTimeNoWarmup = System.currentTimeMillis()
        val timeNoWarmup = endTimeNoWarmup - startTimeNoWarmup

        // 清理缓存
        viewModelNoWarmup.clearAllUiStates()

        // 测试 B: 预热情况下的启动时间
        val startTimeWarmup = System.currentTimeMillis()
        val viewModelWarmup = createViewModelWithoutWarmup()
        viewModelWarmup.warmupCache()
        
        // 等待预热完成 - 检查数据库中的前5个会话是否已加载到缓存
        val getTopSessionsUseCase = GetTopSessionsUseCase(sessionLocalDataSource)
        val topSessionsFromDb = getTopSessionsUseCase(5)
        
        assertTrue("数据库应该返回至少一个会话用于预热", topSessionsFromDb.isNotEmpty())
        
        println("数据库中的前5个会话: ${topSessionsFromDb.map { "${it.name}(${it.id.take(8)})" }}")
        println("测试会话ID: ${testSessions.take(5).map { "${it.name}(${it.id.take(8)})" }}")
        
        // 等待预热完成 - 轮询检查缓存是否已加载
        var retryCount = 0
        val maxRetries = 100 // 增加到100次，给更多时间
        var foundCachedSession = false
        var firstCachedSessionId: String? = null
        
        while (retryCount < maxRetries) {
            // 检查数据库返回的前5个会话中是否有任何一个已在缓存中
            for (session in topSessionsFromDb) {
                if (viewModelWarmup.getSessionUiState(session.id) != null) {
                    foundCachedSession = true
                    firstCachedSessionId = session.id
                    println("找到缓存的会话: ${session.name}(${session.id.take(8)}) (重试次数: $retryCount)")
                    break
                }
            }
            if (foundCachedSession) break
            
            kotlinx.coroutines.delay(100)
            retryCount++
        }
        
        val endTimeWarmup = System.currentTimeMillis()
        val timeWarmup = endTimeWarmup - startTimeWarmup
        
        // 调试信息：检查缓存状态
        println("预热后缓存状态检查 (重试次数: $retryCount, 找到缓存: $foundCachedSession):")
        for (session in topSessionsFromDb) {
            val cached = viewModelWarmup.getSessionUiState(session.id)
            println("  会话 ${session.name}(${session.id.take(8)}): ${if (cached != null) "已缓存" else "未缓存"}")
        }

        // 输出结果
        val improvement = ((timeNoWarmup - timeWarmup).toDouble() / timeNoWarmup * 100)
        println("=========================================")
        println("预热 vs 未预热 - 启动时间测试")
        println("未预热启动时间: ${timeNoWarmup}ms")
        println("预热启动时间: ${timeWarmup}ms")
        println("时间差异: ${timeNoWarmup - timeWarmup}ms")
        println("性能提升: ${String.format("%.2f", improvement)}%")
        println("=========================================")

        // 验证预热确实加载了缓存
        assertTrue(
            "预热后缓存应该包含会话。数据库返回了 ${topSessionsFromDb.size} 个会话，重试了 $retryCount 次，找到缓存: $foundCachedSession。第一个缓存的会话ID: $firstCachedSessionId",
            foundCachedSession && firstCachedSessionId != null
        )
        
        // 额外验证：检查第一个缓存的会话确实在缓存中
        val cachedState = viewModelWarmup.getSessionUiState(firstCachedSessionId!!)
        assertTrue("缓存的会话状态应该存在", cachedState != null)
    }

    /**
     * 测试 2: 从缓存加载会话 vs 从数据库直接加载会话的时间差距
     */
    @Test
    fun testCacheVsDatabase_LoadSessionTime() = runBlocking {
        // 准备测试数据：创建 5 个会话
        val testSessions = createTestSessions(5)
        assertTrue("应该创建 5 个测试会话", testSessions.size == 5)

        val viewModel = createViewModelWithoutWarmup()
        
        // 预热缓存：加载前 5 个会话到缓存
        viewModel.warmupCache()
        // 等待预热完成 - 轮询检查缓存是否已加载
        var retryCount2 = 0
        while (viewModel.getSessionUiState(testSessions[0].id) == null && retryCount2 < 50) {
            kotlinx.coroutines.delay(100)
            retryCount2++
        }

        // 测试 A: 从缓存加载会话的时间
        val cacheLoadTimes = mutableListOf<Long>()
        for (session in testSessions.take(5)) {
            val startTime = System.nanoTime()
            val uiState = viewModel.getOrCreateSessionUiState(session.id, session.name)
            val endTime = System.nanoTime()
            val loadTime = (endTime - startTime) / 1_000 // 转换为微秒
            cacheLoadTimes.add(loadTime)
        }
        val avgCacheLoadTime = cacheLoadTimes.average()

        // 清理缓存
        viewModel.clearAllUiStates()

        // 测试 B: 从数据库直接加载会话的时间
        val databaseLoadTimes = mutableListOf<Long>()
        for (session in testSessions.take(5)) {
            val startTime = System.nanoTime()
            // 模拟从数据库加载（通过 UseCase）
            val getTopSessionsUseCase = GetTopSessionsUseCase(sessionLocalDataSource)
            val sessions = getTopSessionsUseCase(5)
            val foundSession = sessions.find { it.id == session.id }
            // 然后创建 UI 状态
            val uiState = viewModel.getOrCreateSessionUiState(session.id, session.name)
            val endTime = System.nanoTime()
            val loadTime = (endTime - startTime) / 1_000 // 转换为微秒
            databaseLoadTimes.add(loadTime)
        }
        val avgDatabaseLoadTime = databaseLoadTimes.average()

        // 输出结果
        val speedup = avgDatabaseLoadTime / avgCacheLoadTime
        println("=========================================")
        println("缓存 vs 数据库 - 加载会话时间测试")
        println("平均缓存加载时间: ${String.format("%.2f", avgCacheLoadTime)}us")
        println("平均数据库加载时间: ${String.format("%.2f", avgDatabaseLoadTime)}us")
        println("时间差异: ${String.format("%.2f", avgDatabaseLoadTime - avgCacheLoadTime)}us")
        println("缓存加速比: ${String.format("%.2f", speedup)}x")
        println("=========================================")

        // 验证缓存确实更快
        assertTrue("缓存加载应该比数据库加载快", avgCacheLoadTime < avgDatabaseLoadTime)
    }

    /**
     * 测试 3: 多次切换会话的性能测试（缓存命中率）
     */
    @Test
    fun testCacheHitRate_SessionSwitching() = runBlocking {
        // 准备测试数据：创建 5 个会话
        val testSessions = createTestSessions(5)
        assertTrue("应该创建 5 个测试会话", testSessions.size == 5)

        val viewModel = createViewModelWithoutWarmup()
        
        // 预热缓存：加载前 5 个会话到缓存
        viewModel.warmupCache()
        // 等待预热完成 - 轮询检查缓存是否已加载
        var retryCount = 0
        while (viewModel.getSessionUiState(testSessions[0].id) == null && retryCount < 50) {
            kotlinx.coroutines.delay(100)
            retryCount++
        }

        // 测试：多次切换会话（应该都从缓存加载）
        val switchTimes = mutableListOf<Long>()
        repeat(10) {
            val session = testSessions[it % testSessions.size]
            val startTime = System.nanoTime()
            viewModel.selectSession(session)
            val uiState = viewModel.getOrCreateSessionUiState(session.id, session.name)
            val endTime = System.nanoTime()
            val switchTime = (endTime - startTime) / 1_000_000 // 转换为毫秒
            switchTimes.add(switchTime)
        }

        val avgSwitchTime = switchTimes.average()
        val maxSwitchTime = (switchTimes.maxOrNull() ?: 0L).toDouble()
        val minSwitchTime = (switchTimes.minOrNull() ?: 0L).toDouble()

        // 输出结果
        println("=========================================")
        println("缓存命中率 - 会话切换性能测试")
        println("平均切换时间: ${String.format("%.2f", avgSwitchTime)}ms")
        println("最大切换时间: ${String.format("%.2f", maxSwitchTime)}ms")
        println("最小切换时间: ${String.format("%.2f", minSwitchTime)}ms")
        println("=========================================")

        // 验证切换时间应该很快（都在缓存中）
        assertTrue("缓存切换应该很快（< 10ms）", avgSwitchTime < 10.0)
    }
}

