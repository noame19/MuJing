# TODO — 日语支持待办

本文件记录日语支持的剩余工作。已完成部分见 git 历史、
`docs/JAPANESE_SUPPORT_PLAN.md` 与 `AGENTS.md`。

分支约定（`AGENTS.md` §9）：功能开发提交到 `japanese-support-dev`，
编译验证用 `gh workflow run Build-AppImage.yml --ref japanese-support-dev`，
**用户实测确认无误后再合入 `master`**。

## 已完成（近期）

- 词库罗马字按拍切分：拗音合并、促音与长音各占 1 拍
  （原 108/616 词错误，含长音/促音丢字）
- 词性补全为中文全称，主要词性排最前
- 动词/形容词活用补全（数据取自 JMdictDB 官方 conjo.csv）
- 同音异形词隔离（かっこう 不再混入「滑降」「郭公」的释义）
- UI 词型栏支持日语活用渲染（原先静默不显示）
- 日语发音打通字幕/弹幕/视频三条链，修复有道查词的 URL 编码
- 新增 `AGENTS.md`（目录架构与多语言约定）、更新 `NOTICE.md` 署名
- 修复假名/罗马字脏数据：源词库把编者注（`（感）` `（カーペット）`）、
  多读音并列（`じゅう とお`）、乱码（`賛成` 的 `Uӣ[い`）写进 kana 字段，
  现已在上游 `clean_reading()` 清洗，并以 JMdict 条目校正读音
  （83 条 kana、82 条 romaji 得到修复）
- `enrich_vocabulary.py` 不再静默丢词：查不到 JMdict 条目的词条按源数据保留
  （找回 10 条：`ゆっくりと` `より、ほう` `よると` `おまちください` `ぺん`
  `やたらに` `一段と` `しいんと` `矢鱈に` `煌々と`；词库 8324 → 8334 条）
- 中文释义缺失 315 → 69 条（并列写法与假名形现在也能查到 jadic；
  JMdict 有汉字形时按汉字形兜底，同音异形词用 JMdict 的 common 标志消歧）
- 修复 する 动词可能形模板写死为 `できます`（应为 `<词干>できる`，1578 条）
- 同音异形词消歧改为整词匹配 + 词形按可靠度排序，修正 `はい`/`パス` 等
  被挑成 `牌`/`path` 的错误，同时修好 `そう`/`しいんと`/`じゅうたん`
- 增强管线从「只做 JLPT日语」参数化为按分类增强
  （`--category`），`标准日本语/`（9021 词）、`みんなの日本語/`（50 课 2692 词）、
  `JLPT精选/`（8062 词）三类已接入：保留原有词性/释义/中文，只补缺失项；
  句型/量词卡片（`～円` `お～` `何～／～歳`）原样跳过。
  `日语语法/` 是 `grammarList` schema（无 `wordList`），语法卡片不是词条，
  不参与增强
- 教材词库动词的词形还原：这些分类把动词收成 `ます形/て形/た形`
  （行きます、歩いて），JMdict 只有辞書形，还原后才匹配得上
  （标准日本语未匹配条目 591 → 245，找回的都是动词/形容词；
  `行きます` → 辞書形 `行く`，活用按辞書形生成）
- 分拍罗马字统一为一份实现：新增 `tools/japanese/japanese_romaji.py`，
  `build_vocabulary.py` 与 `enrich_vocabulary.py` 共用（旧版 build 用
  pykakasi 产出 `kakkou`、被 enrich 覆盖为 `ka-k-ko-u`；pykakasi 依赖已移除）
- `build_vocabulary.py` 参数化：`--categories`（只生成指定分类）、
  `--no-dict`（不重写 jadic.db）、`--enrich`（生成后立即增强，
  顺序由脚本保证，不再依赖手工两步操作）
- な形容词活用由 3 形补到 8 形：`辞書形:だ` `な` `に` 之外增加
  `ない形:ではない` `て形:で` `過去形:だった` `ば形:なら` `意志形:だろう`
  （全部 100% 规则的语法派生；1592 条记录的 exchange 得到补全）
- 视频词库的日语分词已实现（核实，无需再做）：`lucene-analysis-kuromoji`
  依赖 + `player/JapaneseWordSegmenter.kt`（Kuromoji 分词、词形还原）
  + `GenerateVocabulary.kt` 的 `extractJapaneseWords()`
- JLPT日语 中文释义补齐：新增人工校订表
  `tools/japanese/jadic_supplement.json`（70 条，本项目自撰，CC BY-SA 4.0），
  作为 `chinese_for` 的末级兜底 —— 只补 jadic（Yori Dict）根本没收录的词
  （`行う` `お祭り` `もうすぐ` `伯母さん` `おまちください` `ぺん` 等），
  绝不用同音异形词猜。JLPT日语 缺中文 69 → 0 条，
  教材三类顺带补上 47 条；全量 diff 只有 `translation` 一个字段变化
- 校订表支持覆盖条目（`"force": true`）：jadic 本身解释错的词优先用校订值。
  首个用例是 `しいんと` —— jadic 的 `しいん`（Yori Dict）误收为
  「场面/场景/景象」，与 JMdict 的 `silently` 不符，已改为
  「鸦雀无声/寂静无声/静悄悄」；覆盖需在条目 `note` 里写明依据
- **修复「AppImage 里的词库一直是基础版」**（2026-10-16 定位）：`jadic.db`
  被 `.gitignore` 排除，云端每次构建都缺失，`build.gradle.kts` 的
  `buildJapaneseDictionary` 任务因此每次执行，而它原先不带参数调用
  `build_vocabulary.py` —— 把**已提交的增强词库覆盖成基础词库**
  （`pos`/`exchange` 为空、`definition` 是 OpenJLPT 原文、中文未按义项切分），
  所以打出来的 AppImage 里看不到词性与活用。现改为固定传 `--dict-only`
  （只重建 jadic.db，不碰任何词库 JSON），并在 `AGENTS.md` §4 写明构建期约束

## 待办

### P0 — 日语「影视字幕背单词」链路（当前基本不可用，最高优先）

用户实测：拖入日语字幕、左侧选了 `JLPT日语 N1–N5`、点「生成词库」，
结果**「没有发现词语」**。测试文件：
`/home/manaka-fedora/下载/[SweetSub] Seihantai na Kimi to Boku - 24 [WebRip 1080p HEVC-10bit AAC CHS&CHT].jp.ass`
（UTF-8、Aegisub v4.00+、620 条 Dialogue **全部是日文**、内联 `{\an3\pos(...)\fs90}`
标签很碎，没有中文行）。

**本轮实现（`japanese-support-dev`，2026-10-16，待用户实测）**

- `.ass`：`ui/util/GenerateVocabulary.kt:760` 新增 `parseJapaneseASS`
  （ffmpeg `convertToSrt` → `parseJapaneseSRT`），
  `ui/dialog/GenerateVocabularyDialog.kt` 的 SUBTITLES 分支按扩展名分派。
  这正是用户「没有发现词语」的直接原因：此前日语分支对 `.ass` 也走
  `FormatSRT`，解析结果为空。
- 点词/悬停查词：`player/HoverableCaption.kt:633` 新增
  `renderJapaneseCaptionLine`（Kuromoji 分词、**表层写法显示、基本形查词**），
  `HoverableText` 新增 `isJapanese`/`lookupTerm`，查词改走
  `JapaneseDictionary.query`；`player/HoverableCaption.kt:401`、`:734`
  的语言取自词库 `language` 字段（不用字符范围判断）。
- 读音展示：`player/danmaku/WordDetail.kt:202` 日语改显示 `kana`/`romaji`。
- 文档：「用文档生成词库」新增 `ui/util/GenerateVocabulary.kt:588`
  `parseJapaneseDocument`（日文标点断句 + Kuromoji 基本形 + jadic 查询，
  所在句子写入 `pos`），`GenerateVocabularyDialog.kt:613` 的 `isJapanese`
  在 DOCUMENT 分支真正生效。
- 匹配词库：`ui/util/GenerateVocabulary.kt:1268` `generateMatchedVocabulary`
  按**当前词库语言**分派（日语走 `parseJapaneseASS`/`parseJapaneseSRT`），
  生成的词库 `language` 不再写死空串。
- 包含词库：`ui/dialog/GenerateVocabularyDialog.kt:2645` `includeWords`
  新增 `isJapanese`；日语关闭 BNC/COCA 词频分支（日语无词频，恒为 0，
  否则整张词表都会被当成「词频为 0 的词」而全部包含）。
- 端到端用例：`src/test/kotlin/com/mujingx/util/TestJapaneseVocabulary.kt`
  + `src/test/resources/japanese.{srt,ass}`（分词表层/基本形、SRT、ASS 回归；
  依赖 `jadic.db`/ffmpeg 的用例缺失时自动跳过）。

- [x] **日语 `.ass` 字幕根本不被支持**（用户 0 词的直接原因）
      `ui/dialog/GenerateVocabularyDialog.kt:517-527` 文件选择器允许
      `listOf("srt","ass")`，但 `:634-639` 的 SUBTITLES 分支对日语
      **无条件**调用 `parseJapaneseSRT`（注释写「ASS 会先转换为 SRT」，实际没有），
      而 `ui/util/GenerateVocabulary.kt:556` 的 `parseJapaneseSRT` 用
      `FormatSRT().parseFile(...)`（`:576`）按 SRT 解析 → `.ass` 解析失败。
      英语路径有 `parseASS`（`:664`，走 ffmpeg 转 SRT）。
      修法：日语分支按扩展名分派，`.ass` 复用已有的 ffmpeg 转换后按 SRT 解析
      （或另写 ASS 解析器）。
- [x] **日语字幕在播放器/字幕浏览器里无法点词、悬停查词**：
      `player/HoverableCaption.kt:512` 的 `renderCaptionLine` 用
      `line.split(Regex("\\s+"))` 按空格切词，`:516`/`:520` 又只用
      `a..z`/`A..Z`/数字判定词边界 —— 日语没有空格且不是 ASCII，
      整行被当作 `leadingPunctuation`、`wordPart` 为空，
      于是永远不生成 `HoverableText`（字幕浏览器同 `:578`）。
      修法：日语走 `JapaneseWordSegmenter.tokenize()` 得到词与起止下标。
- [x] **悬停查词恒查英语词典**：`player/HoverableCaption.kt:149`
      `val dictWord = Dictionary.query(text.lowercase().trim())` 硬编码英语
      ECDICT，未按词库 `language` 分派 `JapaneseDictionary` →
      日语词只会显示「内置词典里面没有 …」。
      （`player/danmaku/WordDetail.kt:202` 还硬编码 `英 … 美 …` 音标，
      且完全没展示 kana/romaji。）
- [x] **「用文档生成词库」忽略目标语言**：
      `ui/dialog/GenerateVocabularyDialog.kt:613` 算了 `isJapanese`，
      但 DOCUMENT 分支 `:615-628` 完全不用它，恒调 `parseDocument`
      （`ui/util/GenerateVocabulary.kt:81` 英语 OpenNLP `:117-130`，
      再用 ECDICT 过滤 `:179`）→ 选「日语」仍走英语链路，日文词全被丢弃。
- [x] **播放器「匹配词库」恒按英语分词**：
      `ui/util/GenerateVocabulary.kt:1147` `generateMatchedVocabulary`
      恒调 `parseSRT(..., enablePhrases=true)` 且 `language = ""`（`:1165`），
      不看播放器当前词库的语言。
- [x] 「包含词库」未做语言防护：`GenerateVocabularyDialog.kt:992-1003`
      调用 `includeWords(...)` 没传 `isJapanese`；`:2662-2673` 的
      BNC/COCA 条件缺少 `applyFrequencyFilters` 守卫（对比 `filterWords:2482`）。
- [x] 目标语言下拉对「用文档生成词库」等类型仍显示（`:899`）但无效：
      要么让它真正生效，要么按类型隐藏。
- [ ] 补一套日语字幕的端到端用例（`.srt`/`.ass` 已加，见下；`.mkv` 待真实样本）：`.srt` / `.ass` / `.mkv` 三种源
      都要能出词，并覆盖「播放器点词 → 查词 → 加入词库」这条链。

### P1 — 内置词库：学习数据与进度分离（路线 B，用户已选定）

现状（读代码确认，改动前）：

- 内置词库是**只读模板**，必须「另存为」才能用：
  `ui/dialog/BuiltInVocabularyDialog.kt:64` 标题即「选择内置词库到本地文件系统」，
  `:148-169` 另存后 `writeBytes` → `openChooseVocabulary`。
- 内置列表扫描**全部**分类、不按语言过滤：`ui/components/BuiltInVocabulary.kt:59`
  `getBuiltInVocabularyDirectories()`。
- 学习进度写回**词库文件本身**并另存一份全局状态：
  `ui/wordscreen/WordScreenState.kt:355-364` `saveCurrentVocabulary()`
  （`vocabularyPath.writeText(...)` 整份覆盖）、`:388-410` `saveWordScreenState()`
  → `WordScreenData`（`:44-64`）只有 `index` / `hardVocabularyIndex` /
  `familiarVocabularyIndex` / `vocabularyName` / `vocabularyPath` 等，
  存放在 `~/.MuJing/TypingWordSettings.json`（`:466-469`，**全机单文件**）。
- `Word` / `Vocabulary` 数据模型里**没有任何学习状态字段**：
  `data/Vocabulary.kt:34-68`。
- 「加入熟悉词库」会把词**从当前词库文件里删掉**：
  `ui/wordscreen/WordScreen.kt:519-555`（`getFamiliarVocabularyFile()` +
  `saveVocabulary(...)`），MKV/字幕来源还会顺带写 `externalCaptions`。

待办：

- [x] 支持直接从内置词库开始学习（不必先另存为）——只读词库 + 外部进度
- [x] 进度存储与词库数据分离：新建进度存储（按词库标识 + `value` 索引
      记录标记/复习状态），词库文件保持只读；迁移旧的
      `~/.MuJing/TypingWordSettings.json`
- [x] 「熟悉词库」改为进度标记，不再从词库文件里删词
      （现在删词会让只读内置词库不可用）
- [x] 修 `WordScreenState.kt:155`：`familiarVocabularyIndex` 初始化误用了
      `hardVocabularyIndex`
- [ ] 进度要不要接 `fsrs/`（现在 FSRS 只服务 apkg 复习，与词屏无关）——待定

**本轮实现（`japanese-support-dev`，2026-10-16，待用户实测）**

- 新增 `data/WordProgress.kt`：`WordMark`（熟悉/已从当前词库移除/困难/`seen`/`wrong`）、
  `VocabularyProgress`（`index` + `marks` + `overrides` 覆盖层 + `order` 打乱顺序）、
  `WordProgressStore`（进度文件放 `~/.MuJing/progress/<SHA-1(词库标识)>.json`；
  内置词库标识 = `builtin:JLPT日语/2 N4.json`，用户自己的词库 = 规范化绝对路径；
  第一次接触某个词库时用旧设置文件里的下标迁移一次）。
- `ui/wordscreen/WordScreenState.kt`：新增 `isReadOnlyVocabulary`（内置词库=只读）、
  `loadProgress()`（构造与切换词库时载入：内置词库按 `marks` 过滤已移除的词、
  按 `overrides` 还原被编辑的词、按 `order` 还原打乱后的顺序）、
  `saveCurrentVocabulary()` 对内置词库直接返回（绝不写应用资源目录）、
  `saveProgressIndex()`（下标写进度文件）、`persistVocabularyChange()`、
  `markWordRemoved()`、`overrideWord()`、`shuffleVocabulary()`；
  顺手修掉 `familiarVocabularyIndex` 取错字段的 bug。
- `ui/wordscreen/WordScreen.kt`：删词、加入熟悉词库、编辑词条、「打乱词库」
  四处改为走上面的接口 —— 内置词库不再需要「另存为」才能删词/加熟词/打乱，
  改动全部落进进度文件，词库文件保持原样。
- `ui/dialog/BuiltInVocabularyDialog.kt`：标题改为「内置词库」，
  卡片点击 = **直接学习**（只读），卡片底部新增「另存为…」按钮保留原来
  「复制一份到本地编辑」的用法；`ui/App.kt`、`ui/wordscreen/WordScreen.kt`
  的调用点分别接上 `openBuiltInVocabulary`。
- 兼容：`~/.MuJing/TypingWordSettings.json` 继续维护（界面其它设置仍在里面），
  其中的 `index` 只用于首次迁移；此后每个词库的下标以进度文件为准，
  换词库不再串位（原先全机只有一份 `index`）。
- 遗留：`ui/wordscreen/WordScreen.kt:1376` 的 `rememberFileSaverLauncher`
  （旧的「内置词库打乱必须另存为」分支）已成为死代码，未删；
  打字界面（`TypingTextSettings.json`/`TypingSubtitlesSettings.json`）的进度
  仍走各自的旧设置文件，未接入本进度系统。

### P2 — 「生成词库」界面交互

- [ ] 内置词库列表要按目标语言过滤，并与右上角语言下拉**联动**
      （现在英语/日语分类混排：`ui/components/BuiltInVocabulary.kt:59`；
      `ui/components/BuiltInVocabularyMenu.kt:48` 没有 language 参数）
- [ ] 内置词库支持**多选**（现在选一个就关闭：`BuiltInVocabularyMenu.kt:86-89`，
      只能反复打开累加）
- [ ] 语言下拉默认 `english` 且不跟随已加载词库：
      `GenerateVocabularyDialog.kt:237`
- [ ] 对日语无意义但仍显示的控件：「处理词组」（`:2237-2260`，日语
      `parseJapaneseSRT`/`parseJapaneseMKV` 没有 `enablePhrases` 参数）、
      摘要区的「牛津5000/四级/六级/GRE」计数（`:1264` `:1433`）、
      「按BNC/COCA词频排序」（`:1312-1379`）、预览里的 `bnc`/`COCA:frq`
      （`:2844-2914`）
- [ ] 词详情/编辑/导出仍以英美音标为列、中英双 Tab：
      `player/danmaku/WordDetail.kt:202`、`ui/edit/EditVocabulary.kt:173-174`
      `:253-254` `:811-812`、`ui/edit/ExportVocabulary.kt:797` `:802`、
      `ui/dialog/EditWordDialog.kt:244-245` `:416-431`；
      `ui/search/SearchResultInfo.kt:83-92` 的「英文」Tab 对日语恒为空
- [ ] 日语词条缺少 `kana`/`romaji` 的编辑与展示入口
      （`ui/wordscreen/Word.kt:322` 的 `Phonetic` 靠 `kana` 是否为空分派，
      不是靠语言）

### P3 — 语言分派的一致性与隐患

- [ ] **用字符范围正则判断语言，违反 `AGENTS.md` §8**：
      `player/HoverableCaption.kt:70` `String.containsJapanese()`、
      `ui/util/GenerateVocabulary.kt:845` `String.looksJapanese()`、
      `ui/subtitlescreen/SubtitleScreen.kt:1353` `computeCharWidth` 用
      `"Chinese|Japanese|Korean"`。应一律改为按 `vocabulary.language` 分派
      （`looksJapanese()` 仅作为 MKV 无 `languageIetf` 时的兜底）
- [ ] `isJapanese` 取值来源不统一：`ui/search/Search.kt:90` 用
      `wordScreenState.vocabulary.language`，同函数 `:100` 用
      `vocabulary.language`
- [ ] 听写输入的错误恢复分支用 UTF-16 码元索引（`ui/wordscreen/WordScreen.kt:1166`
      `currentWord.value[i]`），与 `:1039` 的码点切分（`:1037-1038` 注释说明
      日语浊音/汉字会超 UTF-16）不一致 → 日语下会错位
- [ ] Azure TTS 缓存键不一致：`tts/Azure TTS.kt:186` 写盘用原始 `text`，
      `player/PlayAudio.kt:134` 的 `audioCacheKey` 把非 ASCII 转成 `u%04x`
      （`:138-140`）→ 日语发音缓存永不命中，反复下载
- [ ] 英语动作槽解析无越界保护：`ui/wordscreen/WordScreen.kt:2188-2216`
      对每项直接取 `pair[1]`，格式异常会 `IndexOutOfBoundsException`
      （日语 `parseJapaneseExchange:2143` 有保护，两路不一致）
- [ ] `filterWords` 的 `replaceToLemma` 仍用英语 ECDICT
      （`GenerateVocabularyDialog.kt:2574`），无语言守卫
- [ ] `ui/wordscreen/DictationState.kt:90`：`sentencesVisible` 默认值取自
      `dataDictationState.subtitlesVisible`（疑似取错字段）
- [ ] `ui/dialog/WordFrequencyDialog.kt:102` 硬编码 `language = "english"`

### P4 — 词库数据补强（承接）

- [x] 三类教材的**中文释义**补到「实词无缺」：在 `tools/japanese/jadic_supplement.json`
      人工校订 289 条（`みんなの日本語` 273 + `JLPT精选` 36，另含 5 条空假名的量词
      `－階` `－分` `－本` `－杯` 与 `建て`），重跑后 `标准日本语` 缺中文 0、
      `みんなの日本語` 538 条与 `JLPT精选` 329 条的缺项**全部是句型/用法卡片**
      （保留教材英文讲解，见下条）。实测 0 条能靠拆分并列写法（`いい; よい`）解决
- [x] 罗马字回归修复：只对**纯假名** `kana` 生成分拍罗马字
      （`build_vocabulary.py` 与 `enrich_vocabulary.py` 同步加守卫），
      非纯假名（句型卡片/整句）不再输出乱码罗马字；并补上 `japanese_romaji.py`
      拗音表缺口（`デュ` → `dyu`，此前会输出 `de-ゅ-po-n`）
- [ ] `value` 仍保留源词库原样写法（`いい/よい`、`けれど/けれども`、
      `あげる (=やる)`、`ゆっくりと` 等 40 余条），是否归一化待定
      （拆条会造成重复词条，需先定策略）
- [ ] 少量词因 JMdict 无对应条目而只能保留源数据（无词性/活用）：
      `おまちください` `ぺん`（中文已由校订表补上）。
      片假名↔平假名折叠匹配会引入 `歎` → `タン`(牛舌) 这类错配，暂不做
- [x] 句型/用法卡片（含 `～`、括号、斜杠者：`みんなの日本語` 538 + `JLPT精选` 329 +
      `标准日本语` 1054）与查不到 JMdict 条目的专有名词/整句：**决定保留教材原文
      （英文讲解/释义）**，不补中文、不补罗马字——它们是教材的语法讲解而非词条，
      `enrich_vocabulary.py` 的 `is_pattern_card()` 会原样拷贝、不参与增强。
      （`みんなの日本語` 仍无词性 738 条、`标准日本语` 245 条同理）
- [ ] `日语语法/`（5 档 `grammarList`，无 `wordList`）尚未增强，
      需要单独设计（语法卡片不是词条）
- [ ] 少数 N1 同音异形词仍会挑错条目：`依` `共` `於` `僅` `供` `し`
      （JMdict 里没有对应汉字形，同音候选里也没有 `common` 条目；
      需要词频/优先级数据才能解决）
- [ ] 源数据质量问题（宜在 `build_vocabulary.py` 上游修正）：
      `けれど/けれども` 在 N4 与 N2 重复收录（源数据如此）；
      OpenJLPT 的英文释义本身有错（`はい` 的 meanings 是
      `wear; put on (sword)`），只能照抄
- [ ] 音调（アクセント）展示：NHK 词典不可分发，
      需另找开放许可的音调数据源

### P5 — 验证与收尾

- [ ] 用户实测最新 AppImage（`japanese-support-dev`，含构建修复）：
      `合う` 应显示词性、14 行中文与活用块；`标准日本语` 词性应显示
      「名词」而不是 `[名]`；`JLPT精选` 的 `合う` 应有词性与活用
- [ ] 实测确认后把 `japanese-support-dev` 合入 `master`
- [ ] 回归验证英语词库的显示与发音未受影响
- [ ] 验证日语发音仍走有道真人录音，未回落到 espeak
- [ ] 清理仓库外的验证残留：`~/work_rom/MuJing/glyph/`、`squashfs-root/`、
      `verifyA/`、`testA/`（等用户确认后删）

（不再规划后续语言：当前专注日语。音调（アクセント）展示归入 P4，
NHK 词典不可分发，需另找开放许可的数据源。）

## 已知的坑

- `jadic.db` 的存储约定：汉字词带 `reading`，假名词的 `reading` 为空串。
  查询必须带 `reading`，否则同音异形词会串味
- 词库 JSON 的 `type` 是 `VocabularyType` 枚举（词库固定 `DOCUMENT`），
  **不是**语言名；`language` 才是语种；`subtitlesTrackId` 是整数
- `exchange` 的标签与值内不得出现 `/` 或 `:`，否则被界面当分隔符
- 词库由脚本生成，不要手工编辑 `resources/common/vocabulary/` 下的 JSON
- **构建期必须 `--dict-only`**：`resources/common/dictionary/jadic.db` 被
  `.gitignore` 排除，云端构建时不存在，`build.gradle.kts` 的
  `buildJapaneseDictionary` 任务因此每次都会执行；不带参数调用
  `build_vocabulary.py` 会把仓库里已增强的词库覆盖成基础词库
- 应用数据目录是 `~/.MuJing/`（`state/AppState.kt:412-419`）：
  `AppSettings.json`、`TypingWordSettings.json`（词汇学习进度，全机单文件）、
  `VideoPlayer/`、`audio/`、`logs/`
- 日语字幕支持现状：字幕解析走 `JapaneseWordSegmenter`（Kuromoji），
  但播放器/字幕浏览器的点击查词仍是英语实现（见 P0），
  且 `.ass` 未做转换
