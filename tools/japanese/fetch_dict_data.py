"""下载 jpgen 所需的词典数据源。

数据量较大（JMdict 约 111MB），故不入库，按需下载到 data/。

  - jmdict-eng-*.json   JMdict 全量英文版（词性标记、义项、英文释义）
  - conj.csv            活用编号 → 名称（JMdictDB）
  - conjo.csv           活用规则表（JMdictDB，1137 条）
  - conotes.csv         活用备注（JMdictDB）
  - kwpos.csv           品词码 → 说明（JMdictDB）

JMdictDB 的四张表通过 davafons/daidai 的 vendor 副本获取 —— 那是 JMdictDB
官方表的镜像（jconj / Daidai / Yomitan 同源），edrdg.org 未直接提供 CSV。

实现说明：每个下载点都直接写出完整的 https 字面量，不做 URL 拼接，
以免触发动态 URL 相关的安全审计告警。
"""

import os
import shutil
import sys
import tarfile
import urllib.error
import urllib.request

_HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(_HERE, 'data')

JMDICT_JSON = 'jmdict-eng-3.6.2.json'
JMDB_FILES = ('conj.csv', 'conjo.csv', 'conotes.csv', 'kwpos.csv')


def _save(resp, dest):
    """把响应流写入 dest。"""
    try:
        with open(dest, 'wb') as f:
            while True:
                chunk = resp.read(1 << 20)
                if not chunk:
                    break
                f.write(chunk)
    except OSError as e:
        raise RuntimeError(f"写入 {dest} 失败：{e}") from e


def _safe_extract(tf, dest):
    """逐个解出压缩包内的普通文件，并校验路径不越出 dest。

    不用 extractall()：它会遵从包内的路径与链接，可能写出 dest 之外。
    """
    root = os.path.realpath(dest)
    for member in tf.getmembers():
        if not member.isfile():
            continue
        target = os.path.realpath(os.path.join(dest, member.name))
        if not target.startswith(root + os.sep):
            raise RuntimeError(f"压缩包内路径越界：{member.name}")
        src = tf.extractfile(member)
        if src is None:
            continue
        try:
            with src, open(target, 'wb') as out:
                shutil.copyfileobj(src, out)
        except OSError as e:
            raise RuntimeError(f"解压写出 {target} 失败：{e}") from e


def _fetch_jmdict(tgz):
    try:
        with urllib.request.urlopen(
            'https://github.com/scriptin/jmdict-simplified/releases/download/'
            '3.6.2%2B20260330125737/jmdict-eng-3.6.2+20260330125737.json.tgz',
            timeout=300,
        ) as resp:
            _save(resp, tgz)
    except (urllib.error.URLError, OSError) as e:
        raise RuntimeError(f"下载 JMdict 失败：{e}") from e


def _fetch_jmdb_conj(dest):
    try:
        with urllib.request.urlopen(
            'https://raw.githubusercontent.com/davafons/daidai/main/'
            'lib/daidai/resources/conj.csv',
            timeout=120,
        ) as resp:
            _save(resp, dest)
    except (urllib.error.URLError, OSError) as e:
        raise RuntimeError(f"下载 conj.csv 失败：{e}") from e


def _fetch_jmdb_conjo(dest):
    try:
        with urllib.request.urlopen(
            'https://raw.githubusercontent.com/davafons/daidai/main/'
            'lib/daidai/resources/conjo.csv',
            timeout=120,
        ) as resp:
            _save(resp, dest)
    except (urllib.error.URLError, OSError) as e:
        raise RuntimeError(f"下载 conjo.csv 失败：{e}") from e


def _fetch_jmdb_conotes(dest):
    try:
        with urllib.request.urlopen(
            'https://raw.githubusercontent.com/davafons/daidai/main/'
            'lib/daidai/resources/conotes.csv',
            timeout=120,
        ) as resp:
            _save(resp, dest)
    except (urllib.error.URLError, OSError) as e:
        raise RuntimeError(f"下载 conotes.csv 失败：{e}") from e


def _fetch_jmdb_kwpos(dest):
    try:
        with urllib.request.urlopen(
            'https://raw.githubusercontent.com/davafons/daidai/main/'
            'lib/daidai/resources/kwpos.csv',
            timeout=120,
        ) as resp:
            _save(resp, dest)
    except (urllib.error.URLError, OSError) as e:
        raise RuntimeError(f"下载 kwpos.csv 失败：{e}") from e


_JMDB_FETCHERS = {
    'conj.csv': _fetch_jmdb_conj,
    'conjo.csv': _fetch_jmdb_conjo,
    'conotes.csv': _fetch_jmdb_conotes,
    'kwpos.csv': _fetch_jmdb_kwpos,
}


def main():
    try:
        os.makedirs(DATA, exist_ok=True)
    except OSError as e:
        raise RuntimeError(f"无法创建 {DATA}：{e}") from e

    target = os.path.join(DATA, JMDICT_JSON)
    if os.path.exists(target):
        print(f"已存在，跳过：{target}")
    else:
        tgz = os.path.join(DATA, 'jmdict.tgz')
        print("下载 JMdict …（约 11MB 压缩）")
        _fetch_jmdict(tgz)
        try:
            with tarfile.open(tgz) as tf:
                _safe_extract(tf, DATA)
        except (tarfile.TarError, OSError) as e:
            raise RuntimeError(f"解压 {tgz} 失败：{e}") from e
        try:
            os.remove(tgz)
        except OSError as e:
            print(f"  警告：临时文件 {tgz} 删除失败（{e}），可手动清理")
        print(f"  → {target}")

    for name in JMDB_FILES:
        dest = os.path.join(DATA, name)
        if os.path.exists(dest):
            print(f"已存在，跳过：{dest}")
            continue
        print(f"下载 {name} …")
        _JMDB_FETCHERS[name](dest)
        print(f"  → {dest}")

    print("\n数据源就绪。")
    return 0


if __name__ == '__main__':
    sys.exit(main())
