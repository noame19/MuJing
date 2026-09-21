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

On Linux, MuJing configures Compose/skiko before the UI starts. It disables
skiko's automatic Xft.dpi path and uses an explicit `sun.java2d.uiScale=2.5`,
which keeps the Compose content density aligned with the window on Fedora KDE
Wayland fractional scaling. The value is clamped by the application to the
range `1.0` to `4.0`.

Override the Linux scale when launching the AppImage:
```bash
MUJING_SCALE=1.5 ./MuJing-2.12.3-x86_64.AppImage
MUJING_SCALE=2.0 ./MuJing-2.12.3-x86_64.AppImage
```

The AppRun wrapper preserves `MUJING_SCALE` and logs the selected value. KDE,
GNOME, Wayland, and X11 launches use the same application-level setting.
