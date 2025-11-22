package com.example.star.aiwork.ai.util

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.example.star.aiwork.ai.provider.CustomBody
import com.example.star.aiwork.ai.provider.CustomHeader
import com.example.star.aiwork.ai.provider.ProviderProxy
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 扩展函数：挂起等待 OkHttp 的 Call 执行完成。
 *
 * 将异步的 OkHttp Call 转换为 Kotlin 协程的挂起函数。
 * 支持取消操作。
 */
suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }
        })

        continuation.invokeOnCancellation {
            try {
                cancel()
            } catch (ex: Throwable) {
                // Ignore cancel exception
            }
        }
    }
}

/**
 * 扩展函数：配置 OkHttpClient 的代理设置。
 *
 * @param proxySetting 代理配置对象 (ProviderProxy)。
 * @return 如果配置了代理，则返回一个新的 OkHttpClient 实例，否则返回原实例。
 */
fun OkHttpClient.configureClientWithProxy(proxySetting: ProviderProxy): OkHttpClient {
    return when (proxySetting) {
        is ProviderProxy.None -> this
        is ProviderProxy.Http -> {
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(proxySetting.address, proxySetting.port))
            this.newBuilder()
                .proxy(proxy)
                // 如果需要，可以在此处添加身份验证逻辑
                .build()
        }
    }
}

/**
 * 扩展函数：将 CustomHeader 列表转换为 OkHttp 的 Headers 对象。
 */
fun List<CustomHeader>.toHeaders(): Headers {
    val builder = Headers.Builder()
    forEach { builder.add(it.name, it.value) }
    return builder.build()
}

/**
 * 扩展函数：将自定义请求体参数合并到现有的 JsonObject 中。
 *
 * 用于在发送请求时，将用户配置的额外参数注入到 JSON Body 中。
 */
fun JsonObject.mergeCustomBody(customBody: List<CustomBody>): JsonObject {
    if (customBody.isEmpty()) return this
    return buildJsonObject {
        // 添加原始键值对
        this@mergeCustomBody.forEach { (k, v) -> put(k, v) }
        // 添加或覆盖自定义 Body 参数
        customBody.forEach {
            put(it.key, it.value)
        }
    }
}

/**
 * 扩展函数：通过点号分隔的路径获取 JsonObject 中的字符串值。
 *
 * 例如：path 为 "data.total_usage" 将获取嵌套对象 data 中的 total_usage 字段。
 *
 * @param path 键路径。
 * @return 获取到的字符串值，如果未找到则返回空字符串。
 */
fun JsonObject.getByKey(path: String): String {
    val keys = path.split(".")
    var current: JsonElement? = this
    for (key in keys) {
        if (current is JsonObject) {
            current = current[key]
        } else {
            return ""
        }
    }
    return current?.jsonPrimitive?.content ?: ""
}

/**
 * 扩展属性：安全地获取 jsonPrimitive，如果不是 Primitive 类型则返回 null。
 */
val JsonElement.jsonPrimitiveOrNull
    get() = try {
        jsonPrimitive
    } catch (e: IllegalArgumentException) {
        null
    }
