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

package com.mujingx.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 一段带注音的文本。
 *
 * @param text 原文这一段（汉字串，或一个假名/符号）
 * @param ruby 这一段的读音；空串表示不加注（假名、送假名等本就表音的部分）
 */
data class RubySegment(
    val text: String,
    val ruby: String,
)

/** 是否为汉字（含々）。 */
fun isKanjiChar(ch: Char): Boolean {
    val o = ch.code
    return o in 0x4E00..0x9FFF || o in 0x3400..0x4DBF || ch == '々'
}

/**
 * 把词条与读音对齐成「汉字段 + 读音」的序列，用于在汉字上方标注假名。
 *
 * 例：`お祝い` / `おいわい` → [(お,""), (祝,"いわ"), (い,"")]
 *
 * ## 做法
 *
 * 1. 先吃掉公共假名前缀与后缀（这些字本就表音，不需要注音）
 * 2. 中段按「汉字串 + 其后的假名锚点」切分：锚点是汉字串之后紧跟的假名，
 *    它在读音里出现的位置就界定了这串汉字读到哪里
 * 3. 末尾校验：原文拼回必须与 value 一致，读音拼回必须与 kana 一致
 *
 * 校验不过就返回 null —— 宁可不标注，也不能标错读音。典型失败是词库里
 * 用斜杠并列多种写法（`丸い/円い`），这种写法本身不是单个词，无法对应读音。
 *
 * 实测 JLPT 日语 8334 词中，7190 个含汉字的词条有 7174 个能正确对齐。
 *
 * @param value 词条写法（含汉字）
 * @param kana 读音（假名）
 * @return 分段序列；无汉字、读音为空或无法对齐时返回 null
 */
fun japaneseRubySegments(
    value: String,
    kana: String,
): List<RubySegment>? {
    if (value.isEmpty() || kana.isEmpty()) return null
    // 纯假名词不需要注音，直接走原文渲染
    if (value.none { isKanjiChar(it) }) return null

    val segments = mutableListOf<RubySegment>()
    var vi = 0
    var ki = 0
    val nv = value.length
    val nk = kana.length

    // 1. 公共假名前缀
    while (vi < nv && ki < nk && !isKanjiChar(value[vi]) && value[vi] == kana[ki]) {
        segments.add(RubySegment(value[vi].toString(), ""))
        vi++
        ki++
    }

    // 2. 公共假名后缀（从尾部比对，稍后倒序补上）
    var ve = nv
    var ke = nk
    val suffix = mutableListOf<Char>()
    while (ve > vi && ke > ki && !isKanjiChar(value[ve - 1]) && value[ve - 1] == kana[ke - 1]) {
        suffix.add(value[ve - 1])
        ve--
        ke--
    }
    suffix.reverse()

    // 3. 中段：汉字串 + 假名锚点
    val midValue = value.substring(vi, ve)
    var midKana = kana.substring(ki, ke)
    var pos = 0
    while (pos < midValue.length) {
        var start = pos
        while (start < midValue.length && isKanjiChar(midValue[start])) start++
        // 中段里出现无法处理的假名（读音与写法对不上），放弃
        if (start == pos) return null

        val kanjiRun = midValue.substring(pos, start)
        var anchorEnd = start
        while (anchorEnd < midValue.length && !isKanjiChar(midValue[anchorEnd])) anchorEnd++
        val anchor = midValue.substring(start, anchorEnd)

        if (anchor.isNotEmpty()) {
            val idx = midKana.indexOf(anchor)
            if (idx < 0) return null
            segments.add(RubySegment(kanjiRun, midKana.substring(0, idx)))
            midKana = midKana.substring(idx + anchor.length)
            for (ch in anchor) segments.add(RubySegment(ch.toString(), ""))
        } else {
            // 汉字串一直到中段末尾：剩下的读音全归它（熟字訓，如 大人/おとな）
            segments.add(RubySegment(kanjiRun, midKana))
            midKana = ""
        }
        pos = anchorEnd
    }

    for (ch in suffix) segments.add(RubySegment(ch.toString(), ""))

    // 4. 校验：两边的拼回都必须一致
    if (segments.joinToString("") { it.text } != value) return null
    val rebuiltKana = segments.joinToString("") { if (it.ruby.isEmpty()) it.text else it.ruby }
    if (rebuiltKana != kana) return null

    return segments
}

/**
 * 在汉字上方以小号字标注假名读音（日语 ruby）。
 *
 * 布局用 Row + 每段一个 Column：上格是读音（无读音的段留等高空白，
 * 保证底行对齐），下格是原文。因为每段的间距与整串渲染一致
 * （letterSpacing 按字符计，与分段无关），拼起来的总宽与原串相同。
 *
 * @param segments [japaneseRubySegments] 的结果
 * @param fontSize 原文大小
 * @param rubyFontSize 读音大小，通常取原文的 0.5~0.6 倍
 */
@Composable
fun RubyText(
    segments: List<RubySegment>,
    fontSize: TextUnit,
    rubyFontSize: TextUnit = fontSize * 0.55f,
    color: Color = MaterialTheme.colors.onBackground,
    rubyColor: Color = MaterialTheme.colors.onBackground,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = 0.sp,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        segments.forEach { segment ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (segment.ruby.isNotEmpty()) {
                    Text(
                        text = segment.ruby,
                        fontSize = rubyFontSize,
                        color = rubyColor,
                        fontFamily = fontFamily,
                        letterSpacing = 0.sp,
                    )
                } else {
                    // 无读音的段留出与读音等高的空白，让底行汉字保持在同一水平线
                    Spacer(
                        androidx.compose.ui.Modifier
                            .height(rubyFontSize.value.sp),
                    )
                }
                Text(
                    text = segment.text,
                    fontSize = fontSize,
                    color = color,
                    fontFamily = fontFamily,
                    letterSpacing = letterSpacing,
                )
            }
        }
    }
}
