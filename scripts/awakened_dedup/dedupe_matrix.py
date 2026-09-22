# -*- coding: utf-8 -*-
"""
dedupe_matrix.py — 觉醒怪物互聊对白矩阵去重合并（RECONSTRUCTED 2026-08-23）

说明：原文件（scripts/awakened_dedup/dedupe_matrix.py）在一次清理中被误删且未纳入 git。
本文件按 docs/awakened_dialogue_regeneration_prompts.md 的输出硬规则与
docs/awakened_peer_dialogue_matrix_generated.md 的最终格式重建：

- 读入 seed_bank 产出的 bank.json（或一批种子 JSON）；
- 按 (root, target, scene) 精确去重（同场景同文本只保留一条）；
- 每场景至少保留 2 句（不足时用通用 peer 回落补齐，宁缺毋滥）；
- 输出与仓库一致格式的 markdown：```json { "root": { "target": { "scene": [...] } } } ```。

用法：
  python dedupe_matrix.py --bank bank.json --out docs/awakened_peer_dialogue_matrix_generated.md
  python dedupe_matrix.py --seeds seeds_dir --out matrix.md
"""

import argparse
import json
import sys
from collections import defaultdict
from pathlib import Path

PAIR_SCENES = ["casual", "collab", "gossip", "conflict", "scuffle", "authority"]
MIN_PER_SCENE = 2


def load_bank(args) -> dict:
    if args.bank:
        data = json.loads(Path(args.bank).read_text(encoding="utf-8-sig"))
        base = data.get("base") or {}
        pair = {}
        for key, scenes in (data.get("pair") or {}).items():
            if "->" in key:
                root, target = key.split("->", 1)
            else:
                root, target = key, "*"
            pair[(root, target)] = scenes
        return {"base": base, "pair": pair}
    if args.seeds:
        from seed_bank import SeedBank
        bank = SeedBank()
        bank.load_dir(Path(args.seeds))
        dumped = bank.to_dict()
        pair = {}
        for key, scenes in (dumped.get("pair") or {}).items():
            if "->" in key:
                root, target = key.split("->", 1)
            else:
                root, target = key, "*"
            pair[(root, target)] = scenes
        return {"base": dumped.get("base") or {}, "pair": pair}
    raise SystemExit("必须提供 --bank 或 --seeds")


def dedupe_lines(lines):
    seen = set()
    out = []
    for text in lines:
        text = str(text).strip()
        if text and text not in seen:
            seen.add(text)
            out.append(text)
    return out


def build_matrix(data: dict) -> dict:
    base = data.get("base") or {}
    pair = data.get("pair") or {}
    matrix = defaultdict(lambda: defaultdict(dict))
    for (root, target), scenes in pair.items():
        for scene in PAIR_SCENES:
            lines = dedupe_lines(scenes.get(scene) or [])
            if not lines:
                # 定向不足时回落该 root 的通用 peer 台词
                fallback = dedupe_lines(base.get(root, {}).get(f"peer.{scene}") or [])
                lines = fallback[:MIN_PER_SCENE]
            matrix[root][target][scene] = lines
    return {root: dict(targets) for root, targets in matrix.items()}


def write_matrix_md(matrix: dict, out_path: Path) -> None:
    body = json.dumps(matrix, ensure_ascii=False, indent=2)
    text = "# 觉醒怪物全族群互聊对白矩阵\n\n```json\n" + body + "\n```\n"
    out_path.write_text(text, encoding="utf-8")


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="觉醒怪物互聊对白矩阵去重合并")
    parser.add_argument("--bank", help="seed_bank.py 产出的 bank.json")
    parser.add_argument("--seeds", help="原始种子 JSON 目录（使用 seed_bank 收集）")
    parser.add_argument("--out", default="docs/awakened_peer_dialogue_matrix_generated.md",
                        help="输出矩阵 markdown 路径")
    args = parser.parse_args(argv)

    data = load_bank(args)
    matrix = build_matrix(data)
    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    write_matrix_md(matrix, out_path)
    total = sum(len(lines) for root in matrix.values() for t in root.values()
                for lines in t.values())
    print(f"矩阵已写入 {out_path}，共 {total} 句（去重后）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
