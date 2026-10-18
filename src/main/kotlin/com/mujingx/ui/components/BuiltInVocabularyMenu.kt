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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import java.io.File


/**
 * 用在生成词库界面，使用内置词库过滤单词。
 *
 * @param language 只列出该语言的内置词库分类（与「目标语言」下拉联动），空串表示全部
 * @param addVocabulary 每勾选一个词库调用一次；菜单支持多选，点「确定」后逐个加入
 */
@Composable
fun BuiltInVocabularyMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    language: String = "",
    addVocabulary:(File) -> Unit
) {
    var selectedDirectory by remember { mutableStateOf<File?>(null) }
    var showList by remember { mutableStateOf(false) }
    val width = if(showList) 441.dp else 240.dp

    // 多选：先在列表里勾选若干个词库，再统一加入
    val selectedFiles = remember { mutableStateListOf<File>() }

    // 语言切换后，之前选中的分类可能已不在列表里
    LaunchedEffect(language) {
        selectedDirectory = null
        showList = false
    }

    // 高度分配：页脚固定 40dp 常驻底部，中间内容区占据剩余高度。
    // 原先写死「容器 440 + 列表 400 + 页脚 40」，加分隔线后是 441，
    // 页脚被挤出了可视区，用户必须滚动才能看到「已选 N 个词库」与「确定」。
    val contentHeight = 400.dp
    val footerHeight = 40.dp

    DropdownMenu(
        expanded = expanded,
        offset = DpOffset(20.dp, 0.dp),
        onDismissRequest = onDismissRequest,
        modifier = Modifier.width(width).height(contentHeight + footerHeight + 1.dp),
    ) {
        // 用 Column 分成「内容 / 分隔线 / 页脚」三段，页脚不再参与滚动，
        // 也不会因为内容变高而被挤出去。
        Column(Modifier.fillMaxWidth()) {
            Row(
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.width(width).height(contentHeight)
            ) {
                val directories = getBuiltInVocabularyDirectories(language)
                // 左右两栏各自独立滚动：分类多时只滚左栏，词库列表位置保持不动
                Column(
                    modifier = Modifier.width(240.dp).fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (directories.isEmpty()) {
                        Text(
                            text = if (language == "japanese") "没有内置的日语词库"
                            else "没有内置的该语言词库",
                            color = MaterialTheme.colors.onBackground,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    directories.forEach { directory ->
                        VocabularyDirectory(
                            directory = directory,
                            selectedDirectory = selectedDirectory,
                            onClick = {
                                showList = true
                                selectedDirectory = directory
                            }
                        )
                    }
                }

                if (showList && selectedDirectory != null) {
                    Divider(Modifier.width(1.dp).fillMaxHeight())
                    VocabularyList(
                        directory = selectedDirectory!!,
                        selectedFiles = selectedFiles,
                    )
                }
            }

            Divider()
            // 页脚固定高度，永远可见
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.width(width).height(footerHeight)
            ) {
                Text(
                    text = "已选 ${selectedFiles.size} 个词库",
                    color = MaterialTheme.colors.onBackground,
                    modifier = Modifier.padding(start = 12.dp)
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    enabled = selectedFiles.isNotEmpty(),
                    onClick = {
                        selectedFiles.toList().forEach { addVocabulary(it) }
                        selectedFiles.clear()
                        onDismissRequest()
                    }
                ) {
                    Text("确定")
                }
            }
        }
    }
}

@Composable
fun VocabularyDirectory(
    directory: File,
    selectedDirectory: File?,
    onClick: () -> Unit
){
    val selectedBackground = MaterialTheme.colors.onBackground.copy(alpha = 0.12f)
    val background = if(MaterialTheme.colors.isLight) MaterialTheme.colors.background else Color(32, 33, 34)
    DropdownMenuItem(onClick = onClick ,modifier = Modifier.background(if (selectedDirectory == directory) selectedBackground else background)) {

        Row (
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()){
            Text(
                text = directory.nameWithoutExtension,
                color = if (selectedDirectory == directory) MaterialTheme.colors.primary else MaterialTheme.colors.onBackground
            )
            Icon(
                imageVector =  Icons.Default.ArrowRight,
                contentDescription = null,
                tint = if (selectedDirectory == directory) MaterialTheme.colors.primary else MaterialTheme.colors.onBackground)
        }
    }
}

@Composable
fun VocabularyList(
    directory: File,
    selectedFiles: MutableList<File>,
) {
    val files = listVocabularyFiles(directory)
    if (files.isNotEmpty()) {
        // 高度交给外层（fillMaxHeight），不再写死 400dp：
        // 写死后一旦外层高度变化，列表会溢出或留白。
        Box(Modifier.width(200.dp).fillMaxHeight()){
            val listState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(200.dp)
                    .fillMaxHeight(),
                state = listState
            ) {
                items(files) { file ->
                    val name = formatVocabularyName(file, directory)
                    val checked = selectedFiles.contains(file)
                    DropdownMenuItem(onClick = {
                        if (checked) selectedFiles.remove(file) else selectedFiles.add(file)
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = name,
                                color = MaterialTheme.colors.onBackground,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                }

            }

            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState = listState)
            )
        }


    }
}