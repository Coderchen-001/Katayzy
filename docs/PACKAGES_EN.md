# Package Overview

This document answers three practical questions:

1. which package types are currently recommended in public releases
2. what each package includes
3. which one a regular user should download first

## Quick Answer First

This page describes the public release layout of the maintained `LizzieYzy Next` fork, not the older historical `lizzieyzy` release layout.

- The maintained release page now centers on 15 primary user-facing assets
- On Windows, the default recommendation is now `portable.zip`
- Most regular users should start with `windows64.opencl.portable.zip`
- If OpenCL behaves poorly, switch to `windows64.with-katago.portable.zip`
- If you have an RTX 20/30/40 NVIDIA GPU and want more speed, switch to `windows64.nvidia.portable.zip`
- RTX 5070/5080/5090 users should try `windows64.nvidia50.cuda.portable.zip` first; TensorRT acceleration is installed on demand from inside the app
- TensorRT is no longer a giant release package; RTX 20/30/40/50 users can install it from `KataGo Auto Setup`. TensorRT is not recommended for GTX 10 series cards; the regular NVIDIA package requires driver `527.41` or newer, and users should switch to the OpenCL package if it still cannot start
- `KataGo Auto Setup` detects the local NVIDIA GPU / Compute Capability and shows recommended, try, not recommended, or unknown status before TensorRT install

## The 15 Primary Public Release Assets

| Package type | Typical filename | Best for |
| --- | --- | --- |
| Windows x64 OpenCL portable | `<date>-windows64.opencl.portable.zip` | Main recommendation for regular users |
| Windows x64 OpenCL installer | `<date>-windows64.opencl.installer.exe` | OpenCL users who prefer an installer |
| Windows x64 CPU fallback portable | `<date>-windows64.with-katago.portable.zip` | CPU fallback when OpenCL behaves badly |
| Windows x64 CPU fallback installer | `<date>-windows64.with-katago.installer.exe` | CPU fallback with installer flow |
| Windows x64 NVIDIA portable | `<date>-windows64.nvidia.portable.zip` | NVIDIA GPU users who want higher analysis speed without an installer |
| Windows x64 NVIDIA installer | `<date>-windows64.nvidia.installer.exe` | NVIDIA GPU users who prefer an installer |
| Windows x64 RTX 50 CUDA portable | `<date>-windows64.nvidia50.cuda.portable.zip` | First choice for RTX 5070/5080/5090 users |
| Windows x64 RTX 50 CUDA installer | `<date>-windows64.nvidia50.cuda.installer.exe` | RTX 5070/5080/5090 users who prefer an installer |
| Windows x64 no-engine portable | `<date>-windows64.without.engine.portable.zip` | Custom KataGo setup |
| Windows x64 no-engine installer | `<date>-windows64.without.engine.installer.exe` | Users who want installer flow with their own engine |
| macOS Apple Silicon bundle | `<date>-mac-apple-silicon.with-katago.dmg` | M-series Macs |
| macOS Intel bundle | `<date>-mac-intel.with-katago.dmg` | Intel Macs |
| Linux x64 bundle | `<date>-linux64.with-katago.zip` | Linux desktop users |
| Linux x64 OpenCL bundle | `<date>-linux64.opencl.zip` | Linux users with AMD/Intel GPUs |
| Linux x64 NVIDIA CUDA bundle | `<date>-linux64.nvidia.zip` | Linux users with NVIDIA GPUs |

Notes:

- `<date>` is the release date, for example `2026-03-21`.
- The maintained public release page now keeps these 15 user-facing assets as the main list.
- Windows x64 is portable-first, with matching installers kept as optional alternatives.
- Older tags may still show compatibility zips, but those are now historical layouts.

## What Each Package Includes

| Package | Java | KataGo | Weight | How you start it |
| --- | --- | --- | --- | --- |
| `windows64.opencl.portable.zip` | Bundled | Bundled | Bundled | Unzip and run `LizzieYzy Next OpenCL.exe` |
| `windows64.opencl.installer.exe` | Bundled | Bundled | Bundled | Install, then launch from Start Menu or desktop |
| `windows64.with-katago.portable.zip` | Bundled | Bundled | Bundled | Unzip and run `LizzieYzy Next.exe` |
| `windows64.with-katago.installer.exe` | Bundled | Bundled | Bundled | Install, then launch from Start Menu or desktop |
| `windows64.nvidia.portable.zip` | Bundled | Bundled | Bundled | Unzip and run `LizzieYzy Next NVIDIA.exe` |
| `windows64.nvidia.installer.exe` | Bundled | Bundled | Bundled | Install, then launch `LizzieYzy Next NVIDIA` |
| `windows64.nvidia50.cuda.portable.zip` | Bundled | Bundled | Bundled | Unzip and run `LizzieYzy Next NVIDIA 50 CUDA.exe` |
| `windows64.nvidia50.cuda.installer.exe` | Bundled | Bundled | Bundled | Install, then launch `LizzieYzy Next NVIDIA 50 CUDA` |
| `windows64.without.engine.portable.zip` | Bundled | Not bundled | Not bundled | Unzip and run `LizzieYzy Next.exe` |
| `windows64.without.engine.installer.exe` | Bundled | Not bundled | Not bundled | Install, then launch from Start Menu or desktop |
| `mac-apple-silicon.with-katago.dmg` | App runtime | Bundled | Bundled | Follow the installer artwork, drag to Applications, then eject the DMG |
| `mac-intel.with-katago.dmg` | App runtime | Bundled | Bundled | Follow the installer artwork, drag to Applications, then eject the DMG |
| `linux64.with-katago.zip` | Bundled | Bundled | Bundled | Run `start-linux64.sh` |
| `linux64.opencl.zip` | Bundled | Bundled | Bundled | Run `start-linux64.sh` |
| `linux64.nvidia.zip` | Bundled | Bundled | Bundled | Run `start-linux64.sh` |

## Simple Download Advice

If you just want the shortest path:

- Windows: choose `windows64.opencl.portable.zip`
- Windows with an RTX 20/30/40 NVIDIA GPU: choose `windows64.nvidia.portable.zip`
- Windows with RTX 5070/5080/5090: choose `windows64.nvidia50.cuda.portable.zip` first
- macOS: choose the correct `with-katago.dmg` for your chip
- Linux: choose `linux64.with-katago.zip`

If you already manage engines manually:

- Windows: choose `windows64.without.engine.portable.zip` if you do not want installation, or `windows64.without.engine.installer.exe` if you do
- macOS / Linux: you can still start from the standard bundle and point the app to your own engine later

## Why Windows Is Portable-First Now

Because regular users typically need this path:

1. download the app
2. unzip and run immediately
3. keep the option to install only if they want it
4. avoid manual Java setup
5. let first launch auto-configure bundled KataGo when possible

Installers still exist, but they are now secondary to the portable flow.

## Bundled Engine Details

Current bundled defaults:

- KataGo version: `v1.17.1`
- macOS release builds pin the official `v1.17.1` commit `5246793f77b480dee91a3b92902d1a9b92860bd0`. If the stable Homebrew formula still lags behind, packaging builds the Metal engine from that commit and verifies the real binary version instead of trusting `VERSION.txt` alone
- Default weight: official medium Transformer `b10c512h8nbt3tflrs-fson-silu-rsnh.bin.gz`, shown as “Transformer 10B Balanced”
- Default weight size: `94,281,753` bytes (about 94 MB), SHA-256: `c04db4a503721d948bb720324f3cbdac6088cc9eb243632f020e4b6846f58995`
- Standard Windows NVIDIA package: CUDA `12.1` + cuDNN `9.8`; RTX 50 CUDA package: CUDA `12.8` + cuDNN `9.8`
- GTX 10 series cards use the Pascal architecture. The [NVIDIA cuDNN 9.8 support matrix](https://docs.nvidia.com/deeplearning/cudnn/backend/v9.8.0/reference/support-matrix.html) requires Windows driver `527.41` or newer for CUDA 12; use the `windows64.opencl` package if KataGo still cannot start after updating the driver
- Transformer performs best through CUDA or Metal; OpenCL remains fully offline-capable but is normally slower
- `core-update.zip` updates only the application and does not include KataGo 1.17 or the new weight; install the latest full bundle to upgrade from the old default
- Full-bundle migration changes only managed engines still using the old bundled `zhizi 28B` / `default.bin.gz`; custom weights, remote compute, and startup modes are preserved
- TensorRT acceleration: no longer published as a giant GitHub Release package; RTX 20/30/40/50 users can install it on demand from `KataGo Auto Setup` inside the app
- RTX 50 users should still start with the `windows64.nvidia50.cuda` package, with TensorRT as an on-demand acceleration path for the newer architecture
- The TensorRT install UI uses `nvidia-smi` to detect the local NVIDIA GPU, with a lightweight model-name fallback when Compute Capability is unavailable

Paths:

- Windows / Linux bundles: `Lizzieyzy/weights/default.bin.gz`
- macOS bundles: `LizzieYzy Next.app/Contents/app/weights/default.bin.gz`

## Bundled Board Sync Helper

- Windows release packages now include native `readboard/readboard.exe` and its dependency files, so normal users do not need to download a separate board sync tool
- Windows native path: `Lizzieyzy/readboard/`
- The app now keeps only the native readboard sync entry and no longer ships or starts the old simplified Java helper

## How To Read Old Versus New Release Layouts

From the new maintained releases onward:

- the main Windows x64 package is `portable.zip`
- Windows x64 now exposes OpenCL, CPU fallback, NVIDIA, and NVIDIA 50 CUDA variants in both portable and installer forms
- the Windows x64 no-engine option now has both an installer and a portable `.zip`
- the public release page keeps the 15 primary user-facing assets above as the main list; TensorRT acceleration is an in-app on-demand install, not a release asset
- older compatibility zips now stay in historical tags instead of the main recommendation area

## Related Docs

- [Installation Guide](INSTALL_EN.md)
- [Troubleshooting](TROUBLESHOOTING_EN.md)
- [Tested Platforms](TESTED_PLATFORMS.md)
- [Release Checklist](RELEASE_CHECKLIST.md)
- [Chinese README](../README.md)
