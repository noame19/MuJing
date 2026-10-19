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

package com.mujingx.data

import com.mujingx.state.getResourcesFile
import com.mujingx.state.getSettingsDirectory
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import javax.swing.JOptionPane

/**
 * 单个单词的学习标记。
 *
 * 内置词库位于应用资源目录（只读），既不能回写词库文件，也不能把单词真正删掉，
 * 所以「从当前词库移除」这一动作改为在这里记标记，读取词库时再按标记过滤。
 */
@Serializable
data class WordMark(
    /** 已加入熟悉词库 */
    var familiar: Boolean = false,
    /** 已从当前词库移除（删除、加入熟悉词库、加入困难词库都会置位） */
    var removed: Boolean = false,
    /** 已加入困难词库 */
    var hard: Boolean = false,
    /**
     * 记忆过的次数。
     *
     * 目前没有任何地方写入（记忆统计还挂在 FSRS 那一套上），保留字段是为了
     * 以后接统计时不用再改进度文件的格式 —— 删掉会让旧进度文件反序列化
     * 丢失该字段，虽然无害但没必要提前做决定。无写入时它恒为 0。
     */
    var seen: Int = 0,
    /** 记错的次数。同上，当前未写入。 */
    var wrong: Int = 0,
    /** 最近一次修改时间 */
    var updatedAt: Long = 0L,
)

/**
 * 一个词库的学习进度，与词库文件分离保存（见 [WordProgressStore]）。
 *
 * 这样内置词库可以直接学习而不用先另存为副本，进度也不会因为词库文件更新而丢失。
 */
@Serializable
data class VocabularyProgress(
    /** 词库标识，见 [WordProgressStore.idFor] */
    var id: String = "",
    /** 词库名称，仅用于排查问题 */
    var name: String = "",
    /** 上次学到第几个词（在过滤掉已移除的词之后的列表中的下标） */
    var index: Int = 0,
    /** 最近一次保存时间 */
    var updatedAt: Long = 0L,
    /** 单词标记，键为单词写法（Word.value） */
    var marks: Map<String, WordMark> = emptyMap(),
    /**
     * 编辑过的词条（覆盖层），键为词库里原来的写法。
     *
     * 内置词库是只读资源，编辑结果只能记在这里，下次打开该词库时覆盖原词条。
     */
    var overrides: Map<String, Word> = emptyMap(),
    /**
     * 打乱后的顺序（内置词库只读，打乱结果只能记在这里）：
     * 过滤掉已移除的词之后的列表下标排列。
     */
    var order: List<Int> = emptyList(),
) {

    /** 取单词的标记，没有则返回默认标记 */
    fun markOf(value: String): WordMark = marks[value] ?: WordMark()

    /** 单词是否已从当前词库移除 */
    fun isRemoved(value: String): Boolean = markOf(value).removed

    /** 更新单词的标记并返回新的进度 */
    fun withMark(value: String, transform: (WordMark) -> WordMark): VocabularyProgress {
        val mark = transform(markOf(value)).let { it.copy(updatedAt = System.currentTimeMillis()) }
        return copy(marks = marks + (value to mark), updatedAt = System.currentTimeMillis())
    }
}

/**
 * 学习进度的读写。
 *
 * 进度文件存放在 `~/.MuJing/progress/<词库标识的哈希>.json`，与词库文件完全分离：
 * - 内置词库（应用资源目录里的词库）直接学习，不写任何副本；
 * - 词库文件更新、移动都不影响已有进度。
 */
object WordProgressStore {

    /** 内置词库所在的资源目录（与 ui/components/BuiltInVocabulary.kt 的 VOCABULARY_ROOT 一致） */
    private const val VOCABULARY_ROOT = "vocabulary"

    private const val BUILT_IN_PREFIX = "builtin:"

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private fun canonical(file: File): String =
        try {
            file.canonicalPath
        } catch (e: Exception) {
            file.absolutePath
        }

    /** 内置词库根目录（应用资源目录下的 vocabulary），只解析一次 */
    private val rootPath: String by lazy { canonical(getResourcesFile(VOCABULARY_ROOT)) }

    /**
     * 词库路径相对于内置词库目录的路径，例如「JLPT日语/2 N4.json」。
     * 不是内置词库（用户自己的词库）时返回 null。
     */
    fun builtInRelativePath(path: String): String? {
        if (path.isBlank()) return null
        val file = if (File(path).isAbsolute) File(path) else getResourcesFile(path)
        val filePath = canonical(file)
        if (filePath == rootPath) return null
        val prefix = "$rootPath${File.separator}"
        return if (filePath.startsWith(prefix)) filePath.removePrefix(prefix) else null
    }

    /** 是否是内置词库（只读） */
    fun isBuiltIn(path: String): Boolean = builtInRelativePath(path) != null

    /**
     * 词库标识：内置词库用「builtin:<相对路径>」，这样换机器、换解压目录都能对上；
     * 用户自己的词库用规范化的绝对路径。
     */
    fun idFor(path: String): String {
        if (path.isBlank()) return ""
        val relative = builtInRelativePath(path)
        return if (relative != null) "$BUILT_IN_PREFIX$relative" else canonical(File(path))
    }

    private fun progressDirectory(): File {
        val dir = File(getSettingsDirectory(), "progress")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun progressFile(id: String): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(id.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return File(progressDirectory(), "$hex.json")
    }

    /** 读取进度，没有则返回 null */
    fun load(id: String): VocabularyProgress? {
        val file = progressFile(id)
        if (!file.exists()) return null
        return try {
            json.decodeFromString<VocabularyProgress>(file.readText())
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                null,
                "读取学习进度失败，错误信息：\n${e.message}",
                "错误",
                JOptionPane.ERROR_MESSAGE
            )
            null
        }
    }

    /** 保存进度 */
    fun save(progress: VocabularyProgress) {
        // 还没有词库（词库路径为空）时不记录
        if (progress.id.isEmpty()) return
        try {
            val data = progress.copy(updatedAt = System.currentTimeMillis())
            progressFile(data.id).writeText(json.encodeToString(data))
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                null,
                "保存学习进度失败，错误信息：\n${e.message}",
                "错误",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    /**
     * 读取进度；第一次接触这个词库时，用旧设置文件（TypingWordSettings.json）里的下标迁移一次并落盘。
     */
    fun loadOrMigrate(path: String, name: String, legacyIndex: Int): VocabularyProgress {
        // 还没有打开任何词库时只返回内存里的默认进度，不落盘
        if (path.isBlank()) return VocabularyProgress(name = name, index = legacyIndex)
        val id = idFor(path)
        load(id)?.let { return it }
        val progress = VocabularyProgress(id = id, name = name, index = legacyIndex)
        save(progress)
        return progress
    }
}
