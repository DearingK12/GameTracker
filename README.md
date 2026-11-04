# Game Tracker

A dark-themed Java desktop dashboard for combining gaming playtime from multiple launchers. It stores data in a local SQLite database and can keep tracking while its window is hidden.

## Features

- Lifetime hours and game library across Steam, EA, Battle.net, Epic Games, Ubisoft Connect, GOG, and other platforms
- Most-played games, recent session history, and locally tracked weekly/monthly totals
- Manual session controls and automatic game process detection, polled every 10 seconds
- Optional per-game executable names for process detection (comma-separated for alternate names)
- Persistent session records, with progress saved during play and interrupted sessions recovered at the last saved checkpoint
- Manual historical-hour entry for platforms without an accessible playtime API
- Steam lifetime playtime import through Steam's `GetOwnedGames` Web API
- Background operation via the system tray where supported; closing the window hides it, and the tray menu can reopen or quit the app
- Local game data stored in your macOS Application Support folder with owner-only directory and database permissions
- Steam Web API key is masked while entered and is not saved by the app; requests to Steam use HTTPS

## Platform and data limitations

- Requires JDK 17+ and Maven 3.9+ to build from source. JavaFX binaries are selected for the architecture running Maven. The packaging workflow creates separate Apple Silicon and Intel macOS downloads, plus a Windows x64 download.
- Automatic detection matches a running process's executable name to the game name or the optional process name configured for that game. Some games expose a different binary name or run through helper processes; configure the executable name in **Add game** when needed. macOS privacy settings or process permissions may limit detection.
- Steam import needs a Steam Web API key, SteamID64, and profile visibility that permits owned-game data. EA, Battle.net, Epic Games, and other launchers do not provide a universal public API for account-wide lifetime playtime; enter those values manually.
- Weekly/monthly statistics count sessions tracked by this app, not historical/imported hours whose original dates are unknown.
- Game rows from different launchers are not automatically merged. Avoid duplicate rows or reconcile their hours manually to prevent double counting.
- Local file permissions help protect data from other standard macOS user accounts, but do not protect it from malware, administrator/root access, or someone using your unlocked macOS account. Enable FileVault and use a strong Mac login password for stronger protection at rest.
- The macOS disk images and Windows app archive are not code-signed. macOS may require Control-clicking the app and choosing **Open** the first time; Windows may show a SmartScreen warning.

## Run from source

```bash
mvn javafx:run
```

The SQLite database is stored in the user's application data folder: `~/Library/Application Support/GameTracker` on macOS, `%APPDATA%\GameTracker` on Windows, and `$XDG_DATA_HOME/gametracker` (or `~/.local/share/gametracker`) on Linux. On first launch, an existing `game-tracker.db` in the current working directory is moved into that folder.

## Downloadable builds

[Download for Mac (Apple Silicon)](https://github.com/DearingK12/GameTracker/releases/latest/download/GameTracker-1.0.2-macOS-arm64.dmg) ·
[Download for Mac (Intel)](https://github.com/DearingK12/GameTracker/releases/latest/download/GameTracker-1.0.2-macOS-x86_64.dmg) ·
[Download for Windows (64-bit)](https://github.com/DearingK12/GameTracker/releases/latest/download/GameTracker-1.0.2-Windows-x64.zip)

These links download the latest release files directly. The first release is created by pushing a version tag such as `v1.0.2`; after that, use a new version for subsequent releases. You can also run **Package desktop downloads** manually from the repository's **Actions** tab; those files are available as workflow artifacts.

To package locally, run `bash scripts/package-macos.sh` on macOS or `.\scripts\package-windows.ps1` on Windows. The Windows archive contains a self-contained app image; extract it and launch `GameTracker.exe`.
The Mac disk image opens to a custom install screen titled **Game Tracker**, with an arrow showing where to drag the app: the **Applications** folder.

## Steam setup

1. Create a Steam Web API key from Steam's official API key page.
2. Find your SteamID64.
3. In the app, choose **Import Steam hours** and enter both values.

The app does not save the API key. Keep it private.
