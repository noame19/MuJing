#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""日语分拍罗马字（モーラ拍）转换 —— 日语词库相关脚本的唯一实现。

本模块由 `build_vocabulary.py`（基础词库）与 `enrich_vocabulary.py`（增强）
共同导入，避免两边各写一份、导致 romaji 写法互相覆盖：

    python3 tools/japanese/build_vocabulary.py     # 基础词库（已产出分拍罗马字）
    python3 tools/japanese/enrich_vocabulary.py    # 以 JMdict 补全词性/活用

规则（按拍/モーラ，连字符分隔）：
    - 拗音（きゃ/しゅ/ちょ…）算 1 拍，合并为 kya / shu / cho
    - 促音「っ」单独占 1 拍，写作后一拍的辅音（かっこう → ka-k-ko-u）
    - 长音「ー」单独占 1 拍，写作前一拍的元音（アワー → a-wa-a）
    - 拨音「ん」单独占 1 拍，写作 n

促音按「每拍一段」方案，故 かっこう = ka-k-ko-u（4 段 = 4 拍），
而非教材常见的 kakkou 或 ka-kko-u。
"""

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


if __name__ == '__main__':
    import sys
    for arg in sys.argv[1:] or ['かっこう', 'マッチ', 'あっ', 'アワー', 'にほんご', 'きょう']:
        print(f'{arg}\t{romaji(arg)}\t{mora_count(arg)} 拍')
