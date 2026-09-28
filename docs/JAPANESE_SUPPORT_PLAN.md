# 幕境 · 日语支持开发规划

本文件记录日语学习支持的调研结论、数据来源与开发任务拆分。
每个阶段对应一个可回溯的 commit。

---

## 1. 现状：MuJing 的语言耦合点

MuJing 是 Kotlin Multiplatform + Compose Desktop 应用，面向中文用户的英语学习工具。
代码中真正与「英语」绑定的点如下（括号内为文件位置）：

| 耦合点 | 位置 | 影响 |
| --- | --- | --- |
| 内置词库分类硬编码 | `ui/components/BuiltInVocabularyMenu.kt:50-136`、`ui/dialog/BuiltInVocabularyDialog.kt:78-128` | 新增日语分类必须改两处 UI 列表 |
| 词库文件名按数字前缀排序 | `BuiltInVocabularyMenu.kt:187-191`、`:229-235`，`BuiltInVocabularyDialog.kt:217-238` | `nameWithoutExtension.split(" ")[0].toFloat()`，日语文件名会抛异常 |
| 词典后端为 ECDICT（SQLite，英语专用） | `data/Dictionary.kt:72-85`、`89-100` | 查询键为英语单词，日语无音标/释义 |
| `Word` 模型只有英式/美式音标字段 | `data/Vocabulary.kt:72-88` | 无假名、罗马字字段 |
| 单词身份以 `value.lowercase()` 判定 | `data/Vocabulary.kt:90-95`、`ui/search/Search.kt:120` | 大小写折叠对日语无意义，但不会报错 |
| 听写逐字符比对 | `ui/wordscreen/WordScreen.kt:1031-1091` | 依赖 `String` 索引，日文需注意代理对与 IME 上屏时机 |
| 音标渲染固定英/美双栏 | `ui/wordscreen/Word.kt:319-345` | 日语需改为假名/音调展示 |
| TTS 默认 `en-US` / `Ava` | `tts/Azure TTS.kt:54-60`、`:165-182` | 日语需 `ja-JP` 声音 |
| 平台 TTS 无语言参数 | `tts/UbuntuTTS.kt:41`、`tts/MacTTS.kt:43`、`tts/MSTTSpeech.kt:45` | espeak / say / SAPI 未指定语言 |
| 有道查词链接写死 `lang=en` | `player/HoverableCaption.kt:219` | 日语字幕悬停查错语言 |
| VLC 默认 `--sub-language=en` | `player/MediaPlayerComponent.kt:68` | 日语视频需改为 `ja` |
| 生成词库用英语 OpenNLP 模型 | `ui/util/GenerateVocabulary.kt:115-128`、`:271-278`、`:422-425` | tokenizer/POS/chunker 全为 `en-ud-ewt` |

**有利结论**：全库未发现 `[a-zA-Z]` 正则、`isLetter()` 字符集白名单等会直接拒绝假名/汉字的硬性校验。
日语适配主要是「新增分支」而非「推翻重构」。

---

## 2. 数据来源调研

### 2.1 词表分级来源

| 来源 | 内容 | 许可 | 可否随应用分发 |
| --- | --- | --- | --- |
| **OpenJLPT** | JLPT N5–N1 词汇 8,334 条 + 汉字 2,211 字 + 例句（Tatoeba） | CC BY-SA 4.0 | ✅ 可以 |
| Jonathan Waller 词表 | JLPT 分级的社区标准原始清单 | CC BY | ✅ 可以（需署名） |
| 新东方 红宝书 / 蓝宝书 | 中文教材 N1–N5 分级 | 版权受限 | ❌ 不可 |
| 标准日本语 / みんなの日本語 | 教材分级词汇 | 版权受限 | ❌ 不可 |
| BJT 商务日语能力测试 | 官方只公布分数段，不公布词表 | 版权受限 | ❌ 不可 |
| JASSO EJU | 官方大纲不含词表 | 版权受限 | ❌ 不可 |

> **关键事实**：JLPT 自 2010 年改版后**不再公布官方词表**，官方 FAQ 明确说明 Test Content Specifications 已废止。
> 因此市面上所有「JLPT N5 词表」都是第三方基于 Waller 列表或教材归纳的。
> OpenJLPT 正是把 Waller 列表（CC BY）规范化的开源实现，是目前最干净的选择。

已实测下载验证：`https://github.com/evanclan/OpenJLPT` 仓库 `data/json/vocab/n5.json` 结构为

```json
{ "word": "食べる", "reading": "たべる", "meanings": ["to eat"],
  "level": "N5", "examples": [{"ja": "魚を食べる。", "en": "I eat fish."}] }
```

### 2.2 日语词典数据

| 来源 | 内容 | 许可 | 结论 |
| --- | --- | --- | --- |
| **Yori Dict `yori-ja-zh-cn.zip`** | 58,438 条日→简中词条，含假名读音 | CC BY-SA 4.0 | ✅ **中文释义主数据源** |
| JMdict (EDRDG) | 日英词典，含形用/读音 | CC BY-SA 4.0 | ✅ 仅日/英部分可用 |
| KANJIDIC2 (EDRDG) | 汉字读音/笔画/义项 | CC BY-SA 4.0 | ✅ 可用 |
| Tatoeba | 例句 | CC BY 2.0 FR | ✅ 可用 |
| NHK 日本語発音アクセント辞書 | 音调 | 版权受限 | ❌ 不可 |

已实测 `yori-ja-zh-cn.zip`（4.9 MB，Yomichan 格式，6 个 term bank）内容：

```json
// Yomichan 词条格式：[词条, 读音假名, 定义标签, 规则, 分数, 释义列表, 序号, 词条标签]
["食べる", "たべる", "", "", 0, ["吃", "食用"], 32978, ""]
["学校", "がっこう", "", "", 0, ["学校"], 18489, ""]
```

> **注意**：Yori Dict 明确记录了 EDRDG 的授权边界——EDRDG 只授权 JMdict 的**日文与英文**部分，
> 德/荷/俄/法等译文由各自编译器持有独立版权，不得分发。我们只取日→中释义，来源是该项目自建的
> `zh-cn` 内容，合规。

### 2.3 字段映射设计

MuJing 现有 `Word` 结构对应关系：

| MuJing 字段 | 日语取值 | 说明 |
| --- | --- | --- |
| `value` | 汉字/假名原形 | 主键，保持不变 |
| `usphone` / `ukphone` | 不使用 | 日语无对应音标，保持空字符串 |
| `definition` | 英文释义（OpenJLPT `meanings`） | 对应英语词库的英文释义栏 |
| `translation` | 中文释义（Yori `zh-cn`） | 对应英语词库的中文释义栏 |
| `kana`（新增） | 假名读音 | Yori 第 2 字段 / OpenJLPT `reading` |
| `romaji`（新增） | 罗马字 | 日语学习必需 |
| `level`（新增） | N5–N1 | 分级标记 |
| `pos` | 词性 | OpenJLPT 无词性，暂留空 |
| `tag` | `jlpt-n5` 等 | 复用现有 tag 字段，避免新增结构 |

新增字段全部带默认值，`kotlinx.serialization` 兼容旧英语词库文件，无需迁移。

---

## 3. 开发任务拆分

每个任务一个 commit，独立可回滚。

| # | 任务 | 涉及文件 |
| --- | --- | --- |
| 1 | 扩展 `Word` 模型：新增 `kana`/`romaji`/`level`，并让 JSON 兼容旧词库 | `data/Vocabulary.kt` |
| 2 | 编写日语词库生成脚本（Python），拉取 OpenJLPT + Yori Dict，产出 MuJing JSON | `tools/japanese/build_vocabulary.py` |
| 3 | 生成 JLPT N5–N1 五份词库 JSON 到 `resources/common/vocabulary/JLPT日语/` | 产物 |
| 4 | 添加 `NOTICE.md`，声明各数据源许可与署名 | `resources/common/vocabulary/JLPT日语/NOTICE.md` |
| 5 | 重构内置词库目录为动态扫描，消除两处硬编码列表 | `ui/components/BuiltInVocabularyMenu.kt`、`ui/dialog/BuiltInVocabularyDialog.kt` |
| 6 | 修正词库排序：改为通用自然排序，去掉 `toFloat()` | 同上 + `ui/dialog/GenerateVocabularyDialog.kt:2029-2035` |
| 7 | 新增 `JapaneseDictionary`：基于 Yori/SQLite 的日语查词 | `data/JapaneseDictionary.kt` |
| 8 | 搜索界面按 `vocabulary.language` 分发到英语/日语词典 | `ui/search/Search.kt:120-141` |
| 9 | 听写比对支持日语（IME 上屏、假名/罗马字提示） | `ui/wordscreen/WordScreen.kt:1031-1091` |
| 10 | 假名/音调显示替代英美音标 | `ui/wordscreen/Word.kt:319-345` |
| 11 | Azure TTS 支持 `ja-JP` 声音选择 | `tts/Azure TTS.kt`、`ui/dialog/SpeechDialog.kt:177-247` |
| 12 | 平台 TTS 传语言参数（espeak `-v ja`、`say -v Kyoko`） | `tts/UbuntuTTS.kt`、`tts/MacTTS.kt` |
| 13 | 有道查词与 VLC 字幕语言跟随词库语言 | `player/HoverableCaption.kt:219`、`player/MediaPlayerComponent.kt:68` |

---

## 4. 构建与验证

遵循既有约定：**所有编译验证在 GitHub Actions 进行，不在本机构建**。
日语分支沿用 `.github/workflows/Build-AppImage.yml`，打 tag 触发 AppImage 产出。

## 5. 后续（本次不做）

- 韩语适配：上述第 5 步的「动态扫描分类目录」正是为多语言铺路，
  届时只需新增 `resources/common/vocabulary/TOPIK한국어/` 与一个 `KoreanDictionary`，UI 零改动。
- 视频词库生成接入日语文本分词（SudachiPy / MeCab），当前 OpenNLP 英语模型对日语无效，
  但不影响词库学习功能。
- 音调（アクセント）展示：NHK 词典不可分发，暂不实现。
