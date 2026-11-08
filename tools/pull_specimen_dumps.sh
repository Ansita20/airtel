#!/bin/sh
# After running Debug > "Run all specimens" on the phone (debug build), copy the on-device
# OCR dumps into the JUnit replay and the report next to it:
#   tools/pull_specimen_dumps.sh && ./gradlew test
# The JUnit test SpecimenReplayTest then checks the answer key against exactly what the phone read.
set -e
cd "$(dirname "$0")/.."
PKG=com.udyam.digital
DEST=app/src/test/resources/kyc_ocr
TMP=$(mktemp -d)
adb exec-out run-as $PKG tar c -C files kyc_ocr_dump kyc_specimen_report.md | tar x -C "$TMP"
count=$(ls "$TMP/kyc_ocr_dump"/*.json | wc -l | tr -d ' ')
[ "$count" -gt 0 ] || { echo "No dumps found - run the specimen screen first"; exit 1; }
rm -f "$DEST"/*.json
cp "$TMP/kyc_ocr_dump"/*.json "$DEST"/
mkdir -p app/build/reports/kyc
cp "$TMP/kyc_specimen_report.md" app/build/reports/kyc/specimen_report_phone.md
rm -rf "$TMP"
echo "Copied $count dumps to $DEST"
echo "Phone report: app/build/reports/kyc/specimen_report_phone.md (contains raw OCR text: keep it local)"
