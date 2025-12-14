package com.example.star.aiwork.data.database

import android.content.Context
import android.util.Log

data class RetrievalResult(
    val context: String,
    val debugLog: String
)

class LocalRAGService(private val context: Context, private val dao: KnowledgeDao) {
    

    // 2. 检索 (Recall + Re-rank)
    suspend fun retrieve(query: String): RetrievalResult {
        if (query.isBlank()) return RetrievalResult("", "")
        try {
            // A. 预处理查询
            val ftsQuery = formatFtsQuery(query) 
            
            // B. 召回 (Recall): 获取 Top 20 候选
            // 注意：candidates 的顺序就是 FTS 认为的顺序 (基于 BM25 等)
            val candidates = dao.search(ftsQuery)
            
            if (candidates.isEmpty()) {
                 return RetrievalResult("", "No results found for query: $query")
            }

            // C. 重排序 (Re-ranking): 内存中精细打分
            val queryTerms = extractQueryTerms(query)
            
            // 我们创建一个包含 (Chunk, Score, OriginalRank) 的列表
            val scoredCandidates = candidates.mapIndexed { index, chunk ->
                val score = calculateRelevanceScore(queryTerms, chunk.content)
                Triple(chunk, score, index + 1) // index+1 是原始 FTS 排名
            }

            // 按照分数降序排序
            val topResults = scoredCandidates
                .sortedByDescending { it.second } 
                .take(5)
            
            // D. 构建上下文 (Context Construction)
            val context = topResults.map { it.first }
                .distinctBy { it.content }
                .joinToString("\n\n---\n\n") { chunk ->
                    "【来源: ${chunk.sourceFilename}】\n${chunk.content}"
                }

            // E. 构建直观的分析日志 (Visual Debug Log)
            val logBuilder = StringBuilder()
            logBuilder.append("\n\n💡 [RAG 算法分析面板]\n")
            logBuilder.append("--------------------------------------------------\n")
            logBuilder.append("🔍 提取关键词: ${queryTerms.joinToString(", ")}\n")
            logBuilder.append("📊 召回数量: ${candidates.size} (FTS), 精选: ${topResults.size} (Re-rank)\n\n")
            
            topResults.forEachIndexed { i, (chunk, score, originalRank) ->
                val rankChange = if (originalRank > (i + 1)) "⬆️(原#$originalRank)" else "-(原#$originalRank)"
                // 截取内容预览
                val preview = chunk.content.replace("\n", " ").take(30) + "..."
                
                logBuilder.append("${i + 1}. [Score: ${"%.2f".format(score)}] $rankChange\n")
                logBuilder.append("   📄 ${chunk.sourceFilename}\n")
                logBuilder.append("   📝 \"$preview\"\n")
            }
            logBuilder.append("--------------------------------------------------")

            // 打印日志到 Logcat
            Log.d("LocalRAGService", logBuilder.toString())

            return RetrievalResult(context, logBuilder.toString())

        } catch (e: Exception) {
            Log.e("LocalRAGService", "Error retrieving context", e)
            return RetrievalResult("", "Error: ${e.message}")
        }
    }
    
    private fun formatFtsQuery(query: String): String {
        val sanitized = query.replace(Regex("[^\\w\\s\\u4e00-\\u9fa5]"), " ")
        val words = sanitized.trim().split("\\s+".toRegex())
        return words.filter { it.isNotBlank() }.joinToString(" OR ") { "$it*" }
    }

    private fun extractQueryTerms(query: String): Set<String> {
        return query.lowercase()
            .split(Regex("[^a-zA-Z0-9\u4e00-\u9fa5]+"))
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun calculateRelevanceScore(queryTerms: Set<String>, content: String): Double {
        if (queryTerms.isEmpty()) return 0.0
        val contentLower = content.lowercase()
        
        val matchedTermsCount = queryTerms.count { term ->
            contentLower.contains(term)
        }
        
        val coverage = matchedTermsCount.toDouble() / queryTerms.size
        return coverage
    }
    
    suspend fun clearAll() {
        dao.clearAll()
    }
}
