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

import com.mujingx.player.isMacOS
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException

/**
 * 日语词典查询。
 *
 * 与英语的 [Dictionary] 使用相同的 SQLite 方案，但查询的是
 * jadic.db（日 → 中词典，源自 Yori Dict，CC BY-SA 4.0）。
 *
 * 表结构：
 *   jmdict(word TEXT, reading TEXT, translation TEXT,
 *          PRIMARY KEY(word, reading))
 *
 * 与英语词典的差异：日语没有音标，definition 留空；
 * 假名读音写入 kana 字段，中文释义写入 translation。
 */
object JapaneseDictionary {

    private const val DB_FILE = "jadic.db"

    private fun getSQLiteURL(fileName: String): String {
        val property = "compose.application.resources.dir"
        val dir = System.getProperty(property)
        return if (dir != null && !dir.endsWith("prepareAppResources")) {
            // 打包之后的环境
            if (isMacOS()) {
                "jdbc:sqlite:file:/Applications/幕境.app/Contents/app/resources/dictionary/$fileName"
            } else {
                "jdbc:sqlite:app/resources/dictionary/$fileName"
            }
        } else {
            "jdbc:sqlite:resources/common/dictionary/$fileName"
        }
    }

    /** 把结果集映射成单词 */
    private fun mapToWord(result: ResultSet): Word {
        val word = result.getString("word") ?: ""
        val reading = result.getString("reading") ?: ""
        val translation = result.getString("translation") ?: ""

        return Word(
            value = word,
            // 日语无对应 IPA 音标，保留空值
            usphone = "",
            ukphone = "",
            definition = "",
            translation = translation,
            kana = reading
        )
    }

    /** 用法：传入一个已打开的连接执行查询 */
    private fun <T> withConnection(block: (Connection) -> T?): T? {
        return try {
            val url = getSQLiteURL(DB_FILE)
            DriverManager.getConnection(url).use { conn -> block(conn) }
        } catch (e: SQLException) {
            e.printStackTrace()
            null
        }
    }

    /** 查询一个日语单词，优先返回无读音（词条本身即读音）的结果 */
    fun query(word: String): Word? {
        if (word.isBlank()) return null
        return withConnection { conn ->
            val sql = "SELECT word, reading, translation FROM jmdict WHERE word = ? " +
                "ORDER BY CASE WHEN reading = '' THEN 0 WHEN reading = ? THEN 1 ELSE 2 END LIMIT 1"
            conn.prepareStatement(sql).use { statement ->
                statement.setString(1, word)
                statement.setString(2, word)
                val result = statement.executeQuery()
                if (result.next()) {
                    mapToWord(result)
                } else {
                    null
                }
            }
        }
    }

    /**
     * 查一个词的所有义项（同一词条在不同读音下的多条记录）。
     */
    fun queryAllSenses(word: String): List<Word> {
        if (word.isBlank()) return emptyList()
        return withConnection { conn ->
            val sql = "SELECT word, reading, translation FROM jmdict WHERE word = ? ORDER BY reading"
            conn.prepareStatement(sql).use { statement ->
                statement.setString(1, word)
                val result = statement.executeQuery()
                val results = mutableListOf<Word>()
                while (result.next()) {
                    results.add(mapToWord(result))
                }
                results
            }
        } ?: emptyList()
    }

    /**
     * 用假名读音反查词条，例如输入「たべる」找到「食べる」。
     * 同时匹配 word 与 reading 两列，兼顾纯假名词条。
     */
    fun queryByReading(reading: String): Word? {
        if (reading.isBlank()) return null
        return withConnection { conn ->
            val sql = "SELECT word, reading, translation FROM jmdict WHERE reading = ? " +
                "ORDER BY CASE WHEN word = ? THEN 0 ELSE 1 END LIMIT 1"
            conn.prepareStatement(sql).use { statement ->
                statement.setString(1, reading)
                statement.setString(2, reading)
                val result = statement.executeQuery()
                if (result.next()) {
                    mapToWord(result)
                } else {
                    null
                }
            }
        }
    }

    /** 词典是否可用（文件是否存在）。用于在界面上提示降级 */
    fun isAvailable(): Boolean {
        return try {
            val url = getSQLiteURL(DB_FILE)
            DriverManager.getConnection(url).use { it.isValid(1) }
        } catch (e: Exception) {
            false
        }
    }
}
