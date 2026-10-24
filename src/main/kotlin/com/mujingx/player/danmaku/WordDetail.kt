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

package com.mujingx.player.danmaku

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mujingx.data.Word

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.mujingx.player.PlayerState
import com.mujingx.state.rememberAppState
import com.mujingx.ui.components.RubyText
import com.mujingx.ui.components.japaneseRubySegments
import com.mujingx.ui.wordscreen.AddButton
import com.mujingx.ui.wordscreen.CopyButton
import com.mujingx.ui.wordscreen.DeleteButton
import com.mujingx.ui.wordscreen.FamiliarButton

enum class DisplayMode {
    DICT,  // 词典模式
    DANMAKU, // 弹幕模式
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WordDetail(
    word: Word,
    displayMode: DisplayMode = DisplayMode.DANMAKU,
    playerState: PlayerState,
    height: Dp,
    pointerExit:() -> Unit = {},
    deleteWord: (Word) -> Unit = {},
    addWord: (Word) -> Unit = {},
    addToFamiliar: (Word) -> Unit = {},
    playAudio: (String) -> Unit ={}
) {
    val focusRequester = remember { FocusRequester() }
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    Surface(
        elevation = 4.dp,
        shape = RoundedCornerShape(12.dp), // 设置圆角半径为 12dp
    ) {

        Column(
            Modifier.width(400.dp).height(height)
                .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                .focusable(true)
                .focusRequester(focusRequester)
                .onPointerEvent(PointerEventType.Exit){
                    pointerExit()
                }
                .onKeyEvent { keyEvent ->
                    if (keyEvent.key == Key.Delete && keyEvent.isShiftPressed && keyEvent.type == KeyEventType.KeyUp) {
                        deleteWord(word)
                        true
                    } else if (keyEvent.key == Key.Y && keyEvent.isCtrlPressed && keyEvent.type == KeyEventType.KeyUp) {
                        addToFamiliar(word)
                        true
                    } else if (keyEvent.key == Key.C && keyEvent.isCtrlPressed && keyEvent.type == KeyEventType.KeyUp) {
                        clipboardManager.setText(AnnotatedString(word.value))
                        true
                    } else false
                }
        ) {
            var settingsExpanded by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    Text(
                        text = word.value,
                        style = MaterialTheme.typography.h5,
                        color =  MaterialTheme.colors.onBackground,
                    )                }
                IconButton(
                    onClick = { settingsExpanded = !settingsExpanded },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        if (settingsExpanded) Icons.Filled.Close else Icons.Filled.Settings,
                        contentDescription = "Localized description",
                        tint = MaterialTheme.colors.onBackground
                    )
                }

            }


            Box {
                // 释义页签的选中项以「我的母语」设置为唯一来源。
                // 原来这里写的是 `... != "en" || playerState.preferredChinese`，
                // 母语默认中文时前半句恒为 true，`||` 让后半句永远不起作用
                // —— 弹窗里的「优先显示英文」开关就完全失效了。
                val appState = rememberAppState()
                val preferChinese = !appState.global.learnerLanguage.equals("en", true)
                var tabState by remember(preferChinese) {
                    mutableStateOf(if (preferChinese) 0 else 1)
                }
                Column(Modifier.align(Alignment.Center)) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CopyButton(
                            wordValue = word.value,
                            tooltipAlignment = Alignment.BottomCenter
                        )
                        FamiliarButton(
                            onClick = { addToFamiliar(word) },
                            tooltipAlignment = Alignment.BottomCenter
                        )
                        if(displayMode == DisplayMode.DANMAKU){

                            DeleteButton(
                                onClick = { deleteWord(word) },
                                tooltipAlignment =  Alignment.BottomCenter
                            )
                        }else{
                            AddButton(
                                onClick = {addWord(word)},
                                tooltipAlignment =  Alignment.BottomCenter
                            )
                        }

                    }


                    // 语言以当前词库为准（AGENTS.md §8：不用字符范围判断语言）。
                    // 声明在 Row 之外：下面的 TabRow 也要用它决定页签标题。
                    val isJapanese = playerState.vocabulary?.language == "japanese" ||
                            playerState.wordScreenVocabulary?.language == "japanese"

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 日语没有英/美音标，改显示假名与分拍罗马字。
                        if (isJapanese) {
                            // 词条带汉字时用带注音的写法：弹窗里看词最想知道
                            // 的正是「这个汉字怎么读」。纯假名词或无注音数据时
                            // 回退到假名 + 罗马字。
                            val rubySegments = remember(word.value, word.kana) {
                                japaneseRubySegments(word.value, word.kana)
                            }
                            if (rubySegments != null) {
                                RubyText(
                                    segments = rubySegments,
                                    fontSize = MaterialTheme.typography.body1.fontSize * 1.4f,
                                    rubyFontSize = MaterialTheme.typography.body1.fontSize * 0.6f,
                                    rubyColor = MaterialTheme.colors.onBackground.copy(alpha = 0.55f),
                                )
                            } else {
                                Text(
                                    listOf(word.kana, word.romaji)
                                        .filter { it.isNotEmpty() }
                                        .joinToString("  ")
                                )
                            }
                        } else {
                            Text("英 ${word.ukphone}  美 ${word.usphone}")
                        }
                        IconButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                playAudio(word.value)
                            }
                        }) {
                            Icon(
                                Icons.Filled.VolumeUp,
                                contentDescription = "Localized description",
                                tint = MaterialTheme.colors.onBackground,
                            )
                        }
                    }
                    Divider()

                    TabRow(
                        selectedTabIndex = tabState,
                        backgroundColor = Color.Transparent
                    ) {
                        Tab(
                            text = { Text("中文") },
                            selected = tabState == 0,
                            onClick = { tabState = 0 }
                        )
                        Tab(
                            // 日语词条没有英文释义：第二个页签是 definition，
                            // 里面带词性前缀（な形容词. safe; secure），仍属英文，
                            // 但叫「英文释义」比「英文」准确，也不与读音行重复。
                            text = { Text(if (isJapanese) "英文释义" else "英文") },
                            selected = tabState == 1,
                            onClick = { tabState = 1 }
                        )
                    }
                    when (tabState) {
                        0 -> {
                            TextBox(text = word.translation)
                        }

                        1 -> {
                            TextBox(text = word.definition)
                        }
                    }
                }
                if (settingsExpanded) {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
                        val modifier = Modifier.width(300.dp).padding(start = 90.dp).clickable { }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = modifier
                        ) {
                            Text("自动发音", modifier = Modifier.padding(start = 10.dp))
                            Switch(checked = playerState.autoSpeak, onCheckedChange = {
                                playerState.autoSpeak = it
                                playerState.savePlayerState()
                            })
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = modifier
                        ) {
                            Text("自动复制", modifier = Modifier.padding(start = 10.dp))
                            Switch(checked = playerState.autoCopy, onCheckedChange = {
                                playerState.autoCopy = it
                                playerState.savePlayerState()
                            })
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = modifier
                        ) {
                            Text("优先显示中文", modifier = Modifier.padding(start = 10.dp))
                            // 两个开关现在写全局设置，而不是各存一份：
                            // 否则设置页选了中文、弹窗里又选了英文，两处永远对不上。
                            Switch(checked = appState.global.learnerLanguage != "en", onCheckedChange = {
                                if (it) {
                                    appState.global.learnerLanguage = "zh"
                                    appState.saveGlobalState()
                                    tabState = 0
                                }
                            })
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = modifier
                        ) {
                            Text("优先显示英文", modifier = Modifier.padding(start = 10.dp))
                            Switch(checked = appState.global.learnerLanguage == "en", onCheckedChange = {
                                if (it) {
                                    appState.global.learnerLanguage = "en"
                                    appState.saveGlobalState()
                                    tabState = 1
                                }
                            })
                        }
                    }
                }
            }

        }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            if (playerState.autoCopy) {
                clipboardManager.setText(AnnotatedString(word.value))
            }
            if (playerState.autoSpeak) {
                scope.launch(Dispatchers.IO) {
                    playAudio(word.value)
                }
            }
        }
    }
}


@Composable
fun TextBox(text: String) {
    Box(Modifier.fillMaxSize()) {
        val stateVertical = rememberScrollState(0)
        Box(Modifier.verticalScroll(stateVertical)) {
            SelectionContainer {
                Text(
                    text = text,
                    style = MaterialTheme.typography.h6,
                    textAlign = TextAlign.Start,
                    color = MaterialTheme.colors.onBackground,
                )
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd)
                .fillMaxHeight(),
            adapter = rememberScrollbarAdapter(stateVertical)
        )
    }
}