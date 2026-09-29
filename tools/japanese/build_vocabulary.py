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
import re
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

# 扩展词表来源
# 1) 新标准日本语词表（MIT）。字段结构：
#    [课次ID, 词性, 中文释义, "假名(汉字)", 音频时间戳]
STD_WORDS_URL = "https://raw.githubusercontent.com/smartsl/biaori/master/words.json"
STD_OUTPUT_DIR = "resources/common/vocabulary/标准日本语"

# 2) みんなの日本語 初級 I & II（按课次组织）
MINNA_YAML_URL = "https://raw.githubusercontent.com/vitto4/MinnaNoDS/master/minna-no-ds.yaml"
MINNA_OUTPUT_DIR = "resources/common/vocabulary/みんなの日本語"

# 3) JLPT Anki 词表（MIT，CSV：expression,reading,meaning,tags,guid）
ANKI_BASE = "https://raw.githubusercontent.com/jamsinclair/open-anki-jlpt-decks/main/src"
ANKI_OUTPUT_DIR = "resources/common/vocabulary/JLPT精选"

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


def build_word(entry, level, yori_index, to_romaji, romaji_cache):
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

    # 罗马字依赖可选组件 pykakasi。若本次生成时它不可用，
    # 复用上一轮已生成的读音，避免把仓库里已有的数据清空。
    cache_key = (term, kana)
    if to_romaji and kana:
        romaji = to_romaji(kana)
        if romaji:
            romaji_cache[cache_key] = romaji
    else:
        romaji = romaji_cache.get(cache_key, "")

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


# ---------------------------------------------------------------- 扩展词表

def write_vocabulary(out_dir, name, words, order=None):
    """按幕境词库格式写出单个词库文件。"""
    os.makedirs(out_dir, exist_ok=True)
    filename = f"{order} {name}.json" if order else f"{name}.json"
    payload = {
        "name": name,
        "type": VOCABULARY_TYPE,
        "language": LANGUAGE,
        "size": len(words),
        "relateVideoPath": "",
        "subtitlesTrackId": 0,
        "wordList": words,
    }
    path = os.path.join(out_dir, filename)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False, indent=4)
    return path, len(words)


def make_word(term, kana, chinese, english, level, romaji="", pos=""):
    """构造一个 MuJing 词条。"""
    return {
        "value": term,
        "usphone": "",
        "ukphone": "",
        "definition": english,
        "translation": chinese,
        "pos": pos,
        "collins": 0,
        "oxford": False,
        "tag": f"{level}".lower(),
        "bnc": 0,
        "frq": 0,
        "exchange": "",
        "kana": kana,
        "romaji": romaji,
        "level": level,
        "externalCaptions": [],
        "captions": [],
    }


def build_standard_japanese(yori_index, to_romaji):
    """
    新标准日本语词表。

    上游字段为 [课次ID, 词性, 中文释义, "假名(汉字)", 音频时间戳]，
    日语词本身在第 4 字段的括号内（纯假名词条则直接是假名）。
    """
    raw = fetch(STD_WORDS_URL, "std-words.json")
    rows = json.loads(raw.decode("utf-8"))

    # 课次 ID 分册：<1000 与 10000-19999 属初级，20000+ 属中级，0 为未标注
    def book_of(lesson_id):
        if lesson_id >= 20000:
            return "中级"
        if lesson_id > 0:
            return "初级"
        return "未分册"

    grouped = defaultdict(list)
    for row in rows:
        lesson_id, pos, chinese, kana_field = row[0], row[1], row[2], row[3]
        if not kana_field or not chinese:
            continue

        # 上游的注音字段形如「ちゅうごくじん(中国人)」或「しょく（食）」，
        # 半角与全角括号都可能出现，两种都要能切出假名与汉字。
        match = re.search(r"[(（]", kana_field)
        if match:
            kana = kana_field[:match.start()].strip()
            term = kana_field[match.end():]
            term = re.sub(r"[)）]\s*$", "", term).strip()
            # 上游偶有括号不配对的脏数据（如「ＦＩＦＡ）／こくさい…」），
            # 切出后若仍含括号说明解析不可靠，直接丢弃避免生成错乱的词条。
            if "(" in term or ")" in term or "（" in term or "）" in term:
                continue
        else:
            kana = kana_field.strip()
            term = kana
        if not term:
            continue

        grouped[book_of(lesson_id)].append((lesson_id, term, kana, chinese, pos))

    total = 0
    # 固定册别顺序：初级 → 中级 → 未分册
    book_order = {"初级": 1, "中级": 2, "未分册": 3}
    for index, (book, items) in enumerate(
        sorted(grouped.items(), key=lambda kv: book_order.get(kv[0], 9)), start=1
    ):
        # 同一册内按课次升序，保持上游顺序
        items.sort(key=lambda x: x[0])
        words = []
        seen = set()
        for _, term, kana, chinese, pos in items:
            if term in seen:
                continue
            seen.add(term)
            roman = to_romaji(kana) if (to_romaji and kana) else ""
            label = "标准日本语" if book == "未分册" else f"标准日本语-{book}"
            words.append(make_word(term, kana, chinese, "", label, roman, pos))
        path, count = write_vocabulary(STD_OUTPUT_DIR, f"标准日本语 {book}", words, index)
        log(f"标准日本语 {book}: {count} 词 -> {path}")
        total += count
    log(f"标准日本语合计 {total} 词")


def build_minna_no_nihongo(yori_index, to_romaji):
    """みんなの日本語 初級 I & II，按课次生成。"""
    import yaml  # 可选依赖，仅生成该词表时需要

    raw = fetch(MINNA_YAML_URL, "minna-no-ds.yaml")
    data = yaml.safe_load(raw.decode("utf-8"))

    lessons = [k for k in data if k.startswith("lesson-")]
    total = 0
    for order, lesson in enumerate(sorted(lessons), start=1):
        words = []
        seen = set()
        for entry in data[lesson]:
            kanji = entry.get("kanji")
            kana = entry.get("kana") or ""
            # 纯假名词条没有汉字，此时假名就是词条本身
            term = kanji or kana
            if not term or term in seen:
                continue
            seen.add(term)

            english = ""
            meaning = entry.get("meaning") or {}
            if isinstance(meaning, dict):
                english = meaning.get("en") or ""
            elif isinstance(meaning, str):
                english = meaning

            chinese = pick_chinese_glosses(term, kana, yori_index)
            roman = entry.get("romaji") or (to_romaji(kana) if (to_romaji and kana) else "")
            words.append(
                make_word(term, kana, "\n".join(chinese), english,
                          "みんなの日本語-初級", roman)
            )
        if not words:
            continue
        lesson_no = lesson.split("-")[1]
        path, count = write_vocabulary(
            MINNA_OUTPUT_DIR, f"第{lesson_no}課", words, order
        )
        total += count
    log(f"みんなの日本語 初级合计 {total} 词，输出目录 {MINNA_OUTPUT_DIR}")


def build_anki_jlpt(yori_index, to_romaji):
    """JLPT Anki 精选词表（CSV）。"""
    import csv
    import io

    total = 0
    for order, (level, _, _) in enumerate(LEVELS, start=1):
        raw = fetch(f"{ANKI_BASE}/n{level[1]}.csv", f"anki-n{level[1]}.csv")
        reader = csv.DictReader(io.StringIO(raw.decode("utf-8")))

        words = []
        seen = set()
        for row in reader:
            term = (row.get("expression") or "").strip()
            if not term or term in seen:
                continue
            seen.add(term)

            kana = (row.get("reading") or "").strip()
            english = (row.get("meaning") or "").strip()
            chinese = pick_chinese_glosses(term, kana, yori_index)
            roman = to_romaji(kana) if (to_romaji and kana) else ""
            words.append(
                make_word(term, kana, "\n".join(chinese), english, f"JLPT精选-{level}", roman)
            )
        path, count = write_vocabulary(ANKI_OUTPUT_DIR, f"JLPT精选 {level}", words, order)
        log(f"JLPT精选 {level}: {count} 词 -> {path}")
        total += count
    log(f"JLPT精选合计 {total} 词")


# ---------------------------------------------------------------- 主流程

def load_existing_romaji():
    """读取已生成词库中的罗马字，供缺少 pykakasi 时复用。"""
    cache = {}
    if not os.path.isdir(OUTPUT_DIR):
        return cache
    for name in os.listdir(OUTPUT_DIR):
        if not name.endswith(".json"):
            continue
        try:
            with open(os.path.join(OUTPUT_DIR, name), encoding="utf-8") as fh:
                data = json.load(fh)
            for word in data.get("wordList", []):
                # 以「词条 + 读音」为键：同一个词条可能出现在多个等级且读音不同，
                # 只用词条作键会让后出现的读音覆盖先前的，导致罗马字张冠李戴。
                key = (word["value"], word.get("kana") or "")
                if word.get("romaji"):
                    cache[key] = word["romaji"]
        except (OSError, ValueError):
            continue
    if cache:
        log(f"已从现有词库复用 {len(cache)} 条罗马字")
    return cache


def main():
    openjlpt = load_openjlpt()
    yori_index = load_yori_zh_cn()
    to_romaji = load_romaji_converter()
    romaji_cache = load_existing_romaji()

    # 先生成词典，再生成词库，两者共用同一份 Yori 数据
    build_dictionary_db(yori_index)

    os.makedirs(OUTPUT_DIR, exist_ok=True)
    total_words = 0

    for level, order, display_name in LEVELS:
        _, entries = openjlpt[level]

        words = []
        seen = set()
        for entry in entries:
            word = build_word(entry, level, yori_index, to_romaji, romaji_cache)
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

    # 扩展词表。各自失败只记录并跳过，不影响已生成的词库。
    try:
        build_standard_japanese(yori_index, to_romaji)
    except Exception as exc:  # noqa: BLE001
        log(f"跳过标准日本语词表：{exc}")

    try:
        build_minna_no_nihongo(yori_index, to_romaji)
    except Exception as exc:  # noqa: BLE001
        log(f"跳过みんなの日本語词表：{exc}（需要 pyyaml：pip install pyyaml）")

    try:
        build_anki_jlpt(yori_index, to_romaji)
    except Exception as exc:  # noqa: BLE001
        log(f"跳过 JLPT 精选词表：{exc}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
