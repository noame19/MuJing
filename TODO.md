# TODO — 日语支持待办

本文件记录日语学习支持的剩余工作。已完成部分见 git 历史与
`docs/JAPANESE_SUPPORT_PLAN.md`。

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

## 待办

### P0 — 验证

- [ ] 下载最新 AppImage 实测，确认词型栏的活用行**能显示出来**
      （此前 300 个动词/形容词的活用全部静默丢失）
- [ ] 回归验证英语词库的显示与发音未受影响
      （Morphology 增加了 language 分派，英语路径应保持原样）
- [ ] 验证日语发音仍走有道真人录音，未回落到 espeak

### P1 — 数据完整性

- [ ] `value` 仍保留源词库原样写法（`いい/よい`、`けれど/けれども`、
      `あげる (=やる)`、`ゆっくりと` 等 40 余条），未改写词库主键。
      是否归一化（拆成多条 / 去括号注释 / 去尾助词）待定
- [ ] 少量词因 JMdict 无对应条目而只能保留源数据（无词性/活用）：
      `おまちください` `ぺん`（中文释义已由校订表补上）。这类词若今后
      出现得多，可考虑片假名↔平假名折叠匹配（但会引入 `歎` → `タン`(牛舌)
      这类错配，需逐条核对，暂不做）
- [ ] 教材分类里整句卡片与专有名词查不到 JMdict 条目，仍是空的词性/释义：
      `みんなの日本語` 738 条无词性（含 `お元気ですか。` 这类整句、
      地名/公司名），`标准日本语` 245 条（专有名词居多）；
      另 `みんなの日本語` 有 110 条无假名/罗马字（源数据 kana 为空或是
      带 `［ ］` 注记的卡片），需要在源数据或显示层处理
- [ ] 原词库的数据质量问题（宜在 build_vocabulary.py 上游修正）：
      - `けれど/けれども` 在 N4 与 N2 重复收录（源数据如此，未改）
      - OpenJLPT 的英文释义本身有错（`はい` 的 meanings 是 `wear; put on (sword)`），
        我们只能照抄
      - 源 kana 与 JMdict 不符的（`日付` 在 N1 作 `かづけ`）已由 `pick_kana`
        以 JMdict 校正为 `ひづけ`；jadic 本身解释错的（`しいんと` 被串成
        「场面/场景」）改由校订表的 `force` 条目覆盖（见「已完成」）
- [ ] 少数 N1 同音异形词仍会挑错条目（源词条在 JMdict 里没有对应汉字形，
      只能在同音词里猜）：`依` `共` `於` `僅` `供` `し`。
      已用 JMdict 词条的 `common` 标志做同分时的优先项，但这几个词的
      同音候选里没有 `common` 条目，需要词频/优先级数据才能解决
      （jmdict-simplified 不带 `<pri>` 标签）

### P2 — 展示与内容

结论（2026-10 核查后决定不改，均为有意保留）：

- `definition` 保持英文释义（JMdict gloss）。要做「日语释义」需引入
  国語辞典数据源（JMdict 只有英文 gloss），而界面已同时给出中文释义
  （`translation`），英文释义只作补充；引入新数据源是独立任务，
  且会把 `definition` 从现在可直接用的英文换成需要再校对的内容
- 多词性**无需**分行：`definition` 已按义项分行、每行带词性前缀
  （与英语 `n.` / `vt.` / `vi.` 完全同构），`pos` 字段另有整词词性汇总
  （英语词库该字段为空，日语因为能拿到权威词性才填）；
  `WordScreen.kt` 的 `rows` 也按 `\n` 计算，多行本来就支持
- 中文释义保持平铺：jadic 给的是 (词形, 读音) 下的平铺列表，
  与 JMdict 义项无法可靠对齐，强行对齐会引入错配
- な形容词活用已由 3 形补到 8 形（见「已完成」）

### P3 — 管线与工程

- [x] romaji 权威来源统一：`tools/japanese/japanese_romaji.py` 为唯一实现，
  两个脚本共用；`build_vocabulary.py` 不再依赖 pykakasi
- [x] 增强步骤并入 `build_vocabulary.py`：`--enrich` 在生成之后立即调用
  `enrich_vocabulary.py`（顺序由脚本保证，避免在已增强文件上再跑一次
  导致同音异形词消歧被上一轮结果污染）
- [x] 代码风格落地为仓库根的 `.editorconfig`（4 空格缩进、UTF-8、LF、
  末尾单个换行、Markdown 保留行尾空格）。**未**引入 ktlint/spotless：
  构建全部在 GitHub Actions 云端进行，为格式加一个 Gradle 插件会扩大
  构建失败面；编辑器自动格式化改以 `.editorconfig` 为准

### P4 — 后续语言与功能

- [ ] 韩语支持：按 `AGENTS.md` §6 的清单实施
      （`resources/common/vocabulary/TOPIK한국어/` + `KoreanDictionary`）
- [ ] 音调（アクセント）展示：NHK 词典不可分发，
      需另找开放许可的音调数据源
- [x] 视频词库生成接入日语分词：已由 `lucene-analysis-kuromoji` +
      `player/JapaneseWordSegmenter.kt` + `GenerateVocabulary.kt`
      的 `extractJapaneseWords()` 实现（核查确认，不必再改）

## 已知的坑

- `jadic.db` 的存储约定：汉字词带 `reading`，假名词的 `reading` 为空串。
  查询必须带 `reading`，否则同音异形词会串味
- 词库 JSON 的 `type` 是 `VocabularyType` 枚举（词库固定 `DOCUMENT`），
  **不是**语言名；`language` 才是语种；`subtitlesTrackId` 是整数
- `exchange` 的标签与值内不得出现 `/` 或 `:`，否则被界面当分隔符
- 词库由脚本生成，不要手工编辑 `resources/common/vocabulary/` 下的 JSON
