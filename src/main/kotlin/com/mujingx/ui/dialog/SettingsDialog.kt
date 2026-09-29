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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import com.mujingx.state.AppState
import com.mujingx.theme.createColors
import com.mujingx.theme.isSystemDarkMode
import com.mujingx.theme.toAwt
import com.mujingx.tts.rememberAzureTTS
import com.mujingx.ui.flatlaf.updateFlatLaf
import com.mujingx.ui.window.windowBackgroundFlashingOnCloseFixHack
import com.mujingx.ui.wordscreen.*
import com.mujingx.ui.util.rememberMonospace
import com.mujingx.ui.util.JapaneseGrammarMatcher
import java.awt.Toolkit


@OptIn(ExperimentalSerializationApi::class)
@Composable
fun SettingsDialog(
    close: () -> Unit,
    state: AppState,
    wordScreenState: WordScreenState,
) {
    val height = if (Toolkit.getDefaultToolkit().screenSize.height > 720) 700.dp else 662.dp
    DialogWindow(
        title = "设置",
        icon = painterResource("logo/logo.png"),
        onCloseRequest = { close() },
        resizable = true,
        state = rememberDialogState(
            position = WindowPosition(Alignment.Center),
            size = DpSize(900.dp, height)
        ),
    ) {
        windowBackgroundFlashingOnCloseFixHack()
        MaterialTheme(colors = state.colors) {
            Surface(
                elevation = 5.dp,
                shape = RectangleShape,
            ) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {

                    Row(Modifier.fillMaxSize()) {
                        var currentPage by remember { mutableStateOf("Theme") }
                        Column(Modifier.width(100.dp).fillMaxHeight()) {

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { currentPage = "Theme" }
                                    .fillMaxWidth()
                                    .height(48.dp)) {
                                Text("主题", modifier = Modifier.padding(start = 16.dp))
                                if (currentPage == "Theme") {
                                    Spacer(Modifier.fillMaxHeight().width(2.dp).background(MaterialTheme.colors.primary))
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { currentPage = "TextStyle" }
                                    .fillMaxWidth()
                                    .height(48.dp)) {
                                Text("字体样式", modifier = Modifier.padding(start = 16.dp))
                                if (currentPage == "TextStyle") {
                                    Spacer(Modifier.fillMaxHeight().width(2.dp).background(MaterialTheme.colors.primary))
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { currentPage = "AudioSettings" }
                                    .fillMaxWidth()
                                    .height(48.dp)) {
                                Text("发音设置", modifier = Modifier.padding(start = 16.dp))
                                if (currentPage == "AudioSettings") {
                                    Spacer(Modifier.fillMaxHeight().width(2.dp).background(MaterialTheme.colors.primary))
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { currentPage = "Other" }
                                    .fillMaxWidth()
                                    .height(48.dp)) {
                                Text("其它", modifier = Modifier.padding(start = 16.dp))
                                if (currentPage == "Other") {
                                    Spacer(Modifier.fillMaxHeight().width(2.dp).background(MaterialTheme.colors.primary))
                                }
                            }
                        }
                        Divider(Modifier.fillMaxHeight().width(1.dp))
                        when (currentPage) {
                            "Theme" -> {
                                SettingTheme(state)
                            }
                            "TextStyle" -> {
                                SettingTextStyle(state,wordScreenState)
                            }
                            "AudioSettings" -> {
                                AudioSettingsPage(wordScreenState)
                            }
                            "Other" -> {
                                OtherSettings(state)
                            }
                        }
                    }
                    Divider(Modifier.align(Alignment.TopCenter))
                    Column(
                        Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth().height(60.dp)
                    ) {
                        Divider()
                        Row(
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background)
                        ) {
                            OutlinedButton(
                                onClick = { close() },
                                modifier = Modifier.padding(end = 10.dp)
                            ) {
                                Text("关闭")
                            }
                        }
                    }
                }

            }
        }

    }
}



@OptIn(ExperimentalSerializationApi::class)
@Composable
fun SettingTextStyle(
    state: AppState,
    wordScreenState: WordScreenState,
) {
    val fontFamily  = rememberMonospace()
    if (wordScreenState.vocabulary.size > 1) {
        Column(
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(top = 48.dp, bottom = 60.dp)
        ) {
            Column(Modifier.width(600.dp)) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 50.dp, top = 20.dp, bottom = 10.dp, end = 50.dp)
                ) {

                    Text("单词的样式")
                    Spacer(Modifier.width(15.dp))

                    TextStyleChooser(
                        isWord = true,
                        selectedTextStyle = state.global.wordTextStyle,
                        textStyleChange = {
                            state.global.wordTextStyle = it
                            state.saveGlobalState()
                        }
                    )
                    Spacer(Modifier.width(30.dp))
                    Text("字间隔空")
                    Spacer(Modifier.width(15.dp))
                    Box {
                        var spacingExpanded by remember { mutableStateOf(false) }
                        var spacingText by remember { mutableStateOf("5sp") }
                        OutlinedButton(
                            onClick = { spacingExpanded = true },
                            modifier = Modifier
                                .width(120.dp)
                                .background(Color.Transparent)
                                .border(1.dp, Color.Transparent)
                        ) {
                            Text(text = spacingText)
                            Icon(Icons.Default.ExpandMore, contentDescription = "Localized description")
                        }
                        DropdownMenu(
                            expanded = spacingExpanded,
                            onDismissRequest = { spacingExpanded = false },
                            modifier = Modifier.width(120.dp)
                                .height(300.dp)
                        ) {
                            val modifier = Modifier.width(120.dp).height(40.dp)
                            for (i in 0..6) {
                                DropdownMenuItem(
                                    onClick = {
                                        state.global.letterSpacing = (i).sp
                                        spacingText = "${i}sp"
                                        spacingExpanded = false
                                        state.saveGlobalState()
                                    },
                                    modifier = modifier
                                ) {
                                    Text("${i}sp")
                                }
                            }

                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.width(600.dp).padding(bottom = 10.dp)

                ) {
                    var textHeight by remember { mutableStateOf(0.dp) }
                    val smallStyleList =
                        listOf(
                            "H5",
                            "H6",
                            "Subtitle1",
                            "Subtitle2",
                            "Body1",
                            "Body2",
                            "Button",
                            "Caption",
                            "Overline"
                        )
                    val bottom = computeBottom(textStyle = state.global.wordTextStyle, textHeight = textHeight)
                    var previewWord = wordScreenState.getCurrentWord().value
                    if (previewWord.isEmpty()) {
                        previewWord = "Typing"
                    }
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                style = SpanStyle(
                                    color = MaterialTheme.colors.onBackground,
                                    fontFamily = fontFamily,
                                    fontSize = state.global.wordFontSize,
                                    letterSpacing = state.global.letterSpacing
                                )
                            ) {
                                append(previewWord)
                            }
                        },
                        modifier = Modifier
                            .padding(bottom = bottom)
                            .onGloballyPositioned { layoutCoordinates ->
                                textHeight = (layoutCoordinates.size.height).dp
                            }
                    )
                    Spacer(Modifier.width(5.dp))
                    Column(verticalArrangement = Arrangement.Center) {
                        var numberFontSize = LocalTextStyle.current.fontSize
                        if (smallStyleList.contains(state.global.wordTextStyle)) numberFontSize =
                            MaterialTheme.typography.overline.fontSize

                        Text(
                            text = "3",
                            color = MaterialTheme.colors.primary,
                            fontSize = numberFontSize
                        )
                        Spacer(modifier = Modifier.height(textHeight.div(4)))
                        Text(
                            text = "1",
                            color = Color.Red,
                            fontSize = numberFontSize
                        )
                    }
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        Icons.Filled.VolumeDown,
                        contentDescription = "Localized description",
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            Spacer(Modifier.height(30.dp))
            Column(Modifier.width(600.dp)) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 50.dp, top = 20.dp, bottom = 10.dp, end = 100.dp)
                ) {
                    Text("详细信息的样式")
                    Spacer(Modifier.width(15.dp))
                    TextStyleChooser(
                        isWord = false,
                        selectedTextStyle = state.global.detailTextStyle,
                        textStyleChange = {
                            state.global.detailTextStyle = it
                            state.saveGlobalState()
                        }
                    )
                }
                val currentWord = wordScreenState.getCurrentWord()
                Phonetic(
                    word = currentWord,
                    phoneticVisible = true,
                    fontSize = state.global.detailFontSize
                )
                Morphology(
                    word = currentWord,
                    isPlaying = false,
                    searching = false,
                    morphologyVisible = true,
                    fontSize = state.global.detailFontSize
                )
                Definition(
                    word = currentWord,
                    definitionVisible = true,
                    isPlaying = false,
                    fontSize = state.global.detailFontSize
                )
                Translation(
                    word = currentWord,
                    translationVisible = true,
                    isPlaying = false,
                    fontSize = state.global.detailFontSize
                )
            }

        }
    } else {
        Box(Modifier.fillMaxSize()) {
            Text(
                text = "请先选择词库",
                style = MaterialTheme.typography.h6,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }


}

@Composable
fun TextStyleChooser(
    isWord: Boolean,
    selectedTextStyle: String,
    textStyleChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .width(120.dp)
                .background(Color.Transparent)
                .border(1.dp, Color.Transparent)
        ) {
            Text(text = selectedTextStyle)
            Icon(Icons.Default.ExpandMore, contentDescription = "Localized description")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(120.dp)
                .height(if (isWord) 500.dp else 260.dp)
        ) {
            val modifier = Modifier.width(120.dp).height(40.dp)
            if (isWord) {
                DropdownMenuItem(
                    onClick = {
                        textStyleChange("H1")
                        expanded = false
                    },
                    modifier = modifier
                ) {
                    Text("H1")
                }
                DropdownMenuItem(
                    onClick = {
                        textStyleChange("H2")
                        expanded = false
                    },
                    modifier = modifier
                ) {
                    Text("H2")
                }
                DropdownMenuItem(
                    onClick = {
                        textStyleChange("H3")
                        expanded = false
                    },
                    modifier = modifier
                ) {
                    Text("H3")
                }
                DropdownMenuItem(
                    onClick = {
                        textStyleChange("H4")
                        expanded = false
                    },
                    modifier = modifier
                ) {
                    Text("H4")
                }
            }

            DropdownMenuItem(
                onClick = {
                    textStyleChange("H5")
                    expanded = false
                },
                modifier = modifier
            ) {
                Text("H5")
            }
            DropdownMenuItem(
                onClick = {
                    textStyleChange("H6")
                    expanded = false
                },
                modifier = modifier
            ) {
                Text("H6")
            }
            DropdownMenuItem(
                onClick = {
                    textStyleChange("Subtitle1")
                    expanded = false
                },
                modifier = modifier
            ) {
                Text("Subtitle1")
            }
            DropdownMenuItem(
                onClick = {
                    textStyleChange("Subtitle2")
                    expanded = false
                },
                modifier = modifier
            ) {
                Text("Subtitle2")
            }
            DropdownMenuItem(
                onClick = {
                    textStyleChange("Body1")
                    expanded = false
                },
                modifier = modifier
            ) {
                Text("Body1")
            }
            DropdownMenuItem(
                onClick = {
                    textStyleChange("Body2")
                    expanded = false
                },
                modifier = modifier
            ) {
                Text("Body2")
            }
            if (isWord) {
                DropdownMenuItem(
                    onClick = {
                        textStyleChange("Caption")
                        expanded = false
                    },
                    modifier = modifier
                ) {
                    Text("Caption")
                }
                DropdownMenuItem(
                    onClick = {
                        textStyleChange("Overline")
                        expanded = false
                    },
                    modifier = modifier
                ) {
                    Text("Overline")
                }

            }

        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Composable
fun SettingTheme(
    appState: AppState,
) {


    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        val scope = rememberCoroutineScope()
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp,bottom = 10.dp)
        ) {

            val width = 120.dp
            Column (
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(width).padding(end = 10.dp)
                    .clickable {
                        scope.launch {
                            if(appState.global.isFollowSystemTheme || !appState.global.isDarkTheme){
                                appState.global.isDarkTheme = true
                                appState.global.isFollowSystemTheme = false
                                appState.colors = createColors(appState.global)
                                appState.saveGlobalState()
                            }
                        }
                    }
            ){
                val tint = if(!appState.global.isFollowSystemTheme && appState.global.isDarkTheme) MaterialTheme.colors.primary else MaterialTheme.colors.onBackground
                Icon(
                   Icons.Outlined.DarkMode,
                    contentDescription = "Localized description",
                    modifier = Modifier.size(60.dp, 60.dp),
                    tint = tint
                )
                Text(
                    text = "深色模式", fontSize = 12.sp,
                )
            }
            Column (
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(width).padding(end = 10.dp)
                    .clickable {
                       scope.launch {
                           if(appState.global.isFollowSystemTheme || appState.global.isDarkTheme){
                               appState.global.isDarkTheme = false
                               appState.global.isFollowSystemTheme = false
                               appState.colors = createColors(appState.global)
                               appState.saveGlobalState()
                           }
                       }

                    }
            ){
                val tint = if(!appState.global.isFollowSystemTheme && !appState.global.isDarkTheme) MaterialTheme.colors.primary else MaterialTheme.colors.onBackground
                Icon(
                    Icons.Outlined.LightMode,
                    contentDescription = "Localized description",
                    modifier = Modifier.size(60.dp, 60.dp),
                    tint = tint
                )
                Text(
                    text = "浅色模式", fontSize = 12.sp,
                )
            }
            Column (
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(width).padding(end = 10.dp)
                    .clickable {
                        scope.launch {
                            if(!appState.global.isFollowSystemTheme){
                                appState.global.isFollowSystemTheme = true
                                appState.colors = createColors(appState.global)
                                appState.saveGlobalState()
                            }
                        }
                    }
            ){
                val imageVector = if(appState.global.isFollowSystemTheme){
                   val isDark =  isSystemInDarkTheme()
                    if(isDark) Icons.Outlined.Brightness4 else Icons.Outlined.Brightness7
                } else {
                    Icons.Outlined.BrightnessAuto
                }
                Icon(
                    imageVector,
                    contentDescription = "Localized description",
                    modifier = Modifier.size(60.dp, 60.dp),
                    tint = if(appState.global.isFollowSystemTheme) MaterialTheme.colors.primary else MaterialTheme.colors.onBackground
                )
                Text(
                    text = "跟随系统", fontSize = 12.sp,
                )
            }
            Spacer(Modifier.width(90.dp))
        }
        var selectPrimaryColor by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { selectPrimaryColor = true }, Modifier.padding(end = 100.dp)) {
            Text("主色调")
        }
        if(selectPrimaryColor){
            ColorPicker(
                close = {selectPrimaryColor = false},
                mode = "PrimaryColor",
                initColor = appState.global.primaryColor,
                confirm = { selectedColor ->
                    appState.global.primaryColor = selectedColor
                    appState.colors = createColors(appState.global)
                    appState.saveGlobalState()
                    selectPrimaryColor = false
                },
                reset = {
                    // 恢复默认颜色,绿色
                    appState.global.primaryColor = Color(9, 175, 0)
                    appState.colors = createColors(appState.global)
                    appState.saveGlobalState()
                    selectPrimaryColor = false
                },
                appState = appState
            )
        }

        val isDark = if (appState.global.isFollowSystemTheme) {
            isSystemDarkMode()
        } else appState.global.isDarkTheme

        if (!isDark) {
            var selectBackgroundColor by remember { mutableStateOf(false) }
            var selectOnBackgroundColor by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { selectBackgroundColor = true }, Modifier.padding(end = 100.dp)) {
                Text("设置背景颜色")
            }
            if(selectBackgroundColor){
                ColorPicker(
                    close = {selectBackgroundColor = false},
                    mode = "Background",
                    initColor = appState.global.backgroundColor,
                    confirm = { selectedColor ->
                        appState.global.backgroundColor = selectedColor
                        appState.colors = createColors(appState.global)
                        updateFlatLaf(
                            darkTheme = appState.global.isDarkTheme,
                            isFollowSystemTheme = appState.global.isFollowSystemTheme,
                            background = appState.global.backgroundColor.toAwt(),
                            onBackground = appState.global.onBackgroundColor.toAwt()
                        )
                        appState.saveGlobalState()
                        selectBackgroundColor = false
                      },
                    reset = {
                        appState.global.backgroundColor = Color.White
                        appState.colors = createColors(appState.global)
                        updateFlatLaf(
                            darkTheme = appState.global.isDarkTheme,
                            isFollowSystemTheme = appState.global.isFollowSystemTheme,
                            background = appState.global.backgroundColor.toAwt(),
                            onBackground = appState.global.onBackgroundColor.toAwt()
                        )
                        appState.saveGlobalState()
                        selectBackgroundColor = false
                    },
                    appState = appState
                )
            }
            OutlinedButton(onClick = { selectOnBackgroundColor = true }, Modifier.padding(end = 90.dp)) {
                Text("设置前景颜色")
            }

            if(selectOnBackgroundColor){
                ColorPicker(
                    close = {selectOnBackgroundColor = false},
                    mode = "onBackground",
                    initColor = appState.global.onBackgroundColor,
                    confirm = { selectedColor ->
                        appState.global.onBackgroundColor = selectedColor
                        appState.colors = createColors(appState.global)
                        updateFlatLaf(
                            darkTheme = appState.global.isDarkTheme,
                            isFollowSystemTheme = appState.global.isFollowSystemTheme,
                            background = appState.global.backgroundColor.toAwt(),
                            onBackground = appState.global.onBackgroundColor.toAwt()
                        )
                        appState.saveGlobalState()
                        selectOnBackgroundColor = false

                    },
                    reset = {
                        appState.global.onBackgroundColor = Color.Black
                        appState.colors = createColors(appState.global)
                        updateFlatLaf(
                            darkTheme = appState.global.isDarkTheme,
                            isFollowSystemTheme = appState.global.isFollowSystemTheme,
                            background = appState.global.backgroundColor.toAwt(),
                            onBackground = appState.global.onBackgroundColor.toAwt()
                        )
                        appState.saveGlobalState()
                        selectOnBackgroundColor = false
                    },
                    appState = appState
                )
            }
        }

    }

}


@OptIn(ExperimentalSerializationApi::class)
@Composable
fun AudioSettingsPage(wordScreenState: WordScreenState) {
    val scope = rememberCoroutineScope()
    val azureTTS = rememberAzureTTS()

    Box(Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter){
        AudioSettings(
            modifier = Modifier.padding(top = 20.dp, end = 50.dp),
            wordScreenState = wordScreenState,
            scope = scope,
            azureTTS = azureTTS
        )
    }
}

/** 视频字幕轨道的可选语言。值为 VLC --sub-language 的语言代码。 */
private val subtitleLanguageOptions = listOf(
    "en" to "英语",
    "ja" to "日语",
    "zh" to "中文",
    "ko" to "韩语"
)

@OptIn(ExperimentalSerializationApi::class)
@Composable
fun OtherSettings(appState: AppState) {
    var showGrammarLibrary by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter){
        Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp,end = 50.dp),
            horizontalAlignment = Alignment.CenterHorizontally){
        Row(modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center){
            Text("显示输入次数")
            Spacer(Modifier.width(20.dp))
            Switch(
                checked = appState.global.showInputCount,
                onCheckedChange = {
                    appState.global.showInputCount = it
                    appState.saveGlobalState()
                }
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center){
            Text("视频默认字幕语言")
            Spacer(Modifier.width(20.dp))
            var expanded by remember{ mutableStateOf(false) }
            val border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
            val current = appState.global.preferredSubLanguage
            Box{
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .width(180.dp).height(40.dp).padding(start = 12.dp, end = 8.dp)
                        .border(border = border)
                        .clickable { expanded = !expanded }
                ){
                    Text(
                        text = subtitleLanguageOptions.firstOrNull { it.first == current }?.second ?: current,
                        color = MaterialTheme.colors.onBackground
                    )
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = "Localized description",
                        tint = if (MaterialTheme.colors.isLight) Color.DarkGray else MaterialTheme.colors.onBackground,
                        modifier = Modifier.size(32.dp,32.dp)
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.width(180.dp)
                ){
                    subtitleLanguageOptions.forEach { (code, label) ->
                        DropdownMenuItem(
                            onClick = {
                                appState.global.preferredSubLanguage = code
                                expanded = false
                                appState.saveGlobalState()
                            },
                            modifier = Modifier.width(180.dp).height(40.dp)
                        ){
                            Text(
                                text = label,
                                color = if (appState.global.preferredSubLanguage == code)
                                    MaterialTheme.colors.primary
                                else
                                    MaterialTheme.colors.onBackground
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "重启播放器后生效",
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                fontSize = MaterialTheme.typography.caption
            )
        }

        Spacer(Modifier.height(24.dp))

        // 语法库入口。仅在语法数据可用时展示，避免出现打不开的空窗口。
        if (JapaneseGrammarMatcher.isAvailable) {
            Row(modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center){
                Text("日语语法库")
                Spacer(Modifier.width(20.dp))
                OutlinedButton(onClick = { showGrammarLibrary = true }) {
                    Text("打开（${JapaneseGrammarMatcher.size} 条）")
                }
            }

            if (showGrammarLibrary) {
                GrammarLibraryDialog(onClose = { showGrammarLibrary = false })
            }
        }
        }
    }
}