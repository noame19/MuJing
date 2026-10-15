"""MuJing 日语词库增强（JLPT N5–N1）。

在 build_vocabulary.py 产出的基础词库之上，补全：
  - pos        词性（中文全称，如「动词·一段词」「い形容词」）
  - romaji     分拍罗马字（かっこう → ka-k-ko-u）
  - exchange   动词/形容词活用（辞書形:食べる/て形:食べて/…）
  - definition 英文释义（按义项分条，带词性前缀）
  - translation 中文释义（按 (词形, 读音) 精确匹配，避免同音异形词串味）

数据源（均为权威词典，不做规则推测）：
  - JMdict (scriptin/jmdict-simplified)    品词标记、义项、英文释义
  - JMdictDB conjo.csv (EDRDG / Jim Breen)  动词/形容词活用表（1137 条规则）
  - resources/common/dictionary/jadic.db   中文释义

同音异形词隔离：按 (汉字形, 假名) 精确匹配，再以该汉字形查 jadic。
例如 かっこう 只取「格好」，不会把「滑降（滑雪）」「郭公（布谷鸟）」混进来。

用法：
    python3 tools/japanese/enrich_vocabulary.py            # 预览 N4（不写入）
    python3 tools/japanese/enrich_vocabulary.py --all      # 预览全部 5 个级别
    python3 tools/japanese/enrich_vocabulary.py --all --write   # 覆盖写回词库
    python3 tools/japanese/enrich_vocabulary.py --all --out <目录>  # 另存到指定目录

首次运行前需先下载数据源（约 111MB，不入库）：
    python3 tools/japanese/fetch_dict_data.py
"""

import csv
import json
import os
import re
import sqlite3
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(_HERE, 'data')
# 仓库根：本文件位于 <repo>/tools/japanese/，上两级即仓库根
REPO = os.path.abspath(os.path.join(_HERE, '..', '..'))
JMDICT = os.path.join(DATA, 'jmdict-eng-3.6.2.json')
JADIC = os.path.join(REPO, 'resources', 'common', 'dictionary', 'jadic.db')
VOCAB_DIR = os.path.join(REPO, 'resources', 'common', 'vocabulary', 'JLPT日语')
LEVELS = [('1 N5.json', 'N5'), ('2 N4.json', 'N4'), ('3 N3.json', 'N3'),
          ('4 N2.json', 'N2'), ('5 N1.json', 'N1')]


# ══════════════════════════════════════════════════════════════════
# 一、分拍罗马字
#
# 规则（按拍/モーラ，连字符分隔）：
#   - 拗音（きゃ/しゅ/ちょ…）算 1 拍，合并为 kya / shu / cho
#   - 促音「っ」单独占 1 拍，写作后一拍的辅音（かっこう → ka-k-ko-u）
#   - 长音「ー」单独占 1 拍，写作前一拍的元音（アワー → a-wa-a）
#   - 拨音「ん」单独占 1 拍，写作 n
#
# 促音按「每拍一段」方案，故 かっこう = ka-k-ko-u（4 段 = 4 拍），
# 而非教材常见的 kakkou 或 ka-kko-u。
# ══════════════════════════════════════════════════════════════════

BASE = {
    'あ': 'a', 'い': 'i', 'う': 'u', 'え': 'e', 'お': 'o',
    'か': 'ka', 'き': 'ki', 'く': 'ku', 'け': 'ke', 'こ': 'ko',
    'さ': 'sa', 'し': 'shi', 'す': 'su', 'せ': 'se', 'そ': 'so',
    'た': 'ta', 'ち': 'chi', 'つ': 'tsu', 'て': 'te', 'と': 'to',
    'な': 'na', 'に': 'ni', 'ぬ': 'nu', 'ね': 'ne', 'の': 'no',
    'は': 'ha', 'ひ': 'hi', 'ふ': 'fu', 'へ': 'he', 'ほ': 'ho',
    'ま': 'ma', 'み': 'mi', 'む': 'mu', 'め': 'me', 'も': 'mo',
    'や': 'ya', 'ゆ': 'yu', 'よ': 'yo',
    'ら': 'ra', 'り': 'ri', 'る': 'ru', 'れ': 're', 'ろ': 'ro',
    'わ': 'wa', 'ゐ': 'i', 'ゑ': 'e', 'を': 'o', 'ん': 'n',
    'が': 'ga', 'ぎ': 'gi', 'ぐ': 'gu', 'げ': 'ge', 'ご': 'go',
    'ざ': 'za', 'じ': 'ji', 'ず': 'zu', 'ぜ': 'ze', 'ぞ': 'zo',
    'だ': 'da', 'ぢ': 'ji', 'づ': 'zu', 'で': 'de', 'ど': 'do',
    'ば': 'ba', 'び': 'bi', 'ぶ': 'bu', 'べ': 'be', 'ぼ': 'bo',
    'ぱ': 'pa', 'ぴ': 'pi', 'ぷ': 'pu', 'ぺ': 'pe', 'ぽ': 'po',
    'ゔ': 'vu',
    'ぁ': 'a', 'ぃ': 'i', 'ぅ': 'u', 'ぇ': 'e', 'ぉ': 'o',
}

YOON = {
    'きゃ': 'kya', 'きゅ': 'kyu', 'きょ': 'kyo',
    'しゃ': 'sha', 'しゅ': 'shu', 'しょ': 'sho',
    'ちゃ': 'cha', 'ちゅ': 'chu', 'ちょ': 'cho',
    'にゃ': 'nya', 'にゅ': 'nyu', 'にょ': 'nyo',
    'ひゃ': 'hya', 'ひゅ': 'hyu', 'ひょ': 'hyo',
    'みゃ': 'mya', 'みゅ': 'myu', 'みょ': 'myo',
    'りゃ': 'rya', 'りゅ': 'ryu', 'りょ': 'ryo',
    'ぎゃ': 'gya', 'ぎゅ': 'gyu', 'ぎょ': 'gyo',
    'じゃ': 'ja', 'じゅ': 'ju', 'じょ': 'jo',
    'びゃ': 'bya', 'びゅ': 'byu', 'びょ': 'byo',
    'ぴゃ': 'pya', 'ぴゅ': 'pyu', 'ぴょ': 'pyo',
    'ふぁ': 'fa', 'ふぃ': 'fi', 'ふぇ': 'fe', 'ふぉ': 'fo', 'ふゅ': 'fyu',
    'てぃ': 'ti', 'でぃ': 'di', 'とぅ': 'tu', 'どぅ': 'du',
    'しぇ': 'she', 'じぇ': 'je', 'ちぇ': 'che',
    'うぇ': 'we', 'うぃ': 'wi', 'うぉ': 'wo',
    'ゔぁ': 'va', 'ゔぃ': 'vi', 'ゔぇ': 've', 'ゔぉ': 'vo',
    'くぁ': 'kwa', 'ぐぁ': 'gwa',
    'つぁ': 'tsa', 'つぃ': 'tsi', 'つぇ': 'tse', 'つぉ': 'tso',
    'すぃ': 'si',
}

SOKUON = 'っ'
CHOON = 'ー'
SKIP = '・、。「」（）()[]　 '
VOWELS = set('aiueo')

_KATA2HIRA = {chr(i): chr(i - 0x60) for i in range(0x30A1, 0x30FF)}
_KATA2HIRA[CHOON] = CHOON
_KATA2HIRA['・'] = '・'


def _vowel_of(romaji_text):
    for ch in reversed(romaji_text):
        if ch in VOWELS:
            return ch
    return 'a' if romaji_text else ''


def _consonant_of(romaji_text):
    for ch in romaji_text:
        if ch not in VOWELS:
            return ch
    return ''


def _sokuon_marker(next_romaji):
    """促音 っ 的占位字母。

    Hepburn 规定 っち 写作 tchi（如 マッチ → ma-t-chi），
    故 ch 开头时用 t，而非 chi 的首字母 c。

    词尾的 っ（如「あっ」）没有后续辅音可借，用 ' 表示喉塞音，
    以保持「一段 = 一拍」的一致性（あっ → a-'）。
    """
    if not next_romaji:
        return "'"
    if next_romaji.startswith('ch'):
        return 't'
    return _consonant_of(next_romaji) or "'"


def to_morae(kana):
    """把假名串切成拍列表 [(拍文本, 罗马字)]，未识别字符原样保留。"""
    if not kana:
        return []
    hira = ''.join(_KATA2HIRA.get(c) or c for c in kana)
    out, i = [], 0
    while i < len(hira):
        c = hira[i]
        if c in SKIP:
            i += 1
            continue
        if i + 1 < len(hira) and hira[i:i + 2] in YOON:
            out.append((hira[i:i + 2], YOON[hira[i:i + 2]]))
            i += 2
            continue
        if c == SOKUON:
            nxt = ''
            j = i + 1
            while j < len(hira):
                if j + 1 < len(hira) and hira[j:j + 2] in YOON:
                    nxt = YOON[hira[j:j + 2]]
                    break
                if hira[j] in BASE:
                    nxt = BASE[hira[j]]
                    break
                j += 1
            out.append((SOKUON, _sokuon_marker(nxt)))
            i += 1
            continue
        if c == CHOON:
            out.append((CHOON, _vowel_of(out[-1][1] if out else '')))
            i += 1
            continue
        if c in BASE:
            out.append((c, BASE[c]))
            i += 1
            continue
        out.append((c, c))
        i += 1
    return out


def romaji(kana):
    """分拍罗马字，如 かっこう → ka-k-ko-u。"""
    return '-'.join(r for _, r in to_morae(kana) if r)


def mora_count(kana):
    return len(to_morae(kana))


# ══════════════════════════════════════════════════════════════════
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
        ('意志形', '{s}しよう'), ('命令形', '{s}しろ'), ('可能形', 'できます'),
        ('受身形', '{s}される'), ('使役形', '{s}させる')]

# カ変：来 的读音 く/き/こ 不规则，逐形写死。stem 已去掉结尾「る」
KURU = {'辞書形': '{w}る', 'ます形': '{w}ます', 'ない形': '{w}ない', 'て形': '{w}て',
        '過去形': '{w}た', 'ば形': '{w}れば', '意志形': '{w}よう', '命令形': 'こい',
        '可能形': '{w}られる', '受身形': '{w}られる', '使役形': '{w}させる'}

# な形容词：官方 conjo.csv 只有「辞書形 だ」一条，缺 な/に。
# 这 3 形是 100% 规则的语法派生（な形容词无例外词），不属于词条级的
# 活用分类猜测（帰る/走る 那类风险），故在官方表之外补全。
# 动词一律不用此方式 —— 动词活用全部来自 conjo.csv。
NA_ADJ = [('辞書形', 'だ'), ('連体形', 'な'), ('連用形', 'に')]


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
        ent = {'kanji': kanji, 'kana': kana, 'senses': senses}
        for k in kanji:
            for r in kana:
                exact.setdefault((k, r), ent)
            by_kanji.setdefault(k, []).append(ent)
        for r in kana:
            by_kana.setdefault(r, []).append(ent)
    return exact, by_kana, by_kanji


def _split_alt(text):
    """拆分并列写法（けれど/けれども → [けれど, けれども]）。"""
    parts = [p.strip() for p in re.split(r'[/／]', text or '') if p.strip()]
    return parts


def _clean_kana(kana):
    """清洗词库中不规范的假名写法，返回读音候选列表。

      - あたたか(い)      → あたたかい     （括号只是排版，内容属于读音）
      - じゅう とお       → じゅう / とお   （空格分隔的多读音）
      - かねもち/おかねもち → 两个候选      （斜杠并列）
    """
    s = re.sub(r'[()（）]', '', kana or '')
    return [p.strip() for p in re.split(r'[\s/／]+', s) if p.strip()]


def _clean_value(value):
    """去掉词形尾部的说明性括注，如「あげる (=やる)」→「あげる」。

    与假名不同：词形里的括注是编者注（同义词、说明），不属于词本身。
    """
    v = value or ''
    return re.sub(r'\s*[（(][^)）]*[)）]\s*$', '', v).strip() or v.strip()


def _match_candidates(value, kana):
    """生成 (词形, 假名) 匹配候选，覆盖词库里几种非常规写法。

      - 案内 / あんないする        → 假名带 する，去掉后为 あんない
      - あいさつする / あいさつする   → 词形带 する，去掉后为 あいさつ
      - お金持ち / かねもち/おかねもち  → 假名有并列，逐个试
      - あたたか(い)              → 去括号后为 あたたかい
      - あげる (=やる) / あげる     → 词形的括注是编者注，要去掉

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
    return out


def pick_entry(value, kana, hint, exact, by_kana, by_kanji):
    """挑出与词条对应的 JMdict 条目。

    汉字形优先按 (汉字, 假名) 精确匹配；假名形（同音异形词有歧义）
    用词库原有的英文释义打分挑最贴近的。假名不可用（词库里有乱码）时，
    退回只用汉字形匹配。
    """
    cands = _match_candidates(value, kana)
    for v, k in cands:
        if v != k and (v, k) in exact:
            return exact[(v, k)]
    pool, seen = [], set()

    def _add(entries):
        for ent in entries:
            if id(ent) not in seen:
                seen.add(id(ent))
                pool.append(ent)

    for v, k in cands:
        _add(by_kana.get(k, []))
        _add(by_kana.get(v, []))
    if not pool:
        # 假名不可用（如 賛成 的 kana 是乱码）时只用汉字形
        for v, _ in cands:
            _add(by_kanji.get(v, []))
    if not pool:
        return None
    if len(pool) == 1:
        return pool[0]
    hint_words = set(re.findall(r'[a-z]+', (hint or '').lower()))
    best, best_score = pool[0], -1
    for c in pool:
        gloss = ' '.join(g for _, gl in c['senses'] for g in gl).lower()
        score = sum(1 for w in hint_words if w and w in gloss)
        if score > best_score:
            best, best_score = c, score
    return best


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
    return list(dict.fromkeys(out))


def build_entry(word, exact, by_kana, by_kanji, con):
    value = word['value']
    kana = (word.get('kana') or '').strip() or value
    ent = pick_entry(value, kana, word.get('definition', ''), exact, by_kana, by_kanji)
    if ent is None:
        return None

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
    forms = list(dict.fromkeys([value] + _split_alt(value) + list(ent['kanji'])))
    if value.endswith('する'):
        forms.append(value[:-2])
    readings = list(dict.fromkeys(
        [r for r in ([kana] + _split_alt(kana) + list(ent['kana'])) if r]))
    cn = chinese_for(con, forms, readings or forms)

    # 活用基准形：并列写法取第一个（作る/造る → 作る），否则活用形里的「/」
    # 会被界面当成条目分隔符；する动词补上 する。
    base_parts = _split_alt(_clean_value(value)) or [value]
    base = base_parts[0]
    if kana.endswith('する') and not base.endswith('する'):
        base = base + 'する'
    ex = ''
    if conjugatable(codes):
        ex = '/'.join(f'{n}:{f}' for n, f in display_forms(base, codes))

    return {
        'value': value,
        'kana': kana,
        'romaji': romaji(kana),
        'pos': '、'.join(names),
        'definition': '\n'.join(en_lines),
        'translation': '\n'.join(cn),
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
            'level': level, 'externalCaptions': [], 'captions': [],
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


def run_level(filename, level, out_dir):
    exact, by_kana, by_kanji = load_jmdict()
    con = sqlite3.connect(JADIC)
    src = _read_json(os.path.join(VOCAB_DIR, filename))
    entries, miss = [], []
    for w in src['wordList']:
        e = build_entry(w, exact, by_kana, by_kanji, con)
        if e is None:
            miss.append(w['value'])
            continue
        e['level'] = w.get('level', level)
        entries.append(e)
    con.close()
    total = len(src['wordList'])
    print(f"  {level}: 匹配 {len(entries)}/{total} ({len(entries) * 100 // total}%)"
          f"  未匹配 {len(miss)}")
    if out_dir:
        # 保留源词库的名称与 tag：UI 上显示的名称不应被本脚本改写。
        # 就地写入时沿用原文件名，另存时用带标记的新文件名。
        src_name = src.get('name') or f'JLPT {level}'
        src_tag = next((w['tag'] for w in src['wordList'] if w.get('tag')),
                       'jlpt-' + level.lower())
        vocab = to_vocabulary(entries, src_name, level, src_tag)
        in_place = os.path.abspath(out_dir) == os.path.abspath(VOCAB_DIR)
        out_name = filename if in_place else f'JLPT-{level}-增强版.json'
        path = os.path.join(out_dir, out_name)
        _write_json(path, vocab)
        print(f"      → {path}")
    return entries, miss


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
    """解析命令行：--all 处理全部级别；--write 覆盖写回；--out 另存目录。"""
    opts = {'all': '--all' in argv, 'write': '--write' in argv, 'out': None}
    if '--out' in argv:
        i = argv.index('--out')
        if i + 1 >= len(argv):
            raise SystemExit('--out 需要一个目录参数')
        opts['out'] = argv[i + 1]
    return opts


if __name__ == '__main__':
    opts = _parse_args(sys.argv)
    targets = LEVELS if opts['all'] else [('2 N4.json', 'N4')]

    if opts['out']:
        out_dir = os.path.abspath(opts['out'])
        try:
            os.makedirs(out_dir, exist_ok=True)
        except OSError as e:
            raise SystemExit(f"无法创建输出目录 {out_dir}：{e}") from e
    elif opts['write']:
        out_dir = VOCAB_DIR          # 就地覆盖，脚本产出的词库不手工编辑
    else:
        out_dir = None               # 只预览

    if out_dir is None:
        print('（预览模式，不写入。加 --write 覆盖写回，或 --out <目录> 另存）\n')

    for fn, lv in targets:
        entries, miss = run_level(fn, lv, out_dir)
        if miss:
            print(f"     未匹配: {miss}")
        if out_dir is None and not opts['all']:
            preview(entries)
