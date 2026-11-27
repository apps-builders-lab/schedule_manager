#!/usr/bin/env python3
"""文言家規の検査: repo のテキストファイルに簡体字が1字でもあれば file:line を列挙して exit 1。

簡体字表は \\uXXXX エスケープでだけ埋め込む(文字そのものを置くと、このスクリプト自身が検査に掛かる)。
標準ライブラリのみ。repo ルートはこのスクリプトの親ディレクトリの親。
"""
import os
import sys

# 検収側の正本の複製。
SIMPLIFIED = SIMPLIFIED = (
    "\u4E1A \u4E1C \u4E3A \u4E49 \u4E50 \u4E60 "
    "\u4E66 \u4E70 \u4ECE \u4EE4 \u4EEC \u4F18 "
    "\u5173 \u51B3 \u51B5 \u521B \u52A8 \u52BF "
    "\u5355 \u5356 \u5386 \u53D1 \u53D8 \u56FE "
    "\u5706 \u573A \u5904 \u5907 \u591F \u5B83 "
    "\u5B9E \u5BF9 \u5BFC \u5C42 \u5E94 \u5F00 "
    "\u5F39 \u5F55 \u6001 \u6267 \u62A5 \u62E9 "
    "\u65E0 \u65F6 \u672F \u6784 \u67E5 \u6837 "
    "\u68C0 \u6BCF \u6BD5 \u6C14 \u6C49 \u6D4B "
    "\u73AF \u73B0 \u76D1 \u78B0 \u7B14 \u7B80 "
    "\u7EA2 \u7EA7 \u7EB3 \u7ECF \u7ED9 \u7EDF "
    "\u7EED \u7EF4 \u7EFF \u7F51 \u84DD \u867D "
    "\u89C1 \u89C4 \u89C6 \u8BA8 \u8BB0 \u8BBA "
    "\u8BBE \u8BC1 \u8BC6 \u8BCD \u8BD5 \u8BDD "
    "\u8BE2 \u8BED \u8BF4 \u8BFB \u8C01 \u8C03 "
    "\u8F66 \u8F6C \u8F93 \u8FB9 \u8FBE \u8FC7 "
    "\u8FD0 \u8FD8 \u8FD9 \u8FDB \u8FDC \u8FDE "
    "\u9012 \u9519 \u95E8 \u95EE \u95F4 \u96BE "
    "\u9879 \u987A \u9898 \u98CE \u98DE \u9A8C "
    "\u9E1F"
).split()

CHARS = frozenset(SIMPLIFIED)
EXTENSIONS = (".kt", ".kts", ".md", ".xml", ".yml", ".properties", ".toml", ".golden")
EXCLUDED_DIRS = {".git", "build", ".gradle"}


def scan(root: str, self_path: str) -> list[str]:
    hits = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = sorted(d for d in dirnames if d not in EXCLUDED_DIRS)
        for name in sorted(filenames):
            if not name.endswith(EXTENSIONS):
                continue
            path = os.path.join(dirpath, name)
            if os.path.abspath(path) == self_path:
                continue
            try:
                with open(path, encoding="utf-8") as f:
                    lines = f.read().splitlines()
            except (OSError, UnicodeDecodeError):
                continue  # 読めないファイルは読み飛ばす
            rel = os.path.relpath(path, root)
            for number, line in enumerate(lines, start=1):
                found = sorted({c for c in line if c in CHARS})
                if found:
                    codes = " ".join("U+%04X" % ord(c) for c in found)
                    hits.append("%s:%d: %s" % (rel, number, codes))
    return hits


def main() -> int:
    self_path = os.path.abspath(__file__)
    root = os.path.dirname(os.path.dirname(self_path))
    hits = scan(root, self_path)
    for hit in hits:
        print(hit)
    if hits:
        print("simplified characters found: %d line(s)" % len(hits))
        return 1
    print("ok: no simplified characters")
    return 0


if __name__ == "__main__":
    sys.exit(main())
