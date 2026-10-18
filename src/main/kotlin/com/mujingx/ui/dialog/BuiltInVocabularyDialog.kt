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

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.mujingx.ui.components.formatVocabularyName
import com.mujingx.ui.components.getBuiltInVocabularyDirectories
import com.mujingx.ui.components.listVocabularyFiles
import com.mujingx.ui.components.vocabularyCategoryLanguage
import com.mujingx.ui.window.windowBackgroundFlashingOnCloseFixHack
import java.io.File
import javax.swing.JOptionPane

@Composable
fun BuiltInVocabularyDialog(
    show: Boolean,
    close: () -> Unit,
    openChooseVocabulary: (String) -> Unit = {},
    openBuiltInVocabulary: (String) -> Unit = openChooseVocabulary,
) {
    if(show){
        DialogWindow(
            title = "内置词库",
            icon = painterResource("logo/logo.png"),
            onCloseRequest = { close() },
            resizable = false,
            state = rememberDialogState(
                position = WindowPosition(Alignment.Center),
                size = DpSize(940.dp, 700.dp)
            ),
        ) {
            windowBackgroundFlashingOnCloseFixHack()
            Surface(
                elevation = 5.dp,
                shape = RectangleShape,
            ) {
                Box (Modifier.fillMaxSize()
                    .background(color = MaterialTheme.colors.background)

                ){
                    var success by remember{ mutableStateOf(false)}
                    // 当前选中的语言 Tab。默认按目录里实际存在的语言决定：
                    // 有日语词库就先展示日语，否则英语。
                    val languages = remember { availableVocabularyLanguages() }
                    var selectedLanguage by remember {
                        mutableStateOf(
                            when {
                                languages.contains("japanese") -> "japanese"
                                languages.contains("english") -> "english"
                                else -> languages.firstOrNull().orEmpty()
                            }
                        )
                    }
                    val stateVertical = rememberScrollState(0)
                    Column(Modifier.fillMaxSize()) {
                        if (languages.size > 1) {
                            LanguageTabs(
                                languages = languages,
                                selected = selectedLanguage,
                                onSelect = { selectedLanguage = it },
                            )
                        }
                        Box(Modifier.fillMaxSize().verticalScroll(stateVertical)){
                            Column (Modifier.padding(10.dp)){
                                getBuiltInVocabularyDirectories(selectedLanguage).forEach { directory ->
                                    VocabularyCategory(
                                        directory = directory,
                                        success = { success = true },
                                        openChooseVocabulary = openChooseVocabulary,
                                        openBuiltInVocabulary = openBuiltInVocabulary
                                    )
                                }
                            }
                        }
                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.End),
                            adapter = rememberScrollbarAdapter(stateVertical)
                        )
                    }

                    if(success){
                        Surface (Modifier.size(206.dp,83.dp)
                            .align(Alignment.Center)
                            .border(BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f)))
                        ){
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp)
                            ){
                                Icon(
                                    imageVector = Icons.Outlined.TaskAlt,
                                    contentDescription = "",
                                    tint = Color.Green
                                )
                                Text(
                                    text = "保存成功",
                                    color = MaterialTheme.colors.onBackground,
                                    modifier = Modifier.padding(start = 20.dp)
                                )
                            }
                            LaunchedEffect(Unit){
                                delay(2000)
                                success = false
                                close()
                            }
                        }
                    }

                    // 旧的 VerticalScrollbar 已移到上方 Column 内，
                    // 这里只剩分隔线，否则两条滚动条会重叠。
                    Divider(Modifier.align(Alignment.TopCenter))
                }
            }
        }
    }

}

/**
 * 目录里实际存在哪些语言的内置词库，按「英语在前、日语在后」排列。
 *
 * 语言取自词库文件的 `language` 字段（见 getBuiltInVocabularyDirectories），
 * 不按目录名硬编码。没有可识别的语言时返回空列表，调用方不显示 Tab。
 *
 * 不是 @Composable：只读磁盘，调用方用 remember 包一层避免每次重组都扫目录。
 */
private fun availableVocabularyLanguages(): List<String> {
    val present = getBuiltInVocabularyDirectories()
        .map { vocabularyCategoryLanguage(it) }
        .filter { it.isNotEmpty() }
        .toSet()
    // 固定顺序，避免 HashSet 遍历顺序导致 Tab 每次重组都可能换位
    return listOf("english", "japanese").filter { present.contains(it) } +
        present.filter { it != "english" && it != "japanese" }.sorted()
}

/**
 * 内置词库的语言切换 Tab。
 *
 * 英语与日语词库数量都不少，混在一个长列表里很难找，
 * 因此按语言分页。只有一种语言时不显示 Tab。
 */
@Composable
private fun LanguageTabs(
    languages: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    TabRow(
        selectedTabIndex = languages.indexOf(selected).coerceAtLeast(0),
        backgroundColor = MaterialTheme.colors.background,
        contentColor = MaterialTheme.colors.primary,
    ) {
        languages.forEach { language ->
            Tab(
                selected = language == selected,
                onClick = { onSelect(language) },
            ) {
                Text(
                    text = languageLabel(language),
                    fontWeight = if (language == selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }
    }
}

/** 语言的展示名称。 */
private fun languageLabel(language: String): String = when (language) {
    "english" -> "英语"
    "japanese" -> "日语"
    else -> language
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VocabularyCategory(
    directory: File,
    success:()->Unit = {},
    openChooseVocabulary: (String) -> Unit = {},
    openBuiltInVocabulary: (String) -> Unit = {},
){
    val scope = rememberCoroutineScope()
    var selectedFile by remember { mutableStateOf<File?>(null) }
    // 文件选择器
    val launcher = rememberFileSaverLauncher(
        dialogSettings = FileKitDialogSettings.createDefault()
    ) {  platformFile ->
        scope.launch (Dispatchers.IO){
            platformFile?.let{
                selectedFile?.let {
                    val fileToSave = platformFile.file
                    try{
                        fileToSave.writeBytes(selectedFile!!.readBytes())
                        success()
                        openChooseVocabulary(fileToSave.absolutePath)
                    }catch (e:Exception){
                        e.printStackTrace()
                        JOptionPane.showMessageDialog(null,"保存失败，错误信息：\n${e.message}")
                    }
                }

            }
        }


    }


    Column (Modifier.fillMaxWidth()
        .heightIn(min = 100.dp,max = 500.dp)
        .padding(bottom = 30.dp)){
        Text(directory.nameWithoutExtension,
            color = MaterialTheme.colors.onBackground,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 17.dp,top = 5.dp))
        val files = listVocabularyFiles(directory)
        if(files.isNotEmpty()){
            val listState = rememberLazyGridState()
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(2.dp),
                modifier = Modifier.fillMaxWidth(),
                state = listState
            ) {

                items(files){file ->

                    val name = formatVocabularyName(file, directory)

                    var disPlayName = name

                    if(name == "高中英语"){
                        disPlayName = "高中英语 3500"
                    }else if(name == "高中英语-乱序版") {
                        disPlayName = "高中英语3500-乱序版"
                    }

                    // 鼠标悬停才显示「另存为」图标。
                    // 每个卡片底部常驻一个文字按钮会堆成一片，把卡片本身的
                    // 名字淹没；而「另存为」只是偶发操作，收进悬停态就够了。
                    var hovered by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier
                            .padding(7.5.dp)
                            // Modifier.hoverable() 只接管 interactionSource，不接受回调；
                            // 悬停状态用 pointer 事件跟踪（与项目里其他处的写法一致）。
                            .onPointerEvent(PointerEventType.Enter) { hovered = true }
                            .onPointerEvent(PointerEventType.Exit) { hovered = false },
                        backgroundColor = MaterialTheme.colors.surface,
                        elevation = 3.dp
                    ) {
                        Box(Modifier.size(width = 160.dp, height = 84.dp)) {
                            // 整个卡片点击即开始学习
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        // 直接学习内置词库：不复制文件，学习进度记在
                                        // ~/.MuJing/progress 下
                                        openBuiltInVocabulary(file.absolutePath)
                                    }
                            ) {
                                Text(
                                    text = disPlayName,
                                    color = MaterialTheme.colors.onBackground,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.align(Alignment.Center)
                                        .padding(horizontal = 8.dp)
                                )
                            }

                            // 悬停时才出现的另存为入口，放在右上角，不占名字的位置
                            if (hovered) {
                                IconButton(
                                    onClick = {
                                        selectedFile = file
                                        launcher.launch(name, "json")
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FileDownload,
                                        contentDescription = "另存为",
                                        tint = MaterialTheme.colors.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

    }
}