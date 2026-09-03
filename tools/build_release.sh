#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 tools/business_rules_audit.py
python3 tools/production_contract_audit.py
python3 tools/static_audit.py
if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle 9.3.1 is required on the build machine (or use Android Studio/CI)."
  exit 2
fi
gradle --version
gradle clean assembleRelease
python3 tools/verify_release.py
