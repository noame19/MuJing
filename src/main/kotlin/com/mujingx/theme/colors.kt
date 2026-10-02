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

package com.mujingx.theme

import androidx.compose.material.Colors
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.ui.graphics.Color
import org.slf4j.LoggerFactory
import com.mujingx.state.GlobalState
import org.slf4j.Logger
import java.io.File


val IDEADarkThemeOnBackground = Color(133, 144, 151)

fun createColors(
    isDarkTheme: Boolean,
    isFollowSystemTheme:Boolean = true,
    primary: Color,
    background:Color,
    onBackground:Color
): Colors {
    val isDark = if (isFollowSystemTheme) {
        isSystemDarkMode()
    } else isDarkTheme

    return if (isDark) {
        darkColors(
            primary = primary,
            onBackground = IDEADarkThemeOnBackground
        )
    } else {
        lightColors(
            primary = primary,
            background = background,
            surface = background,
            onBackground = onBackground
        )
    }
}


fun createColors(
    global: GlobalState
): Colors {
    val isDark = if (global.isFollowSystemTheme) {
        isSystemDarkMode()
    } else global.isDarkTheme

    return if (isDark) {
        darkColors(
            primary = global.primaryColor,
            onBackground = IDEADarkThemeOnBackground
        )
    } else {
        lightColors(
            primary = global.primaryColor,
            background = global.backgroundColor,
            surface = global.backgroundColor,
            onBackground = global.onBackgroundColor
        )
    }
}

fun java.awt.Color.toCompose(): Color {
    return Color(red, green, blue)
}

fun Color.toAwt(): java.awt.Color {
    return java.awt.Color(red, green, blue)
}

fun isSystemDarkMode(): Boolean {
    val logger = LoggerFactory.getLogger("isSystemDarkMode")
    return when {
        System.getProperty("os.name").contains("Mac", ignoreCase = true) -> {
            val command = arrayOf("/usr/bin/defaults", "read", "-g", "AppleInterfaceStyle")
            try {
                val process = Runtime.getRuntime().exec(command)
                process.inputStream.bufferedReader().use { it.readText().trim() == "Dark" }
            } catch (e: Exception) {
                logError(e, logger)
                false
            }
        }
        System.getProperty("os.name").contains("Windows", ignoreCase = true) -> {
            val command = "reg query HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize /v AppsUseLightTheme"
            try {
                val process = Runtime.getRuntime().exec(command)
                process.inputStream.bufferedReader().use { reader ->
                    val output = reader.readText()
                    !output.contains("0x1")
                }
            } catch (e: Exception) {
                logError(e, logger)
                false
            }
        }
        System.getProperty("os.name").contains("Linux", ignoreCase = true) -> {
            // KDE 与 GNOME 把主题存在完全不同的地方，读错来源会恒定判成浅色。
            // 实测 KDE Plasma 上 gsettings 读 org.gnome.desktop.interface
            // 拿到的是 GNOME 遗留的 'Breeze'，与应用实际使用的
            // ~/.config/kdeglobals 里的 ColorScheme=BreezeDark 无关。
            val kdeColorScheme = readKdeColorScheme()
            if (kdeColorScheme != null) {
                kdeColorScheme.contains("dark", ignoreCase = true)
            } else {
                val command = arrayOf("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme")
                try {
                    val process = Runtime.getRuntime().exec(command)
                    process.inputStream.bufferedReader().use { reader ->
                        val output = reader.readText().trim()
                        output.contains("dark", ignoreCase = true)
                    }
                } catch (e: Exception) {
                    logError(e, logger)
                    false
                }
            }
        }
        else -> false
    }
}

/**
 * 读 KDE Plasma 的配色方案名，取自 ~/.config/kdeglobals 的
 * `[General] ColorScheme`。
 *
 * 不能改用 `gsettings get org.kde.desktopinterface colorScheme`：实测本机
 * `gsettings list-schemas` 里没有任何 org.kde 条目，该命令直接报「没有这个
 * 架构」。kdeglobals 是 Plasma 真正写配置的地方，解析它才靠得住。
 *
 * 非 KDE 环境或文件不存在时返回 null，由调用方回落到 GNOME 的 gsettings。
 */
private fun readKdeColorScheme(): String? {
    return try {
        val configHome = System.getenv("XDG_CONFIG_HOME")
            ?.takeIf { it.isNotBlank() }
            ?: "${System.getProperty("user.home")}/.config"
        val file = File(configHome, "kdeglobals")
        if (!file.isFile) {
            return null
        }
        var inGeneral = false
        var colorScheme: String? = null
        file.forEachLine { line ->
            val text = line.trim()
            when {
                text.startsWith("[") ->
                    inGeneral = text.equals("[General]", ignoreCase = true)
                inGeneral && text.startsWith("ColorScheme=", ignoreCase = true) ->
                    colorScheme = text.substringAfter('=').trim()
            }
        }
        colorScheme
    } catch (e: Exception) {
        null
    }
}

fun logError(e: Exception, logger: Logger) {
    logger.error("Error StackTrace: ${e.stackTraceToString()}\n")
}