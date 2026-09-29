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

import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.ja.JapaneseBaseFormFilter
import org.apache.lucene.analysis.ja.JapaneseTokenizer
import org.apache.lucene.analysis.ja.dict.UserDictionary
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute
import org.apache.lucene.analysis.tokenattributes.TypeAttribute
import java.io.StringReader

/**
 * 日语分词结果中的一个词。
 *
 * @param term 词的写法。启用基本形还原后为基本形（「食べました」→「食べる」），
 *             未登录词原样返回
 * @param partOfSpeech 词性，如「名詞」「動詞」
 * @param startIndex 在原句中的起始字符下标（含）
 * @param endIndex 在原句中的结束字符下标（不含）
 */
data class JapaneseToken(
    val term: String,
    val partOfSpeech: String,
    val startIndex: Int,
    val endIndex: Int
)

/**
 * 日语文本分词器。
 *
 * 底层为 Apache Lucene 的 Kuromoji（`org.apache.lucene:lucene-analysis-kuromoji`）：
 * 纯 JVM 实现，IPADIC 派生词典已内嵌在 jar 中，
 * 无需在构建机安装 Python 或编译 C++，可直接随 AppImage / dmg / msi 打包。
 *
 * 与英语链路使用的 Apache OpenNLP 完全独立，不影响既有功能。
 *
 * 分词默认还原为基本形：日语词库以汉字写法为词条（「食べる」），
 * 而字幕里通常是活用形（「食べました」），查词需要用基本形才对得上。
 *
 * 用法：
 * ```
 * val terms = JapaneseWordSegmenter.tokenize("昨日は映画を見ました。")
 * // [昨日, 映画, 見る]
 * ```
 */
object JapaneseWordSegmenter {

    /**
     * Kuromoji 的 Tokenizer 内部持有可变状态且非线程安全，
     * 因此每次调用新建实例并串行化；词典数据本身是只读的，不会重复加载内存占用。
     */
    private val lock = Any()

    /** 是否已打印过分词失败堆栈，避免逐句刷屏 */
    @Volatile
    private var failureReported: Boolean = false

    /**
     * 对日语文本分词，并还原为基本形。
     *
     * @param text 日语文本，通常是一行字幕
     * @return 词列表，按出现顺序
     */
    fun tokenize(text: String): List<JapaneseToken> {
        if (text.isBlank()) return emptyList()
        return try {
            synchronized(lock) { tokenizeInternal(text) }
        } catch (e: Throwable) {
            // 词典缺失属于环境问题（词典已内嵌于 jar，正常不会发生）。
            // 只在首次失败时打印堆栈，避免逐句刷屏。
            if (!failureReported) {
                e.printStackTrace()
                failureReported = true
            }
            fallbackTokenize(text)
        }
    }

    private fun tokenizeInternal(text: String): List<JapaneseToken> {
        // JapaneseTokenizer 没有无参构造，且 UserDictionary 只有私有构造，
        // 公开入口是 open(Reader)。传空 Reader 即表示不启用用户自定义词典。
        // discardPunctuation = false：保留标点，字幕断句与词性判断都需要。
        // Mode.NORMAL：普通分词，不做搜索模式或复合词二次切分。
        val tokenizer = JapaneseTokenizer(
            UserDictionary.open(StringReader("")),
            false,
            JapaneseTokenizer.Mode.NORMAL
        )
        // JapaneseBaseFormFilter 会把表层形式改写为基本形：
        // 「食べました」→「食べる」。词典外的未知词保持原样。
        val stream: TokenStream = JapaneseBaseFormFilter(tokenizer)

        val termAttr = stream.addAttribute(CharTermAttribute::class.java)
        val offsetAttr = stream.addAttribute(OffsetAttribute::class.java)
        val typeAttr = stream.addAttribute(TypeAttribute::class.java)

        val tokens = mutableListOf<JapaneseToken>()
        try {
            // setReader 定义在 Tokenizer 上而非 TokenStream，
            // 且必须在包装成 filter 之前设定，否则过滤器读到的是空输入。
            tokenizer.setReader(StringReader(text))
            stream.reset()
            while (stream.incrementToken()) {
                tokens.add(
                    JapaneseToken(
                        term = termAttr.toString(),
                        partOfSpeech = typeAttr.type().orEmpty(),
                        startIndex = offsetAttr.startOffset(),
                        endIndex = offsetAttr.endOffset()
                    )
                )
            }
            stream.end()
        } finally {
            stream.close()
        }
        return tokens
    }

    /**
     * 取去重后的基本形列表，用于与词库匹配。
     * 「食べました」「食べます」「食べる」都会归并为「食べる」。
     */
    fun tokenizeToBaseForms(text: String): List<String> =
        tokenize(text).map { it.term }
            .filter { it.isNotBlank() && it.any { c -> !c.isWhitespace() } }
            .distinct()

    /**
     * 分词器不可用时的降级实现：逐字切分。
     * 质量远不如真正分词，仅保证词库生成流程不中断。
     */
    private fun fallbackTokenize(text: String): List<JapaneseToken> =
        text.mapIndexed { index, ch ->
            JapaneseToken(
                term = ch.toString(),
                partOfSpeech = "未知語",
                startIndex = index,
                endIndex = index + 1
            )
        }
}
