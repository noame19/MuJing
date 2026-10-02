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

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class MacTTS {
    private var process: Process? = null

    /**
     * @param text 待朗读文本
     * @param voice macOS `say` 的声音名。英语传 null 使用系统默认，
     *               日语传 "Kyoko"。
     */
    fun speakAndWait(text:String, voice: String? = null) {
        process = speak(text, voice)
        if (process != null) {
            try {
                process!!.waitFor()
            } catch (exception: InterruptedException) {
                exception.printStackTrace()
            }
        }
    }

    private fun speak(text: String, voice: String?): Process? {
        process = null
        try {
            // 用参数数组而非字符串拼接，避免文本中的引号与元字符被 shell 解释
            val command = if (voice.isNullOrEmpty()) {
                arrayOf("say", text)
            } else {
                arrayOf("say", "-v", voice, text)
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
        }

        return process
    }
}

internal class ProcessReader(process: Process, errorStream: Boolean) {
    init {
        val processStream = if (errorStream) process.errorStream else process.inputStream
            try {
                val br = BufferedReader(InputStreamReader(processStream))
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    if (errorStream) {
                        System.err.println(line)
                    } else {
                        println(line)
                    }
                }
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
    }
}

