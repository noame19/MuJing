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

package com.mujingx.ui.dialog

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mujingx.ui.util.GrammarMatch
import com.mujingx.ui.util.GrammarPoint
import com.mujingx.ui.util.JapaneseGrammarMatcher

/**
 * 语法详情卡片。
 *
 * 显示某句字幕中检出的语法点：命中片段、含义、接续方式与例句。
 *
 * @param match 匹配结果；浏览语法库时只取 point，matchedText 会被忽略
 */
@Composable
fun GrammarDetailCard(
    match: GrammarMatch,
    modifier: Modifier = Modifier
) {
    val point = match.point

    Column(
        modifier = modifier
            .background(MaterialTheme.colors.background)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LevelBadge(point.level)
            Spacer(Modifier.width(10.dp))
            Text(
                text = point.patterns.joinToString(" / "),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Default,
                color = MaterialTheme.colors.onBackground
            )
        }

        // 本句中实际出现的片段
        Surface(
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.06f),
            shape = MaterialTheme.shapes.small
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(
                    text = "本句命中",
                    fontSize = 11.sp,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = match.matchedText,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Default,
                    color = MaterialTheme.colors.primary
                )
            }
        }

        if (point.meaning.isNotEmpty()) {
            KeyValueRow("含义", point.meaning)
        }
        if (point.formation.isNotEmpty()) {
            KeyValueRow("接续", point.formation)
        }

        if (point.examples.isNotEmpty()) {
            Text(
                text = "例句",
                fontSize = 12.sp,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f)
            )
            // 例句数量有限，用 Column + 滚动即可
            Column(Modifier.verticalScroll(rememberScrollState())) {
                point.examples.forEach { example ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = example.ja,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Default,
                            color = MaterialTheme.colors.onBackground
                        )
                        if (example.en.isNotEmpty()) {
                            Text(
                                text = example.en,
                                fontSize = 12.sp,
                                color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = "提示：匹配只判断句中是否出现该形式，具体含义需结合上下文判断。",
            fontSize = 11.sp,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f)
        )
    }
}

/** 等级徽标，如 N3 */
@Composable
fun LevelBadge(level: String) {
    Surface(
        color = MaterialTheme.colors.primary.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = level,
            fontSize = 11.sp,
            color = MaterialTheme.colors.primary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Row {
        Text(
            text = "$label ",
            fontSize = 12.sp,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = MaterialTheme.colors.onBackground
        )
    }
}

/**
 * 语法库浏览窗口。
 *
 * 按等级分组列出全部语法条目，点击「详情」查看接续方式与例句。
 */
@Composable
fun GrammarLibraryDialog(
    onClose: () -> Unit
) {
    val points = remember { JapaneseGrammarMatcher.allPoints() }
    var selected by remember { mutableStateOf<GrammarPoint?>(null) }

    if (!JapaneseGrammarMatcher.isAvailable) {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text("语法库不可用") },
            text = {
                Text("未能加载内置语法数据，请确认 resources/common/vocabulary/日语语法 目录是否存在。")
            },
            confirmButton = {
                TextButton(onClick = onClose) { Text("关闭") }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("日语语法库  共 ${points.size} 条") },
        text = {
            Column(Modifier.height(440.dp).width(560.dp)) {
                val current = selected
                if (current != null) {
                    TextButton(onClick = { selected = null }) { Text("← 返回列表") }
                    GrammarDetailCard(
                        match = GrammarMatch(
                            point = current,
                            matchedText = current.patterns.joinToString(" / "),
                            startIndex = 0,
                            endIndex = 0
                        ),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val listState = rememberLazyListState()
                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(state = listState) {
                            items(points) { point ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LevelBadge(point.level)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = point.patterns.joinToString(" / "),
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colors.onBackground
                                        )
                                        Text(
                                            text = point.meaning,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f)
                                        )
                                    }
                                    TextButton(onClick = { selected = point }) { Text("详情") }
                                }
                                Divider()
                            }
                        }
                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            adapter = rememberScrollbarAdapter(listState)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("关闭") }
        }
    )
}
