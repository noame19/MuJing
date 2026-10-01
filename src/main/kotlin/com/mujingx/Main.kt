package com.mujingx
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

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.window.application
import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLightLaf
import io.github.vinceglb.filekit.FileKit
import kotlinx.serialization.ExperimentalSerializationApi
import com.mujingx.theme.isSystemDarkMode
import com.mujingx.ui.App


@OptIn(ExperimentalSerializationApi::class)
@ExperimentalFoundationApi
@ExperimentalAnimationApi
fun main() {
    disableXRenderFontPipeline()
    configureLinuxScaling()
    application {
        init()
        App()
    }
}

/**
 * Turn off the JDK's XRender glyph cache on Linux.
 *
 * Under KDE Plasma 6 Wayland (via XWayland) `XRender` reports glyph format 0,
 * which `sun.font.XRGlyphCacheEntry.getType` does not recognise and rejects
 * with `IllegalStateException: Unknown glyph format: 0`.
 *
 * The throw happens while painting text through
 * `sun.swing.SwingUtilities2.drawStringUnderlineCharAt`, which is core Swing
 * rather than any look-and-feel specific: `FlatButtonUI`, `MetalButtonUI` and
 * `BasicLabelUI` all route mnemonic underlines through it. So every Swing
 * message dialog raised that exception midway through its own paint, which
 * aborted the remainder of the drawing pass and left an empty frame with no
 * error text at all.
 *
 * `sun.java2d.xrender=false` keeps Java2D on the older X11 font pipeline, which
 * never populates `XRGlyphCache`, so the crash cannot happen. Compose/Skia
 * rasterises its own text and is unaffected; only Swing/Java2D drawing changes.
 *
 * Must run before any AWT font initialisation, hence the call at the very top
 * of [main]. The AppImage build also passes this on the command line through
 * the jpackage config; this call is the fallback for every other launch path.
 */
private fun disableXRenderFontPipeline() {
    if (!System.getProperty("os.name").contains("linux", ignoreCase = true)) {
        return
    }

    // Respect an explicit override from the command line or AppRun.
    if (System.getProperty("sun.java2d.xrender") != null) {
        return
    }

    System.setProperty("sun.java2d.xrender", "false")
}

/**
 * Compose/skiko's Linux autodpi path enlarges the AWT window from Xft.dpi,
 * while the Compose content can remain at 1x density under KDE Plasma
 * fractional scaling. Set one explicit scale before Compose initializes so
 * the window and its content use the same density.
 */
private fun configureLinuxScaling() {
    if (!System.getProperty("os.name").contains("linux", ignoreCase = true)) {
        return
    }

    val scale = System.getenv("MUJING_SCALE")
        ?.toFloatOrNull()
        ?.takeIf { it in 1.0f..4.0f }
        ?: 2.5f

    System.setProperty("skiko.linux.autodpi", "false")
    System.setProperty("sun.java2d.uiScale.enabled", "true")
    System.setProperty("sun.java2d.uiScale", scale.toString())
}

fun init(){
    FileKit.init(appId = "幕境")
    if(isSystemDarkMode()) {
        FlatDarkLaf.setup()
    }else {
        FlatLightLaf.setup()
    }
}