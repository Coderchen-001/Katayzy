#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
verify_paths.py — 从 Menu.java 解析真实的菜单挂载树，
与 docs/ui2/feature-inventory.csv 的 path 逐条比对，找出"路径挂错"（跑谱贡献类错误）。

用法: python tools/ui2/verify_paths.py   （仓库根目录运行）
输出: docs/ui2/path-mismatch.csv
"""
import re
import csv
import sys
from collections import defaultdict

MENU_JAVA = "src/main/java/featurecat/lizzie/gui/Menu.java"
PROPS = "src/main/resources/l10n/DisplayStrings_zh_CN.properties"
INV = "docs/ui2/feature-inventory.csv"

TYPES = (r'JFont(?:Menu|MenuItem|CheckBoxMenuItem|RadioButtonMenuItem)'
         r'|JMenu|JMenuItem|JCheckBoxMenuItem|JRadioButtonMenuItem')
NEW_RE = re.compile(
    r'(?:' + TYPES + r')\s+(\w+)\s*=\s*new\s+\w+\s*\(|'
    r'^\s*(?:this\.)?(\w+)\s*=\s*new\s+(?:' + TYPES + r')\s*\(',
    re.M)
GETSTRING_RE = re.compile(r'getString\(\s*"([^"]+)"\s*\)')
ADD_RE = re.compile(r'(?:(\w+)\s*\.\s*)?add\(\s*(\w+)\s*\)')


def strip_line_comment(line: str) -> str:
    in_str = False
    i = 0
    while i < len(line) - 1:
        c = line[i]
        if c == '"':
            in_str = not in_str
        elif not in_str and c == '/' and line[i + 1] == '/':
            return line[:i]
        i += 1
    return line


def load_props(path):
    props = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line or line.startswith("#") or line.startswith("!"):
                continue
            if "=" in line:
                k, _, v = line.partition("=")
                props[k.strip()] = v.strip()
    return props


def unescape(v: str) -> str:
    if "\\u" in v or "\\U" in v:
        try:
            return v.encode("utf-8").decode("unicode_escape")
        except Exception:
            return v
    return v


def norm(s: str) -> str:
    """标签归一化：去尾部快捷键括号、去空白。"""
    s = s.strip()
    s = re.sub(r'\s*[\(\[（【]\s*[A-Za-z0-9+\-\[\]\s]*\s*[\)\]）】]\s*$', '', s)
    s = re.sub(r'\.\.\.$', '', s)
    return s.replace(' ', '').replace('/', '').replace('／', '')


def match(a: str, b: str, strict: bool = False) -> bool:
    na, nb = norm(a), norm(b)
    if not na or not nb:
        return False
    if na == nb:
        return True
    if strict:
        return False
    # 前缀匹配（较短方≥2字符），容忍 CSV 缩写；仅用于链路通过判定
    if len(na) >= 2 and len(nb) >= 2 and (na.startswith(nb) or nb.startswith(na)):
        return True
    return False


def extract_paren(text: str, open_pos: int) -> str:
    """从 open_pos 的 '(' 做括号平衡，返回内部文本（处理字符串内括号）。"""
    depth = 0
    i = open_pos
    in_str = False
    while i < len(text):
        c = text[i]
        if c == '"':
            in_str = not in_str
        elif not in_str:
            if c == '(':
                depth += 1
            elif c == ')':
                depth -= 1
                if depth == 0:
                    return text[open_pos + 1:i]
        i += 1
    return text[open_pos + 1:open_pos + 300]


def parse_menu_java():
    raw_lines = open(MENU_JAVA, encoding="utf-8").read().splitlines()
    clean = [strip_line_comment(l) for l in raw_lines]
    offsets, buf, pos = [], [], 0
    for l in clean:
        offsets.append(pos)
        buf.append(l)
        pos += len(l) + 1
    text = "\n".join(buf)

    def lineno(off):
        lo, hi = 0, len(offsets) - 1
        while lo < hi:
            mid = (lo + hi + 1) // 2
            if offsets[mid] <= off:
                lo = mid
            else:
                hi = mid - 1
        return lo + 1

    var_key = {}
    for m in NEW_RE.finditer(text):
        var = m.group(1) or m.group(2)
        if var is None:
            continue
        open_pos = text.index('(', m.end() - 1)
        args = extract_paren(text, open_pos)
        g = GETSTRING_RE.search(args)
        if g:
            var_key.setdefault(var, g.group(1))
            continue
        # 硬编码字面量（含非 ASCII 的第一个字符串参数）
        lit = re.search(r'"([^"]*)"', args)
        if lit and re.search(r'[^\x00-\x7f]', lit.group(1)):
            var_key.setdefault(var, "#" + lit.group(1))

    edges, bare_adds = [], []
    for m in ADD_RE.finditer(text):
        recv, child = m.group(1), m.group(2)
        if child == "Separator":
            continue
        ln = lineno(m.start())
        if recv in (None, "this"):
            bare_adds.append((child, ln))
        else:
            edges.append((recv, child, ln))
    return var_key, edges, bare_adds


def label_of(var, var_key, props):
    k = var_key.get(var)
    if k is None:
        return None
    if k in props:
        return unescape(props[k])
    return "«" + k + "»"


def main():
    # Windows consoles may default to GBK; Korean/Japanese labels must not crash the verifier.
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass
    props = load_props(PROPS)
    var_key, edges, bare_adds = parse_menu_java()

    print("== 源码真实顶级挂载 ==")
    for v, ln in sorted(set(bare_adds), key=lambda t: t[1]):
        print(f"  L{ln:5d}  {v:24s} label={label_of(v, var_key, props)}")

    edge_by_label = defaultdict(list)
    unresolved = []
    for p, c, ln in edges:
        pl, cl = label_of(p, var_key, props), label_of(c, var_key, props)
        if pl is None or cl is None:
            unresolved.append((p, c, ln, pl, cl))
        else:
            edge_by_label[(pl, cl)].append(ln)

    print(f"\n统计: 变量带标签 {len(var_key)}, 边 {len(edges)}, "
          f"已解析 {sum(len(v) for v in edge_by_label.values())}, 未解析 {len(unresolved)}")

    # ---- 构建真实树并输出 ----
    children = defaultdict(set)
    for (pl, cl) in edge_by_label:
        children[pl].add(cl)
    top_labels = sorted({label_of(v, var_key, props) or v for v, _ in bare_adds})

    def dfs(node, prefix, out, seen):
        kids = sorted(children.get(node, []))
        for k in kids:
            if k in seen:
                out.append(f"{prefix}{k}  (循环引用，截断)")
                continue
            out.append(f"{prefix}{k}")
            dfs(k, prefix + "  ", out, seen | {k})

    tree_lines = []
    for t in top_labels:
        tree_lines.append(t)
        dfs(t, "  ", tree_lines, {t})
    with open("docs/ui2/real-menu-tree.txt", "w", encoding="utf-8") as f:
        f.write("\n".join(tree_lines) + "\n")
    print(f"\n真实树已写入 docs/ui2/real-menu-tree.txt（{len(tree_lines)} 行）")

    # CSV 内部: path 中间段 → 出现过的 label 候选（辅助父级匹配）
    seg_labels = defaultdict(set)
    all_rows = list(csv.DictReader(open(INV, encoding="utf-8-sig")))
    rows = [r for r in all_rows if r["entry_type"] == "menu"]
    respect = "--respect-dispositions" in sys.argv
    exempt_ids = {
        r["id"]
        for r in all_rows
        if respect and (r.get("migration_status") == "no-entry" or r.get("path_exempt"))
    }
    for r in rows:
        parts = [p.strip() for p in r["path"].split(">")]
        for seg in parts:
            seg_labels[seg].add(r["label"])
            seg_labels[seg].add(seg)

    def edge_matches(pseg, lseg, pl, cl, leaf_label):
        p_ok = any(match(pl, cand) for cand in seg_labels.get(pseg, {pseg}))
        l_ok = match(cl, lseg) or (leaf_label and match(cl, leaf_label))
        return p_ok and l_ok

    print("\n== CSV path 与源码挂载比对（仅 menu 行）==")
    problems, exempt_rows, checked, ok, exempt = [], [], 0, 0, 0
    for row in rows:
        parts = [p.strip() for p in row["path"].split(">")]
        if len(parts) < 2:
            continue
        checked += 1
        if row["id"] in exempt_ids:
            exempt += 1
            exempt_rows.append(row)
            continue
        bad = None
        for i in range(len(parts) - 1):
            hit = False
            for (pl, cl), lns in edge_by_label.items():
                if edge_matches(parts[i], parts[i + 1], pl, cl, row["label"]):
                    hit = True
                    break
            if not hit:
                bad = (parts[i], parts[i + 1])
                break
        if bad is None:
            ok += 1
            continue
        leaf = parts[-1]
        real_parents = sorted({pl for (pl, cl) in edge_by_label
                               if match(cl, leaf, strict=True) or match(cl, row["label"], strict=True)})
        problems.append((row["id"], row["path"], bad, real_parents))

    print(f"  校验 {checked} 条多级 path, 通过 {ok}, 豁免 {exempt}, 异常 {len(problems)}")
    print(f"  判据: {checked - exempt} 条需通过, 当前 {ok}/{checked - exempt}")
    for pid, path, bad, real_parents in problems:
        if real_parents:
            print(f"    {pid}: CSV=[{path}]  断于[{bad[0]}>{bad[1]}]  源码真实父级={real_parents}")
        else:
            print(f"    {pid}: CSV=[{path}]  断于[{bad[0]}>{bad[1]}]  源码无此leaf")

    with open("docs/ui2/path-mismatch.csv", "w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f)
        w.writerow(["id", "csv_path", "broken_edge", "real_parents_in_source"])
        for pid, path, bad, real_parents in problems:
            w.writerow([pid, path, " > ".join(bad), " / ".join(real_parents)])
    print("\n清单已写入 docs/ui2/path-mismatch.csv")
    if respect:
        with open("docs/ui2/path-exemptions.csv", "w", encoding="utf-8-sig", newline="") as f:
            w = csv.writer(f)
            w.writerow(["id", "csv_path", "reason"])
            for r in exempt_rows:
                w.writerow([r["id"], r["path"], r.get("path_exempt") or r.get("migration_status")])
        print(f"豁免 {len(exempt_rows)} 条已写入 docs/ui2/path-exemptions.csv")

    print(f"\n== 未解析边（{len(unresolved)} 条，前 40）==")
    for p, c, ln, pl, cl in unresolved[:40]:
        print(f"  L{ln:5d} {p}.add({c})  pl={pl} cl={cl}")


if __name__ == "__main__":
    main()
