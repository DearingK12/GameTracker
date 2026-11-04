#!/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

MAVEN_COMMAND="${MAVEN:-mvn}"
APP_VERSION="${APP_VERSION:-1.0.0}"
ARCH="$(uname -m)"
case "$ARCH" in
    arm64)
        JAVAFX_CLASSIFIER="mac-aarch64"
        ;;
    x86_64)
        JAVAFX_CLASSIFIER="mac"
        ;;
    *)
        echo "Unsupported macOS architecture: $ARCH" >&2
        exit 1
        ;;
esac

"$MAVEN_COMMAND" clean package dependency:copy-dependencies \
    -DincludeScope=runtime \
    -DoutputDirectory=target/package-dependencies

for jar in target/package-dependencies/javafx-*.jar; do
    case "$(basename "$jar")" in
        *-"$JAVAFX_CLASSIFIER".jar) ;;
        *) rm -f "$jar" ;;
    esac
done

mkdir -p target/package-output dist
rm -rf target/package-output/GameTracker.app target/dmg-staging
rm -f target/GameTracker-installer-temp.dmg
jpackage \
    --type app-image \
    --name GameTracker \
    --app-version "$APP_VERSION" \
    --vendor "Game Tracker" \
    --module-path "target/classes:target/package-dependencies" \
    --module dev.gametracker/dev.gametracker.Main \
    --dest target/package-output

STAGING="target/dmg-staging"
BACKGROUND="$STAGING/.background/background.png"
mkdir -p "$STAGING/.background"
cp -R target/package-output/GameTracker.app "$STAGING/GameTracker.app"
ln -s /Applications "$STAGING/Applications"
swift scripts/create-dmg-background.swift "$BACKGROUND"

TEMP_DMG="target/GameTracker-installer-temp.dmg"
OUTPUT_DMG="dist/GameTracker-${APP_VERSION}-macOS-${ARCH}.dmg"
MOUNT_POINT="/Volumes/Game Tracker"
rm -f "$OUTPUT_DMG"

hdiutil create \
    -volname "Game Tracker" \
    -srcfolder "$STAGING" \
    -ov \
    -format UDRW \
    "$TEMP_DMG"
hdiutil attach "$TEMP_DMG" \
    -readwrite \
    -noverify \
    -noautoopen >/dev/null

osascript - "$MOUNT_POINT" <<'APPLESCRIPT'
on run argv
    set mountPath to item 1 of argv
    tell application "Finder"
        tell disk "Game Tracker"
            open
            set theWindow to container window
            set current view of theWindow to icon view
            set toolbar visible of theWindow to false
            set statusbar visible of theWindow to false
            set bounds of theWindow to {120, 80, 1144, 720}
            set iconOptions to icon view options of theWindow
            set icon size of iconOptions to 112
            set text size of iconOptions to 14
            set arrangement of iconOptions to not arranged
            set background picture of iconOptions to (POSIX file (mountPath & "/.background/background.png"))
            set position of item "GameTracker.app" of theWindow to {250, 330}
            set position of item "Applications" of theWindow to {775, 330}
            update without registering applications
        end tell
    end tell
end run
APPLESCRIPT

sync
sleep 3
hdiutil detach "$MOUNT_POINT" >/dev/null
hdiutil convert "$TEMP_DMG" \
    -ov \
    -format UDZO \
    -imagekey zlib-level=9 \
    -o "$OUTPUT_DMG"
printf 'Created %s\n' "dist/GameTracker-${APP_VERSION}-macOS-${ARCH}.dmg"
