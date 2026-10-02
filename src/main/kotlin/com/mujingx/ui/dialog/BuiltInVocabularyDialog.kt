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
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
import com.mujingx.ui.window.windowBackgroundFlashingOnCloseFixHack
import java.io.File
import javax.swing.JOptionPane

@Composable
fun BuiltInVocabularyDialog(
    show: Boolean,
    close: () -> Unit,
    openChooseVocabulary: (String) -> Unit = {},
) {
    if(show){
        DialogWindow(
            title = "选择内置词库到本地文件系统",
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
                    val stateVertical = rememberScrollState(0)
                    Box(Modifier.fillMaxSize().verticalScroll(stateVertical)){
                        Column (Modifier.padding(10.dp)){
                            getBuiltInVocabularyDirectories().forEach { directory ->
                                VocabularyCategory(
                                    directory = directory,
                                    success = { success = true },
                                    openChooseVocabulary = openChooseVocabulary
                                )
                            }
                        }
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

                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd)
                            .fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(stateVertical)
                    )
                    Divider(Modifier.align(Alignment.TopCenter))
                }
            }
        }
    }

}

@Composable
fun VocabularyCategory(
    directory: File,
    success:()->Unit = {},
    openChooseVocabulary: (String) -> Unit = {},
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

                    Card(
                        modifier = Modifier
                            .padding(7.5.dp)
                            .clickable {
                                selectedFile = file
                                launcher.launch(name,"json")
                            },
                        backgroundColor = MaterialTheme.colors.surface,
                        elevation = 3.dp
                    ) {
                        Box(Modifier.size(width = 160.dp, height = 65.dp)) {
                            Text(
                                text = disPlayName,
                                color = MaterialTheme.colors.onBackground,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }
        }

    }
}