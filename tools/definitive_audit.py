import json, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
LANGS=['en', 'pt-BR', 'pt-PT', 'es', 'zh-CN', 'zh-HK', 'ar', 'fr', 'de', 'it', 'ja', 'ko', 'ms', 'ru', 'pl', 'tr']
errs=[]
assets=ROOT/"app/src/main/assets"
for lang in LANGS:
    p=assets/f"reminders_{lang}.json"
    if not p.exists(): errs.append(f"missing {lang}"); continue
    d=json.loads(p.read_text(encoding="utf-8"))
    if sum(len(v) for v in d.values()) != 10000: errs.append(f"{lang}: reminder count")
    if any(len(x)>180 or not x.strip() for v in d.values() for x in v): errs.append(f"{lang}: long/blank reminder")
    if len(set(x for v in d.values() for x in v)) != 10000: errs.append(f"{lang}: duplicate reminder")
for f in ["MainActivity.kt","AlarmReceiver.kt","BootReceiver.kt","FocusedMindData.kt","HuaweiIapManager.kt","LocalizedStrings.kt","LocalizedContentRepository.kt"]:
    if not (ROOT/"app/src/main/java/com/focusedmind/app"/f).exists(): errs.append("missing source "+f)
text="\n".join(p.read_text(encoding="utf-8",errors="ignore") for p in (ROOT/"app/src/main/java").rglob("*.kt"))
for token in ["STAGE_TEN","STAGE_EXACT","STAGE_EXPIRE","HUAWEI_IAP_PUBLIC_KEY"]:
    if token not in text: errs.append("missing "+token)
if "STAGE_FIVE" in text: errs.append("forbidden T-5 stage")
manifest=(ROOT/"app/src/main/AndroidManifest.xml").read_text()
for p in ["android.permission.INTERNET","android.permission.POST_NOTIFICATIONS","android.permission.SCHEDULE_EXACT_ALARM","android.permission.RECEIVE_BOOT_COMPLETED"]:
    if p not in manifest: errs.append("manifest missing "+p)
if errs:
    print("DEFINITIVE AUDIT: FAIL")
    print("\n".join(errs)); sys.exit(1)
print("DEFINITIVE AUDIT: PASS")
print(f"{len(LANGS)} languages × 10,000 reminders = {len(LANGS)*10000:,}")
