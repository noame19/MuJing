#!/usr/bin/env python3
"""
为「日语语法」词库注入中文释义与接续（tools/japanese/grammar_zh.json）。

为什么单独一个脚本：语法词库（resources/common/vocabulary/日语语法/*.json）的
上游是 OpenJLPT 的 grammar JSON，只有英文的 meaning/formation；而
build_vocabulary.py 的 build_grammar() 每次重建都会用上游数据把这些文件覆盖掉。
因此中文必须由脚本注入，并且要在 build_grammar() 写文件之前调用 apply_zh()，
否则重建一次中文就没了（与「构建期不要重生成词库」是同一类问题）。

字段约定：
  meaning / formation    英文原文（上游）
  meaningZh / formationZh 中文（本表）
显示端（ui/dialog/GrammarDetail.kt）优先用中文，中文为空时回退英文。

用法:
    python3 tools/japanese/enrich_grammar.py [--check] [--dir <语法词库目录>]
"""

import argparse
import json
import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
if _HERE not in sys.path:
    sys.path.insert(0, _HERE)

# 语法词库目录（相对仓库根）：与 JapaneseGrammarMatcher.GRAMMAR_DIR 一致
DEFAULT_GRAMMAR_DIR = os.path.join(
    os.path.dirname(os.path.dirname(_HERE)), "resources", "common", "vocabulary", "日语语法"
)
ZH_FILE = os.path.join(_HERE, "grammar_zh.json")

_ZH_CACHE = None


def load_zh():
    """读取中文释义表（带缓存）。"""
    global _ZH_CACHE
    if _ZH_CACHE is None:
        with open(ZH_FILE, encoding="utf-8") as fh:
            _ZH_CACHE = json.load(fh).get("grammar", {})
    return _ZH_CACHE


def apply_zh(payload, zh=None):
    """
    给一份语法词库 payload 的每条 grammarList 条目补上 meaningZh/formationZh。

    返回 (命中条数, 缺失的 id 列表)。没有中文的条目保持原样（显示端回退英文）。
    幂等：重复调用不会重复加字段，也不会改变已有字段顺序。
    """
    table = load_zh() if zh is None else zh
    hit = 0
    missing = []
    items = []
    for item in payload.get("grammarList", []):
        gid = item.get("id", "")
        zh_entry = table.get(gid) or {}
        meaning_zh = (zh_entry.get("meaning") or "").strip()
        formation_zh = (zh_entry.get("formation") or "").strip()
        if not meaning_zh and not formation_zh:
            missing.append(gid or "<无 id>")
            items.append(item)
            continue
        out = {}
        for key, value in item.items():
            if key in ("meaningZh", "formationZh"):
                continue  # 先跳过旧值，按下面的位置重新插入，保证字段顺序稳定
            out[key] = value
            if key == "meaning" and meaning_zh:
                out["meaningZh"] = meaning_zh
            if key == "formation" and formation_zh:
                out["formationZh"] = formation_zh
        items.append(out)
        hit += 1
    payload["grammarList"] = items
    return hit, missing


def grammar_files(grammar_dir):
    if not os.path.isdir(grammar_dir):
        raise SystemExit(f"语法词库目录不存在：{grammar_dir}")
    return sorted(
        (os.path.join(grammar_dir, name)
         for name in os.listdir(grammar_dir)
         if name.endswith(".json")),
        key=lambda p: os.path.basename(p),
    )


def main(argv=None):
    parser = argparse.ArgumentParser(description="为日语语法词库注入中文释义（grammar_zh.json）")
    parser.add_argument("--check", action="store_true", help="只检查覆盖率，不写文件")
    parser.add_argument("--dir", default=DEFAULT_GRAMMAR_DIR, help="语法词库目录")
    args = parser.parse_args(argv)

    total_hit = 0
    total_missing = []
    for path in grammar_files(args.dir):
        with open(path, encoding="utf-8") as fh:
            payload = json.load(fh)
        hit, missing = apply_zh(payload)
        total_hit += hit
        total_missing += missing
        if not args.check:
            with open(path, "w", encoding="utf-8") as fh:
                json.dump(payload, fh, ensure_ascii=False, indent=4)
        print(f"{os.path.basename(path)}: 注入中文 {hit} 条"
              + (f"，缺中文 {len(missing)} 条：{'、'.join(missing)}" if missing else ""))

    verb = "检查" if args.check else "写入"
    print(f"[grammar] {verb}完成：命中 {total_hit} 条，缺失 {len(total_missing)} 条")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
