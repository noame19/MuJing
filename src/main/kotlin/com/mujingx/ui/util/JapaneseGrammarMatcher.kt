/*
 * Copyright (c) 2023-2025 tang shimin
 *
 * This file is part of MuJing.
 *
 * MuJing is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MuJing is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MuJing. If not, see <https://www.gnu.org/licenses/>.
 */

package com.mujingx.ui.util

import com.mujingx.state.getResourcesFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 一个语法条目。
 *
 * @param id 唯一标识，如「N2-3」
 * @param level 等级，N5 到 N1
 * @param patterns 可匹配的表面形式。上游用「〜」表示词尾变化的位置，
 *                 例如「〜ない」实际会匹配「食べない」
 * @param meaning 语法含义（英文）
 * @param formation 接续方式
 * @param examples 例句
 */
@Serializable
data class GrammarPoint(
    val id: String,
    val level: String,
    val patterns: List<String>,
    val meaning: String = "",
    val formation: String = "",
    val examples: List<GrammarExample> = emptyList(),
    val tags: List<String> = emptyList()
)

/** 语法例句 */
@Serializable
data class GrammarExample(
    val ja: String = "",
    val en: String = ""
)

/** 语法库文件 */
@Serializable
private data class GrammarLibrary(
    val name: String = "",
    val level: String = "",
    val size: Int = 0,
    val grammarList: List<GrammarPoint> = emptyList()
)

/**
 * 字幕中命中的一段语法。
 *
 * @param point 对应的语法条目
 * @param matchedText 字幕里实际命中的片段，如「食べさせられた」
 * @param startIndex 在原句中的起始下标
 * @param endIndex 在原句中的结束下标
 */
data class GrammarMatch(
    val point: GrammarPoint,
    val matchedText: String,
    val startIndex: Int,
    val endIndex: Int
)

/**
 * 日语语法匹配引擎。
 *
 * 从字幕文本中检出语法点，供「结合视频学语法」使用。
 *
 * 工作方式：
 * 1. 加载内置语法库（JLPT N5-N1，共 100 条）
 * 2. 把每个 pattern 编译成正则，其中「〜」表示词尾变化，
 *    转为「任意日语文本」；其余部分按字面匹配
 * 3. 在字幕文本上扫描，收集全部命中
 *
 * **关于准确率**：本引擎只做「候选检出」，不判断语法意义。
 * 例如「〜と思います」既可能是「我认为…」（陈述），
 * 也可能是「你觉得…如何？」（疑问），字面匹配无法区分。
 * 因此界面上的提示应理解为「这里可能用到了某个语法点」，
 * 由用户结合上下文判断。
 */
object JapaneseGrammarMatcher {

    private const val GRAMMAR_DIR = "vocabulary/日语语法"

    /** 语法条目 -> 编译后的匹配器 */
    private class CompiledPoint(val point: GrammarPoint, val matcher: PatternMatcher)

    private val grammarDir: File by lazy { getResourcesFile(GRAMMAR_DIR) }

    private val compiled: List<CompiledPoint> by lazy { loadGrammarPoints() }

    /** 语法库是否可用 */
    val isAvailable: Boolean get() = compiled.isNotEmpty()

    /** 已加载的语法条目数量 */
    val size: Int get() = compiled.size

    /**
     * 加载并编译全部语法条目。
     * 文件缺失或解析失败时返回空列表，界面据此隐藏语法功能入口。
     */
    private fun loadGrammarPoints(): List<CompiledPoint> {
        val dir = grammarDir
        if (!dir.isDirectory) return emptyList()

        val json = Json { ignoreUnknownKeys = true }
        val result = mutableListOf<CompiledPoint>()

        dir.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.sortedBy { it.name }
            ?.forEach { file ->
                try {
                    val library = json.decodeFromString<GrammarLibrary>(file.readText())
                    library.grammarList.forEach { point ->
                        val matcher = PatternMatcher.compile(point.patterns)
                        if (matcher.isUsable) {
                            result.add(CompiledPoint(point, matcher))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        return result
    }

    /**
     * 把「〜」占位符形式的语法模式编译为可执行的匹配器。
     *
     * **为什么不用字符正则**：日语语法点紧跟在词干之后，
     * 而词干长度不定（「食べない」「行かない」都是「ない」前的活用）。
     * 若用「〜」匹配任意字符再拼上固定尾巴，正则会一路向左吞掉整句
     * （「明日の朝早く起きなければなりません」会被整体命中），
     * 既得不到准确的片段位置，也会连带产生大量误报。
     *
     * 改为在**词序列**上匹配：先用 Kuromoji 把句子切成词，
     * 再检查各词在还原为基本形后是否满足模式的固定部分。
     * 这样「〜」自然对应「任意个前置词」，边界准确。
     */
    class PatternMatcher private constructor(
        /** 去掉「〜」后的尾部字面形式 */
        private val tails: List<String>,
        /** 「〜」之前是否还有固定文字（少数模式以实词开头） */
        private val hasLeadingLiteral: Boolean
    ) {
        val isUsable: Boolean get() = tails.isNotEmpty()

        /**
         * 在已分词的句子上尝试匹配。
         *
         * @param terms 词形序列（Kuromoji 还原后的基本形）
         * @return 命中起点下标；未命中返回 -1
         */
        fun findStart(terms: List<String>): Int {
            if (tails.isEmpty() || terms.isEmpty()) return -1

            for (tail in tails) {
                val start = findTail(terms, tail)
                if (start >= 0) return start
            }
            return -1
        }

        /**
         * 在词序列中找 tail 的出现位置，并回溯到承载活用形的那一个词。
         *
         * 语法尾巴往往跨多个词（如「なければなりません」= なければ + なり + ません），
         * 因此先在「词的拼接串」上定位，再换算回词的下标。
         */
        private fun findTail(terms: List<String>, tail: String): Int {
            val joined = terms.joinToString("")
            val at = joined.indexOf(tail)
            if (at < 0) return -1

            // 把字符下标换算成词下标
            var chars = 0
            for (i in terms.indices) {
                val end = chars + terms[i].length
                if (end > at) return i
                chars = end
            }
            return -1
        }
    }

    companion object {
        fun compile(patterns: List<String>): PatternMatcher {
            val tails = mutableListOf<String>()
            var hasLeadingLiteral = false

            for (raw in patterns) {
                val cleaned = raw.trim()
                if (cleaned.isEmpty()) continue

                val head = cleaned.takeWhile { it != '〜' && it != '~' }
                val tail = cleaned.dropWhile { it != '〜' && it != '~' }
                    .drop(1)   // 去掉「〜」本身
                    .trim()

                // 尾部是判定语法身份的关键；尾部为空说明该模式无法用于匹配
                if (tail.isEmpty()) continue
                if (head.isNotEmpty()) hasLeadingLiteral = true

                // 「〜がいます / 〜があります」已在构建期按 / 拆分；
                // 这里再按「或」处理一次，兼容含顿号的写法
                tail.split('、', '/').map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { tails.add(it) }
            }
            return PatternMatcher(tails.toList(), hasLeadingLiteral)
        }
    }

    /**
     * 在一段文本中检出全部语法点。
     *
     * 流程：先用 Kuromoji 分词并还原基本形，再逐条语法在词序列上比对。
     *
     * @param text 通常是一行字幕
     * @return 命中列表，按出现位置排序；同一条语法在一句内只记一次
     */
    fun match(text: String): List<GrammarMatch> {
        if (text.isBlank() || compiled.isEmpty()) return emptyList()

        val tokens = JapaneseWordSegmenter.tokenize(text)
        if (tokens.isEmpty()) return emptyList()
        val terms = tokens.map { it.term }

        val matches = mutableListOf<GrammarMatch>()

        for (item in compiled) {
            // 一条语法在一句话里命中一次即可，取最后一次出现的位置：
            // 字幕常常一句里多次出现同一语法，靠后的一般更接近句意焦点。
            val tailStart = item.matcher.findStart(terms)
            if (tailStart < 0) continue

            // 语法点从「承载活用形的那一个词」开始，
            // 即尾部匹配点往前回溯一个词，避免把整句都算成语法片段。
            val from = if (tailStart > 0) tailStart - 1 else 0
            val to = (tailStart + MAX_SPAN_TOKENS).coerceAtMost(tokens.size)
            val matchedText = terms.subList(from, to).joinToString("")

            val startIndex = tokens[from].startIndex
            val endIndex = tokens[to - 1].endIndex
            matches.add(
                GrammarMatch(
                    point = item.point,
                    matchedText = matchedText,
                    startIndex = startIndex,
                    endIndex = endIndex
                )
            )
        }
        return matches.sortedBy { it.startIndex }
    }

    /** 语法点最多覆盖多少个词，避免尾部较短时向前吞掉整个句子 */
    private const val MAX_SPAN_TOKENS = 4

    /**
     * 按等级取语法条目，供语法浏览界面使用。
     */
    fun pointsByLevel(level: String): List<GrammarPoint> =
        compiled.map { it.point }.filter { it.level.equals(level, ignoreCase = true) }

    /** 全部语法条目 */
    fun allPoints(): List<GrammarPoint> = compiled.map { it.point }
}
