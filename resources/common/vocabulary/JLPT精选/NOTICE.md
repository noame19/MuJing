# 日语词库数据来源与许可（JLPT精选）

本目录下的 `1 JLPT精选 N5.json` ~ `5 JLPT精选 N1.json` 由以下脚本生成，
**请勿手工编辑**：

1. `python3 tools/japanese/build_vocabulary.py --categories anki --no-dict` —— 拉取词表，产出基础词库
2. `python3 tools/japanese/enrich_vocabulary.py --category JLPT精选 --all --write` —— 以 JMdict 补全词性、分拍罗马字、活用形与中文释义

两步顺序不可颠倒（先用 build 生成基础词库，再用 enrich 增强一次）。

---

## 数据来源

### 1. JLPT 精选词表 — jamsinclair/open-anki-jlpt-decks

- 项目：<https://github.com/jamsinclair/open-anki-jlpt-decks>
- 取用内容：`src/n5.csv` ~ `src/n1.csv`
- 许可：MIT License
- 说明：该词表源自社区整理的 JLPT 备考 Anki 牌组，等级归属为第三方整理，
  **非日本国际教育支援协会官方发布**（官方自 2010 年改版起不再公布词表）。

### 2. 词性、义项、活用形 — JMdict / JMdictDB（EDRDG）

- 项目：<https://www.edrdg.org/jmdict/j_jmdict.html>（JMdict）
- 项目：<https://www.edrdg.org/edrdg/>（JMdictDB，活用规则表）
- 许可：Creative Commons Attribution-ShareAlike 4.0 International
- 使用方式：仅在**构建期**下载并生成词库，不随应用分发原始词典文件

### 3. 中文释义 — Yori Dict + 本项目自撰

- Yori Dict（日中词典）：<https://github.com/YoriJP/yori-dict>，CC BY-SA 4.0
- 词典未收录的 36 条（实词、惯用表达）取自本项目自撰的
  `tools/japanese/jadic_supplement.json`（CC BY-SA 4.0）

### 4. 罗马字读音 — 本项目自建

分拍罗马字（かっこう → ka-k-ko-u）由 `tools/japanese/japanese_romaji.py` 生成，
不依赖第三方罗马字转换库。

---

## 说明

- 含 `～`、括号、斜杠的**句型/用法卡片**（329 条）与查不到 JMdict 条目的
  专有名词、整句保留教材原文（英文释义），不补中文、不补罗马字 ——
  `enrich_vocabulary.py` 的 `is_pattern_card()` 对这类条目原样拷贝、不做增强。
