# -*- coding: utf-8 -*-
"""
seed_bank.py — 觉醒怪物对白种子库（RECONSTRUCTED 2026-08-23）

说明：原文件（scripts/awakened_dedup/seed_bank.py）在一次清理中被误删且未纳入 git。
本文件按 docs/awakened_dialogue_regeneration_prompts.md 描述的语料结构重建，功能等价：
把按族群分批生成的 AI 对白 JSON（base + pair）收集成种子库，供
dedupe_matrix.py 去重合并，产出互聊矩阵（docs/awakened_peer_dialogue_matrix_generated.md）。

种子 JSON 结构（与生成提示词模板一致）：
{
  "root": "zombie",
  "base": {"ambient": [...], "peer.casual": [...], ...},
  "pair": {"skeleton": {"casual": [...], ...}, ...}
}

用法：
  python seed_bank.py <seeds_dir> [--out bank.json]
  python seed_bank.py --load bank.json --pair zombie skeleton   # 查询
"""

import argparse
import json
import sys
from pathlib import Path

BASE_SCENES = [
    "ambient", "first_meet", "repeat_meet", "request", "gift", "reminder",
    "hostile", "player", "hero",
    "peer.same", "peer.casual", "peer.collab", "peer.gossip",
    "peer.conflict", "peer.scuffle", "peer.authority", "peer.reply",
]
PAIR_SCENES = ["casual", "collab", "gossip", "conflict", "scuffle", "authority"]


class SeedBank:
    """按 (root, target, scene) 索引的对白种子库。"""

    def __init__(self):
        self.base = {}   # root -> {scene: [text]}
        self.pair = {}   # (root, target) -> {scene: [text]}

    def add_seed(self, seed: dict) -> int:
        """合并一份族群种子 JSON，返回新增句子数。"""
        root = str(seed.get("root", "")).strip()
        if not root:
            raise ValueError("seed 缺少 root 字段")
        added = 0
        base = seed.get("base") or {}
        root_base = self.base.setdefault(root, {})
        for scene in BASE_SCENES:
            lines = base.get(scene) or []
            seen = set(root_base.get(scene, []))
            for text in lines:
                text = str(text).strip()
                if text and text not in seen:
                    root_base.setdefault(scene, []).append(text)
                    seen.add(text)
                    added += 1
        pair = seed.get("pair") or {}
        for target, scenes in pair.items():
            target = str(target).strip()
            if not target:
                continue
            key = (root, target)
            root_pair = self.pair.setdefault(key, {})
            for scene in PAIR_SCENES:
                lines = scenes.get(scene) or []
                seen = set(root_pair.get(scene, []))
                for text in lines:
                    text = str(text).strip()
                    if text and text not in seen:
                        root_pair.setdefault(scene, []).append(text)
                        seen.add(text)
                        added += 1
        return added

    def load_dir(self, seeds_dir: Path) -> int:
        """加载目录下所有 *.json 种子文件。"""
        total = 0
        for path in sorted(seeds_dir.glob("*.json")):
            try:
                with path.open("r", encoding="utf-8-sig") as fh:
                    data = json.load(fh)
            except (json.JSONDecodeError, OSError) as exc:
                print(f"[warn] 跳过 {path.name}: {exc}", file=sys.stderr)
                continue
            if isinstance(data, list):
                for seed in data:
                    try:
                        total += self.add_seed(seed)
                    except ValueError as exc:
                        print(f"[warn] {path.name}: {exc}", file=sys.stderr)
            else:
                try:
                    total += self.add_seed(data)
                except ValueError as exc:
                    print(f"[warn] {path.name}: {exc}", file=sys.stderr)
        return total

    def for_pair(self, root: str, target: str) -> dict:
        """返回 (root, target) 的定向组合台词；无定向时回落 root 的通用 peer 台词。"""
        key = (root, target)
        if key in self.pair:
            return dict(self.pair[key])
        # 回落：peer.* 通用场景
        fallback = {}
        base = self.base.get(root, {})
        for scene in PAIR_SCENES:
            lines = base.get(f"peer.{scene}")
            if lines:
                fallback[scene] = list(lines)
        return fallback

    def to_dict(self) -> dict:
        return {"base": self.base, "pair": {f"{r}->{t}": v for (r, t), v in self.pair.items()}}

    @classmethod
    def from_dict(cls, data: dict) -> "SeedBank":
        bank = cls()
        bank.base = data.get("base") or {}
        for key, scenes in (data.get("pair") or {}).items():
            if "->" in key:
                root, target = key.split("->", 1)
            else:
                root, target = key, "*"
            bank.pair[(root, target)] = scenes
        return bank

    def save(self, out_path: Path) -> None:
        out_path.write_text(
            json.dumps(self.to_dict(), ensure_ascii=False, indent=2), encoding="utf-8"
        )


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="觉醒怪物对白种子库")
    parser.add_argument("seeds_dir", nargs="?", help="含 *.json 种子的目录")
    parser.add_argument("--out", default="bank.json", help="输出 bank 文件")
    parser.add_argument("--load", help="加载已有 bank 文件")
    parser.add_argument("--pair", nargs=2, metavar=("ROOT", "TARGET"), help="查询定向组合台词")
    args = parser.parse_args(argv)

    bank = SeedBank()
    if args.load:
        data = json.loads(Path(args.load).read_text(encoding="utf-8"))
        bank = SeedBank.from_dict(data)
    elif args.seeds_dir:
        added = bank.load_dir(Path(args.seeds_dir))
        print(f"已收集 {added} 句去重后对白")
        bank.save(Path(args.out))
        print(f"已写入 {args.out}")
    else:
        parser.print_help()
        return 1

    if args.pair:
        root, target = args.pair
        scenes = bank.for_pair(root, target)
        print(json.dumps({root: {target: scenes}}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
