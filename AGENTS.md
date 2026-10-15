# AGENTS.md — MuJing 项目结构与开发约定

本文件说明仓库的目录架构、各类文件应放的位置，以及改动时必须遵守的约定。
新增语言支持（日语、韩语…）时请先读本文。

---

## 1. 项目定位

MuJing（幕境）是面向中文用户的**语言学习工具**，核心玩法是「结合影视字幕背单词」：
导入视频与字幕 → 字幕里点词查义 → 存入词库 → 用 FSRS 间隔重复复习。

- 技术栈：Kotlin Multiplatform + Compose Desktop（JVM），打包为 AppImage / MSI / DMG
- 首个语言：英语（含 ECDICT 词典、OpenNLP 分词、英美音标）
- 已增加：日语（JLPT 词库、假名/罗马字、日语词典）
- 规划中：韩语（见 §6）

**语言不是硬编码分支，而是词库的一个属性。** 每个词库文件带 `language` 字段
（`english` / `japanese` / …），UI 与逻辑按该字段分发。新增语言原则上只加数据与
少量分支，不改动既有英语路径。

---

## 2. 目录架构

```text
repo/
├── src/main/kotlin/com/mujingx/     Kotlin 源码（按职责分包）
│   ├── data/                        数据模型与词典后端
│   │   ├── Vocabulary.kt            Word / Vocabulary / VocabularyType
│   │   ├── Dictionary.kt            英语词典（ECDICT）
│   │   └── JapaneseDictionary.kt    日语词典（jadic.db）
│   ├── state/                       AppState：资源定位、设置持久化
│   ├── ui/                          Compose 界面
│   │   ├── wordscreen/              背单词主界面（含 Morphology 变形展示）
│   │   ├── subtitlescreen/          字幕界面
│   │   ├── components/              可复用组件（含内置词库扫描）
│   │   ├── dialog/  edit/  search/  对话框 / 词库编辑 / 查词
│   │   └── util/                    词库生成等工具
│   ├── player/                      视频播放、字幕、弹幕
│   ├── tts/                         语音合成（Azure / 各平台本地 TTS）
│   ├── theme/                       配色（含 KDE/GNOME 深色判断）
│   └── fsrs/                        间隔重复算法
│
├── resources/                       随应用分发的资源（非编译产物）
│   ├── common/                      全平台共用
│   │   ├── vocabulary/              ★ 内置词库，按语言/教材分类
│   │   ├── dictionary/              词典数据库
│   │   ├── audio/  icon/  logo/     音频缓存、图标
│   │   └── LICENSE
│   ├── linux/  windows/            各平台附带二进制（ffmpeg、VLC、jacob）
│   └── macos-arm64/  macos-x64/
│
├── tools/                           构建期脚本（不参与应用运行）
│   └── japanese/                    日语词库相关
│       ├── build_vocabulary.py      生成 JLPT / 教材词库
│       └── enrich_vocabulary.py     用 JMdict 补全词性、罗马字、活用
│
├── dict/                            英语词典原始包（ecdict.7z）
├── lib/                             第三方 jar（VLC、字幕转换等）
├── docs/                            开发规划与调研文档
├── .github/workflows/               CI（构建与测试全部在云端进行）
├── src/main/resources/              编译进 jar 的资源（字体、logo、OpenNLP 模型）
└── build.gradle.kts  wix.gradle.kts  gradle/
```

### 放文件的判断依据

| 要放的东西 | 位置 | 理由 |
| --- | --- | --- |
| 运行时读的词典/词库/音频 | `resources/common/` | 打包进安装包，运行时按路径读取 |
| 平台专用二进制 | `resources/<平台>/` | 按平台分目录，打包时择一 |
| 编译进 jar 的静态资源 | `src/main/resources/` | 随 classpath 走，如字体、模型 |
| 生成/转换脚本 | `tools/<语言>/` | 构建期工具，不进安装包 |
| 开发文档、调研结论 | `docs/` | 不参与打包 |
| 需要版本管理的第三方库 | `lib/` | 无中央仓库来源的 jar |

**不要**把生成脚本产出的数据放进 `tools/`，产物属于 `resources/`。
**不要**把大体积数据源（原始词典包）放进 `resources/`，它会进安装包。

---

## 3. 内置词库

### 3.1 目录与命名

```text
resources/common/vocabulary/
├── JLPT日语/            1 N5.json … 5 N1.json
├── 标准日本语/ みんなの日本語/ 日语语法/     日语相关
├── JLPT精选/
├── 大学英语/ 牛津核心词/ 高中英语/ …          英语相关
└── <分类>/NOTICE.md     该分类的数据来源与许可
```

- 每个子目录 = 一个分类，**运行时扫描得到**，不在 UI 里硬编码
- 文件名形如 `<数字> <名称>.json`，数字前缀用于排序（避免 `10` 排在 `2` 前）
- 每个分类**必须**有 `NOTICE.md`，声明数据来源、许可与署名要求

### 3.2 词库 JSON 结构

顶层是**对象**（不是数组），字段与类型受 `Vocabulary` 数据类约束：

```json
{
  "name": "日语能力测试 N4",
  "type": "DOCUMENT",
  "language": "japanese",
  "size": 632,
  "relateVideoPath": "",
  "subtitlesTrackId": 0,
  "wordList": [ { … } ]
}
```

- `type` 是 `VocabularyType` 枚举，词库固定为 `"DOCUMENT"`，**不能写语言名**
- `language` 才是语种：`english` / `japanese`
- `subtitlesTrackId` 是**整数**，不是字符串
- `size` 必须等于 `wordList` 长度

词条字段（17 个，顺序与类型需与既有词库一致）：

| 字段 | 说明 |
| --- | --- |
| `value` | 词形，词库内主键 |
| `usphone` / `ukphone` | 英语音标；日语留空 |
| `definition` | 英文释义，多义项用 `\n` 分隔，可带词性前缀 |
| `translation` | 中文释义，多义项用 `\n` 分隔 |
| `pos` | 词性，中文全称（`名词`/`动词·一段词`/`い形容词`…） |
| `exchange` | 变形/活用，格式见 §3.3 |
| `kana` | 日语假名读音；英语留空 |
| `romaji` | 罗马字，按拍用 `-` 分隔 |
| `level` | 分级标记（`N5`…`N1`） |
| `tag` | 与 `level` 对应的短标记（`jlpt-n5`） |
| `collins` `oxford` `bnc` `frq` | 英语词频相关，日语填 `0`/`false` |
| `externalCaptions` `captions` | 字幕关联，词库填 `[]` |

### 3.3 `exchange` 字段格式

`exchange` 是**自描述**的 `标签:值` 列表，条目之间用 `/` 分隔：

```text
英语（既有）：d:cancelled/p:cancelled/i:cancelling/3:cancels
日语（新增）：辞書形:食べる/ます形:食べます/ない形:食べない/て形:食べて/過去形:食べた
```

- 值里**不能**出现 `/` 或 `:`，否则会被界面误当分隔符
- 英语沿用单字母码（`p` 过去式 / `d` 过去分词 / `i` 现在分词 / `3` 三单 /
  `r` 比较级 / `t` 最高级 / `s` 复数 / `0` 原形）
- 日语用中文活用名（`辞書形` `ます形` `ない形` `て形` `過去形` `ば形`
  `意志形` `命令形` `可能形` `受身形` `使役形` `連体形` `連用形`）
- 名词、副词等无变形的词性，`exchange` 留空字符串

### 3.4 罗马字分拍规则

按**拍（モーラ）**切分，用 `-` 连接，一段即一拍：

| 规则 | 例 |
| --- | --- |
| 拗音算 1 拍，合并 | `きゃく` → `kya-ku` |
| 促音单独占 1 拍，写作后一拍的辅音 | `かっこう` → `ka-k-ko-u` |
| `っ` 后接 `ち` 时写作 `t`（Hepburn） | `マッチ` → `ma-t-chi` |
| 词尾促音用 `'` | `あっ` → `a-'` |
| 长音单独占 1 拍，写作前一拍的元音 | `アワー` → `a-wa-a` |
| 拨音单独占 1 拍 | `にほんご` → `ni-ho-n-go` |

---

## 4. 词库生成管线

内置词库**不手工编辑**，一律由脚本生成。产物头部与同目录 `NOTICE.md` 会注明来源。

```text
tools/japanese/build_vocabulary.py     拉取 OpenJLPT + Yori Dict，产出基础词库
        ↓  resources/common/vocabulary/JLPT日语/*.json
tools/japanese/enrich_vocabulary.py    以 JMdict + JMdictDB 补全词性/罗马字/活用
        ↓  覆盖同目录 JSON
```

### 运行

```bash
# 数据源体积大（JMdict 约 111MB），不入库，按需下载到 tools/japanese/data/
python3 tools/japanese/fetch_dict_data.py

python3 tools/japanese/build_vocabulary.py     # 基础词库
python3 tools/japanese/enrich_vocabulary.py    # 补全词性/罗马字/活用
```

脚本依赖放在 `tools/japanese/.venv/`，与仓库其他部分隔离。
`tools/japanese/data/`、`.venv/`、`__pycache__/` 已在 `.gitignore` 中排除。

### 数据源与许可

| 数据源 | 用途 | 许可 |
| --- | --- | --- |
| [OpenJLPT](https://github.com/evanclan/OpenJLPT) | 分级、英文释义 | CC BY-SA 4.0 |
| [Yori Dict](https://github.com/YoriJP/yori-dict) | 中文释义 | CC BY-SA 4.0 |
| [JMdict](https://www.edrdg.org/jmdict/) | 词性、义项 | CC BY-SA 4.0（仅日/英部分） |
| [JMdictDB](https://www.edrdg.org/) `conjo.csv` | 动词/形容词活用表 | CC BY-SA 4.0 |

新增数据源时必须同步更新 `NOTICE.md` 与本节表格。**许可不明或禁止再分发的资料
一律不得引入**（详见 `docs/JAPANESE_SUPPORT_PLAN.md` §2 的排除清单）。

---

## 5. 词典数据

| 语言 | 文件 | 后端 |
| --- | --- | --- |
| 英语 | `dict/ecdict.7z` → 解包为 SQLite | `data/Dictionary.kt` |
| 日语 | `resources/common/dictionary/jadic.db` | `data/JapaneseDictionary.kt` |

查词按 `vocabulary.language` 分发（见 `ui/search/Search.kt`）。
新增语言时，加一个 `<语言>Dictionary` 并在分发处加分支。

`jadic.db` 表结构：

```sql
CREATE TABLE jmdict (word TEXT NOT NULL, reading TEXT DEFAULT '', translation TEXT DEFAULT '',
                     PRIMARY KEY(word, reading));
```

存储约定：**汉字词带 `reading`；假名词的 `reading` 为空字符串**。
查询务必带 `reading` 条件，否则同音异形词会串味
（如 `かっこう` 同时是「格好」「滑降」「郭公」）。

---

## 6. 新增一门语言

以韩语为例，需要的改动：

1. `resources/common/vocabulary/TOPIK한국어/` + `NOTICE.md`，词库 JSON 带
   `"language": "korean"`
2. `tools/korean/` 放生成脚本
3. `data/KoreanDictionary.kt` + `resources/common/dictionary/korean.db`
4. `ui/search/Search.kt` 的词典分发处加分支
5. `ui/wordscreen/` 的音标/读音展示区按 `language` 加分支
6. `tts/` 增加该语言的语音参数
7. `AGENTS.md` 与 `docs/` 同步更新

词库分类扫描（`ui/components/BuiltInVocabulary.kt`）已是动态的，无需改动。

---

## 7. 构建与验证

- **所有编译验证在 GitHub Actions 进行**，不在本机装 Gradle/JDK 构建链
- 触发方式：`gh workflow run Build-AppImage.yml --repo noame19/MuJing --ref master`
- 产物：AppImage / MSI / DMG，从对应 run 的 artifact 下载
- 提交前不必本地编译；推送后看 CI 结果

```bash
gh run list --repo noame19/MuJing --limit 1
gh run view <run-id> --repo noame19/MuJing --log-failed
gh run download <run-id> --repo noame19/MuJing --name MuJing-linux-appimage-x86_64 --dir <目录>
```

---

## 8. 编码约定

- 注释与文档用中文，与既有代码一致
- 缩进 4 空格（Kotlin 与 Python 均如此）
- 日语相关分支一律以 `vocabulary.language` 或 `Word` 字段为判断依据，
  **不要**用字符范围正则判断语言
- 改动英语既有路径时须回归验证：日语分支不得影响英语词库的显示与发音
- `resources/` 下的词库 JSON 由脚本生成，**不手工编辑**
