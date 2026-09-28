#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成幕境日语内置词库（JLPT N5-N1）。

数据来源（均为可再分发的开放许可）：

1. OpenJLPT — JLPT N5-N1 词汇分级 + 例句
   https://github.com/evanclan/OpenJLPT
   许可：CC BY-SA 4.0
   分级原始清单来自 Jonathan Waller 的社区标准列表（CC BY）。
   注意：JLPT 官方自 2010 年改版起不再公布官方词表。

2. Yori Dict（zh-cn）— 日语词条的中文释义
   https://github.com/YoriJP/yori-dict
   许可：CC BY-SA 4.0
   词条格式为 Yomichan：
   [词条, 读音假名, 定义标签, 规则, 分数, 释义列表, 序号, 词条标签]

用法：
    python3 tools/japanese/build_vocabulary.py

可选依赖：
    pykakasi  用于生成罗马字读音。缺失时 romaji 字段留空，其余功能不受影响。
    （pykakasi 为 GPL-3.0-or-later，仅作为构建期工具被调用，
      不链接进应用；应用本身同为 GPL-3.0，许可兼容。）

    pip install pykakasi
"""

import json
import os
import sqlite3
import ssl
import sys
import urllib.request
import zipfile
from collections import defaultdict
from io import BytesIO

# ---------------------------------------------------------------- 配置

OUTPUT_DIR = "resources/common/vocabulary/JLPT日语"

# 日→中词典（供 JapaneseDictionary.kt 查询），与 ECDICT 放在同一目录。
# 该文件不纳入版本库，由 .gitignore 排除，构建时重新生成。
DICT_OUTPUT = "resources/common/dictionary/jadic.db"

OPENJLPT_BASE = "https://raw.githubusercontent.com/evanclan/OpenJLPT/main/data/json/vocab"
YORI_LANG = "zh-cn"
# Yori Dict release，固定版本以便复现
YORI_TAG = "data-2026-08-08"
YORI_URL = (
    f"https://github.com/YoriJP/yori-dict/releases/download/{YORI_TAG}/yori-ja-{YORI_LANG}.zip"
)

# JLPT 等级：由易到难，数字前缀用于词库列表排序
LEVELS = [
    ("N5", 1, "日语能力测试 N5"),
    ("N4", 2, "日语能力测试 N4"),
    ("N3", 3, "日语能力测试 N3"),
    ("N2", 4, "日语能力测试 N2"),
    ("N1", 5, "日语能力测试 N1"),
]

VOCABULARY_TYPE = "DOCUMENT"
LANGUAGE = "japanese"

# 单个词条最多保留的中文释义数，避免「猫」这类词条刷屏
MAX_GLOSSES = 6

# 缓存目录：避免重复下载上游大文件
CACHE_DIR = os.environ.get("MUJING_JP_CACHE", ".cache/japanese")


def log(msg):
    print(f"[build] {msg}", flush=True)


def fetch(url, cache_name):
    """下载并缓存。优先使用缓存，便于离线复现。"""
    os.makedirs(CACHE_DIR, exist_ok=True)
    cache_path = os.path.join(CACHE_DIR, cache_name)

    if os.path.exists(cache_path) and os.path.getsize(cache_path) > 0:
        log(f"使用缓存 {cache_name}")
        with open(cache_path, "rb") as fh:
            return fh.read()

    log(f"下载 {url}")
    ctx = ssl.create_default_context()
    req = urllib.request.Request(url, headers={"User-Agent": "MuJing-vocab-builder"})
    with urllib.request.urlopen(req, timeout=180, context=ctx) as resp:
        data = resp.read()

    with open(cache_path, "wb") as fh:
        fh.write(data)
    log(f"已缓存 {cache_name}（{len(data) / 1024:.0f} KiB）")
    return data


# ---------------------------------------------------------------- 数据加载

def load_openjlpt():
    """加载 OpenJLPT 词汇分级数据。"""
    result = {}
    for level, order, _ in LEVELS:
        raw = fetch(f"{OPENJLPT_BASE}/{level.lower()}.json", f"openjlpt-{level.lower()}.json")
        entries = json.loads(raw.decode("utf-8"))
        result[level] = (order, entries)
        log(f"OpenJLPT {level}: {len(entries)} 条")
    return result


def load_yori_zh_cn():
    """加载 Yori Dict 日→简中词条，返回 {词条: (假名读音, [中文释义])}。"""
    raw = fetch(YORI_URL, f"yori-ja-{YORI_LANG}.zip")
    archive = zipfile.ZipFile(BytesIO(raw))

    banks = sorted(n for n in archive.namelist() if n.startswith("term_bank_"))
    if not banks:
        raise RuntimeError("Yori 压缩包中未找到 term_bank 文件")

    index = defaultdict(dict)  # word -> {reading: [gloss, ...]}
    for name in banks:
        for entry in json.loads(archive.read(name).decode("utf-8")):
            term, reading = entry[0], entry[1]
            glosses = [g for g in (entry[5] or []) if isinstance(g, str) and g.strip()]
            if not glosses:
                continue
            bucket = index[term]
            existing = bucket.get(reading)
            if existing:
                for g in glosses:
                    if g not in existing:
                        existing.append(g)
            else:
                bucket[reading] = list(glosses)

    log(f"Yori zh-cn: {len(index)} 个词条")
    return index


def load_romaji_converter():
    """返回 kana -> romaji 转换函数；依赖缺失时返回 None。"""
    try:
        from pykakasi import kakasi
    except ImportError:
        log("未安装 pykakasi，跳过罗马字生成（可选依赖）")
        return None

    conv = kakasi()
    log("已加载 pykakasi，将生成罗马字读音")
    return lambda kana: conv.convert(kana)[0]["hepburn"] if kana else ""


# ---------------------------------------------------------------- 词条构建

def pick_chinese_glosses(term, kana, yori_index):
    """取出中文释义，优先匹配与假名读音一致的词条。"""
    bucket = yori_index.get(term)
    if not bucket:
        return []

    if kana and kana in bucket:
        glosses = bucket[kana]
    else:
        glosses = []
        for readings in bucket.values():
            glosses.extend(readings)

    # 去重并保序
    seen = set()
    ordered = []
    for g in glosses:
        if g not in seen:
            seen.add(g)
            ordered.append(g)
    return ordered[:MAX_GLOSSES]


def build_word(entry, level, yori_index, to_romaji):
    term = (entry.get("word") or "").strip()
    if not term:
        return None

    kana = (entry.get("reading") or "").strip()

    # OpenJLPT 的 reading 对纯假名词条常为空，回退用词条本身
    if not kana:
        kana = term if is_kana(term) else ""

    meanings = [m for m in (entry.get("meanings") or []) if isinstance(m, str) and m.strip()]
    definition = "\n".join(meanings)

    chinese = pick_chinese_glosses(term, kana, yori_index)
    translation = "\n".join(chinese)

    romaji = to_romaji(kana) if (to_romaji and kana) else ""

    return {
        "value": term,
        # 日语无对应 IPA 音标，保留空值以兼容现有 UI 结构
        "usphone": "",
        "ukphone": "",
        "definition": definition,
        "translation": translation,
        "pos": "",
        "collins": 0,
        "oxford": False,
        "tag": f"jlpt-{level.lower()}",
        "bnc": 0,
        "frq": 0,
        "exchange": "",
        "kana": kana,
        "romaji": romaji,
        "level": level,
        "externalCaptions": [],
        "captions": [],
    }


def is_kana(text):
    """判断字符串是否由平假名/片假名构成。"""
    if not text:
        return False
    for ch in text:
        code = ord(ch)
        if not (0x3041 <= code <= 0x309F or 0x30A0 <= code <= 0x30FF):
            return False
    return True


# ---------------------------------------------------------------- 词典数据库

def build_dictionary_db(yori_index):
    """
    生成日→中 SQLite 词典 jadic.db，供 JapaneseDictionary.kt 查询。

    表结构与 Kotlin 侧一一对应：
        jmdict(word, reading, translation)，主键 (word, reading)
    """
    os.makedirs(os.path.dirname(DICT_OUTPUT), exist_ok=True)
    if os.path.exists(DICT_OUTPUT):
        os.remove(DICT_OUTPUT)

    conn = sqlite3.connect(DICT_OUTPUT)
    try:
        conn.execute(
            "CREATE TABLE jmdict ("
            " word TEXT NOT NULL, "
            " reading TEXT DEFAULT '', "
            " translation TEXT DEFAULT '', "
            " PRIMARY KEY(word, reading))"
        )
        conn.execute("CREATE INDEX idx_jmdict_word ON jmdict(word)")

        rows = [
            (word, reading, "\n".join(glosses))
            for word, readings in yori_index.items()
            for reading, glosses in readings.items()
        ]
        conn.executemany(
            "INSERT OR REPLACE INTO jmdict(word, reading, translation) VALUES(?,?,?)",
            rows,
        )
        conn.commit()
    finally:
        conn.close()

    size_mb = os.path.getsize(DICT_OUTPUT) / 1048576
    log(f"词典 {DICT_OUTPUT}：{len(rows)} 条记录，{size_mb:.1f} MiB")


# ---------------------------------------------------------------- 主流程

def main():
    openjlpt = load_openjlpt()
    yori_index = load_yori_zh_cn()
    to_romaji = load_romaji_converter()

    # 先生成词典，再生成词库，两者共用同一份 Yori 数据
    build_dictionary_db(yori_index)

    os.makedirs(OUTPUT_DIR, exist_ok=True)
    total_words = 0

    for level, order, display_name in LEVELS:
        _, entries = openjlpt[level]

        words = []
        seen = set()
        for entry in entries:
            word = build_word(entry, level, yori_index, to_romaji)
            if word is None:
                continue
            # 同一等级内去重，保持首次出现顺序
            if word["value"] in seen:
                continue
            seen.add(word["value"])
            words.append(word)

        vocabulary = {
            "name": display_name,
            "type": VOCABULARY_TYPE,
            "language": LANGUAGE,
            "size": len(words),
            "relateVideoPath": "",
            "subtitlesTrackId": 0,
            "wordList": words,
        }

        out_path = os.path.join(OUTPUT_DIR, f"{order} {level}.json")
        with open(out_path, "w", encoding="utf-8") as fh:
            json.dump(vocabulary, fh, ensure_ascii=False, indent=4)

        with_cn = sum(1 for w in words if w["translation"])
        with_romaji = sum(1 for w in words if w["romaji"])
        log(
            f"{level}: {len(words)} 词 | 中文释义 {with_cn} "
            f"({100 * with_cn // max(len(words), 1)}%) | 罗马字 {with_romaji} -> {out_path}"
        )
        total_words += len(words)

    log(f"完成，共 {total_words} 词，输出目录 {OUTPUT_DIR}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
