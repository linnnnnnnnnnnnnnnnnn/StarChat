package com.example.star.aiwork.conversation

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 有道智云实时语音翻译 (Stream Speech Translation) WebSocket 客户端封装。
 *
 * 该类负责与有道 API 建立 WebSocket 连接，发送音频流，并接收识别和翻译结果。
 * 文档参考: https://ai.youdao.com/DOCSIRMA.html?p=2&a=trans
 *
 * @property listener 用于接收转录结果和错误的回调接口。
 */
class YoudaoWebSocket(private val listener: TranscriptionListener) {

    private var webSocket: WebSocket? = null
    
    // 缓存最后一次的部分识别结果，以便在连接关闭时将其作为最终结果返回
    private var lastPartialResult: String = ""
    
    // 配置 OkHttpClient，设置读写和连接超时时间
    private val client = OkHttpClient.Builder()
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    // TODO: 请替换为您真实的 App Key 和 App Secret
    // 您可以在有道智云控制台创建应用并获取这些信息
    private val APP_KEY = "1fa9647ca43dd17a"
    private val APP_SECRET = "adcF7pXU5MK2yfzVRN5OfJSSUVsIpLEg"

    /**
     * 转录结果监听器接口。
     */
    interface TranscriptionListener {
        /**
         * 当收到识别结果时调用。
         *
         * @param text 识别出的文本内容。
         * @param isFinal 是否为最终结果（true 表示该句已结束，false 表示是中间结果）。
         */
        fun onResult(text: String, isFinal: Boolean)

        /**
         * 当发生错误时调用。
         *
         * @param t 异常对象。
         */
        fun onError(t: Throwable)
    }

    /**
     * 建立 WebSocket 连接。
     *
     * 此方法会计算鉴权签名，构建请求 URL，并初始化 WebSocket 连接。
     * 连接参数说明：
     * - from: 源语言 (zh-CHS 中文)。
     * - to: 目标语言 (en 英文)。
     * - rate: 采样率 (16000Hz)。
     * - format: 音频格式 (wav)。
     * - channel: 声道数 (1)。
     * - type: 签名类型 (v4)。
     * - transPattern: 传输模式 (stream 流式)。
     */
    fun connect() {
        val nonce = UUID.randomUUID().toString()
        val curTime = (System.currentTimeMillis() / 1000).toString()
        val sign = calculateSign(APP_KEY, nonce, curTime, APP_SECRET)

        // 有道实时语音翻译 API 地址
        val url = "wss://openapi.youdao.com/stream_speech_trans?appKey=$APP_KEY&salt=$nonce&curtime=$curTime&sign=$sign&signType=v4&from=zh-CHS&to=en&rate=16000&format=wav&channel=1&version=v1&transPattern=stream"

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket 已打开")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "收到消息: $text")
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket 正在关闭: $reason")
                // 如果在关闭时有挂起的部分结果，将其作为最终结果发送
                if (lastPartialResult.isNotEmpty()) {
                    Log.d(TAG, "关闭时发送最后的部分结果: $lastPartialResult")
                    listener.onResult(lastPartialResult, true)
                    lastPartialResult = ""
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket 失败: ${response?.message}", t)
                listener.onError(t)
            }
        })
    }

    /**
     * 处理从服务器接收到的 JSON 消息。
     *
     * 解析 JSON，提取识别结果或处理错误信息。
     *
     * @param text 服务器返回的 JSON 字符串。
     */
    private fun handleMessage(text: String) {
        try {
            val jsonObject = JSONObject(text)
            val action = jsonObject.optString("action")
            
            if (action == "recognition") {
                // 处理识别结果
                val resultObj = jsonObject.optJSONObject("result")
                if (resultObj != null) {
                    // 新版 API 返回结构
                    val currentText = extractText(resultObj)
                    val isFinal = !resultObj.optBoolean("partial")
                    
                    if (isFinal) {
                        lastPartialResult = "" // 收到最终结果，清除部分结果缓存
                    } else {
                        lastPartialResult = currentText // 缓存部分结果
                    }
                    listener.onResult(currentText, isFinal)
                    
                } else {
                    // 处理旧版 API 返回结构 (数组形式)
                    val resultArray = jsonObject.optJSONArray("result")
                    if (resultArray != null && resultArray.length() > 0) {
                        for (i in 0 until resultArray.length()) {
                            val item = resultArray.getJSONObject(i)
                            val currentText = extractText(item)
                            val isFinal = !item.optBoolean("partial")
                            
                            if (isFinal) {
                                lastPartialResult = ""
                            } else {
                                lastPartialResult = currentText
                            }
                            listener.onResult(currentText, isFinal)
                        }
                    }
                }
            } else if (action == "error") {
                // 处理 API 错误
                val code = jsonObject.optString("errorCode")
                listener.onError(RuntimeException("API Error: $code - $text"))
            }
        } catch (e: Exception) {
            listener.onError(e)
        }
    }

    /**
     * 从 JSON 对象中提取文本内容。
     *
     * 根据 API 返回字段的优先级尝试获取内容：
     * 1. context: 识别出的源语言文本 (中文识别)。
     * 2. txt: 通用文本字段。
     * 3. tranContent: 翻译内容。
     * 4. trans: 翻译内容 (备用)。
     *
     * @param json 包含结果信息的 JSONObject。
     * @return 提取出的文本字符串。
     */
    private fun extractText(json: JSONObject): String {
        // 优先获取 'context' (识别出的文本)，因为用户需求通常是“中文识别”
        var text = json.optString("context")
        if (text.isNotEmpty()) return text

        // 回退到 'txt'
        text = json.optString("txt")
        if (text.isNotEmpty()) return text
        
        // 回退到 'tranContent' (翻译结果)
        text = json.optString("tranContent")
        if (text.isNotEmpty()) return text
        
        // 回退到 'trans'
        text = json.optString("trans")
        if (text.isNotEmpty()) return text
        
        return ""
    }

    /**
     * 发送音频数据。
     *
     * 将 PCM 音频数据转换为 ByteString 并通过 WebSocket 发送。
     *
     * @param data 音频数据字节数组。
     * @param len 有效数据长度。
     */
    fun sendAudio(data: ByteArray, len: Int) {
        webSocket?.send(data.toByteString(0, len))
    }

    /**
     * 关闭 WebSocket 连接。
     */
    fun close() {
        webSocket?.close(1000, "User closed")
        webSocket = null
    }

    /**
     * 计算鉴权签名 (SignType=v4)。
     *
     * 算法: sha256(appKey + salt + curtime + appSecret)
     *
     * @param appKey 应用 ID。
     * @param salt 随机盐 (UUID)。
     * @param curTime 当前时间戳 (秒)。
     * @param appSecret 应用密钥。
     * @return 计算出的 SHA-256 签名字符串。
     */
    private fun calculateSign(appKey: String, salt: String, curTime: String, appSecret: String): String {
        val str = appKey + salt + curTime + appSecret
        return sha256(str)
    }

    /**
     * 计算字符串的 SHA-256 哈希值。
     *
     * @param str 输入字符串。
     * @return 十六进制格式的哈希字符串。
     */
    private fun sha256(str: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(str.toByteArray())
        val hexString = StringBuilder()
        for (b in hash) {
            val hex = Integer.toHexString(0xff and b.toInt())
            if (hex.length == 1) hexString.append('0')
            hexString.append(hex)
        }
        return hexString.toString()
    }

    companion object {
        private const val TAG = "YoudaoWebSocket"
    }
}
