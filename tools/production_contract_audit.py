#!/usr/bin/env python3
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
errors = []

def read(rel):
    return (ROOT / rel).read_text(encoding='utf-8')

manifest = read('app/src/main/AndroidManifest.xml')
alarm = read('app/src/main/java/com/focusedmind/app/AlarmReceiver.kt')
store = read('app/src/main/java/com/focusedmind/app/FocusedMindData.kt')
main = read('app/src/main/java/com/focusedmind/app/MainActivity.kt')
lang = read('app/src/main/java/com/focusedmind/app/LanguageManager.kt')
gradle = read('app/build.gradle.kts')
workflow_debug = read('.github/workflows/build-debug.yml')
workflow_release = read('.github/workflows/build-release.yml')

# Package / launchability contracts.
if 'namespace = "com.focusedmind.app"' not in gradle or 'applicationId = "com.focusedmind.app"' not in gradle:
    errors.append('namespace/applicationId mismatch or missing')
if 'android:theme="@style/Theme.FocusedMind"' not in manifest or 'Theme.MyApplication' in manifest:
    errors.append('canonical Focused Mind Android theme wiring missing')
if 'android:name=".MainActivity"' not in manifest or 'MAIN' not in manifest or 'LAUNCHER' not in manifest:
    errors.append('launcher activity contract missing')
if 'class MainActivity : ComponentActivity()' not in main:
    errors.append('MainActivity implementation missing')

# Notification contract: only T-10 and exact are user-visible.
if 'STAGE_TEN = 0' not in alarm or 'STAGE_EXACT = 1' not in alarm or 'STAGE_EXPIRE = 2' not in alarm:
    errors.append('alarm stage contract missing')
if 'manager.notify(notificationId(id, stage), notification)' not in alarm:
    errors.append('notification dispatch missing')
if 'stage != STAGE_TEN && stage != STAGE_EXACT' not in alarm:
    errors.append('receiver allows an unexpected user-visible stage')
if 'STAGE_T5' in alarm or '5 minutes before' in alarm or 'timestamp - 5' in alarm:
    errors.append('forbidden T-5 notification contract found')
if 'STAGE_EXPIRE' not in alarm or 'RESPONSE_WINDOW_MS' not in store:
    errors.append('silent expiry window contract missing')

# Permission lifecycle.
for needle in [
    'POST_NOTIFICATIONS', 'SCHEDULE_EXACT_ALARM',
    'ACTION_REQUEST_SCHEDULE_EXACT_ALARM',
    'ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED'
]:
    if needle not in manifest + main + alarm + read('app/src/main/java/com/focusedmind/app/BootReceiver.kt'):
        errors.append(f'permission lifecycle missing: {needle}')

# Recurrence and response window.
for mode in ['ONE_TIME', 'DAILY', 'WEEKDAYS', 'WEEKENDS', 'SPECIFIC']:
    if mode not in store or mode not in main:
        errors.append(f'recurrence mode missing: {mode}')
if 'now < occurrence.timestamp' not in store or 'RESPONSE_WINDOW_MS' not in store or 'MIN_LEAD_MS' not in store:
    errors.append('completion-window/minimum-lead validation missing')
if 'nextOccurrenceAfter' not in store:
    errors.append('recurrence generation missing')
if 'calendarDay' in read('app/src/main/java/com/focusedmind/app/LocalizedStrings.kt') and 'MONDAY..Calendar.SUNDAY' in main:
    errors.append('invalid Monday..Sunday range still present')

# Premium contracts.
for product in ['focused_mind_important_event', 'focused_mind_academic_focus']:
    if product not in read('app/src/main/java/com/focusedmind/app/PremiumAccessManager.kt') and product not in read('app/src/main/java/com/focusedmind/app/HuaweiIapManager.kt'):
        errors.append(f'premium product missing: {product}')
if 'IN_APP_NONCONSUMABLE' not in read('app/src/main/java/com/focusedmind/app/HuaweiIapManager.kt'):
    errors.append('non-consumable Huawei IAP flow missing')
if 'verify(' not in read('app/src/main/java/com/focusedmind/app/HuaweiIapManager.kt'):
    errors.append('IAP signature verification missing')
iap = read('app/src/main/java/com/focusedmind/app/HuaweiIapManager.kt')
for needle in ['obtainProductInfo', 'InAppPurchaseData', 'purchaseState', 'microsPrice', 'currency', 'packageName']:
    if needle not in iap: errors.append(f'IAP receipt/product consistency check missing: {needle}')

# Localization contract.
try:
    supported = re.findall(r'Language\("([^"]+)",', lang)
    expected = ['system','pt-BR','pt-PT','en','es','zh-CN','zh-HK','ar','fr','de','it','ja','ko','ms','ru','pl','tr']
    if supported != expected:
        errors.append(f'language list mismatch: {supported}')
except Exception as exc:
    errors.append(str(exc))

# CI/toolchain contract.
if "gradle-version: '9.3.1'" not in workflow_debug or "gradle-version: '9.3.1'" not in workflow_release:
    errors.append('GitHub workflows are not aligned to Gradle 9.3.1')
if 'com.huawei.hms:iap:6.13.0.300' not in gradle:
    errors.append('Huawei IAP SDK not aligned to 6.13.0.300')

# JSON resources.
for rel in ['app/src/main/res/raw/motivational_phrases.json','app/src/main/res/raw/academic_phrases.json','app/src/main/res/raw/gamification_messages.json']:
    try:
        data = json.loads((ROOT / rel).read_text(encoding='utf-8'))
        if not data:
            errors.append(f'empty JSON resource: {rel}')
    except Exception as exc:
        errors.append(f'bad JSON {rel}: {exc}')

# Secret exclusion.
for path in ROOT.rglob('*'):
    if not path.is_file():
        continue
    relative = path.relative_to(ROOT).as_posix()
    if relative.startswith('signing/') and path.name not in {'.gitignore', 'README.txt'}:
        errors.append(f'private signing material present: {relative}')
    if path.suffix.lower() in {'.jks', '.keystore', '.p12', '.pfx'}:
        errors.append(f'private key material present: {relative}')

if errors:
    print('PRODUCTION CONTRACT AUDIT: FAIL')
    for error in errors:
        print(' -', error)
    raise SystemExit(1)

print('PRODUCTION CONTRACT AUDIT: PASS')
print(' - package/launcher wiring')
print(' - two visible notification stages + silent T+5 expiry')
print(' - exact-alarm permission lifecycle')
print(' - recurrence modes + 5-minute response rules')
print(' - Huawei non-consumable purchase + signature verification')
print(' - 16 requested locales + system default')
print(' - Gradle/AGP workflow alignment')
print(' - phrase-bank JSON validation')
print(' - private signing material exclusion')
