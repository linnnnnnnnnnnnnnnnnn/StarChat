package com.example.star.aiwork.domain.usecase.embedding

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.star.aiwork.infra.embedding.EmbeddingService
import com.example.star.aiwork.infra.embedding.RemoteEmbeddingAPI
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis
import kotlin.math.abs

/**
 * A/B 测试：比较本地嵌入服务（EmbeddingService）和远程嵌入 API（RemoteEmbeddingAPI）的性能。
 * 
 * 测试指标：
 * - 计算时间（本地 vs 远程）
 * - 向量维度（是否一致）
 * - 成功率
 * - 多次运行的平均性能
 */
@RunWith(AndroidJUnit4::class)
class EmbeddingABTest {

    private lateinit var context: Context
    private lateinit var localEmbeddingService: EmbeddingService
    private lateinit var remoteEmbeddingAPI: RemoteEmbeddingAPI
    private lateinit var localUseCase: ComputeEmbeddingUseCase
    private lateinit var remoteUseCase: ComputeEmbeddingUseCase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        
        // 初始化本地服务
        localEmbeddingService = EmbeddingService(context)
        localUseCase = ComputeEmbeddingUseCase(
            embeddingService = localEmbeddingService,
            useRemote = false
        )
        
        // 初始化远程服务
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        remoteEmbeddingAPI = RemoteEmbeddingAPI(okHttpClient)
        remoteUseCase = ComputeEmbeddingUseCase(
            remoteEmbeddingAPI = remoteEmbeddingAPI,
            useRemote = true
        )
    }

    @After
    fun tearDown() {
        localEmbeddingService.close()
    }

    /**
     * 测试 1: 单次计算性能对比
     */
    @Test
    fun testSingleEmbedding_PerformanceComparison() = runBlocking {
        val text = "这是一个用于性能测试的文本"
        println("\n" + "=".repeat(60))
        println("单次嵌入计算性能对比")
        println("=".repeat(60))
        println("测试文本: $text")
        println("文本长度: ${text.length} 字符\n")

        // 测试本地服务
        var localTime = 0L
        var localEmbedding: FloatArray? = null
        var localSuccess = false
        
        try {
            localTime = measureTimeMillis {
                localEmbedding = localUseCase(text)
                localSuccess = localEmbedding != null
            }
        } catch (e: Exception) {
            println("❌ 本地服务失败: ${e.message}")
            e.printStackTrace()
        }

        // 测试远程服务
        var remoteTime = 0L
        var remoteEmbedding: FloatArray? = null
        var remoteSuccess = false
        
        try {
            remoteTime = measureTimeMillis {
                remoteEmbedding = remoteUseCase(text)
                remoteSuccess = remoteEmbedding != null
            }
        } catch (e: Exception) {
            println("❌ 远程服务失败: ${e.message}")
            e.printStackTrace()
        }

        // 输出结果
        println("本地服务:")
        println("  时间: ${localTime}ms")
        println("  成功: $localSuccess")
        println("  向量维度: ${localEmbedding?.size ?: 0}")
        if (localEmbedding != null && localEmbedding!!.isNotEmpty()) {
            val min = localEmbedding!!.minOrNull() ?: 0f
            val max = localEmbedding!!.maxOrNull() ?: 0f
            val avg = localEmbedding!!.average().toFloat()
            println("  值范围: [$min, $max], 平均值: $avg")
        }

        println("\n远程服务:")
        println("  时间: ${remoteTime}ms")
        println("  成功: $remoteSuccess")
        println("  向量维度: ${remoteEmbedding?.size ?: 0}")
        if (remoteEmbedding != null && remoteEmbedding!!.isNotEmpty()) {
            val min = remoteEmbedding!!.minOrNull() ?: 0f
            val max = remoteEmbedding!!.maxOrNull() ?: 0f
            val avg = remoteEmbedding!!.average().toFloat()
            println("  值范围: [$min, $max], 平均值: $avg")
        }

        if (localSuccess && remoteSuccess) {
            val speedup = if (remoteTime > 0) localTime.toDouble() / remoteTime else 0.0
            println("\n性能对比:")
            println("  时间差异: ${abs(localTime - remoteTime)}ms")
            println("  速度比: ${String.format("%.2f", speedup)}x")
            if (localEmbedding != null && remoteEmbedding != null) {
                val dimensionMatch = localEmbedding!!.size == remoteEmbedding!!.size
                println("  维度匹配: $dimensionMatch")
            }
        }
        println("=".repeat(60) + "\n")
    }

    /**
     * 测试 2: 多次运行平均性能对比
     */
    @Test
    fun testMultipleRuns_AveragePerformance() = runBlocking {
        val text = "这是用于多次运行测试的文本"
        val runs = 5
        
        println("\n" + "=".repeat(60))
        println("多次运行平均性能对比")
        println("=".repeat(60))
        println("测试文本: $text")
        println("运行次数: $runs\n")

        // 本地服务多次运行
        val localTimes = mutableListOf<Long>()
        val localSuccesses = mutableListOf<Boolean>()
        val localDimensions = mutableListOf<Int>()
        
        repeat(runs) { runIndex ->
            try {
                val time = measureTimeMillis {
                    val embedding = localUseCase(text)
                    if (embedding != null) {
                        localSuccesses.add(true)
                        localDimensions.add(embedding.size)
                    } else {
                        localSuccesses.add(false)
                    }
                }
                localTimes.add(time)
                println("本地运行 ${runIndex + 1}: ${time}ms")
            } catch (e: Exception) {
                println("❌ 本地运行 ${runIndex + 1} 失败: ${e.message}")
                localSuccesses.add(false)
            }
        }

        // 远程服务多次运行
        val remoteTimes = mutableListOf<Long>()
        val remoteSuccesses = mutableListOf<Boolean>()
        val remoteDimensions = mutableListOf<Int>()
        
        repeat(runs) { runIndex ->
            try {
                val time = measureTimeMillis {
                    val embedding = remoteUseCase(text)
                    if (embedding != null) {
                        remoteSuccesses.add(true)
                        remoteDimensions.add(embedding.size)
                    } else {
                        remoteSuccesses.add(false)
                    }
                }
                remoteTimes.add(time)
                println("远程运行 ${runIndex + 1}: ${time}ms")
            } catch (e: Exception) {
                println("❌ 远程运行 ${runIndex + 1} 失败: ${e.message}")
                remoteSuccesses.add(false)
            }
        }

        // 统计结果
        val localAvgTime = localTimes.average()
        val localMinTime = localTimes.minOrNull() ?: 0L
        val localMaxTime = localTimes.maxOrNull() ?: 0L
        val localSuccessRate = localSuccesses.count { it }.toDouble() / runs * 100

        val remoteAvgTime = remoteTimes.average()
        val remoteMinTime = remoteTimes.minOrNull() ?: 0L
        val remoteMaxTime = remoteTimes.maxOrNull() ?: 0L
        val remoteSuccessRate = remoteSuccesses.count { it }.toDouble() / runs * 100

        println("\n本地服务统计:")
        println("  平均时间: ${String.format("%.2f", localAvgTime)}ms")
        println("  最短时间: ${localMinTime}ms")
        println("  最长时间: ${localMaxTime}ms")
        println("  成功率: ${String.format("%.1f", localSuccessRate)}%")
        if (localDimensions.isNotEmpty()) {
            println("  向量维度: ${localDimensions.first()} (所有运行维度一致: ${localDimensions.all { it == localDimensions.first() }})")
        }

        println("\n远程服务统计:")
        println("  平均时间: ${String.format("%.2f", remoteAvgTime)}ms")
        println("  最短时间: ${remoteMinTime}ms")
        println("  最长时间: ${remoteMaxTime}ms")
        println("  成功率: ${String.format("%.1f", remoteSuccessRate)}%")
        if (remoteDimensions.isNotEmpty()) {
            println("  向量维度: ${remoteDimensions.first()} (所有运行维度一致: ${remoteDimensions.all { it == remoteDimensions.first() }})")
        }

        if (localTimes.isNotEmpty() && remoteTimes.isNotEmpty()) {
            val speedup = remoteAvgTime / localAvgTime
            println("\n性能对比:")
            println("  平均时间差异: ${String.format("%.2f", abs(localAvgTime - remoteAvgTime))}ms")
            println("  速度比: ${String.format("%.2f", speedup)}x")
            println("  时间稳定性 (本地): ${String.format("%.2f", (localMaxTime - localMinTime).toDouble())}ms")
            println("  时间稳定性 (远程): ${String.format("%.2f", (remoteMaxTime - remoteMinTime).toDouble())}ms")
        }
        println("=".repeat(60) + "\n")
    }

    /**
     * 测试 3: 不同长度文本的性能对比
     */
    @Test
    fun testDifferentTextLengths_PerformanceComparison() = runBlocking {
        val testCases = listOf(
            "短文本" to "这是一个短文本",
            "中等文本" to "这是一个中等长度的文本测试。".repeat(5),
            "长文本" to "这是一个较长的文本测试，用于测试模型处理长文本的能力。".repeat(10)
        )

        println("\n" + "=".repeat(60))
        println("不同长度文本性能对比")
        println("=".repeat(60) + "\n")

        testCases.forEach { (name, text) ->
            println("测试用例: $name (${text.length} 字符)")
            
            // 本地服务
            var localTime = 0L
            var localSuccess = false
            try {
                localTime = measureTimeMillis {
                    val embedding = localUseCase(text)
                    localSuccess = embedding != null
                }
            } catch (e: Exception) {
                println("  ❌ 本地服务失败: ${e.message}")
            }

            // 远程服务
            var remoteTime = 0L
            var remoteSuccess = false
            try {
                remoteTime = measureTimeMillis {
                    val embedding = remoteUseCase(text)
                    remoteSuccess = embedding != null
                }
            } catch (e: Exception) {
                println("  ❌ 远程服务失败: ${e.message}")
            }

            println("  本地: ${localTime}ms (${if (localSuccess) "成功" else "失败"})")
            println("  远程: ${remoteTime}ms (${if (remoteSuccess) "成功" else "失败"})")
            if (localSuccess && remoteSuccess) {
                val speedup = if (remoteTime > 0) localTime.toDouble() / remoteTime else 0.0
                println("  速度比: ${String.format("%.2f", speedup)}x")
            }
            println()
        }
        println("=".repeat(60) + "\n")
    }

    /**
     * 测试 4: 预热后的性能对比（排除首次加载开销）
     */
    @Test
    fun testWarmup_PerformanceComparison() = runBlocking {
        val text = "这是用于预热测试的文本"
        val warmupRuns = 2
        val measurementRuns = 3

        println("\n" + "=".repeat(60))
        println("预热后性能对比")
        println("=".repeat(60))
        println("测试文本: $text")
        println("预热次数: $warmupRuns, 测量次数: $measurementRuns\n")

        // 本地服务预热
        println("本地服务预热...")
        repeat(warmupRuns) {
            try {
                localUseCase(text)
            } catch (e: Exception) {
                println("  预热失败: ${e.message}")
            }
        }

        // 本地服务测量
        val localTimes = mutableListOf<Long>()
        repeat(measurementRuns) { runIndex ->
            try {
                val time = measureTimeMillis {
                    val embedding = localUseCase(text)
                    assert(embedding != null) { "嵌入向量计算失败" }
                }
                localTimes.add(time)
                println("本地测量 ${runIndex + 1}: ${time}ms")
            } catch (e: Exception) {
                println("  ❌ 本地测量 ${runIndex + 1} 失败: ${e.message}")
            }
        }

        // 远程服务测量（远程服务通常不需要预热，但可以测试）
        println("\n远程服务测量...")
        val remoteTimes = mutableListOf<Long>()
        repeat(measurementRuns) { runIndex ->
            try {
                val time = measureTimeMillis {
                    val embedding = remoteUseCase(text)
                    assert(embedding != null) { "嵌入向量计算失败" }
                }
                remoteTimes.add(time)
                println("远程测量 ${runIndex + 1}: ${time}ms")
            } catch (e: Exception) {
                println("  ❌ 远程测量 ${runIndex + 1} 失败: ${e.message}")
            }
        }

        // 统计结果
        if (localTimes.isNotEmpty()) {
            val localAvg = localTimes.average()
            println("\n本地服务平均时间: ${String.format("%.2f", localAvg)}ms")
        }
        if (remoteTimes.isNotEmpty()) {
            val remoteAvg = remoteTimes.average()
            println("远程服务平均时间: ${String.format("%.2f", remoteAvg)}ms")
        }
        if (localTimes.isNotEmpty() && remoteTimes.isNotEmpty()) {
            val speedup = remoteTimes.average() / localTimes.average()
            println("速度比: ${String.format("%.2f", speedup)}x")
        }
        println("=".repeat(60) + "\n")
    }
}






