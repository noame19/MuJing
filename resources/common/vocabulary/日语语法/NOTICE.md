# 日语语法库数据来源与许可

本目录下的 `1 JLPT语法 N5.json` ~ `5 JLPT语法 N1.json` 是 **JLPT 语法点库**，
结构与词库不同：顶层为 `grammarList`（没有 `wordList`），
每个条目为一条语法点（`patterns` / `meaning` / `formation` / `examples`）。

生成方式（**请勿手工编辑**）：

1. `python3 tools/japanese/build_vocabulary.py --categories grammar`
   —— 拉取上游语法 JSON，产出基础语法库；
   写文件前会调用 `tools/japanese/enrich_grammar.py` 的 `apply_zh()` 注入中文
2. 仅补/更新中文时：`python3 tools/japanese/enrich_grammar.py`
   （`--check` 只统计不写）

> 中文必须由脚本注入：`build_grammar()` 每次重建都会覆盖这 5 个文件，
> 手工改动会在下次重新生成时丢失。

---

## 数据来源

### 1. 语法点 — OpenJLPT

- 项目：<https://github.com/evanclan/OpenJLPT>
- 取用内容：`data/json/grammar/{n5,n4,n3,n2,n1}.json`
  的 `pattern`、`meaning`、`formation`、`examples`、`tags` 字段
- 许可：Creative Commons Attribution-ShareAlike 4.0 International
- 署名要求：保留 OpenJLPT 名称与链接，并标注 CC BY-SA 4.0

### 2. 中文含义与接续 — 本项目自撰

- 文件：`tools/japanese/grammar_zh.json`（CC BY-SA 4.0）
- 说明：按上游英文 `meaning` / `formation` 翻译的 100 条中文（N5–N1 各 20 条），
  以语法点 `id`（如 `N5-1`）为键；写入 JSON 的 `meaningZh` / `formationZh` 字段
- 显示端（`ui/dialog/GrammarDetail.kt`）优先显示中文，缺失时回退英文

---

## 说明

- 界面上的「含义 / 接续 / 例句」来自本目录的 JSON，
  由 `ui/util/JapaneseGrammarMatcher.kt` 加载并做模式匹配；
  仅在当前词库 `language` 为 `japanese` 时启用。
