# 日语词库数据来源与许可（みんなの日本語）

本目录下的 JSON 由以下脚本生成，**请勿手工编辑**：

1. `python3 tools/japanese/build_vocabulary.py --categories minna --no-dict` —— 拉取教材单词表，产出基础词库
2. `python3 tools/japanese/enrich_vocabulary.py --category みんなの日本語 --all --write` —— 以 JMdict 补全词性、分拍罗马字、活用形与中文释义

两步顺序不可颠倒（先用 build 生成基础词库，再用 enrich 增强一次）。

---

## 数据来源

### 1. 教材单词表 — vitto4/MinnaNoDS

- 项目：<https://github.com/vitto4/MinnaNoDS>
- 取用内容：`minna-no-ds.yaml`
- 许可：**上游未声明许可（license: none）**
- 风险提示：该仓库未标注任何许可证，严格来说再分发条款不明。
  本项目按“社区整理的教材单词表”引用，仅用于学习用途；若后续需要
  正式分发，应先与上游确认许可，或改用其他可自由使用的词表。

### 2. 词性、义项、活用形 — JMdict / JMdictDB（EDRDG）

- 项目：<https://www.edrdg.org/jmdict/j_jmdict.html>（JMdict）
- 项目：<https://www.edrdg.org/edrdg/>（JMdictDB，活用规则表）
- 许可：Creative Commons Attribution-ShareAlike 4.0 International
- 使用方式：仅在**构建期**下载并生成词库，不随应用分发原始词典文件

### 3. 中文释义 — 本项目自撰

- 文件：`tools/japanese/jadic_supplement.json`（CC BY-SA 4.0）
- 说明：按教材英文释义翻译，共 273 条（实词、量词、专有名词、问候句）；
  其余实词的中文来自 Yori Dict（CC BY-SA 4.0，见 JLPT日语 目录的 NOTICE）。

### 4. 罗马字读音 — 本项目自建

分拍罗马字由 `tools/japanese/japanese_romaji.py` 生成，不依赖第三方罗马字转换库。

---

## 说明

- 教材中的**句型/用法卡片**（含 `～`、括号、斜杠者）与整句保留教材原文
  （英文讲解/释义），不补中文、不补罗马字：它们是教材语法讲解而非词条，
  `enrich_vocabulary.py` 的 `is_pattern_card()` 对这类条目原样拷贝、不做增强。
- 教材本身（3A 出版的《みんなの日本語》）受版权保护，本目录只包含由社区
  整理的单词表衍生出的词表数据，不含教材正文。
