"""MuJing 日语词库增强（JLPT N5–N1 + 教材类分类）。

在 build_vocabulary.py 产出的基础词库之上，补全：
  - pos        词性（中文全称，如「动词·一段词」「い形容词」）
  - romaji     分拍罗马字（かっこう → ka-k-ko-u；实现见
               tools/japanese/japanese_romaji.py，与 build_vocabulary.py 共用）
  - exchange   动词/形容词活用（辞書形:食べる/て形:食べて/…）
  - definition 英文释义（按义项分条，带词性前缀）
  - translation 中文释义（按 (词形, 读音) 精确匹配，避免同音异形词串味）
  - kana       校正源词库里被写歪的读音（编者注、乱码、多读音混排），
               以 JMdict 条目为准（见 pick_kana）

除查不到 JMdict 条目的词条会保留源数据外，本脚本不丢词：源词库里的每一条
都会出现在产物里（见 _source_only_entry）。

分类（--category，默认 JLPT日语）：
  - JLPT日语      释义以 JMdict 为准逐条重写（原有英文释义只当消歧线索）
  - 标准日本语     词形/假名来自教材，只补空：词性由 [名]/[动3] 括号码换为
                  中文全称、补分拍罗马字与活用、补英文释义（原先为空）
  - みんなの日本語  同上，补词性/活用，中文释义缺失时补
  - JLPT精选      同上
  含 ～ 或斜杠并列写法的「句型/量词卡片」不是单词，原样跳过（is_pattern_card）。

数据源（均为权威词典，不做规则推测）：
  - JMdict (scriptin/jmdict-simplified)    品词标记、义项、英文释义
  - JMdictDB conjo.csv (EDRDG / Jim Breen)  动词/形容词活用表（1137 条规则）
  - resources/common/dictionary/jadic.db   中文释义

同音异形词隔离：按 (汉字形, 假名) 精确匹配，再以该汉字形查 jadic。
例如 かっこう 只取「格好」，不会把「滑降（滑雪）」「郭公（布谷鸟）」混进来。

用法：
    python3 tools/japanese/build_vocabulary.py     # 先生成基础词库（务必先跑）
    python3 tools/japanese/enrich_vocabulary.py --all --write   # 再增强 JLPT日语
    python3 tools/japanese/enrich_vocabulary.py --category 标准日本语 --all --write

⚠ 运行顺序：本脚本的输入就是 resources/common/vocabulary/JLPT日语/*.json，
而它在同音异形词消歧时会读词条原有的 definition（源英文释义）当线索。
若在已增强过的文件上再跑一次，线索就变成上一轮的 JMdict 释义，会自我强化
错误结果（如 はい → 「名词. medal…」）。因此**必须先重跑 build_vocabulary.py
生成基础词库，再跑本脚本一次**。run_level 会检测到这种误用并警告。
教材分类（标准日本语等）没有这个限制：它们的 definition/translation 原样保留，
不吃 JMdict 释义，故可反复跑。

单独预览（不写入）：
    python3 tools/japanese/enrich_vocabulary.py            # 预览 N4
    python3 tools/japanese/enrich_vocabulary.py --all      # 预览全部 5 个级别
    python3 tools/japanese/enrich_vocabulary.py --all --out <目录>  # 另存到指定目录

首次运行前需先下载数据源（约 111MB，不入库）：
    python3 tools/japanese/fetch_dict_data.py
"""

import csv
import glob
import json
import os
import re
import sqlite3
import sys

# 分拍罗马字（モーラ拍）转换与 build_vocabulary.py 共用同一实现，
# 见 tools/japanese/japanese_romaji.py
_HERE_DIR = os.path.dirname(os.path.abspath(__file__))
if _HERE_DIR not in sys.path:
    sys.path.insert(0, _HERE_DIR)
from japanese_romaji import romaji  # noqa: E402

_HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(_HERE, 'data')
# 仓库根：本文件位于 <repo>/tools/japanese/，上两级即仓库根
REPO = os.path.abspath(os.path.join(_HERE, '..', '..'))
JMDICT = os.path.join(DATA, 'jmdict-eng-3.6.2.json')
JADIC = os.path.join(REPO, 'resources', 'common', 'dictionary', 'jadic.db')
VOCAB_ROOT = os.path.join(REPO, 'resources', 'common', 'vocabulary')
VOCAB_DIR = os.path.join(VOCAB_ROOT, 'JLPT日语')
LEVELS = [('1 N5.json', 'N5'), ('2 N4.json', 'N4'), ('3 N3.json', 'N3'),
          ('4 N2.json', 'N2'), ('5 N1.json', 'N1')]

# 可增强的日语分类。JLPT日语 的释义以 JMdict 为准、逐级重写；其余三类
# 的词形/假名来自各自教材，只做「补空」与「对齐工程标准」——
# 词性、分拍罗马字、活用一律按 JMdict 补齐（标准日本语原先只有 [名] 这类
# 括号码、且无活用），释义与中文只在缺失时补，不覆盖教材原有内容。
# files 留空表示按目录内文件排序取用（文件名前缀即册序/课序）。
CATEGORIES = {
    'JLPT日语': {'files': LEVELS, 'keep_source': False},
    '标准日本语': {'files': None, 'keep_source': True},
    'みんなの日本語': {'files': None, 'keep_source': True},
    'JLPT精选': {'files': None, 'keep_source': True},
}
# 词形或假名含以下记号的是「句型/量词卡片」（～円 / お～ / 何～／～歳），
# 不是单个词，JMdict 里没有对应条目，增强时原样跳过。
_PATTERN = re.compile(r'[～~/／;；()（）\[\]［］・\s]')


def is_pattern_card(word):
    """句型/量词卡片（含 ～ 、斜杠并列写法、括注、空白）不做增强。"""
    return bool(_PATTERN.search(word.get('value', ''))
                or _PATTERN.search(word.get('kana', '')))


# 教材词库（标准日本语 / みんなの日本語）把动词收成 ます形、て形、た形，
# JMdict 里只有辞書形，不还原就完全匹配不上（动词的词性、活用、释义全拿不到）。
# 还原只产生「候选」，一律交给 JMdict 判定：查不到的候选自然落空，
# 不会像同音词猜测那样引入错误条目。
_POLITE_SUFFIX = ('ませんでした', 'ましょう', 'ません', 'ました', 'ます')
_TE_SUFFIX = {'って': ('う', 'つ', 'る'), 'んで': ('ぬ', 'ぶ', 'む'),
              'いて': ('く',), 'いで': ('ぐ',), 'して': ('す',),
              'きて': ('くる',)}
_TA_SUFFIX = {'った': ('う', 'つ', 'る'), 'んだ': ('ぬ', 'ぶ', 'む'),
              'いた': ('く',), 'いだ': ('ぐ',), 'した': ('す',),
              'きた': ('くる',), 'かった': ('い',)}
_COPULA = ('ですか', 'でした', 'です', 'だった', 'だ')
_GODAN_MASU = {'い': 'う', 'き': 'く', 'ぎ': 'ぐ', 'し': 'す', 'ち': 'つ',
               'に': 'ぬ', 'び': 'ぶ', 'み': 'む', 'り': 'る'}


def deinflect(text):
    """教材词库的活用形 → 辞書形候选（行きます → 行く、歩いて → 歩く）。

    き/し 结尾既可能是五段（行く）也可能是一段（起きる），两种候选都给出，
    由 JMdict 判定。本就不是词条的句子（お元気ですか）会返回空列表。
    """
    t0 = text.strip()
    stripped = t0.rstrip('。．.！!？?、,')
    cands = [stripped] if stripped and stripped != t0 else []
    t = stripped or t0
    # ます形：行きます → 行く / 起きます → 起きる / します → する
    for suf in _POLITE_SUFFIX:
        if t.endswith(suf) and len(t) > len(suf):
            stem = t[:-len(suf)]
            if stem == 'し':                       # します → する（サ変）
                return cands + ['する']
            if stem == 'き':                       # きます → くる
                return cands + ['くる']
            if stem == '来':
                return cands + ['来る']
            if stem.endswith('し'):                # 勉強します → 勉強する
                cands.append(stem + 'る')
                # する 名词也可能是 名词+vs 词条（勉強、コピー），
                # JMdict 里查不到「勉強する」但查得到「勉強」。
                cands.append(stem[:-1])
            elif stem.endswith('来'):              # 持って来ます → 持って来る
                cands.append(stem[:-1] + '来る')
            tail = stem[-1]
            if tail in _GODAN_MASU:
                cands.append(stem[:-1] + _GODAN_MASU[tail])
            cands.append(stem + 'る')
            break
    # て形/た形：歩いて → 歩く、読んだ → 読む、楽しかった → 楽しい
    for table in (_TE_SUFFIX, _TA_SUFFIX):
        for suf, tails in table.items():
            if t.endswith(suf) and len(t) > len(suf):
                stem = t[:-len(suf)]
                cands += [stem + x for x in tails]
    # 结尾助动词：元気です → 元気、学生だった → 学生
    for suf in _COPULA:
        if t.endswith(suf) and len(t) > len(suf):
            cands.append(t[:-len(suf)])
    return list(dict.fromkeys(c for c in cands if c and c != t0))



# 分拍罗马字（モーラ拍）转换见文件头部的 japanese_romaji 导入：
# 与 build_vocabulary.py 共用同一份实现，避免两边各自生成、互相覆盖
# （曾出现 build 出 kakkou、本脚本覆盖为 ka-k-ko-u 的冲突）。
# 二、活用生成
#
# 规则全部来自 JMdictDB 的 conjo.csv，不做启发式推导。
# conjo.csv 每行 = (pos, conj, neg, fml, onum, stem, okuri, euphr, euphk)：
#   stem  要从词尾删掉几个假名
#   okuri 接上的送假名
#   euphr 词干音变时的替换假名（如 来る 的 く→こ）
#   euphk 词干汉字变化时的替换（仅 する 的可能形 出来る）
#
# 例外词（帰る/走る/入る/切る/知る 长得像一段实为五段）由 JMdict 的
# pos 标记（v5r 而非 v1）直接决定，无需猜测。
# ══════════════════════════════════════════════════════════════════


def _read_tsv(name):
    path = os.path.join(DATA, name)
    try:
        with open(path, encoding='utf-8', newline='') as f:
            return list(csv.DictReader(f, delimiter='\t'))
    except OSError as e:
        raise RuntimeError(
            f"缺少数据文件 {path}；请先运行 fetch_data.py 下载 JMdictDB 活用表"
        ) from e


_KW = {r['id']: r['kw'] for r in _read_tsv('kwpos.csv')}
_CONJNAME = {r['id']: r['name'] for r in _read_tsv('conj.csv')}
_ROWS = _read_tsv('conjo.csv')
_POSID = {v: k for k, v in _KW.items()}
_BYPOS = {}
for _r in _ROWS:
    _BYPOS.setdefault(_r['pos'], []).append(_r)

# conj 编号 → 中文活用名
CN = {'1': '辞書形', '2': '過去形', '3': 'て形', '4': 'ば形', '5': '可能形',
      '6': '受身形', '7': '使役形', '8': '使役受身形', '9': '意志形',
      '10': '命令形', '11': 'たら形', '12': 'たり形', '13': '連用形'}

# 界面展示顺序：辞書形 → ます/ない → 核心活用形 → な形容词的 な/に
DISPLAY = ['辞書形', 'ます形', 'ない形', '過去形', 'て形', 'ば形',
           '意志形', '命令形', '可能形', '受身形', '使役形',
           '連体形', '連用形']

# サ変：conjo.csv 对 vs 只有「辞書形」一条，其余按送假名规则补全
SURU = [('辞書形', '{s}する'), ('ます形', '{s}します'), ('ない形', '{s}しない'),
        ('て形', '{s}して'), ('過去形', '{s}した'), ('ば形', '{s}すれば'),
        ('意志形', '{s}しよう'), ('命令形', '{s}しろ'), ('可能形', '{s}できる'),
        ('受身形', '{s}される'), ('使役形', '{s}させる')]

# カ変：来 的读音 く/き/こ 不规则，逐形写死。stem 已去掉结尾「る」
KURU = {'辞書形': '{w}る', 'ます形': '{w}ます', 'ない形': '{w}ない', 'て形': '{w}て',
        '過去形': '{w}た', 'ば形': '{w}れば', '意志形': '{w}よう', '命令形': 'こい',
        '可能形': '{w}られる', '受身形': '{w}られる', '使役形': '{w}させる'}

# な形容词：官方 conjo.csv 只有「辞書形 だ」一条，缺 な/に 及其余规则派生形。
# 这些形都是 100% 规则的语法派生（な形容词无例外词），不属于词条级的
# 活用分类猜测（帰る/走る 那类风险），故在官方表之外补全，与 い形容词
# （活用全部来自 conjo.csv）在界面上呈现同一组形。
# 动词一律不用此方式 —— 动词活用全部来自 conjo.csv。
NA_ADJ = [
    ('辞書形', 'だ'), ('連体形', 'な'), ('連用形', 'に'),
    ('ない形', 'ではない'), ('て形', 'で'), ('過去形', 'だった'),
    ('ば形', 'なら'), ('意志形', 'だろう'),
]


def _suru(word):
    stem = word[:-2] if word.endswith('する') else word
    out = {}
    for name, tpl in SURU:
        out[name] = word if (tpl == '{s}する' and stem == '') else tpl.format(s=stem)
    return out


def _kuru(word):
    stem = word[:-1] if word.endswith('る') else word
    return {name: tpl.format(w=stem) for name, tpl in KURU.items()}


def conjugate(word, pos_codes):
    """按官方 conjo.csv 生成活用。返回 {中文活用名: 形}；无活用返回 {}。"""
    out = {}
    for code in pos_codes:
        if code == 'vs':
            out.update(_suru(word))
            continue
        if code == 'vk':
            out.update(_kuru(word))
            continue
        if code == 'adj-na':
            for name, okuri in NA_ADJ:
                out[name] = word + okuri
            continue
        pid = _POSID.get(code)
        if pid is None:
            continue
        for r in _BYPOS.get(pid, []):
            if r['neg'] == 't' or r['fml'] == 't':
                continue
            cid = r['conj']
            n = _int_or_zero(r['stem'])
            if n > len(word):
                continue
            stem = word[:len(word) - n] if n else word
            euphr, euphk = r['euphr'], r['euphk']
            body = euphk if (euphk and stem) else (euphr or stem)
            form = body + r['okuri']
            name = CN.get(cid)
            if not name or name in out:
                continue
            out[name] = form
    if out:
        out.setdefault('辞書形', word)
    return out


def _int_or_zero(text):
    """conjo.csv 的 stem 列应为整数；异常时按 0 处理并保持安静。"""
    try:
        return int(text)
    except (TypeError, ValueError):
        return 0


def display_forms(word, pos_codes):
    """界面展示用活用列表 [(名, 形)]，按学习顺序排列。"""
    forms = conjugate(word, pos_codes)
    if not forms:
        return []
    seen, out = set(), []
    for name in DISPLAY:
        if name in forms and name not in seen:
            seen.add(name)
            out.append((name, forms[name]))
    return out


def conjugatable(pos_codes):
    return any(c.startswith('v') or c in ('adj-i', 'adj-na') for c in pos_codes)


# ══════════════════════════════════════════════════════════════════
# 三、词条组装
# ══════════════════════════════════════════════════════════════════

POS_CN = {
    'n': '名词', 'n-t': '名词·サ变可', 'n-adv': '副词性名词',
    'n-suf': '名词后缀', 'n-pref': '名词前缀',
    'adj-i': 'い形容词', 'adj-na': 'な形容词', 'adj-no': '体言性形容词',
    'adj-pn': '连体修饰词', 'adj-ix': '不规则形容词',
    'v1': '动词·一段词', 'v1-s': '动词·一段词·サ变', 'v2a-s': '动词·下二段·サ变',
    'v5aru': '动词·五段词·aru类', 'v5b': '动词·五段词·b行', 'v5g': '动词·五段词·g行',
    'v5k': '动词·五段词·k行', 'v5k-s': '动词·五段词·k行·サ变', 'v5m': '动词·五段词·m行',
    'v5n': '动词·五段词·n行', 'v5r': '动词·五段词·r行', 'v5r-i': '动词·五段词·r行例外',
    'v5s': '动词·五段词·s行', 'v5t': '动词·五段词·t行', 'v5u': '动词·五段词·u行',
    'v5u-s': '动词·五段词·u行·サ变', 'v5uru': '动词·五段词·古语',
    'vi': '动词·自动词', 'vt': '动词·他动词', 'vs': '动词·サ变', 'vk': '动词·カ变',
    'vs-i': '动词·サ变·来', 'vs-s': '动词·サ变·去', 'vs-c': '动词·サ变复合',
    'vn': '动词·ヌ变', 'vr': '动词·ラ变', 'v4r': '动词·下二段', 'v-unspec': '动词',
    'ad': '副词', 'adv': '副词', 'prt': '助词', 'int': '感叹词', 'conj': '连接词',
    'cop': '判断词', 'ctr': '量词', 'num': '数词', 'pn': '代名词', 'rct': '连体词',
    'aux': '助动词', 'aux-v': '动词性助动词', 'aux-adj': '形容词性助动词',
    'bv': '动词性辞书形', 'suf': '后缀', 'pref': '前缀', 'prf': '前缀',
    'exp': '惯用句', 'adj-f': '连体词性形容词',
    'adj-t': 'ト・タル形容词', 'adv-to': 'ト副词', 'unc': '未分类',
}

POS_ORDER = ['aux', 'aux-v', 'aux-adj', 'bv', 'exp', 'adj-f', 'adj-no', 'adj-pn',
             'adj-ix', 'adj-t', 'rct', 'conj', 'int', 'cop', 'ctr', 'num', 'pn',
             'ad', 'adv', 'adv-to', 'adj-i', 'adj-na', 'prt',
             'n-suf', 'n-pref', 'pref', 'suf', 'n', 'n-t', 'n-adv', 'unc']

_VERB_SUB = {k for k in POS_CN if k.startswith('v')}


def pos_names(codes):
    """JMdict 品词码 → 中文词性名列表（按名称去重、按优先级排序）。"""
    has_sub = any(c in _VERB_SUB and c not in ('vi', 'vt') for c in codes)
    picked = []
    for c in codes:
        if c in ('vi', 'vt') and has_sub:
            continue
        nm = POS_CN.get(c)
        if nm and nm not in picked:
            picked.append(nm)
    # 多个码可能映射到同一中文名（ad/adv 都是「副词」），按名称去重
    ordered = []
    for c in POS_ORDER:
        nm = POS_CN.get(c)
        if nm and nm in picked and nm not in ordered:
            ordered.append(nm)
    for nm in picked:
        if nm not in ordered:
            ordered.append(nm)
    return ordered


def _read_json(path):
    try:
        with open(path, encoding='utf-8') as f:
            return json.load(f)
    except OSError as e:
        raise RuntimeError(f"无法读取 {path}：{e}") from e
    except json.JSONDecodeError as e:
        raise RuntimeError(f"{path} 不是合法 JSON：{e}") from e


def load_jmdict():
    """构建索引：(汉字, 假名) → 条目；假名 → [条目]；汉字 → [条目]。"""
    data = _read_json(JMDICT)
    exact, by_kana, by_kanji = {}, {}, {}
    for e in data['words']:
        kanji = [k['text'] for k in e.get('kanji', [])]
        kana = [k['text'] for k in e.get('kana', [])]
        senses = []
        for s in e.get('sense', []):
            pos = [p for p in s.get('partOfSpeech', []) if p in POS_CN]
            gl = [g['text'] for g in s.get('gloss', []) if g['lang'] == 'eng']
            if gl:
                senses.append((pos, gl))
        if not senses:
            continue
        common = (any(k.get('common') for k in e.get('kana', []))
                  or any(k.get('common') for k in e.get('kanji', [])))
        ent = {'kanji': kanji, 'kana': kana, 'senses': senses,
               'common': common}
        for k in kanji:
            for r in kana:
                exact.setdefault((k, r), ent)
            by_kanji.setdefault(k, []).append(ent)
        for r in kana:
            by_kana.setdefault(r, []).append(ent)
    return exact, by_kana, by_kanji


def _split_alt(text):
    """拆分并列写法（けれど/けれども → [けれど, けれども]）。"""
    parts = [p.strip() for p in re.split(r'[/／、,]', text or '') if p.strip()]
    return parts


def _clean_kana(kana):
    """清洗词库中不规范的假名写法，返回读音候选列表。

      - あたたか(い)      → あたたかい     （括号只是排版，内容属于读音）
      - じゅう とお       → じゅう / とお   （空格分隔的多读音）
      - かねもち/おかねもち → 两个候选      （斜杠并列）
    """
    s = re.sub(r'[()（）]', '', kana or '')
    return [p.strip() for p in re.split(r'[\s/／、,]+', s) if p.strip()]


def is_kana_text(text):
    """判断字符串是否由平假名/片假名构成（含长音符、中点）。"""
    if not text:
        return False
    for ch in text:
        code = ord(ch)
        if not (0x3041 <= code <= 0x309F or 0x30A0 <= code <= 0x30FF):
            return False
    return True


# 词库里常把助词连着副词一起收（ゆっくりと / 一段と / やたらに），
# 匹配 JMdict 时允许去掉尾部的 と / に。
_TRAILING_PARTICLES = ('と', 'に')


def _kana_variants(kana):
    """读音的等价写法：本身，以及去掉尾部助词后的形式。"""
    out = [kana]
    for p in _TRAILING_PARTICLES:
        if kana.endswith(p) and len(kana) > 1:
            out.append(kana[:-1])
    return out


def _particle_stems(text):
    """词形去掉尾部助词后的写法（尾助词是语法成分，不是词的一部分）。

    仅取长度 > 2 的形式，避免把 こと → こ 这类噪声当作词干。
    """
    out = []
    for part in _split_alt(_clean_value(text)):
        if len(part) > 2 and part.endswith(_TRAILING_PARTICLES):
            out.append(part[:-1])
    return out


def _clean_value(value):
    """去掉词形尾部的说明性括注，如「あげる (=やる)」→「あげる」。

    与假名不同：词形里的括注是编者注（同义词、说明），不属于词本身。
    """
    v = value or ''
    return re.sub(r'\s*[（(][^)）]*[)）]\s*$', '', v).strip() or v.strip()


def _match_candidates(value, kana, strip=False):
    """生成 (词形, 假名) 匹配候选，覆盖词库里几种非常规写法。

      - 案内 / あんないする        → 假名带 する，去掉后为 あんない
      - あいさつする / あいさつする   → 词形带 する，去掉后为 あいさつ
      - お金持ち / かねもち/おかねもち  → 假名有并列，逐个试
      - あたたか(い)              → 去括号后为 あたたかい
      - あげる (=やる) / あげる     → 词形的括注是编者注，要去掉

    strip=True 时再额外尝试去掉尾部助词 と/に（ゆっくりと → ゆっくり），
    只在基本候选全都查不到时才启用，避免把 こと→こ 这类噪声拉进候选池。

    する 的剥离对词形与假名两边都做，因为词库把两种写法混用。
    """
    vals = _split_alt(_clean_value(value)) or [_clean_value(value)]
    ks = _clean_kana(kana) or vals
    vals2 = list(vals)
    ks2 = list(ks)
    for v in vals:
        if v.endswith('する'):
            vals2.append(v[:-2])
    for k in ks:
        if k.endswith('する'):
            ks2.append(k[:-2])
    out = []
    for v in dict.fromkeys(vals2):
        for k in dict.fromkeys(ks2):
            out.append((v, k))
    if strip:
        # 尾助词是语法成分，不是词的一部分：剥它的时候，词形与假名必须一起剥。
        # 若各自独立相剥再取笛卡尔积，会造出 (一段と, いちだん) 这种错配，
        # 把 一団/いちだん 这类同音异义词拉进候选池。
        seen = set(out)
        for v in dict.fromkeys(vals):
            if len(v) <= 2 or not v.endswith(_TRAILING_PARTICLES):
                continue
            for k in dict.fromkeys(ks):
                pair = (v[:-1], k[:-1])
                if len(k) > 2 and k[-1] == v[-1] and pair not in seen:
                    seen.add(pair)
                    out.append(pair)
    return out


_EN_STOPWORDS = {
    'a', 'an', 'the', 'of', 'to', 'and', 'or', 'in', 'on', 'at', 'by', 'as',
    'is', 'are', 'was', 'were', 'be', 'been', 'being', 'it', 'its', 'i', 's',
    'with', 'from', 'you', 'your', 'etc', 'e', 'g', 'esp', 'usu',
}


def _best(pool, hint):
    """池中多条时，用源英文释义给最贴近的一条打分。

    按整词（而非子串）比对，并滤掉纯虚词：早先用子串匹配时，報（information）
    会因为含 for 而白得一分；而像 this/that/so/no 这类词在简短释义里是
    有效信号（この 的释义就是 this），故不滤。释义分数相同时，优先 JMdict
    标为 common（高频词）的条目：同音异形词里只有常用词才值得进 JLPT 词库，
    如 し 应取「死」而不是「屍」。
    """
    if len(pool) == 1:
        return pool[0]
    hint_words = {w for w in re.findall(r'[a-z]+', (hint or '').lower())
                  if w not in _EN_STOPWORDS}
    best, best_key = pool[0], None
    for c in pool:
        gloss = ' '.join(g for _, gl in c['senses'] for g in gl).lower()
        score = len(hint_words & set(re.findall(r'[a-z]+', gloss)))
        key = (score, 1 if c.get('common') else 0)
        if best_key is None or key > best_key:
            best, best_key = c, key
    return best


def pick_entry(value, kana, hint, exact, by_kana, by_kanji):
    """挑出与词条对应的 JMdict 条目。

    汉字形优先按 (汉字, 假名) 精确匹配；假名形（同音异形词有歧义）
    用词库原有的英文释义打分挑最贴近的。假名不可用（词库里有乱码）时，
    退回只用汉字形匹配。基本候选都落空后，才尝试去掉尾部助词的写法。
    """
    base = _match_candidates(value, kana)
    stripped = _match_candidates(value, kana, strip=True)
    # 去尾助词后的写法（ゆっくりと → ゆっくり、一段と → 一段）同样值得先试
    # 精确匹配：能对上(汉字, 假名)时，比在候选池里按释义打分可靠得多。
    for v, k in base + stripped:
        if v != k and (v, k) in exact:
            return exact[(v, k)]

    for cands in (base, stripped):
        pool, seen = [], set()
        for v, k in cands:
            for ent in by_kana.get(k, []) + by_kana.get(v, []):
                if id(ent) not in seen:
                    seen.add(id(ent))
                    pool.append(ent)
        if pool:
            return _best(pool, hint)

    # 假名不可用（如 賛成 的 kana 是乱码）时只用汉字形
    for v, _ in stripped:
        if v in by_kanji:
            return _best(by_kanji[v], hint)
    return None


def _pick_deinflected(v_alts, k_alts, hint, exact, by_kana, by_kanji):
    """在词形还原的候选里挑 JMdict 条目。

    候选是规则猜出来的（起きます → 起く / 起きる），必须由 JMdict 判定：
    优先级为「(汉字,假名) 精确对上」>「汉字形存在」>「读音存在」，
    同级再比常用词与源释义重合度。这样 起きます 会取 起きる（一段）而不是
    同音的 奥，貸します 会取 貸す 而不是 貸する。
    """
    hits = []
    for i, av in enumerate(v_alts):
        ak = k_alts[i] if i < len(k_alts) else ''
        if ak and (av, ak) in exact:
            hits.append((2, exact[(av, ak)]))
        elif av in by_kanji:
            hits.append((1, _best(by_kanji[av], hint)))
        elif ak and ak in by_kana:
            hits.append((0, _best(by_kana[ak], hint)))
    if not hits:
        return None
    rank = max(r for r, _ in hits)
    return _best([e for r, e in hits if r == rank], hint)


def pick_kana(value, raw_kana, ent):
    """定出词条的显示读音。

    以 JMdict 条目为准：源词库的读音只有在能被 JMdict 条目确认时才采用
    （否则会把写进 reading 字段的编者注当读音，如
    じゅうたん →「カーペット」、しいんと →「する」）；确认不了就用
    JMdict 的主读音；连 JMdict 条目都没有时，才退回源词库的假名写法。
    """
    cands = _clean_kana(raw_kana) + _clean_kana(_clean_value(value))
    ent_kana = list(ent['kana']) if ent else []
    if ent_kana:
        for c in cands:
            if any(v in ent_kana for v in _kana_variants(c)):
                return c
        return ent_kana[0]
    for c in cands:
        if is_kana_text(c):
            return c
    return ''


def chinese_for(con, forms, readings):
    """按 (词形, 假名) 精确取中文释义，避免同音异形词串味。

    jadic 的存储约定：汉字词带 reading，假名词的 reading 为空字符串。
    forms/readings 是候选列表，逐个组合尝试。
    """
    tries = []
    for f in forms:
        for r in readings:
            tries.append((f, r))
        tries.append((f, ''))
    out = []
    for w, r in tries:
        if not w:
            continue
        try:
            rows = con.execute(
                'select translation from jmdict where word=? and reading=?',
                (w, r)).fetchall()
        except sqlite3.Error:
            return out
        for (t,) in rows:
            out += [x.strip() for x in t.split('\n') if x.strip()]
        if out:
            break
    # 二级兜底：只认汉字形，不比读音。jadic 里一个汉字词只收部分读音
    # （私 有 わたし/あたし 却没有 わたくし，勘 有 かん 却没有 かんじる），
    # 精确匹配会漏掉这些；按汉字形取不会串到同音异形词上。
    if not out:
        for w in forms:
            if not w:
                continue
            try:
                rows = con.execute(
                    'select translation from jmdict where word=? limit 1',
                    (w,)).fetchall()
            except sqlite3.Error:
                return out
            for (t,) in rows:
                out += [x.strip() for x in t.split('\n') if x.strip()]
            if out:
                break
    return list(dict.fromkeys(out))


def _source_only_entry(word, kana):
    """JMdict 里查不到对应条目时，保留源词库内容，绝不静默丢词。

    典型是 OpenJLPT 收的收尾助词词组（ゆっくりと / 一段と / やたらに）与
    假名写法与 JMdict 不一致的词（ぺん / おまちください / しいんと）。
    这类词条没有 JMdict 的词性与活用，但释义仍在，丢掉才是真的损失。
    """
    return {
        'value': word['value'],
        'kana': kana,
        'romaji': romaji(kana),
        'pos': (word.get('pos') or '').strip(),
        'definition': (word.get('definition') or '').strip(),
        'translation': (word.get('translation') or '').strip(),
        'exchange': '',
        'level': word.get('level', ''),
        '_source_only': True,
    }


def build_entry(word, exact, by_kana, by_kanji, con, keep_source=False):
    value = word['value']
    raw_kana = (word.get('kana') or '').strip()
    hint = word.get('definition', '')
    ent = pick_entry(value, raw_kana or value, hint, exact, by_kana, by_kanji)
    # 教材分类（标准日本语/みんなの日本語/JLPT精选）的假名已是纯假名，原样沿用；
    # JLPT日语 的假名常带编者注，一律按 JMdict 校正。
    kana = raw_kana if (keep_source and is_kana_text(raw_kana)) \
        else pick_kana(value, raw_kana, ent)
    # 教材词库把动词收成 ます形/て形（行きます、歩いて），JMdict 里只有辞書形，
    # 不还原就完全匹配不上（动词的词性、活用、释义全拿不到）。还原出的辞書形
    # 同时用作活用基准形与中文释义的候选词形。
    dict_value = dict_kana = ''
    if ent is None and keep_source:
        v_alts = deinflect(value)
        k_alts = deinflect(kana) if kana else []
        hit = _pick_deinflected(v_alts, k_alts, hint, exact, by_kana, by_kanji)
        if hit:
            ent = hit
            # 辞書形（与假名一一对齐，起きます → 起きる / おきる）
            for i, av in enumerate(v_alts):
                if hit.get('kanji') and av in hit['kanji']:
                    dict_value = av
                    dict_kana = k_alts[i] if i < len(k_alts) else ''
                    break
            if not dict_value:
                for i, av in enumerate(v_alts):
                    if av in hit['kana']:
                        dict_value = av
                        dict_kana = k_alts[i] if i < len(k_alts) else ''
                        break
    if ent is None:
        return _source_only_entry(word, kana)

    codes = []
    for pos, _ in ent['senses']:
        codes += pos
    codes = list(dict.fromkeys(codes))
    # 第一个义项的词性排最前（通常是该词的主要词性）
    primary = pos_names(ent['senses'][0][0])
    names = primary + [n for n in pos_names(codes) if n not in primary]

    en_lines = []
    for pos, gl in ent['senses']:
        pn = pos_names(pos)
        en_lines.append((pn[0] + '. ' if pn else '') + '; '.join(gl))

    # 中文释义：候选词形包括词条写法、去 する 的写法、JMdict 给出的汉字形；
    # 读音同样取候选并集，以覆盖并列写法与假名为空的词条。
    # 中文释义：候选词形按「可靠度」排序——源写法、JMdict 给出的汉字形与读音，
    # 最后才是去尾助词的猜测（尾助词剥出来的写法容易撞上同音异义词，
    # 如 いやに → いや →「否」，所以放最后）。
    forms = list(dict.fromkeys(
        [value] + _split_alt(value)
        + ([value[:-2]] if value.endswith('する') else [])
        + ([dict_value] if dict_value else [])
        + list(ent['kanji']) + list(ent['kana']) + _particle_stems(value)))
    readings = list(dict.fromkeys(
        [r for r in ([kana] + _split_alt(kana) + ([dict_kana] if dict_kana else [])
                     + list(ent['kana']) + _particle_stems(kana)) if r]))
    cn = chinese_for(con, forms, readings or forms)

    # 活用基准形：并列写法取第一个（作る/造る → 作る），否则活用形里的「/」
    # 会被界面当成条目分隔符；する动词补上 する。教材分类的 ます形/て形
    # 一律换成还原出的辞書形（行きます → 行く）。
    base_parts = _split_alt(_clean_value(dict_value or value)) or [value]
    base = base_parts[0]
    # 尾助词不是词的一部分：能与 JMdict 条目对上时，活用基准形也用词干，
    # 否则会按写全的词去活用（よると → 辞書形:よるる、やたらに → やたらにだ）。
    if len(base) > 2 and base.endswith(_TRAILING_PARTICLES):
        stem = base[:-1]
        if stem in ent['kanji'] or stem in ent['kana'] or kana[:-1] in ent['kana']:
            base = stem
    if kana.endswith('する') and not base.endswith('する'):
        base = base + 'する'
    ex = ''
    if conjugatable(codes):
        ex = '/'.join(f'{n}:{f}' for n, f in display_forms(base, codes))

    # 教材分类不覆盖原有内容：词性用 JMdict 的中文全称补/换（原先只有
    # [名]/[动3] 这类括号码），释义与中文只在缺失时补。
    src_pos = (word.get('pos') or '').strip()
    src_def = (word.get('definition') or '').strip()
    src_cn = (word.get('translation') or '').strip()
    pos_text = '、'.join(names) if names else (src_pos if keep_source else '')
    def_text = src_def if (keep_source and src_def) else '\n'.join(en_lines)
    cn_text = src_cn if (keep_source and src_cn) else '\n'.join(cn)

    return {
        'value': value,
        'kana': kana,
        'romaji': romaji(kana),
        'pos': pos_text,
        'definition': def_text,
        'translation': cn_text,
        'exchange': ex,
        'level': word.get('level', ''),
    }


def to_vocabulary(entries, name, level, tag):
    """转成 MuJing 词库 schema（顶层为对象，非裸数组）。"""
    wl = []
    for e in entries:
        wl.append({
            'value': e['value'], 'usphone': '', 'ukphone': '',
            'definition': e['definition'], 'translation': e['translation'],
            'pos': e['pos'], 'collins': 0, 'oxford': False,
            'tag': tag, 'bnc': 0, 'frq': 0,
            'exchange': e['exchange'], 'kana': e['kana'], 'romaji': e['romaji'],
            'level': e.get('level') or level, 'externalCaptions': [], 'captions': [],
        })
    return {
        'name': name, 'type': 'DOCUMENT', 'language': 'japanese',
        'size': len(wl), 'relateVideoPath': '', 'subtitlesTrackId': 0,
        'wordList': wl,
    }


def _write_json(path, obj):
    try:
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(obj, f, ensure_ascii=False, indent=1)
    except OSError as e:
        raise RuntimeError(f"无法写入 {path}：{e}") from e


def run_level(filename, level, out_dir, category='JLPT日语', indexes=None):
    cat = CATEGORIES[category]
    keep_source = cat['keep_source']
    src_dir = os.path.join(VOCAB_ROOT, category)
    exact, by_kana, by_kanji = indexes or load_jmdict()
    con = sqlite3.connect(JADIC)
    src = _read_json(os.path.join(src_dir, filename))
    # 误用检测：输入若已带「中文词性 + . 」前缀，说明是在增强产物上再跑，
    # 同音异形词消歧的线索已被污染（详见模块 docstring 的运行顺序）。
    padded = sum(1 for w in src['wordList']
                 if re.match(r'^[\u4e00-\u9fff、]+\. ', (w.get('definition') or '').split('\n')[0]))
    if padded > len(src['wordList']) * 0.5:
        print(f"  ⚠ {filename} 看起来已经是增强后的文件（{padded}/{len(src['wordList'])} 条）"
              f"，请先重跑 build_vocabulary.py 再增强，否则同音异形词会挑错条目。")
    entries, kept, skipped = [], [], 0
    for w in src['wordList']:
        # 句型/量词卡片（～円、お～、何～／～歳）不是单词，保持原样。
        if keep_source and is_pattern_card(w):
            skipped += 1
            e = dict(w)
            e['level'] = w.get('level', level)
            entries.append(e)
            continue
        e = build_entry(w, exact, by_kana, by_kanji, con, keep_source)
        if e.pop('_source_only', False):
            kept.append(w['value'])
        e['level'] = w.get('level', level)
        entries.append(e)
    con.close()
    total = len(src['wordList'])
    print(f"  {level}: {len(entries)}/{total} 词全部保留"
          f"（{len(kept)} 条无 JMdict 条目，按源数据保留）"
          + (f"，跳过 {skipped} 条句型卡片" if skipped else ""))
    if out_dir:
        # 保留源词库的名称与 tag：UI 上显示的名称不应被本脚本改写。
        # 就地写入时沿用原文件名，另存时用带标记的新文件名。
        src_name = src.get('name') or f'JLPT {level}'
        src_tag = next((w['tag'] for w in src['wordList'] if w.get('tag')),
                       'jlpt-' + level.lower())
        vocab = to_vocabulary(entries, src_name, level, src_tag)
        in_place = os.path.abspath(out_dir) == os.path.abspath(src_dir)
        out_name = filename if in_place else f'{category}-{level}-增强版.json'
        path = os.path.join(out_dir, out_name)
        _write_json(path, vocab)
        print(f"      → {path}")
    return entries, kept


def preview(entries):
    """打印几个代表性词条，供人工检查格式。"""
    for target in ('かっこう', '格好', '食べる', '静か', 'アワー', 'きっと'):
        for e in entries:
            if e['value'] == target:
                print('\n' + '=' * 70)
                print(f"  {e['value']}    {e['romaji']}    [{e['kana']}]")
                print(f"  词性  {e['pos']}")
                if e['exchange']:
                    print(f"  活用  {e['exchange']}")
                print("  英文释义")
                for ln in e['definition'].split('\n'):
                    print(f"      {ln}")
                print("  中文释义")
                for ln in (e['translation'] or '(无)').split('\n'):
                    print(f"      {ln}")
                break


def _parse_args(argv):
    """解析命令行。

    --category <名称>  处理哪个日语分类（默认 JLPT日语）
    --all              处理该分类下全部文件（默认只预览第一个/ N4）
    --write            就地覆盖写回
    --out <目录>       另存到指定目录
    """
    opts = {'all': '--all' in argv, 'write': '--write' in argv, 'out': None,
            'category': 'JLPT日语'}
    if '--category' in argv:
        i = argv.index('--category')
        if i + 1 >= len(argv):
            raise SystemExit('--category 需要一个分类名')
        opts['category'] = argv[i + 1]
    if '--out' in argv:
        i = argv.index('--out')
        if i + 1 >= len(argv):
            raise SystemExit('--out 需要一个目录参数')
        opts['out'] = argv[i + 1]
    return opts


def _category_files(category):
    """取某分类下的文件列表 [(文件名, 级别名)]，按文件名排序（前缀即册序/课序）。"""
    cat = CATEGORIES[category]
    if cat['files'] is not None:
        return cat['files']
    d = os.path.join(VOCAB_ROOT, category)
    names = sorted(os.path.basename(p) for p in glob.glob(os.path.join(d, '*.json')))
    return [(n, os.path.splitext(n)[0]) for n in names]


if __name__ == '__main__':
    opts = _parse_args(sys.argv)
    category = opts['category']
    if category not in CATEGORIES:
        raise SystemExit(f"未知分类 {category}，可选：{'、'.join(CATEGORIES)}")
    files = _category_files(category)
    if opts['all']:
        targets = files
    elif category == 'JLPT日语':
        targets = [('2 N4.json', 'N4')]     # 历史默认：预览 N4
    else:
        targets = files[:1]

    if opts['out']:
        out_dir = os.path.abspath(opts['out'])
        try:
            os.makedirs(out_dir, exist_ok=True)
        except OSError as e:
            raise SystemExit(f"无法创建输出目录 {out_dir}：{e}") from e
    elif opts['write']:
        out_dir = os.path.join(VOCAB_ROOT, category)   # 就地覆盖，脚本产出不手工编辑
    else:
        out_dir = None               # 只预览

    if out_dir is None:
        print('（预览模式，不写入。加 --write 覆盖写回，或 --out <目录> 另存）\n')

    # JMdict 索引只建一次，供该分类下所有文件复用（115MB，逐文件重建太慢）。
    indexes = load_jmdict()
    for fn, lv in targets:
        entries, kept = run_level(fn, lv, out_dir, category, indexes)
        if kept:
            print(f"     无 JMdict 条目（已按源数据保留）: {kept}")
        if out_dir is None and not opts['all']:
            preview(entries)
