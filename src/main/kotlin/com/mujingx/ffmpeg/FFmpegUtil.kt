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

package com.mujingx.ffmpeg

import net.bramp.ffmpeg.FFmpeg
import net.bramp.ffmpeg.FFmpegExecutor
import net.bramp.ffmpeg.builder.FFmpegBuilder
import net.bramp.ffmpeg.builder.FFmpegBuilder.Verbosity
import net.bramp.ffmpeg.job.FFmpegJob
import com.mujingx.player.isWindows
import com.mujingx.state.getResourcesFile
import com.mujingx.state.getSettingsDirectory
import com.mujingx.ui.dialog.replaceNewLine
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.swing.JOptionPane

/**
 * 定位随应用分发的 ffmpeg。
 *
 * 可执行权限由构建脚本烙进 AppImage（见 Build-AppImage.yml 的 Stage AppDir
 * 步骤）：AppImage 挂载点是只读 squashfs，运行期 setExecutable() 会静默失败，
 * 不能依赖它。这里仍尝试一次，只是为了覆盖直接从源码目录运行、或用 zip/deb
 * 安装（权限位可能没有被保留）的情形。
 */
fun findFFmpegPath(): String {
    val path: String = if(isWindows()){
        getResourcesFile("ffmpeg/ffmpeg.exe").absolutePath
    }else{
        val ffmpegFile = getResourcesFile("ffmpeg/ffmpeg")
        if(ffmpegFile.exists() && !ffmpegFile.canExecute()){
            // 只读挂载上一定失败，失败也不报错：真正运行时若权限不对，
            // 由 extractSubtitles 的异常处理给出可读提示。
            ffmpegFile.setExecutable(true)
        }
        ffmpegFile.absolutePath
    }
    return path
}

/**
 * 把 ffmpeg 启动失败翻译成能给用户看的提示。
 *
 * 最常见的是权限位没保留（errno 13）——AppImage 挂载点只读，无法现场 chmod，
 * 只能提示重新下载或改用其他安装包，而不是把裸的 errno 抡给用户。
 */
private fun ffmpegErrorMessage(e: Exception): String {
    val raw = e.message.orEmpty()
    return if (raw.contains("error: 13") || raw.contains("Permission denied")) {
        "ffmpeg 没有执行权限，无法处理字幕。\n" +
            "路径：${findFFmpegPath()}\n" +
            "请重新下载应用包；若仍报错，请告知开发者。"
    } else {
        "选择的字幕格式暂时不支持\n $raw"
    }
}

/**
 * 使用 FFmpeg 提取视频里的字幕,并且把字幕转换成 SRT 格式
 */
fun extractSubtitles(
    input: String, subtitleId: Int,
    output: String,
    verbosity: Verbosity = Verbosity.INFO
): String {
    return try {
        val ffmpeg = FFmpeg(findFFmpegPath())
        val builder = FFmpegBuilder()
            .setVerbosity(verbosity)
            .setInput(input)
            .addOutput(output)
            .addExtraArgs("-map", "0:s:$subtitleId") //  -map 0:s:0 表示提取第一个字幕，-map 0:s:1 表示提取第二个字幕。
            .done()
        val executor = FFmpegExecutor(ffmpeg)
        val job = executor.createJob(builder)
        job.run()
        if (job.state == FFmpegJob.State.FINISHED) {
            "finished"
        } else {
            JOptionPane.showMessageDialog(null, "提取字幕失败", "错误", JOptionPane.ERROR_MESSAGE)
            "failed"
        }
    } catch (e: Exception) {
        JOptionPane.showMessageDialog(null, ffmpegErrorMessage(e), "错误", JOptionPane.ERROR_MESSAGE)
        "failed"
    }
}

/**
 * 使用 FFmpeg 把字幕格式转换成 SRT 格式
 */
fun convertToSrt(
    input:String,
    output:String,
    verbosity: Verbosity = Verbosity.INFO
    ):String{
    return try {
        val ffmpeg = FFmpeg(findFFmpegPath())
        val builder = FFmpegBuilder()
            .setVerbosity(verbosity)
            .setInput(input)
            .addOutput(output)
            .done()
        val executor = FFmpegExecutor(ffmpeg)
        val job = executor.createJob(builder)
        job.run()
        if (job.state == FFmpegJob.State.FINISHED) "finished" else "failed"
    } catch (e: Exception) {
        JOptionPane.showMessageDialog(null, ffmpegErrorMessage(e), "错误", JOptionPane.ERROR_MESSAGE)
        "failed"
    }
}



/**
 * 匹配富文本标签的正则表达式
 * 保留换行标签 <br />
 */

const val RICH_TEXT_REGEX = "</?(b|i|u|font|s|ruby|rt|rb|sub|sup)(\\s[^>]*)?>"
/**
 * 移除 SRT 字幕里的富文本标签
 *使用 FFmpeg 提取 mov_text 字幕时，会保留这些富文本标签，但是我们只需要纯文本，所以需要移除这些标签。
 *
 * mov_text 字幕支持以下富文本格式标签：
 * <font>: 字体样式（包括 face、size 和 color 属性）
 * <b>: 粗体文本
 * <i>: 斜体文本
 * <u>: 下划线文本
 * <s>: 删除线文本
 * <ruby>: 用于注音或解释的文本
 * <rt>: 注音文本
 * <rb>: 基本文本（与 <ruby> 一起使用）
 * <sub>: 下标文本
 * <sup>: 上标文本
 */
fun removeRichText(srtFile: File){
    var content = srtFile.readText()
    content = removeRichText(content)
    // 把换行标签 <br /> 替换成 \n
   content =  replaceNewLine(content)
    srtFile.writeText(content)
}

fun removeRichText(content: String): String {
    val richTextRegex = Regex(RICH_TEXT_REGEX)
    return richTextRegex.replace(content, "")
}


fun hasRichText(srtFile: File): Boolean {
    val content = srtFile.readText()
    val richTextRegex = Regex(RICH_TEXT_REGEX)
        return richTextRegex.containsMatchIn(content)
}
fun hasRichText(content: String): Boolean {
    val richTextRegex = Regex(RICH_TEXT_REGEX)
        return richTextRegex.containsMatchIn(content)
}


/**
 * 使用 Whisper 模型生成 SRT 字幕
 * @param input 输入视频/音频文件路径
 * @param output 输出 SRT 文件路径
 * @param modelPath Whisper 模型文件的绝对路径（必需参数）
 * @param language 语言代码 (例如 "en", "zh", "auto")
 * @param queue 队列大小，控制处理延迟和准确性的平衡
 * @return Result<Unit> 成功时返回 Result.success()，失败时返回 Result.failure()
 */
fun generateSrtWithWhisper(
    input: String,
    output: String,
    modelPath: String,
    language: String = "en",
    queue: Int = 3,
    useGpu: Boolean = true,
    gpuDevice: Int = 0,
): Result<Unit> {
    return try {
        val ffmpeg = FFmpeg(findFFmpegPath())

        // 检查模型文件是否存在
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            val error = "Whisper 模型文件不存在: $modelPath\n" +
                    "请运行 './gradlew downloadWhisperModels' 下载模型文件"
            println("错误: $error")
            return Result.failure(IllegalArgumentException(error))
        }

        // 将路径转换为绝对路径并统一为 Unix 风格
        val normalizedModelPath = modelFile.absolutePath.replace("\\", "/")
        val normalizedOutput = File(output).absolutePath.replace("\\", "/")

        val escapedModelPath = escapeFilterPath(normalizedModelPath)
        val escapedOutput = escapeFilterPath(normalizedOutput)

        // 构建 whisper 滤镜参数
        val whisperFilter = buildString {
            append("whisper=model=$escapedModelPath:language=$language:queue=$queue")
            append(":use_gpu=").append(if (useGpu) "true" else "false")
            append(":gpu_device=").append(gpuDevice)
            append(":destination=$escapedOutput:format=srt")
        }

        // 确保输出目录存在
        val outputFile = File(output)
        outputFile.parentFile?.mkdirs()

        val builder = FFmpegBuilder()
            .setVerbosity(Verbosity.INFO)
            .setInput(input)
            .addOutput(output)
            .addExtraArgs("-vn") // 禁用视频流
            .addExtraArgs("-af", whisperFilter)
            .addExtraArgs("-f", "null") // 输出格式为 null，因为实际输出由 whisper 滤镜处理
            .done()

        val executor = FFmpegExecutor(ffmpeg)
        val job = executor.createJob(builder)
        job.run()

        if (job.state == FFmpegJob.State.FINISHED) {
            // 检查输出文件是否生成成功
            val outputFile2 = File(output)
            if (outputFile2.exists() && outputFile2.length() > 0) {
                Result.success(Unit)
            } else {
                val error = "生成字幕文件失败，文件为空或不存在: $output"
                println("错误: $error")
                Result.failure(RuntimeException(error))
            }
        } else {
            val error = "使用 Whisper 生成字幕失败，FFmpeg 任务状态: ${job.state}"
            println("错误: $error")
            Result.failure(RuntimeException(error))
        }
    } catch (e: Exception) {
        println("使用 Whisper 生成字幕时出现错误:")
        e.printStackTrace()
        Result.failure(e)
    }
}

// 在 FFmpeg 滤镜参数中，需要转义某些特殊字符
// Windows 路径中的盘符冒号需要转义为 \\:
fun escapeFilterPath(path: String): String {
    return path
        .replace(":", "\\\\:")
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace("'", "\\'")
        .replace("\"", "\\\"")
}

/**
 * 提取选择的字幕到用户目录,字幕浏览器界面使用
 * */
fun writeSubtitleToFile(
    videoPath: String,
    trackId: Int,
): File? {
    val settingsDir = getSettingsDirectory()
    val subtitleFile = File(settingsDir, "subtitles.srt")
    val result = extractSubtitles(videoPath, trackId, subtitleFile.absolutePath)
    if(result == "finished"){
        // 检查字幕文件是否包含富文本标签
        val hasRichText = hasRichText(subtitleFile)
        if(hasRichText){
            removeRichText(subtitleFile)
        }

        return subtitleFile
    }else{
        return null
    }
}

/**
 * 启动一个可中断的 Whisper 生成字幕进程（非阻塞）。
 * 返回 Process 用于外部中断；完成与否需调用方等待 process.waitFor() 后自判输出文件。
 * @param onProgress 解析 FFmpeg 日志后的进度回调 (timeSec, totalSec, speed)
 */
fun startWhisperSrt(
    input: String,
    output: String,
    modelPath: String,
    language: String = "en",
    queue: Int = 3,
    useGpu: Boolean = true,
    gpuDevice: Int = 0,
    onProgress: (Double?, Double?, String?) -> Unit = { _, _, _ -> }
): Result<Process> {
    return try {
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            return Result.failure(IllegalArgumentException("Whisper 模型文件不存在: $modelPath"))
        }
        File(output).parentFile?.mkdirs()

        val ffmpegPath = findFFmpegPath()
        val normalizedModelPath = modelFile.absolutePath.replace("\\", "/")
        val normalizedOutput = File(output).absolutePath.replace("\\", "/")
        val escapedModelPath = escapeFilterPath(normalizedModelPath)
        val escapedOutput = escapeFilterPath(normalizedOutput)
        val whisperFilter = buildString {
            append("whisper=model=$escapedModelPath:language=$language:queue=$queue")
            append(":use_gpu=").append(if (useGpu) "true" else "false")
            append(":gpu_device=").append(gpuDevice)
            append(":destination=$escapedOutput:format=srt")
        }

        val command = listOf(
            ffmpegPath,
            "-y",
            "-loglevel", "info",
            "-vn",
            "-i", input,
            "-af", whisperFilter,
            "-f", "null",
            "-"
        )
        val builder = ProcessBuilder(command)
        builder.redirectErrorStream(true)
        val process = builder.start()

        // 解析日志，提取 Duration 与 time/speed
        Thread {
            var totalSec: Double? = null
            val durationRegex = Regex("Duration: (\\d+):(\\d+):(\\d+\\.?\\d*)")
            val timeRegex = Regex("time=(\\d+):(\\d+):(\\d+\\.?\\d*)")
            val speedRegex = Regex("speed=([0-9.]+)x")
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val l = line!!
                        println(l)
                        if (totalSec == null) {
                            val dm = durationRegex.find(l)
                            if (dm != null) {
                                val h = dm.groupValues[1].toDouble()
                                val m = dm.groupValues[2].toDouble()
                                val s = dm.groupValues[3].toDouble()
                                totalSec = h * 3600 + m * 60 + s
                            }
                        }
                        val tm = timeRegex.find(l)
                        val sm = speedRegex.find(l)
                        if (tm != null || sm != null) {
                            val timeSec = if (tm != null) {
                                val h = tm.groupValues[1].toDouble()
                                val m = tm.groupValues[2].toDouble()
                                val s = tm.groupValues[3].toDouble()
                                h * 3600 + m * 60 + s
                            } else null
                            val speed = sm?.groupValues?.get(1)?.let { it + "x" }
                            try {
                                onProgress(timeSec, totalSec, speed)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }


            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()

        Result.success(process)
    } catch (e: Exception) {
        e.printStackTrace()
        Result.failure(e)
    }
}
