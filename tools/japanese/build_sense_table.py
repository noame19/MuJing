"""把 Tomoshi 词典的逐义项中文翻译并入 jadic.db 的 sense 表。

## 为什么需要这一步

jadic.db（Yori Dict）给的中文是**整条平铺**的，没有义项结构：

    お祝い → ["祝贺", "庆祝", "庆典", "贺礼", "贺礼", "礼物"]

而 JMdict 的义项（sense）是分好的，每个义项还带词性：

    sense1 [n] congratulation; celebration → 祝贺
    sense2 [n] congratulatory gift        → 贺礼

平铺中文与义项之间没有对应关系，实测 8334 个 JLPT 词里只有 8% 恰好条数
相等，靠均分或按位置猜会把「安全/稳当」这种归到错误的词性下。

Tomoshi 词典开放数据层提供的是**已经按义项对齐好的**中文翻译
（`zh_defs.senses["N"]` 对应 `entries.senses[N]`，同一个 entry_id），
所以这里把它转成 jadic.db 里的一张 sense 表，供词库增强与界面直接使用。

## 产出

在 jadic.db 里新增一张表：

    CREATE TABLE sense (
        word     TEXT NOT NULL,   -- 词条写法（汉字形，或纯假名词）
        reading  TEXT NOT NULL,   -- 读音；纯假名词为空串（与 jmdict 表同一约定）
        idx      INTEGER NOT NULL,-- 第几个义项，从 0 开始
        pos      TEXT NOT NULL,   -- 词性，中文全称，多个用「、」连接
        gloss    TEXT NOT NULL,   -- 该义项的中文翻译，多个用「；」连接（原始分隔）
        PRIMARY KEY (word, reading, idx)
    )

## 用法

    python3 tools/japanese/fetch_dict_data.py      # 先下数据源
    python3 tools/japanese/build_sense_table.py    # 再重建 sense 表

脚本只读 data/tomoshi-dict-open.db，写入 resources/common/dictionary/jadic.db。
"""

import csv
import json
import os
import sqlite3
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(_HERE, '..', '..'))
DATA = os.path.join(_HERE, 'data')

TOMOSHI_DB = os.path.join(DATA, 'tomoshi-dict-open.db')
KWPOS_CSV = os.path.join(DATA, 'kwpos.csv')
JADIC = os.path.join(REPO, 'resources', 'common', 'dictionary', 'jadic.db')

# 从 enrich_vocabulary.py 直接复用词性映射，两处不能各写一份：
# 词库增强与 sense 表必须用同一套中文词性名，否则界面里同一个词会
# 出现「动词·一段词」和「动词·一段」两种写法。
sys.path.insert(0, _HERE)
from enrich_vocabulary import pos_names  # noqa: E402


def load_desc_to_code():
    """kwpos.csv：JMdict 品词码 ↔ 英文说明。Tomoshi 的 pos 存的是英文说明。"""
    mapping = {}
    try:
        with open(KWPOS_CSV, encoding='utf-8') as f:
            for row in csv.DictReader(f, delimiter='\t'):
                desc = (row.get('descr') or '').strip()
                code = (row.get('kw') or '').strip()
                if desc and code:
                    mapping[desc] = code
    except OSError as e:
        raise RuntimeError(f"无法读取 {KWPOS_CSV}：{e}") from e
    except csv.Error as e:
        raise RuntimeError(f"{KWPOS_CSV} 不是合法 CSV：{e}") from e
    return mapping


def load_entries(con):
    """读出全部条目：(entry_id, kanji 列表, kana 列表, 每个 sense 的词性码)。"""
    out = {}
    for eid, data in con.execute('SELECT id, data FROM entries'):
        try:
            e = json.loads(data)
        except (TypeError, ValueError):
            continue
        out[eid] = e
    return out


def load_zh(con):
    """读出全部中文义项：entry_id → {sense_index: [中文串]}。"""
    out = {}
    for eid, data in con.execute('SELECT entry_id, data FROM zh_defs'):
        try:
            d = json.loads(data)
        except (TypeError, ValueError):
            continue
        senses = {}
        for idx, s in (d.get('senses') or {}).items():
            try:
                i = int(idx)
            except (TypeError, ValueError):
                continue
            texts = [g.get('text', '').strip() for g in s.get('glosses', [])]
            texts = [t for t in texts if t]
            if texts:
                senses[i] = texts
        if senses:
            out[eid] = senses
    return out


def build_rows(tomoshi_db, desc_to_code):
    """把 Tomoshi 的两个表拼成 sense 表的行。

    一个 JMdict 条目可能对应多个写法（漢字形与假名形），每个写法的
    读音各不相同，因此按 (写法, 读音) 逐对展开 —— 与 jmdict 表的存储
    约定一致：汉字词带 reading，纯假名词的 reading 为空串。
    """
    con = sqlite3.connect(tomoshi_db)
    try:
        entries = load_entries(con)
        zh = load_zh(con)
    finally:
        con.close()

    rows = {}
    for eid, senses in zh.items():
        e = entries.get(eid)
        if not e:
            continue
        kanji = [k['text'] for k in e.get('kanji', [])]
        kana = [k['text'] for k in e.get('kana', [])]
        sense_list = e.get('senses') or []

        forms = []
        # 汉字形：每个汉字形与每个读音配对（JMdict 不额外区分 restricted_to，
        # 全部配上比漏掉好；查不到时上层还有 jadic 平铺兜底）
        for k in kanji:
            for r in kana:
                forms.append((k, r))
        # 纯假名词：reading 留空，与 jadic 的约定一致
        if not kanji:
            for r in kana:
                forms.append((r, ''))

        for word, reading in forms:
            for idx, texts in senses.items():
                if idx >= len(sense_list):
                    continue
                codes = []
                for desc in sense_list[idx].get('pos', []):
                    code = desc_to_code.get(desc)
                    if code:
                        codes.append(code)
                names = pos_names(codes)
                key = (word, reading, idx)
                rows.setdefault(key, {
                    'pos': '、'.join(names),
                    'gloss': '；'.join(texts),
                })
    return rows


def write_table(rows):
    con = sqlite3.connect(JADIC)
    try:
        con.execute('DROP TABLE IF EXISTS sense')
        con.execute(
            'CREATE TABLE sense ('
            ' word TEXT NOT NULL, '
            ' reading TEXT NOT NULL, '
            ' idx INTEGER NOT NULL, '
            ' pos TEXT NOT NULL, '
            ' gloss TEXT NOT NULL, '
            ' PRIMARY KEY (word, reading, idx))'
        )
        con.execute('CREATE INDEX idx_sense_word ON sense(word)')
        con.executemany(
            'INSERT OR REPLACE INTO sense(word, reading, idx, pos, gloss) '
            'VALUES(?,?,?,?,?)',
            [(w, r, i, v['pos'], v['gloss'])
             for (w, r, i), v in rows.items()],
        )
        con.commit()
    finally:
        con.close()


def main():
    for path, what in ((TOMOSHI_DB, 'Tomoshi 词典'),
                       (KWPOS_CSV, 'kwpos.csv'),
                       (JADIC, 'jadic.db')):
        if not os.path.exists(path):
            print(f"缺少{what}：{path}", file=sys.stderr)
            print("请先运行 tools/japanese/fetch_dict_data.py，"
                  "以及 tools/japanese/build_vocabulary.py --dict-only",
                  file=sys.stderr)
            return 1

    print("读取 Tomoshi 词典 …")
    desc_to_code = load_desc_to_code()
    if not desc_to_code:
        print("kwpos.csv 为空或格式不对", file=sys.stderr)
        return 1

    rows = build_rows(TOMOSHI_DB, desc_to_code)
    print(f"共 {len(rows)} 条义项记录")

    print("写入 jadic.db 的 sense 表 …")
    write_table(rows)

    # 自检：统计有多少词的义项带词性
    with_pos = sum(1 for v in rows.values() if v['pos'])
    print(f"  sense 行数 {len(rows)}，带词性 {with_pos} "
          f"({with_pos * 100 // max(len(rows), 1)}%)")
    print("完成。")
    return 0


if __name__ == '__main__':
    sys.exit(main())
