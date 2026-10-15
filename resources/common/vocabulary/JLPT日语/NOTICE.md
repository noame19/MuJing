# 日语词库数据来源与许可

本目录下的 `1 N5.json` ~ `5 N1.json` 由以下两个脚本依次生成，请勿手工编辑：

1. `tools/japanese/build_vocabulary.py` —— 拉取分级、释义，产出基础词库
2. `tools/japanese/enrich_vocabulary.py` —— 以 JMdict 补全词性、分拍罗马字、活用形

两步顺序不可颠倒；也可用 `python3 tools/japanese/build_vocabulary.py --enrich`
一次完成（脚本会先重建基础词库再增强）。重新生成方式见各脚本头部说明。

---

## 数据来源

### 1. JLPT 等级分级与英文释义 — OpenJLPT

- 项目：<https://github.com/evanclan/OpenJLPT>
- 取用内容：`data/json/vocab/{n5,n4,n3,n2,n1}.json`
  的 `word`、`reading`、`meanings` 字段
- 许可：Creative Commons Attribution-ShareAlike 4.0 International
- 署名要求：保留 OpenJLPT 名称与链接，并标注 CC BY-SA 4.0

### 2. JLPT 分级原始清单 — Jonathan Waller

- 来源：<http://www.tanos.co.uk/jlpt/>
- 许可：Creative Commons Attribution (CC BY)
- 说明：OpenJLPT 的等级归属依据此社区通行清单整理。

> **JLPT 官方立场**：日本国际教育支援协会自 2010 年改版起**不再公布官方词表**，
> 官方 FAQ 明确说明 Test Content Specifications 已废止。
> 因此本目录中的「N5 词表」是第三方整理结果，**非官方发布**。
> 参考：<https://www.jlpt.jp/e/faq/>

### 3. 中文释义 — Yori Dict（日中词典）

- 项目：<https://github.com/YoriJP/yori-dict>
- 取用内容：release `data-2026-08-08` 的 `yori-ja-zh-cn.zip`（简体）
- 许可：Creative Commons Attribution-ShareAlike 4.0 International
- 署名要求：保留 Yori Dict 名称与链接，并标注 CC BY-SA 4.0

该项目的日→简中释义为其自建内容，其底层 JMdict 数据由
Electronic Dictionary Research and Development Group (EDRDG) 提供。

### 4. 罗马字读音 — 本项目自建

分拍罗马字（かっこう → ka-k-ko-u）由本仓库的
`tools/japanese/japanese_romaji.py` 生成，不依赖第三方罗马字转换库，
故此处无需第三方署名。早期版本曾使用 PyKakasi（GPL-3.0-or-later），
现已移除该依赖。

### 5. 词性、义项与活用形 — JMdict / JMdictDB（EDRDG）

由 `tools/japanese/enrich_vocabulary.py` 使用，为每个词条补全 `pos`
（词性）、`definition`（英文释义，按义项分条）与 `exchange`（动词、形容词活用）。

- 项目：<https://www.edrdg.org/jmdict/j_jmdict.html>（JMdict）
- 项目：<https://www.edrdg.org/edrdg/>（JMdictDB，提供活用规则表 `conjo.csv`）
- 取用内容：JMdict 的**日文与英文**部分（品词标记、义项、英文释义）；
  JMdictDB 的活用规则表 `conj.csv` / `conjo.csv` / `conotes.csv` / `kwpos.csv`
- 许可：Creative Commons Attribution-ShareAlike 4.0 International
- 使用方式：仅在**构建期**下载并生成词库，不随应用分发原始词典文件

> **授权边界**：EDRDG 仅授权 JMdict 的日文与英文部分。德/荷/俄/法等译文
> 由各自编译器持有独立版权，不得分发。本目录只取日→英部分，合规。
> 详见 <https://www.edrdg.org/edrdg/licence.html>。

---

## 未使用的数据源及原因

以下资料虽有对应的中文学习者常用分级，但**不具备可再分发的许可**，
因此没有内置进本目录：

| 资料 | 原因 |
| --- | --- |
| 新东方 红宝书 / 蓝宝书 | 商业出版物，版权受限 |
| 标准日本语、みんなの日本語（3A 出版） | 商业教材，版权受限 |
| BJT 商务日本语能力测试 词表 | 官方仅公布分数段，未授权词表 |
| JASSO 日本留学试验（EJU）大纲 | 官方大纲不含词表 |
| NHK 日本語発音アクセント辞書（音调） | 版权受限，音调数据本次未引入 |

若后续需要音调（アクセント）功能，需另行寻找开放许可的音调数据源。

---

## 应用内署名建议

在应用的「关于」页面应包含如下署名文本：

```text
日语词库数据

本词库的等级分级与英文释义来自 OpenJLPT (CC BY-SA 4.0)，
其等级清单源自 Jonathan Waller 的 JLPT 词表 (CC BY)。
中文释义来自 Yori Dict (CC BY-SA 4.0)。
词性与活用形来自 JMdict / JMdictDB。

上述词典数据均由 EDRDG (CC BY-SA 4.0) 提供，
依据 CC BY-SA 4.0 授权。
```

注：少数 Yori Dict 未收录的词，其中文释义取自本项目自撰的
`tools/japanese/jadic_supplement.json`（同以 CC BY-SA 4.0 授权），
不涉及第三方署名。

EDRDG 原始许可要求：软件包内应随附许可与文档副本，或提供其链接；
若应用提供屏幕上的词典展示，需在界面上作出致谢。
详见 <https://www.edrdg.org/edrdg/licence.html>。
