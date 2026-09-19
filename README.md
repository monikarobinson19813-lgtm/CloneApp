# CloneApp (CA) — v0.1 Architecture Prototype

This repository is the first implementation scaffold for CloneApp.

## Engineering source of truth

Use these files to answer "what are we building?", "where are we now?" and "what is next?":

- [PRODUCT_VISION.md](PRODUCT_VISION.md) — what CA should ultimately become; competitive aspiration and product promise.
- [ASPIRATION_MATRIX.md](ASPIRATION_MATRIX.md) — Multiple Accounts / Parallel Space / peer feature baseline vs CA target.
- [ROADMAP.md](ROADMAP.md) — milestone path from v0.1 to v1.0 and current engineering focus.
- [FEATURES.md](FEATURES.md) — complete feature inventory with live status.
- [DECISIONS.md](DECISIONS.md) — architecture/product decisions and why they were made.
- [COMPATIBILITY_MATRIX.md](COMPATIBILITY_MATRIX.md) — app/device/Android-version test evidence.
- [ARCHITECTURE.md](ARCHITECTURE.md) — current Virtual Engine architecture.
- [TEST_PLAN.md](TEST_PLAN.md) — v0.1 isolation and regression criteria.

GitHub is the authoritative engineering record. Notion may be used later for broader research or product notes, but should not duplicate live implementation status.

## Current status

**NOT YET A WORKING APP VIRTUALIZER.**

Implemented:
- `app`: CloneApp host/dashboard scaffold.
- `ca-core`: virtual-instance contracts, metadata registry and per-instance host storage layout.
- `testapp`: controlled CA Test App with state-isolation probes.

Not implemented yet:
- loading a guest APK into CA;
- virtual PackageManager/ActivityManager behavior;
- guest activity/service/provider virtualization;
- IO/framework interception;
- native-library guest loading;
- per-instance notification translation;
- two simultaneously runnable copies of the same APK.

## v0.1 objective

Run the exact same unmodified `com.cloneapp.testapp` APK as two independent CA virtual users:

- Instance A: Alice / counter 10
- Instance B: Bob / counter 50

Both must survive app/process restart and device reboot without state leakage.

## Modules

### app
Package: `com.cloneapp.ca`

Prototype CA dashboard. It currently creates metadata entries for Alice and Bob and assigns each a virtual user ID and host-side storage root.

### ca-core
Package: `com.cloneapp.core`

Defines the engine boundary. `CapabilityReport` defaults every real virtualization capability to `false` so the product cannot accidentally claim unsupported functionality.

### testapp
Package: `com.cloneapp.testapp`

Controlled guest candidate containing:
- SharedPreferences
- SQLite
- internal file
- cache file
- notification
- UID/PID/path diagnostics

## Toolchain

- Android Gradle Plugin: 9.4.0
- Kotlin: AGP 9.4 built-in Kotlin support
- compileSdk: 36
- targetSdk: 36
- minSdk: 33
- JDK: 17

## Definition of GREEN for v0.1

The milestone is GREEN only when:
1. One unmodified Test App APK is imported once.
2. CA creates virtual users A and B without package resigning or package-name rewriting.
3. A and B launch independently.
4. Preferences do not leak.
5. SQLite data does not leak.
6. Private files do not leak.
7. Cache does not leak.
8. Provider state/URIs do not collide.
9. Notifications identify the correct virtual instance.
10. Process death and CA restart preserve both states.
11. Device reboot preserves both states.
12. Deleting A leaves B untouched.
13. Native `.so` loading passes after the native probe is added.

Until all relevant checks pass, CA must remain marked experimental.


## Build pipeline

Every push to `main` runs `.github/workflows/android-build.yml` on GitHub Actions.
The workflow builds and uploads two debug APK artifacts:

- `CloneApp-debug`
- `CA-Test-App-debug`

The build uses JDK 17, Gradle 9.6.0, Android Gradle Plugin 9.4.0, and compile/target SDK 36.

A green CI build means the source compiles and the APK artifacts were produced. It does **not** mean guest-app virtualization is working; that remains a separate physical-device validation gate.
