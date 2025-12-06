package com.example.star.aiwork.infra.embedding

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 测试 embedding 模型配置的类。
 * 加载 1.tflite 模型并测试生成 embedding 向量。
 * 
 * @param context Android Context 对象，用于访问应用的 assets 资源。
 *                在 Activity 中可以直接使用 `this`，在 Fragment 中使用 `requireContext()` 或 `context`。
 *                
 * 使用示例：
 * ```
 * // 在 Activity 中：
 * val test = EmbeddingModelTest(this)
 * test.runFullTest()
 * 
 * // 在 Fragment 中：
 * val test = EmbeddingModelTest(requireContext())
 * test.runFullTest()
 * 
 * // 在 Compose 中：
 * val context = LocalContext.current
 * val test = EmbeddingModelTest(context)
 * test.runFullTest()
 * ```
 */
class EmbeddingModelTest(private val context: Context) {
    
    private val TAG = "EmbeddingModelTest"
    private var interpreter: Interpreter? = null
    
    /**
     * 从 assets 文件夹加载模型文件。
     */
    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    
    /**
     * 初始化模型。
     */
    fun initialize() {
        try {
            Log.d(TAG, "开始加载模型: 1.tflite")
            val modelBuffer = loadModelFile("1.tflite")
            interpreter = Interpreter(modelBuffer)
            Log.d(TAG, "模型加载成功")
            
            // 打印模型输入输出信息
            val inputTensorCount = interpreter!!.inputTensorCount
            val outputTensorCount = interpreter!!.outputTensorCount
            
            Log.d(TAG, "输入张量数量: $inputTensorCount")
            Log.d(TAG, "输出张量数量: $outputTensorCount")
            
            for (i in 0 until inputTensorCount) {
                val inputTensor = interpreter!!.getInputTensor(i)
                Log.d(TAG, "输入张量 $i: shape=${inputTensor.shape().contentToString()}, dtype=${inputTensor.dataType()}")
            }
            
            for (i in 0 until outputTensorCount) {
                val outputTensor = interpreter!!.getOutputTensor(i)
                Log.d(TAG, "输出张量 $i: shape=${outputTensor.shape().contentToString()}, dtype=${outputTensor.dataType()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "模型加载失败", e)
            throw e
        }
    }
    
    /**
     * 测试生成 embedding。
     * 注意：这里假设输入是文本的 tokenized 形式，实际使用时需要根据模型要求进行预处理。
     * 
     * @param inputText 输入文本（示例，实际可能需要 tokenization）
     * @return embedding 向量
     */
    fun testEmbedding(inputText: String = "Hello, world!"): FloatArray? {
        val interp = interpreter ?: run {
            Log.e(TAG, "模型未初始化")
            return null
        }
        
        try {
            // 获取输入输出张量信息
            val inputTensor = interp.getInputTensor(0)
            val outputTensor = interp.getOutputTensor(0)
            
            val inputShape = inputTensor.shape()
            val outputShape = outputTensor.shape()
            val inputDataType = inputTensor.dataType()
            val outputDataType = outputTensor.dataType()
            
            Log.d(TAG, "输入形状: ${inputShape.contentToString()}, 数据类型: $inputDataType")
            Log.d(TAG, "输出形状: ${outputShape.contentToString()}, 数据类型: $outputDataType")
            
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
                    return null
                }
            }
            
            // 填充输入数据（根据实际模型调整）
            // 这里使用简单的字符编码作为示例，实际应该使用 tokenizer
            val sequenceLength = if (inputShape.size > 1) inputShape[1] else inputShape[0]
            when (inputDataType) {
                org.tensorflow.lite.DataType.FLOAT32 -> {
                    val floatBuffer = inputBuffer.asFloatBuffer()
                    // 简单的示例：将文本转换为浮点数
                    inputText.map { it.code.toFloat() / 1000f }.take(sequenceLength).forEach { 
                        floatBuffer.put(it) 
                    }
                    // 如果输入长度不足，用0填充
                    while (floatBuffer.position() < inputSize) {
                        floatBuffer.put(0f)
                    }
                }
                org.tensorflow.lite.DataType.INT32 -> {
                    val intBuffer = inputBuffer.asIntBuffer()
                    // 简单的示例：将文本转换为整数
                    inputText.map { it.code }.take(sequenceLength).forEach { 
                        intBuffer.put(it) 
                    }
                    // 如果输入长度不足，用0填充
                    while (intBuffer.position() < inputSize) {
                        intBuffer.put(0)
                    }
                }
                org.tensorflow.lite.DataType.INT8 -> {
                    inputText.map { it.code.toByte() }.take(sequenceLength).forEach { 
                        inputBuffer.put(it) 
                    }
                    // 如果输入长度不足，用0填充
                    while (inputBuffer.position() < inputSize) {
                        inputBuffer.put(0.toByte())
                    }
                }
                org.tensorflow.lite.DataType.UINT8 -> {
                    inputText.map { it.code.toUByte().toByte() }.take(sequenceLength).forEach { 
                        inputBuffer.put(it) 
                    }
                    // 如果输入长度不足，用0填充
                    while (inputBuffer.position() < inputSize) {
                        inputBuffer.put(0.toByte())
                    }
                }
                else -> {
                    Log.e(TAG, "不支持的输入数据类型: $inputDataType")
                    return null
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
                    return null
                }
            }
            
            // 运行推理
            Log.d(TAG, "开始推理...")
            interp.run(inputBuffer, outputBuffer)
            Log.d(TAG, "推理完成")
            
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
                    return null
                }
            }
            
            // 打印结果
            Log.d(TAG, "Embedding 向量维度: ${embedding.size}")
            Log.d(TAG, "Embedding 向量前10个值: ${embedding.take(10).joinToString(", ")}")
            Log.d(TAG, "Embedding 向量后10个值: ${embedding.takeLast(10).joinToString(", ")}")
            Log.d(TAG, "Embedding 向量统计: min=${embedding.minOrNull()}, max=${embedding.maxOrNull()}, mean=${embedding.average()}")
            
            return embedding
        } catch (e: Exception) {
            Log.e(TAG, "生成 embedding 失败", e)
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * 测试多个输入文本。
     */
    fun testMultipleInputs(texts: List<String> = listOf("Hello", "World", "Test")) {
        Log.d(TAG, "开始测试多个输入...")
        texts.forEachIndexed { index, text ->
            Log.d(TAG, "\n=== 测试输入 ${index + 1}: $text ===")
            val embedding = testEmbedding(text)
            if (embedding != null) {
                Log.d(TAG, "输入 ${index + 1} 的 embedding 生成成功，维度: ${embedding.size}")
            } else {
                Log.e(TAG, "输入 ${index + 1} 的 embedding 生成失败")
            }
        }
    }
    
    /**
     * 释放资源。
     */
    fun close() {
        interpreter?.close()
        interpreter = null
        Log.d(TAG, "模型资源已释放")
    }
    
    /**
     * 执行完整的测试流程。
     * 这是一个便捷方法，用于快速测试模型配置。
     */
    fun runFullTest() {
        Log.d(TAG, "========== 开始 Embedding 模型测试 ==========")
        try {
            // 1. 初始化模型
            initialize()
            
            // 2. 测试单个输入
            Log.d(TAG, "\n--- 测试单个输入 ---")
            testEmbedding("Hello, world!")
            
            // 3. 测试多个输入
            Log.d(TAG, "\n--- 测试多个输入 ---")
            testMultipleInputs(listOf("Hello", "World", "Test embedding"))
            
            Log.d(TAG, "\n========== Embedding 模型测试完成 ==========")
        } catch (e: Exception) {
            Log.e(TAG, "测试过程中发生错误", e)
            e.printStackTrace()
        } finally {
            // 4. 释放资源
            close()
        }
    }
}

