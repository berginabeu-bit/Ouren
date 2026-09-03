#!/usr/bin/env python3
from pathlib import Path
import json,re,sys
ROOT=Path(__file__).resolve().parents[1]
errors=[]; warnings=[]
def read(rel):
 p=ROOT/rel
 if not p.exists(): errors.append(f"Missing: {rel}"); return ""
 return p.read_text(encoding="utf-8",errors="replace")
manifest=read("app/src/main/AndroidManifest.xml"); main=read("app/src/main/java/com/focusedmind/app/MainActivity.kt")
alarm=read("app/src/main/java/com/focusedmind/app/AlarmReceiver.kt"); build=read("app/build.gradle.kts"); langs=read("app/src/main/java/com/focusedmind/app/LanguageManager.kt")
if 'android:name=".MainActivity"' not in manifest and 'android:name="com.focusedmind.app.MainActivity"' not in manifest:
 errors.append("Manifest does not declare the canonical MainActivity")
if 'package com.focusedmind.app' not in main:
 errors.append("MainActivity package mismatch")
if 'namespace = "com.focusedmind.app"' not in build: errors.append("Gradle namespace mismatch")
if 'applicationId = "com.focusedmind.app"' not in build: errors.append("Gradle applicationId mismatch")
for needle,msg in [
 ('AlarmReceiver.STAGE_TEN to commitment.timestamp - 10 * 60_000L','10-minute reminder scheduling missing'),
 ('AlarmReceiver.STAGE_EXACT to commitment.timestamp','exact-time reminder scheduling missing'),
 ('AlarmReceiver.STAGE_EXPIRE to commitment.timestamp + FocusedMindStore.RESPONSE_WINDOW_MS','silent expiry timer missing'),
 ('stage != STAGE_TEN && stage != STAGE_EXACT','expiry stage is not blocked from notification creation')]:
 if needle not in alarm: errors.append(msg)
if 'STAGE_EXPIRE' in alarm and '.setContentTitle' in alarm and 'stage != STAGE_TEN && stage != STAGE_EXACT' not in alarm:
 errors.append("Invalid stage can create notification")
if 'next?.let { AlarmScheduler.schedule(context, it) }' not in read('app/src/main/java/com/focusedmind/app/FocusedMindData.kt'):
 warnings.append("Review recurrence: resolved occurrences must schedule the next occurrence.")
required={"pt-BR","pt-PT","en","es","zh-CN","zh-HK","ar","fr","de","it","ja","ko","ms","ru","pl","tr"}
found=set(re.findall(r'Language\("([^"]+)"',langs)); missing=required-found
if missing: errors.append("Missing language tags: "+", ".join(sorted(missing)))
for prefix in ("reminders","academic","progress"):
 for tag in required:
  if not (ROOT/f"app/src/main/assets/{prefix}_{tag}.json").exists():
   errors.append(f"Missing offline pack: {prefix}_{tag}.json")
for p in list((ROOT/"app/src/main/res/raw").glob("*.json"))+list((ROOT/"app/src/main/assets").glob("*.json")):
 try: json.loads(p.read_text(encoding="utf-8"))
 except Exception as e: errors.append(f"Invalid JSON: {p.relative_to(ROOT)}: {e}")

# Content volume guardrails: the large offline English banks must remain present.
try:
    general=json.loads((ROOT/"app/src/main/res/raw/motivational_phrases.json").read_text(encoding="utf-8"))
    academic=json.loads((ROOT/"app/src/main/res/raw/academic_phrases.json").read_text(encoding="utf-8"))
    def count_strings(x):
        if isinstance(x,list): return len(x)
        if isinstance(x,dict): return sum(count_strings(v) for v in x.values())
        return 0
    if count_strings(general) < 10000: errors.append("General phrase bank is below 10,000 entries")
    if count_strings(academic) < 50000: errors.append("Academic phrase bank is below 50,000 entries")
except Exception: pass

print("Berna Focus static audit")
print("="*28)
if errors:
 print("ERRORS:"); [print("  -",e) for e in errors]
else: print("No structural errors found.")
if warnings:
 print("WARNINGS:"); [print("  -",w) for w in warnings]
sys.exit(1 if errors else 0)
