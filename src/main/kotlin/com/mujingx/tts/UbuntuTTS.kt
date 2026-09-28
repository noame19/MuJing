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
        try {
            // 用参数数组而非字符串拼接，避免文本中的引号与元字符被 shell 解释
            val command = if (language == "en") {
                arrayOf("espeak", text)
            } else {
                arrayOf("espeak", "-v", language, text)
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
                JOptionPane.showMessageDialog(null, "请安装 espeak", "错误", JOptionPane.ERROR_MESSAGE)
            } else {
                JOptionPane.showMessageDialog(null, "${exception.message}", "错误", JOptionPane.ERROR_MESSAGE)
            }
        }

        return process
    }
}
