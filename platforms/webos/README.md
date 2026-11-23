# AirPlay TV for LG webOS — interface preview

[معرفی فارسی و شروع کار](../../README.md)

This branch packages the Teli interface for LG webOS. Persian is the default language; English and mixed mode, dark/light/solid themes, local preferences and the creator section are available. The initial UI target is **webOS 6 and newer**, with JavaScript built for Chromium 79. Hardware compatibility is unverified.

**There is no webOS AirPlay receiver core yet.** Installing the IPK provides the interface only. It does not register an AirPlay service, accept mirroring connections or receive audio/video. The Start button is disabled and the interface displays this limitation. No LG TV installation or end-to-end AirPlay test has been completed.

| webOS Teli dark — browser preview | webOS Teli light — browser preview |
| --- | --- |
| ![Dark](../../docs/screenshots/webos/teli-dark.jpg) | ![Light](../../docs/screenshots/webos/teli-light.jpg) |

## Build and preview

From the repository root, using Node.js 20+, npm and Python 3:

```sh
npm --prefix tv-web ci
npm --prefix tv-web test
npm --prefix tv-web run dev
```

Open `http://127.0.0.1:4180`. The preview server binds to localhost. Arrow keys move focus, Enter selects, and Escape acts as Back. Language and theme selections persist locally. The same buttons support pointer clicks.

## Package

```sh
npm --prefix tv-web run package:webos
python3 platforms/webos/tests/package.py
```

The official **@webos-tools/cli 3.2.6** is pinned as a development dependency. The package script bundles the webOS entry point, copies metadata and 80/130-pixel icons, and calls `ares-package --no-minify`.

- App ID: `io.github.mehdieskandari.airplaytv.webos`
- Version: `0.1.0`
- Output: `platforms/webos/output/io.github.mehdieskandari.airplaytv.webos_0.1.0_all.ipk`
- Scope: development **interface preview**, not a working AirPlay receiver or store release

CI runs the JS checks, builds the IPK, validates its payload and uploads the preview package as a workflow artifact. Generated directories and npm dependencies are ignored by Git.

## Install the preview on an LG TV

Prepare the TV with LG's Developer Mode app and an LG developer account. Configure the test device and obtain its development key through the official CLI. Complete account, password and terms steps yourself.

From the repository root:

```sh
./tv-web/node_modules/.bin/ares-setup-device
./tv-web/node_modules/.bin/ares-novacom --device lg-tv --getkey
./tv-web/node_modules/.bin/ares-install --device lg-tv platforms/webos/output/io.github.mehdieskandari.airplaytv.webos_0.1.0_all.ipk
./tv-web/node_modules/.bin/ares-launch --device lg-tv io.github.mehdieskandari.airplaytv.webos
```

Use the device name chosen during setup in place of `lg-tv`. Check language selection, both themes, focus movement, pointer clicks, Back navigation and app exit. These steps validate the interface; there is no AirPlay playback test in this version.

## webOS adapter and receiver boundary

`tv-web/src/platforms/webos.js` detects a packaged LG app through `PalmSystem`. Its `available` flag remains false even on a detected TV; the receiver engine cannot load or announce a core. The webOS build selects this adapter at bundle time and excludes the Samsung native bundle and SDK loader.

`appinfo.json` enables `disableBackHistoryAPI`, and the remote handler recognizes LG's Back key code 461. Nested screens return to Home; the entry screen uses a custom exit confirmation followed by `window.close()`. The package requests no Luna permissions (`requiredACG: []`) because it calls no Luna APIs.

A future receiver implementation must provide discovery, protocol handling, pairing, media decoding/playback and real readiness events through this platform boundary. Merely detecting webOS must not enable the receiver. Device tests remain necessary before selecting a supported model range or making playback claims.

## Verification

- JavaScript tests cover preferences, navigation, receiver lifecycle, the unavailable webOS core, LG Back normalization and device-gated exit.
- The actual IPK payload is checked for its manifest, Back configuration, icon dimensions, bundled fonts/licenses and absence of an incompatible native receiver.
- Browser checks cover onboarding, dark/light themes, settings, arrow/Enter navigation and Back. Screenshots above are from those checks.
- Physical LG installation, remote behavior and playback remain unverified.

## Official references

- [LG webOS CLI: packaging, installation and launching](https://webostv.developer.lge.com/develop/tools/cli-dev-guide)
- [appinfo.json metadata](https://webostv.developer.lge.com/develop/references/appinfo-json)
- [Back button handling](https://webostv.developer.lge.com/develop/guides/back-button)
- [Web engines by webOS version](https://webostv.developer.lge.com/develop/specifications/web-api-and-web-engine)
- [Luna access control declarations](https://webostv.developer.lge.com/develop/guides/acg-guide)
