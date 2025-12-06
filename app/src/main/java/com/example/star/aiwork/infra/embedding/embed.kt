package com.example.star.aiwork.infra.embedding

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Embedding 服务类，用于生成文本的向量嵌入。
 * 
 * 此类封装了 TensorFlow Lite 模型的加载和推理逻辑，为 data 层提供简单的接口。
 * 
 * @param context Android Context 对象，用于访问应用的 assets 资源。
 * @param modelPath 模型文件路径，默认为 "1.tflite"
 * 
 * 使用示例：
 * ```
 * val embeddingService = EmbeddingService(context)
 * // 在协程中调用
 * val embedding = withContext(Dispatchers.IO) {
 *     embeddingService.embed("Hello, world!")
 * }
 * // 或者在 ViewModel/Repository 中：
 * viewModelScope.launch {
 *     val embedding = embeddingService.embed("Hello, world!")
 * }
 * ```
 */
class EmbeddingService(
    private val context: Context,
    private val modelPath: String = "1.tflite"
) {
    
    private val TAG = "EmbeddingService"
    private var interpreter: Interpreter? = null
    private var isInitialized = false
    
    /**
     * 从 assets 文件夹加载模型文件。
     */
    private fun loadModelFile(path: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(path)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    
    /**
     * 初始化模型。
     * 如果模型已经初始化，则不会重复初始化。
     * 在后台线程执行以避免阻塞 UI。
     */
    private suspend fun ensureInitialized() {
        if (isInitialized && interpreter != null) {
            return
        }
        
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "开始加载模型: $modelPath")
                val modelBuffer = loadModelFile(modelPath)
                interpreter = Interpreter(modelBuffer)
                isInitialized = true
                Log.d(TAG, "模型加载成功")
            } catch (e: Exception) {
                Log.e(TAG, "模型加载失败", e)
                throw e
            }
        }
    }
    
    /**
     * 生成文本的向量嵌入。
     * 
     * 此方法在后台线程执行，不会阻塞 UI 线程。
     * 
     * @param text 输入的文本句子
     * @return 向量嵌入数组，如果生成失败则返回 null
     */
    suspend fun embed(text: String): FloatArray? {
        return withContext(Dispatchers.Default) {
            ensureInitialized()
            
            val interp = interpreter ?: run {
                Log.e(TAG, "模型未初始化")
                return@withContext null
            }
            
            try {
            // 获取输入输出张量信息
            val inputTensor = interp.getInputTensor(0)
            val outputTensor = interp.getOutputTensor(0)
            
            val inputShape = inputTensor.shape()
            val outputShape = outputTensor.shape()
            val inputDataType = inputTensor.dataType()
            val outputDataType = outputTensor.dataType()
            
            // 根据输入数据类型和形状创建输入缓冲区
            val inputSize = inputShape.fold(1) { acc, dim -> acc * dim }
            val inputBuffer = when (inputDataType) {
                org.tensorflow.lite.DataType.FLOAT32 -> {
                    ByteBuffer.allocateDirect(inputSize * 4).order(ByteOrder.nativeOrder())
                }
                org.tensorflow.lite.DataType.INT32 -> {
                    ByteBuffer.allocateDirect(inputSize * 4).order(ByteOrder.nativeOrder())
                }
                org.tensorflow.lite.DataType.INT8 -> {
                    ByteBuffer.allocateDirect(inputSize).order(ByteOrder.nativeOrder())
                }
                org.tensorflow.lite.DataType.UINT8 -> {
                    ByteBuffer.allocateDirect(inputSize).order(ByteOrder.nativeOrder())
                }
                else -> {
                    Log.e(TAG, "不支持的输入数据类型: $inputDataType")
                    return@withContext null
                }
            }
            
            // 填充输入数据
            val sequenceLength = if (inputShape.size > 1) inputShape[1] else inputShape[0]
            when (inputDataType) {
                org.tensorflow.lite.DataType.FLOAT32 -> {
                    val floatBuffer = inputBuffer.asFloatBuffer()
                    text.map { it.code.toFloat() / 1000f }.take(sequenceLength).forEach { 
                        floatBuffer.put(it) 
                    }
                    while (floatBuffer.position() < inputSize) {
                        floatBuffer.put(0f)
                    }
                }
                org.tensorflow.lite.DataType.INT32 -> {
                    val intBuffer = inputBuffer.asIntBuffer()
                    text.map { it.code }.take(sequenceLength).forEach { 
                        intBuffer.put(it) 
                    }
                    while (intBuffer.position() < inputSize) {
                        intBuffer.put(0)
                    }
                }
                org.tensorflow.lite.DataType.INT8 -> {
                    text.map { it.code.toByte() }.take(sequenceLength).forEach { 
                        inputBuffer.put(it) 
                    }
                    while (inputBuffer.position() < inputSize) {
                        inputBuffer.put(0.toByte())
                    }
                }
                org.tensorflow.lite.DataType.UINT8 -> {
                    text.map { it.code.toUByte().toByte() }.take(sequenceLength).forEach { 
                        inputBuffer.put(it) 
                    }
                    while (inputBuffer.position() < inputSize) {
                        inputBuffer.put(0.toByte())
                    }
                }
                else -> {
                    Log.e(TAG, "不支持的输入数据类型: $inputDataType")
                    return@withContext null
                }
            }
            inputBuffer.rewind()
            
            // 准备输出缓冲区
            val outputSize = outputShape.fold(1) { acc, dim -> acc * dim }
            val outputBuffer = when (outputDataType) {
                org.tensorflow.lite.DataType.FLOAT32 -> {
                    ByteBuffer.allocateDirect(outputSize * 4).order(ByteOrder.nativeOrder())
                }
                org.tensorflow.lite.DataType.INT32 -> {
                    ByteBuffer.allocateDirect(outputSize * 4).order(ByteOrder.nativeOrder())
                }
                else -> {
                    Log.e(TAG, "不支持的输出数据类型: $outputDataType")
                    return@withContext null
                }
            }
            
            // 运行推理
            interp.run(inputBuffer, outputBuffer)
            
            // 读取输出结果
            outputBuffer.rewind()
            val embedding = when (outputDataType) {
                org.tensorflow.lite.DataType.FLOAT32 -> {
                    val floatArray = FloatArray(outputSize)
                    outputBuffer.asFloatBuffer().get(floatArray)
                    floatArray
                }
                org.tensorflow.lite.DataType.INT32 -> {
                    val intArray = IntArray(outputSize)
                    outputBuffer.asIntBuffer().get(intArray)
                    intArray.map { it.toFloat() }.toFloatArray()
                }
                else -> {
                    Log.e(TAG, "不支持的输出数据类型: $outputDataType")
                    return@withContext null
                }
            }
            
                Log.d(TAG, "成功生成 embedding，维度: ${embedding.size}")
                embedding
            } catch (e: Exception) {
                Log.e(TAG, "生成 embedding 失败", e)
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * 释放资源。
     */
    fun close() {
        interpreter?.close()
        interpreter = null
        isInitialized = false
        Log.d(TAG, "模型资源已释放")
    }
}

