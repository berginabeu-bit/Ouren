#!/usr/bin/env python3
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
LANGS = ["en","pt-BR","pt-PT","es","zh-CN","zh-HK","ar","fr","de","it","ja","ko","ms","ru","pl","tr"]
EXPECTED_REMINDERS = 10_000

total = 0
for lang in LANGS:
    path = ASSETS / f"reminders_{lang}.json"
    if not path.is_file():
        raise SystemExit(f"Missing reminder asset: {path.name}")
    data = json.loads(path.read_text(encoding="utf-8"))
    count = sum(len(v) for v in data.values() if isinstance(v, list))
    if count != EXPECTED_REMINDERS:
        raise SystemExit(f"{path.name}: expected {EXPECTED_REMINDERS}, got {count}")
    total += count
print(f"LOCALIZATION AUDIT: PASS — {len(LANGS)} locales, {total} reminder phrases")
