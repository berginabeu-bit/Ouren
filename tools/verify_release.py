from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
apk = ROOT / "app/build/outputs/apk/release/app-release.apk"
if not apk.is_file() or apk.stat().st_size == 0:
    print("SOURCE VERIFICATION: no release APK is present in this source package.")
    print("Run tools/build_release.sh on Android Studio/CI to perform the real signed APK verification.")
    raise SystemExit(0)

with zipfile.ZipFile(apk) as z:
    names = set(z.namelist())
    if not any(name.startswith("AndroidManifest.xml") for name in names):
        raise SystemExit("ERROR: release APK has no AndroidManifest.xml")
print(f"OK: {apk} ({apk.stat().st_size:,} bytes)")
