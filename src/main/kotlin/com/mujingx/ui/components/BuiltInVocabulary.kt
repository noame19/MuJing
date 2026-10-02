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

package com.mujingx.ui.components

import com.mujingx.state.getResourcesFile
import java.io.File

/**
 * 内置词库目录：resources/common/vocabulary 下的每个子目录都是一个词库分类，
 * 例如「大学英语」「牛津核心词」「JLPT日语」。
 *
 * 分类列表在运行时扫描得到，新增语言或新增分类时无需再改动 UI 代码。
 */
private const val VOCABULARY_ROOT = "vocabulary"

/**
 * 内置词库文件后缀。目录下可能混有 NOTICE.md 等说明文件，
 * 只有 .json 才算作可选择的词库。
 *
 * 注意：java.io.File.extension 返回不带点的扩展名，因此这里不能带 "."。
 */
private const val VOCABULARY_EXTENSION = "json"

/**
 * 需要按文件名中的数字前缀排序的分类。
 * 这些分类的文件名形如「3.1 人教版三年级上.json」「1 N5.json」，
 * 字典序会排在错误的位置（10 会排在 2 前面），因此按数字排序。
 */
private val NUMERIC_SORT_CATEGORIES = setOf(
    "人教版英语",
    "外研版英语",
    "北师大版高中英语",
    "JLPT日语",
    "标准日本语",
    "みんなの日本語",
    "JLPT精选"
)

/**
 * 扫描内置词库的全部分类目录，按名称排序返回。
 */
fun getBuiltInVocabularyDirectories(): List<File> {
    val root = getResourcesFile(VOCABULARY_ROOT)
    if (!root.isDirectory) return emptyList()
    return root.listFiles()
        ?.filter { it.isDirectory }
        ?.sortedBy { it.name }
        ?: emptyList()
}

/**
 * 列出某个分类下可选择的词库文件（仅 .json），必要时按数字前缀排序。
 */
fun listVocabularyFiles(directory: File): List<File> {
    if (!directory.isDirectory) return emptyList()
    val files = directory.listFiles()?.filter { it.isFile && it.extension == VOCABULARY_EXTENSION }
        ?: return emptyList()

    if (directory.nameWithoutExtension in NUMERIC_SORT_CATEGORIES) {
        return files.sortedBy { numericSortKey(it.nameWithoutExtension) }
    }
    return files.sortedBy { it.nameWithoutExtension }
}

/**
 * 取文件名空格前的部分作为排序依据。
 *
 * 文件名前缀可能带小数（人教版用「3.1」「3.2」区分上下册），
 * 因此不能只取第一个点号之前的部分，否则 3.1 与 3.2 会得到相同排序值。
 * 取不到数字时回退到最大值，让这些文件排在数字前缀的文件之后，
 * 而不是抛异常。
 */
private fun numericSortKey(nameWithoutExtension: String): Float {
    val prefix = nameWithoutExtension.substringBefore(' ')
    return prefix.toFloatOrNull() ?: Float.MAX_VALUE
}

/**
 * 生成词库在 UI 上显示的名称。
 *
 * 形如「1 N5」的排序前缀不展示给用户，只显示「N5」；
 * 其余分类直接使用文件名。
 */
fun formatVocabularyName(file: File, directory: File): String {
    val name = file.nameWithoutExtension
    if (directory.nameWithoutExtension in NUMERIC_SORT_CATEGORIES && name.contains(' ')) {
        return name.substringAfter(' ').ifEmpty { name }
    }
    return name
}
