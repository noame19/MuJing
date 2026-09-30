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

package com.mujingx.ui.flatlaf

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.FlatLightLaf
import com.mujingx.theme.isSystemDarkMode
import java.awt.Color
import java.awt.Font
import java.awt.Insets
import javax.swing.UIManager
import javax.swing.border.LineBorder


/**
 * 更新 FlatLaf
 */
fun updateFlatLaf(
    darkTheme: Boolean,
    background: Color,
    onBackground: Color,
    isFollowSystemTheme: Boolean=true,
) {
    val isDark =  if(isFollowSystemTheme){
        isSystemDarkMode()
    }else darkTheme

    // 启动 FlatLaf
    // FlatLaf 绘制带下划线的文字（按钮/标签的助记符，如 Swing 对话框里的
    // 快捷键提示）会走 FlatUIUtils.drawStringUnderlineCharAt → XRGlyphCache。
    // 在 KDE Plasma 6 Wayland（经 XWayland）下 XRender 返回 glyph format 0，
    // JDK 无法识别，抛 IllegalStateException: Unknown glyph format: 0，
    // 结果是绘制中断：对话框正文与按钮文字都画不出来，只剩一个空框。
    //
    // 该问题只影响 Swing 对话框的助记符下划线装饰，不影响任何文字内容，
    // 因此在 Linux 上关闭助记符显示即可绕开这条渲染路径。
    // 此属性需在 FlatLaf.setup() 之前设置，setup 会读取客户端属性。
    if (System.getProperty("os.name").contains("linux", ignoreCase = true)) {
        UIManager.put("flatlaf.showMnemonic", "false")
    }

    if(isDark) {
        FlatDarkLaf.setup()
    }else {
        FlatLightLaf.setup()
    }

   // 先设置共同的样式
    UIManager.put("TitlePane.showIcon", false)
    UIManager.put("TitlePane.menuBarEmbedded", true)
    UIManager.put("TitlePane.centerTitle", true)
    UIManager.put("TitlePane.unifiedBackground", false)
    UIManager.put("TitlePane.TitlePane.noIconLeftGap", 0)
    UIManager.put("TitlePane.iconMargins", Insets(0, 0, 0, 0))
    UIManager.put("defaultFont", Font("Default", Font.PLAIN, 16))
    UIManager.put("SplitPaneDivider.gripDotCount", 0)
    UIManager.put("SplitPane.dividerSize", 1)

    if (isDark) {
        // Panel
        UIManager.put("Panel.background", Color(18, 18, 18))

        // TitlePane
        UIManager.put("TitlePane.background", Color(18, 18, 18))
        UIManager.put("TitlePane.foreground", Color(133, 144, 151))
        UIManager.put("TitlePane.focusColor", Color(30, 30, 30))

        // ScrollPane
        UIManager.put("ScrollPane.background", Color(62, 66, 68))
        UIManager.put("ScrollPane.foreground", Color(187, 187, 187))
        UIManager.put("ScrollPane.border", LineBorder(Color(55, 55, 55), 1))
        //MenuBar
        UIManager.put("MenuBar.borderColor", Color(55, 55, 55))
        //SplitPane
        UIManager.put("SplitPane.background", Color(30, 30, 30))
        //Tree
        UIManager.put("Tree.background", Color(18, 18, 18))
        UIManager.put("Tree.foreground", Color(133, 144, 151))
        UIManager.put("Tree.icon.closedColor", Color(133, 144, 151))
        UIManager.put("Tree.icon.collapsedColor", Color(133, 144, 151))
        UIManager.put("Tree.icon.leafColor", Color(133, 144, 151))
        UIManager.put("Tree.icon.openColor", Color(133, 144, 151))

        //Table
        UIManager.put("Table.background", Color(18, 18, 18))
        UIManager.put("Table.foreground", Color(133, 144, 151))
        UIManager.put("Table.selectionBackground", Color(30, 30, 30))
        UIManager.put("TableHeader.background", Color(35, 35, 35))
        UIManager.put("Table.gridColor", Color(29, 29, 29))
        UIManager.put("Table.cellFocusColor", Color(13, 152, 6))
        UIManager.put("Table.selectionInactiveForeground", Color(13, 152, 6))
        UIManager.put("TableHeader.bottomSeparatorColor", Color(50, 50, 50))
        UIManager.put("TableHeader.separatorColor", Color(50, 50, 50))

        // TextField
        UIManager.put("TextField.background", Color(18, 18, 18))

        // Highlighter
        UIManager.put("textHighlightText", Color(210, 210, 210))

        // Button
        UIManager.put("Button.toolbar.hoverBackground", Color(31, 31, 31))
        UIManager.put("Button.focusedBorderColor", Color(18, 18, 18))
        UIManager.put("Button.default.focusedBorderColor", Color(18, 18, 18))
        UIManager.put("Button.default.focusColor", Color(18, 18, 18))
        UIManager.put("Button.pressedBackground", Color(18, 18, 18))

    } else {
        // Panel
        UIManager.put("Panel.background", background)

        // TitlePane
        val border = Color(224, 224, 224)
        UIManager.put("TitlePane.background", background)
        UIManager.put("TitlePane.foreground", onBackground)
        UIManager.put("TitlePane.focusColor", border)

        // ScrollPane
        UIManager.put("ScrollPane.background", Color(245, 245, 245))
        UIManager.put("ScrollPane.foreground", Color(0, 0, 0))
        UIManager.put("ScrollPane.border", LineBorder(border, 1))

        //MenuBar
        UIManager.put("MenuBar.borderColor", border)

        //SplitPane
        UIManager.put("SplitPane.background", background)
        UIManager.put("Tree.background", background)
        UIManager.put("Tree.foreground", onBackground)

        //Table
        UIManager.put("Table.background", Color(255, 255, 255))
        UIManager.put("Table.foreground", Color(0, 0, 0))
        UIManager.put("Table.selectionBackground", Color(195, 195, 195))
        UIManager.put("TableHeader.background", Color(239, 239, 239))
        UIManager.put("Table.gridColor", Color(235, 235, 235))
        UIManager.put("Table.cellFocusColor", Color(0, 0, 0))
        UIManager.put("Table.selectionInactiveForeground", Color(0, 0, 0))
        UIManager.put("TableHeader.bottomSeparatorColor", Color(230, 230, 230))
        UIManager.put("TableHeader.separatorColor", Color(230, 230, 230))

        // TextField
        UIManager.put("TextField.background", Color(255, 255, 255))

        // Highlighter
        UIManager.put("textHighlightText", background)

        // Button
        UIManager.put("Button.toolbar.hoverBackground", Color(245, 245, 245))
        UIManager.put("Button.focusedBorderColor", background)
        UIManager.put("Button.default.focusedBorderColor", background)
        UIManager.put("Button.default.pressedBackground", background)
        UIManager.put("Button.pressedBackground", background)
    }

    FlatLaf.updateUI()
}

