# Linux AppImage Build

This repo ships a `.github/workflows/Build-AppImage.yml` that produces a self-contained Linux AppImage via GitHub Actions, targeting Fedora KDE 44, Ubuntu 24.04/22.04 and other modern Linux desktops.

## Build flow

1. **Toolchain** — `ubuntu-22.04` runner with JDK 21 (JetBrains), Gradle, Rust (for `rust-zstd-jni`), and the X11 / GL / GTK runtime libraries the Compose Desktop plugin expects.
2. **Compose runtime** — `./gradlew createDistributable` lays down the self-contained JetBrains runtime (JDK + Compose jars + resources + the launcher script) at `build/compose/binaries/main/app/<packageName>/`. The launcher is preserved at its original path (`<packageName>/bin/<packageName>`).
3. **Fcitx5 IME** — Ubuntu 22.04 doesn't ship `libfcitx5-qt6-1`; we pull the prebuilt `.deb` packages from noble (24.04) and extract just `libfcitx5platforminputcontextplugin.so` into the Compose runtime's `platforminputcontexts/` plugins dir. IBus reuses the system daemon via `QT_IM_MODULE=ibus`.
4. **AppDir + AppRun** — `linuxdeploy` wraps the staged AppDir into an AppImage. The icon is downscaled to 256×256 (linuxdeploy rejects the project's 1280×1280 logo) and the AppRun wrapper prefers Wayland (`QT_QPA_PLATFORM=wayland;xcb`) to avoid XWayland fractional-scaling blur on KDE Plasma 6 and GNOME.
5. **ffmpeg** — the upstream `resources/linux/ffmpeg/ffmpeg` (compiled with the whisper filter) is copied into the runtime `bin/` so the in-app whisper transcription still works inside the AppImage.
6. **Release** — on tag push, the AppImage is uploaded to the workflow's draft release as `MuJing-<version>-x86_64.AppImage`.

## Triggering

- `workflow_dispatch` from the Actions tab.
- Any `v*` tag push (e.g. `git tag v2.12.3 && git push --tags`).

## Output artifact

- `MuJing-linux-appimage-x86_64` — a 219 MB zip containing `MuJing-<version>-x86_64.AppImage`. Make it executable and run; FUSE-less extraction is supported via `APPIMAGE_EXTRACT_AND_RUN=1`.

## HiDPI / fractional scaling

Compose Desktop on Linux uses the bundled **skiko** AWT backend. By default skiko calls `linuxGetSystemDpiScale`, reads `Xft.dpi` from Xlib, and sets `sun.java2d.uiScale = Xft.dpi / 96`. On KDE Plasma 6 / GNOME with fractional scaling (e.g. 175%), this grows the window but the Compose UI keeps its 1× density, so fonts look tiny inside a giant window.

AppRun disables `skiko.linux.autodpi` and picks an explicit scale factor at runtime, in this order:

1. `MUJING_SCALE` env var (user override)
2. `xrandr --query` per-output scales (max)
3. `Xft.dpi / 96`
4. `~/.config/kwinoutputconfig.json` per-output scales (max)
5. `MUJING_SCALE_DEFAULT` (default `2.0`)

The detected value is clamped to `[1.0, 4.0]`. Override at launch:
```bash
MUJING_SCALE=1.5 ./MuJing-2.12.3-x86_64.AppImage
MUJING_SCALE=2.5 ./MuJing-2.12.3-x86_64.AppImage
MUJING_SCALE_DEFAULT=2.0 ./MuJing-2.12.3-x86_64.AppImage   # change the fallback
```
