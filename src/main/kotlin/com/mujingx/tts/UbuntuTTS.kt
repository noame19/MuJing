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

package com.mujingx.tts

import java.io.IOException
import javax.swing.JOptionPane

class UbuntuTTS {
    private var process: Process? = null

    /**
     * @param text 待朗读文本
     * @param language espeak 语言代码，默认英语。日语传 "ja"。
     */
    fun speakAndWait(text:String, language: String = "en") {
        process = speak(text, language)
        if (process != null) {
            try {
                process!!.waitFor()
            } catch (exception: InterruptedException) {
                exception.printStackTrace()
            }
        }
    }

    private fun speak(text: String, language: String): Process? {
        process = null
        val engine = resolveEngine()
        if (engine == null) {
            JOptionPane.showMessageDialog(
                null,
                "未找到语音合成引擎。\n" +
                        "请安装 espeak-ng 或 espeak 后重试。\n" +
                        "（Debian/Ubuntu：sudo apt install espeak-ng）\n" +
                        "（Fedora：sudo dnf install espeak-ng）",
                "错误", JOptionPane.ERROR_MESSAGE
            )
            return null
        }

        try {
            // 用参数数组而非字符串拼接，避免文本中的引号与元字符被 shell 解释
            val command = if (language == "en") {
                arrayOf(engine, text)
            } else {
                arrayOf(engine, "-v", language, text)
            }
            process = Runtime.getRuntime().exec(command)
            if (process != null) {
                // consume the output stream
                ProcessReader(process!!, false)
                // consume the error stream
                ProcessReader(process!!, true)

            }
        } catch (exception: IOException) {
            exception.printStackTrace()
            if(exception.message?.endsWith("No such file or directory") == true) {
                JOptionPane.showMessageDialog(null, "请安装 $engine", "错误", JOptionPane.ERROR_MESSAGE)
            } else {
                JOptionPane.showMessageDialog(null, "${exception.message}", "错误", JOptionPane.ERROR_MESSAGE)
            }
        }

        return process
    }

    /**
     * 查找可用的语音合成引擎。
     *
     * 上游原代码硬编码调用 `espeak`，但很多发行版（含 Fedora）只提供
     * `espeak-ng`，且不附带 `espeak` 兼容名，导致 Runtime.exec 抛
     * IOException 而朗读始终失败。这里依次探测可用二进制。
     *
     * 结果缓存，避免每次朗读都做一遍进程探测。
     */
    private fun resolveEngine(): String? {
        cachedEngine?.let { return it.takeIf { it.isNotEmpty() } }
        for (candidate in CANDIDATE_ENGINES) {
            if (isAvailable(candidate)) {
                cachedEngine = candidate
                return candidate
            }
        }
        // 记下空串，避免每帧都重复探测
        cachedEngine = ""
        return null
    }

    /**
     * 用 `command -v` 判定二进制是否存在。
     * 不直接启动引擎，避免每次朗读都付出进程启动与合成音频的代价。
     */
    private fun isAvailable(command: String): Boolean = try {
        val probe = ProcessBuilder("sh", "-c", "command -v $command")
        probe.redirectErrorStream(true)
        val process = probe.start()
        process.inputStream.close()
        process.waitFor() == 0
    } catch (e: Exception) {
        false
    }

    private companion object {
        /** 常见发行版上的 espeak 实现，按优先级排列 */
        private val CANDIDATE_ENGINES = listOf("espeak-ng", "espeak")

        /** 缓存探测结果，空串表示「已探测过且都不可用」 */
        private var cachedEngine: String? = null
    }
}
