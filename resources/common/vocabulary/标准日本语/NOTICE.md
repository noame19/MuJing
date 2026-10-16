# 日语词库数据来源与许可（标准日本语）

本目录下的 JSON 由以下脚本生成，**请勿手工编辑**：

1. `python3 tools/japanese/build_vocabulary.py --categories standard --no-dict` —— 拉取教材单词表，产出基础词库
2. `python3 tools/japanese/enrich_vocabulary.py --category 标准日本语 --all --write` —— 以 JMdict 补全词性、分拍罗马字、活用形与中文释义

两步顺序不可颠倒（先用 build 生成基础词库，再用 enrich 增强一次）。

---

## 数据来源

### 1. 教材单词表 — smartsl/biaori

- 项目：<https://github.com/smartsl/biaori>（新标日单词一览表）
- 取用内容：`words.json`
- 许可：MIT License
- 署名要求：保留项目名称与链接及 MIT 许可说明

### 2. 词性、义项、活用形与中文释义 — JMdict / JMdictDB（EDRDG）

- 项目：<https://www.edrdg.org/jmdict/j_jmdict.html>（JMdict）
- 项目：<https://www.edrdg.org/edrdg/>（JMdictDB，活用规则表）
- 许可：Creative Commons Attribution-ShareAlike 4.0 International
- 使用方式：仅在**构建期**下载并生成词库，不随应用分发原始词典文件

### 3. 中文释义（词典未收录的部分）— 本项目自撰

- 文件：`tools/japanese/jadic_supplement.json`（CC BY-SA 4.0）
- 说明：教材英文释义的对应中文，仅用于词典查不到的词条

### 4. 罗马字读音 — 本项目自建

分拍罗马字（かっこう → ka-k-ko-u）由 `tools/japanese/japanese_romaji.py` 生成，
不依赖第三方罗马字转换库。

---

## 说明

- 教材中的**句型/用法卡片**（含 `～`、括号、斜杠者）与查不到 JMdict 条目的
  专有名词、整句保留教材原文（英文讲解/释义），不补中文、不补罗马字 ——
  它们是教材语法讲解而非词条，`enrich_vocabulary.py` 的 `is_pattern_card()`
  对这类条目原样拷贝、不做增强。
- 教材本身（3A 出版的《标准日本语》）受版权保护，本目录只包含由社区整理的
  单词一览表衍生出的词表数据，不含教材正文。
