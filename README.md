<p align="center">
  <img src="docs/icon.png" width="120" alt="GhostGuard icon">
</p>

<h1 align="center">GhostGuard</h1>

<p align="center">Block ghost touches from a damaged Android screen — no root, no internet.</p>

---

## What it does

A cracked or dropped phone can spam fake touches, often near the top of the screen.
GhostGuard puts an invisible shield over the affected area, inspects every touch, and:

- **replays real touches** at the exact same spot (via the accessibility gesture API),
- **drops ghost touches** that look fake.

A touch is treated as a ghost when it is part of a **burst** (several taps within ~1 s),
is **too short** to be a finger, **teleports** across the screen in a single frame, or is a
**second finger** suddenly appearing in the top zone.

## Privacy

**No internet needed — nothing leaves your phone.**
GhostGuard does not request the `INTERNET` permission, so Android itself prevents it from
going online. Touches are analyzed in memory and never stored or sent anywhere; the only
things saved are your settings and the blocked/passed counters.

## Features

| | |
|---|---|
| **Top zone filter** | Protected strip at the top, adjustable height (40–400 dp) |
| **Whole-screen filter** | Optional: filter everywhere (gestures replay after release) |
| **Strictness** | 1 (lenient) → 5 (strict) — tunes burst, duration and jump thresholds |
| **Custom zones** | Draw rectangles anywhere; every touch inside is blocked |
| **Ghost heatmap** | See where ghost touches land; turn hotspots into zones in one tap |
| **Learning mode** | 3-min stress test (color flashes, strobe, vibration, brightness) that records ghosts and suggests settings |
| **Per-app rules** | Default / Strict / Off for each app |
| **Cascade guard** | Blocks the top zone for a moment after you touch elsewhere (ghosts set off by your own taps) |
| **Keyboard lock** | Blocks the whole top zone while the keyboard is open |
| **Statistics** | Ghosts per day, per hour of the day and per app |
| **Block system gestures** | Stops the back swipe and shade pull from starting inside zones |
| **Notification shade** | Swipe down from the top still opens notifications / quick settings |
| **Shrink screen** | Shrinks the display with two equal black bars to hide the broken area |
| **Quick Settings tile** | Pause / resume in one tap |
| **Stats** | Blocked vs. passed touch counters |

## Install

1. Download `GhostGuard.apk` from [Releases](https://github.com/fpbasile/GhostGuard/releases/latest), or [build it](#build).
2. Install it, open **GhostGuard**, tap **Open accessibility settings** and enable it.
3. If the toggle is greyed out: *Settings → Apps → GhostGuard → ⋮ → Allow restricted settings*.

Or over USB:

```sh
adb install -r GhostGuard.apk
adb shell settings put secure enabled_accessibility_services \
    com.ghostguard/com.ghostguard.GuardService
```

> ⚠️ The setting above **replaces** the list of enabled services. Read it first with
> `adb shell settings get secure enabled_accessibility_services` and join entries with `:`.

### Optional: Shrink screen

This feature needs two one-time adb commands:

```sh
adb shell pm grant com.ghostguard android.permission.WRITE_SECURE_SETTINGS
adb shell settings put global hidden_api_blacklist_exemptions \
    "Landroid/view/IWindowManager\;,Landroid/view/WindowManagerGlobal\;"
```

The second command relaxes a system restriction for **all apps** (limited to those two
window-manager classes). Undo it with:

```sh
adb shell settings delete global hidden_api_blacklist_exemptions
adb shell wm size reset
```

## Build

No Gradle — just the Android SDK build tools (35.0.0) and JDK 17+.

```sh
./build.sh
```

Output: `GhostGuard.apk`, signed with a local `ghostguard.keystore` (generated on first build).
Requires Android 12+ (minSdk 31).

## Limitations

- Real taps in the protected zone have a small delay (they are replayed on release).
- During a ghost burst, real touches in the zone are blocked for ~1–2 s.
- Pinch-to-zoom does not pass through the protected zone.
- Reinstalling the app disables the accessibility service — re-enable it afterwards.
- Without root, Android always centers a shrunk display: bars are top **and** bottom.
- The bottom home / recents swipe can't be blocked without root. Back-gesture blocking is
  limited by Android to 200 dp of height per screen edge.
- Learning mode uses rapid flashing lights — avoid it if you are photosensitive.

## Project layout

```
src/com/ghostguard/
  GuardService.java    accessibility service: shield overlay, touch analysis, replay
  MainActivity.java    settings UI
  LearnActivity.java   3-minute learning mode and suggestions
  HeatmapActivity.java ghost heatmap screen (HeatView, Heat)
  AppRulesActivity.java per-app rules (AppRules)
  ZoneEditorActivity.java custom zone editor (Zones)
  Ui.java              shared Material You styling
  DisplayResizer.java  shrink-screen helper
  StatsActivity.java   statistics screen (Stats)
  ToggleTile.java      Quick Settings tile
  Prefs.java           shared preference keys
res/                   icons, strings, accessibility config
build.sh               aapt2 + javac + d8 + apksigner build
```
