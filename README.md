# Game Tracker

A dark-themed Java desktop dashboard for combining gaming playtime across launchers. It stores data in a local SQLite database and can keep tracking while its window is hidden.

## Features

- Lifetime hours and a game library across Steam, EA, Battle.net, Epic Games, Ubisoft Connect, GOG, and other platforms
- Most-played games, recent session history, and locally tracked weekly/monthly totals
- Manual sessions and automatic game process detection, polled every 10 seconds
- Optional per-game executable names for process detection
- Persistent session history with periodic checkpoints and interrupted-session recovery
- Manual historical hours and Steam lifetime playtime import through Steam's GetOwnedGames Web API
- Background operation via the system tray where supported
- Local database with owner-only permissions on macOS and Steam API key masked and not stored

## Platform and data limitations

- Requires JDK 17+ and Maven 3.9+ to build from source. GitHub Actions creates separate Apple Silicon and Intel Mac downloads and a Windows x64 download.
- Process detection matches the running executable name to the game name or an optional configured process name. Some games need a custom process name.
- Steam import needs an API key, SteamID64, and profile visibility that permits owned-game data. Other launchers may require manual hours.
- Weekly/monthly statistics count sessions tracked by this app, not imported historical hours whose dates are unknown.
- Games from multiple launchers are not automatically merged. Reconcile duplicates to avoid double counting.
- Local permissions do not protect from malware or someone using your unlocked account. Enable FileVault for stronger protection at rest.
- Downloads are not code-signed. macOS may require Control-click > Open on first launch; Windows may show a SmartScreen warning.

## Run from source

```bash
mvn javafx:run
```

The database is stored in the user's application data folder: ~/Library/Application Support/GameTracker on macOS, %APPDATA%\GameTracker on Windows, and $XDG_DATA_HOME/gametracker (or ~/.local/share/gametracker) on Linux. An existing game-tracker.db in the working directory is moved there on first launch.

## Downloadable builds

[Download for Mac (Apple Silicon)](https://github.com/DearingK12/GameTracker/releases/latest/download/GameTracker-1.0.0-macOS-arm64.dmg)<br>
[Download for Mac (Intel)](https://github.com/DearingK12/GameTracker/releases/latest/download/GameTracker-1.0.0-macOS-x86_64.dmg)<br>
[Download for Windows (64-bit)](https://github.com/DearingK12/GameTracker/releases/latest/download/GameTracker-1.0.0-Windows-x64.zip)

These links download files from the latest GitHub release. The first release is built from the v1.0.0 tag. For later versions, push a new version tag. You can also run Package desktop downloads from the repository Actions tab to create workflow artifacts.

To package locally, run scripts/package-macos.sh on macOS or PowerShell -File scripts/package-windows.ps1 on Windows. The Windows ZIP contains a self-contained app image; extract it and launch GameTracker.exe. The Mac disk image shows a title and an arrow pointing from the app to Applications.

## Steam setup

1. Create a Steam Web API key from Steam's official API key page.
2. Find your SteamID64.
3. Choose Import Steam hours in the app and enter both values.

The app does not save the API key. Keep it private.
