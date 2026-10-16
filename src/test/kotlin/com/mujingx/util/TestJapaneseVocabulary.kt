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

package com.mujingx.util

import com.mujingx.data.JapaneseDictionary
import com.mujingx.ffmpeg.findFFmpegPath
import com.mujingx.ui.util.JapaneseWordSegmenter
import com.mujingx.ui.util.parseJapaneseASS
import com.mujingx.ui.util.parseJapaneseSRT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 日语「影视字幕背单词」链路的端到端用例。
 *
 * 与英语链路的关键差别（AGENTS.md §8）：
 *  - 语言按词库的 `language` 字段分派，不用字符范围判断；
 *  - 入词库前用 JMdict 基本形还原（食べました → 食べる）；
 *  - ASS 字幕不能直接按 SRT 解析，必须先转 SRT（这是「选了 N1～N5 却
 *    没有发现词语」的直接原因）。
 *
 * 依赖 `resources/common/dictionary/jadic.db`（未入库）与 ffmpeg（打包在
 * 项目里）的用例在缺失时跳过，避免在别的机器上误报失败。
 */
class TestJapaneseVocabulary {

    /** 纯 Kotlin：表层写法用于显示/复制/发音，基本形用于查词。 */
    @Test
    fun `Test Japanese Tokenize Surface Form`() {
        val tokens = JapaneseWordSegmenter.tokenizeSurface("私はご飯を食べました。")
        val surfaces = tokens.map { it.surface }
        val baseForms = tokens.map { it.baseForm }
        println("surface=$surfaces")
        println("baseForm=$baseForms")

        assertTrue("分词结果不应为空", tokens.isNotEmpty())

        // 「食べました」是活用形，查词要用基本形「食べる」，
        // 但显示/复制/发音要用字幕里的表层写法。
        val index = baseForms.indexOf("食べる")
        assertTrue("应还原出基本形「食べる」，实际：$baseForms", index >= 0)
        assertEquals("食べました", surfaces[index])
    }

    @Test
    fun `Test Parse Japanese SRT To Vocabulary`() {
        assumeTrue("需要 jadic.db：resources/common/dictionary/jadic.db", JapaneseDictionary.isAvailable())

        val result = parseJapaneseSRT(
            pathName = "src/test/resources/japanese.srt",
            setProgressText = { println(it) }
        )
        result.forEach { println("${it.value}  假名=${it.kana}  罗马字=${it.romaji}  中文=${it.translation}") }

        assertTrue("日语 SRT 应能生成词库", result.isNotEmpty())
        // 词条应带假名（jadic 的 reading）
        assertTrue("日语词条应有假名", result.any { it.kana.isNotEmpty() })
    }

    /** 回归用户报的 bug：日语 ASS 字幕点「生成」后一个词都没有。 */
    @Test
    fun `Test Parse Japanese ASS To Vocabulary`() {
        assumeTrue("需要 jadic.db：resources/common/dictionary/jadic.db", JapaneseDictionary.isAvailable())
        assumeTrue("需要 ffmpeg 做 ASS → SRT", File(findFFmpegPath()).exists())

        val result = parseJapaneseASS(
            pathName = "src/test/resources/japanese.ass",
            setProgressText = { println(it) }
        )
        result.forEach { println("${it.value}  假名=${it.kana}  罗马字=${it.romaji}  中文=${it.translation}") }

        assertTrue("日语 ASS 应先转 SRT 再生成词库，不应为空", result.isNotEmpty())
    }
}
