package com.example.star.aiwork.infra.network

import android.util.Log
import com.example.star.aiwork.infra.util.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * SSE 客户端，负责长连接读取与统一异常处理。
 */
class SseClient(
    private val okHttpClient: OkHttpClient = defaultOkHttpClient(),
    private val charset: Charset = Charsets.UTF_8
) {

    private val activeCalls = ConcurrentHashMap<String, Call>()

    /**
     * 根据请求创建 SSE 流。
     */
    fun createStream(
        request: Request,
        taskId: String,
        clientOverride: OkHttpClient? = null
    ): Flow<String> = flow {
        val callClient = clientOverride ?: okHttpClient
        val call = callClient.newCall(request)
        activeCalls[taskId]?.cancel()
        activeCalls[taskId] = call

        try {
            val response = call.await()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errorBody = resp.body?.string().orEmpty()
                    throw NetworkException.HttpException(resp.code, errorBody)
                }
                val body = resp.body ?: throw NetworkException.UnknownException("SSE 响应体为空")
                body.source().inputStream().use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream, charset))
                    reader.useLines { sequence ->
                        for (line in sequence) {
                            coroutineContext.ensureActive()
                            val payload = line.parseSseData() ?: continue
                            emit(payload)
                        }
                    }
                }
            }
        } catch (io: IOException) {
            // 检查是否是取消操作导致的异常
            if (isCancellationException(io)) {
                throw CancellationException("SSE stream was cancelled", io)
            }
            throw mapIOException(io)
        } catch (ce: CancellationException) {
            // 重新抛出取消异常，不转换为NetworkException
            throw ce
        } finally {
            activeCalls.remove(taskId)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 取消指定任务。
     */
    fun cancel(taskId: String) {
        try {
            activeCalls.remove(taskId)?.cancel()
        } catch (e: Exception) {
            // 取消操作应该静默处理，即使失败也不应该抛出异常
            // Call.cancel() 通常不会抛出异常，但在某些边缘情况下可能会
            Log.d("SseClient", "Cancel call failed for taskId: $taskId", e)
        }
    }

    /**
     * 取消所有活动任务。
     */
    fun cancelAll() {
        activeCalls.entries.forEach { (_, call) ->
            call.cancel()
        }
        activeCalls.clear()
    }

    private fun String.parseSseData(): String? {
        // 忽略心跳或注释
        if (isBlank() || startsWith(":")) return null
        return if (startsWith("data:")) {
            substringAfter("data:").trimStart()
        } else {
            this
        }
    }
}

/**
 * 检查异常是否是取消操作导致的。
 * 当用户取消请求时，OkHttp会抛出StreamResetException，错误码为CANCEL。
 * 由于StreamResetException是OkHttp的内部API，我们通过类名和消息来检测。
 */
private fun isCancellationException(e: Throwable): Boolean {
    var cause: Throwable? = e
    while (cause != null) {
        val className = cause.javaClass.name
        val message = cause.message?.lowercase() ?: ""
        
        // 检查是否是StreamResetException（OkHttp内部类）
        if (className.contains("StreamResetException", ignoreCase = true)) {
            // 检查异常消息是否包含"cancel"
            if (message.contains("cancel", ignoreCase = true) || 
                message.contains("stream was reset: cancel", ignoreCase = true)) {
                return true
            }
            // 尝试通过反射检查errorCode
            try {
                val errorCodeField = cause.javaClass.getDeclaredField("errorCode")
                errorCodeField.isAccessible = true
                val errorCode = errorCodeField.getInt(cause)
                // HTTP/2 CANCEL error code is 0x8
                if (errorCode == 0x8) {
                    return true
                }
            } catch (ex: Exception) {
                // 如果反射失败，继续使用消息检查
            }
        }
        
        // 也检查异常消息中是否包含取消相关的关键词
        if (message.contains("stream was reset: cancel", ignoreCase = true) ||
            (message.contains("stream was reset") && message.contains("cancel", ignoreCase = true))) {
            return true
        }
        
        cause = cause.cause
    }
    return false
}

private fun mapIOException(e: IOException): NetworkException {
    return when (e) {
        is SocketTimeoutException -> NetworkException.TimeoutException(cause = e)
        is UnknownHostException -> NetworkException.ConnectionException(cause = e)
        is ConnectException -> NetworkException.ConnectionException(cause = e)
        else -> NetworkException.UnknownException(message = "SSE 读取失败", cause = e)
    }
}

