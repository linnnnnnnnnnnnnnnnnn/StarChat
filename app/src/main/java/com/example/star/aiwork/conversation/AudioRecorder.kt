package com.example.star.aiwork.conversation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.app.ActivityCompat

/**
 * 音频录制器类。
 *
 * 该类封装了 Android 的 [AudioRecord] API，用于从麦克风捕获原始音频数据 (PCM)。
 * 它配置为以 16kHz 采样率、单声道、16位 PCM 格式进行录制，这通常是语音识别 (ASR) 系统所需的格式。
 *
 * @param context Android 上下文，用于检查权限。
 */
class AudioRecorder(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    
    // 标志位，用于控制录制线程的运行
    private var isRecording = false
    
    // 采样率：16000Hz (16kHz)，这是大多数语音识别引擎的标准采样率
    private val sampleRate = 16000
    
    // 声道配置：单声道 (Mono)
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    
    // 音频格式：16位 PCM 编码
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    
    // 计算最小缓冲区大小，确保 AudioRecord 对象能成功创建
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    /**
     * 开始录制音频。
     *
     * 此方法会初始化 [AudioRecord]，启动录制，并开启一个后台线程不断读取音频缓冲区数据。
     * 读取到的数据通过 [onAudioData] 回调返回。
     *
     * 注意：在调用此方法之前，必须确保已授予 [Manifest.permission.RECORD_AUDIO] 权限。
     *
     * @param onAudioData 当读取到音频数据时的回调。(ByteArray: 音频数据, Int: 读取到的字节数)
     * @param onError 当发生初始化错误或读取错误时的回调。
     */
    fun startRecording(onAudioData: (ByteArray, Int) -> Unit, onError: (Exception) -> Unit) {
        // 双重检查权限，虽然调用方应该已经处理，但作为安全措施
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // 权限应由 UI 在调用此之前请求
            return
        }

        try {
            // 初始化 AudioRecord 对象
            // AudioSource.MIC: 指定麦克风为音频源
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            // 检查初始化状态
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onError(IllegalStateException("AudioRecord initialization failed"))
                return
            }

            // 开始录制
            audioRecord?.startRecording()
            isRecording = true

            // 启动后台线程读取数据，避免阻塞主线程
            Thread {
                val buffer = ByteArray(bufferSize)
                while (isRecording) {
                    // 从硬件设备读取音频数据到缓冲区
                    // read 方法会阻塞，直到读取到数据
                    val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                    if (read > 0) {
                        // 成功读取数据，通过回调发送
                        onAudioData(buffer, read)
                    } else if (read < 0) {
                        // AudioRecord.read 返回负数表示发生了错误
                        onError(RuntimeException("AudioRecord read failed: $read"))
                        isRecording = false // 发生错误时停止循环
                    }
                }
            }.start()
        } catch (e: Exception) {
            onError(e)
        }
    }

    /**
     * 停止录制并释放资源。
     *
     * 将 [isRecording] 标志设为 false 以停止读取循环，
     * 然后停止 [AudioRecord] 并释放相关资源。
     */
    fun stopRecording() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null
    }
}
