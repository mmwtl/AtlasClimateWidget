# AtlasClimateWidget Repository Guide

## Scope

These instructions apply to the entire repository.

## Project purpose

AtlasClimateWidget is a real Android `AppWidget` (not an overlay) for portrait Geely OneOS head
units on Android 11. It is a user-built climate widget: a temperature bar, a fan bar and tiles of
climate functions. Package name: `com.mmwtl.atlasclimatewidget`; do not change it without an
explicit migration request. All car access goes through the GInputBridge broadcast API
(`com.salat.gbinder`); do not bind ECarX services directly or add fuel/tank logic.

## Architecture

- `ClimateService` is a foreground service that owns the bridge receiver, the periodic
  `LISTEN_*`/`GET_*` refresh, widget controls and widget redraws. The bridge answers with implicit
  broadcasts, so the service must stay running while widgets exist.
- `WidgetGeometry` splits the widget into full-width strips; `WidgetRenderer` draws each strip as a
  bitmap; `WidgetViews` stacks the strips with an overlay row of equal-weight touch cells. Android 11
  `RemoteViews` cannot set weights, margins or positions dynamically, so every touch target must sit
  on the equal-cell grid computed by `WidgetGeometry`.
- `MainActivity` is the full settings editor with Blocks, Tiles, Look and System tabs pinned under
  the title, as in the widgetkit branches of AtlasAppWidget and AtlasMediaWidget. The widget
  selector and live preview are pinned under the tabs on the three layout tabs and hidden on
  System. Keep fine-tuning controls (card sliders, the list of available functions) in collapsed
  sections; the selected tab survives recreation.
- `TileDragLayer` hosts the settings preview and lets the finger drag tiles to reorder them
  (`WidgetConfig.dropTile`); the preview follows each step and the order is saved on release.
  Home-screen widgets cannot be dragged: `RemoteViews` only deliver clicks.
- `WidgetSetupActivity` is the `APPWIDGET_CONFIGURE` dialog (also the launcher's ⚙ reconfigure).
  It edits a copy and saves only on confirm; a widget with reported size counts as reconfigured.
- Settings backup follows GInputBridge's .gibb flow: head units have no file picker, so
  `SettingsBackup` (global settings, template, placed widgets in id order) is written as an `.acws`
  JSON file with its own MIME and leaves via the share sheet (`BackupProvider`); it comes back when
  the file is opened with the app (`MainActivity`'s `VIEW` filter), always behind a confirmation.
  Widget ids do not survive a reinstall; layouts are restored by position.
- `ScrubActivity` is a borderless window placed over a tapped bar (from the host's source
  bounds) that lets the finger drag the value; while it is open the widget draws that strip
  card-only and the window draws content only. Commands are sent on release. The strip is
  hidden only after the window's first frame and restored 300 ms before it closes; `finish()`
  skips the task transition, otherwise the window slides down over HOME.
- Height: `HeightMode.FILL` grows tile rows up to `MAX_TILE_ASPECT`, then card padding, so the
  last card ends at the cell bottom; bars keep their size. `CONTENT` keeps square tiles and uses
  the root gravity for alignment. Shrinking below the cell never depends on the mode.
- Cards: `CardLayout.SEPARATE` gives every block its own card; `SINGLE` puts all rows in one card,
  blocks a card padding apart with a hairline divider drawn in the card layer. Fill shares extra
  height per block in both modes.
- Style: `Style.CLASSIC` keeps the user's tile order and the knob-labelled temperature bar.
  `CONSOLE` gives each zone a `TEMP_VALUE` row (−, the value centred, +; the middle is inert)
  above a thin all-steps bar, splits fan directions and auto-fan presets into equal segmented
  rows, and arranges tiles via `WidgetConfig.tileRows()`: climate modes, glass, then seats
  mirrored driver ↔ passenger. All console round buttons share one diameter and sit on the
  content edges; labels centre on font metrics, not glyph bounds.
  When a console layout is too tall, tile rows flatten first (down to
  `CONSOLE_MIN_TILE_ASPECT`) so bars and fan buttons keep their size; fill never grows console
  tiles past square.
- `ClimateCommands`, `ClimateStore`, `WidgetConfig` and `WidgetGeometry` are pure Java and covered by
  JVM unit tests; keep Android framework code at the service/provider/activity edges.
- Property ids, zones and value encodings live in `Hvac` and `ClimateFunction`; they mirror
  GInputBridge. Keep unverified values listed in `docs/ginputbridge-contract.md`.

## Behaviour rules

- Never show stale values indefinitely: values expire when the bridge stops answering, and the
  widget shows an explicit "no bridge" state.
- Optimistic values after a command are temporary and replaced by the bridge's confirmation.
- The settings preview uses the same `RemoteViews` as the widget, inert; demo values are labelled.
- Preserve per-widget layouts across upgrades; `WidgetConfig.fromJson` must tolerate unknown and
  missing fields.

## UI guidelines

Maintain the Atlas graphite palette shared with AtlasAppWidget and AtlasMediaWidget: `#171717`
background, `#262626` cards, `#333333` nested surfaces, `#F5F5F5` primary text, `#D4D4D4` secondary
text and `#7893A0` accent. Tiles follow the GInputBridge launcher tile proportions.

## Version and build

- Keep `appVersionCode` and `appVersionName` at the top of `app/build.gradle`; non-`main` branches
  append the sanitized branch name to the effective version name only.
- Artifact name: `<effectiveVersionName>[<versionCode>]AtlasClimateWidget-<buildType>.apk`.
- Before handing off an improvement run `sh gradlew --offline clean check assembleRelease`.
- For UI changes validate on the Android 11 1440×1920 emulator with a widget placed in a launcher.
- Never commit APKs, decompiler output, the `reference/` folder, signing files or keystores.
- After completing and verifying each improvement, create a Git commit unless asked otherwise.
