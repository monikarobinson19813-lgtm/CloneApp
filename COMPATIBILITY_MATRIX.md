# CloneApp (CA) Compatibility Matrix

_Last updated: 2026-09-22_

This file records tested compatibility. Do not mark an app compatible until the stated tests have actually passed.

## Tier definitions

- **Tier A** — reliable in CA Virtual Engine with core functions working.
- **Tier B** — usable in CA Virtual Engine with documented limitations.
- **Tier C** — unsuitable for Virtual Engine but potentially supportable through the future Native/Profile engine.
- **Tier D** — unsupported.
- **TBD** — not tested yet.

## Result definitions

- ✅ PASS
- ⚠️ PARTIAL
- ❌ FAIL
- ⬜ NOT TESTED
- N/A — not relevant

---

# Current v0.1 evidence basis

- Tested main SHA: `5a0af17056dcb4802e8dda3edcadf415e4e411d1`
- Android Build Run: `35691541115`
- Jobs: `build` SUCCESS; `Android 16 emulator smoke` SUCCESS
- Emulator: Android 16 / API 36 / x86_64 / pixel_7
- Instrumentation: 15 expected / 15 executed / 15 passed / 0 skipped / 0 failed
- Every single-method invocation produced `OK (1 test)` and positive exact-count-guard acceptance.
- Corrected guard ancestry commit `7c162e88b43b3a872a7029c236cd181a68f022ee` is an ancestor of the tested main SHA.
- Native ARM64 APK-content status: **N/A for these APKs** — demonstrated from Run `35809965282` / SHA `9d024b7b1fb37469cc95a7c8ef23d29ad31c0c4d` artifacts. Actual ZIP listings of CloneApp `app-debug.apk` (artifact `10729208466`, APK SHA-256 `2c0fdd03eacb2b3afb08135b642a497399c8608916bd3c0438fe7b2389b3b080`) and CA Test App `testapp-debug.apk` (artifact `10729496558`, APK SHA-256 `124efd0ecc2da4469e70e485070209b1aaba42aa6d14377e977d1a4d30c4cffa`) contain **zero `lib/` entries**. Therefore these APKs bundle no native `.so` payload to execute; this does not demonstrate native ARM64 code execution.
- Final #55 current-SHA physical-device exit remains pending.

Required isolation qualifier:

**Isolation is guest-cooperative: the CA Test App chooses its storage from vUser; CloneApp does not enforce it.**

---

# App Matrix

| App | App version | Android | OEM/device | Engine | Instances | Launch | Login | Prefs/DB/Files | Notifications | FCM | Camera | Mic | Contacts | Media | Background | Reboot | Update | Integrity / secure-env behavior | Tier | Result / notes |
|---|---|---|---|---|---:|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| CA Test App | SHA `5a0af170…` | Android 16 / API 36 | pixel_7 x86_64 emulator | Virtual | 2 | ✅ | N/A | ✅ | ✅ | N/A | N/A | N/A | N/A | N/A | ⚠️ | ⬜ | ⬜ | N/A | TBD | Run `35691541115`; controlled Test App only; current-SHA physical exit pending |
| Simple third-party app | TBD | TBD | TBD | Virtual | 2 | ⬜ | ⬜ | ⬜ | ⬜ | TBD | TBD | TBD | TBD | TBD | ⬜ | ⬜ | ⬜ | TBD | TBD | Select after v0.1 GREEN |
| Firebase/FCM controlled app | internal test build | TBD | TBD | Virtual | 2 | ⬜ | TBD | ⬜ | ⬜ | ⬜ | N/A | N/A | N/A | N/A | ⬜ | ⬜ | ⬜ | TBD | TBD | Controlled push identity test |
| WhatsApp | TBD | TBD | TBD | Virtual | 2 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | TBD | Test only after controlled POCs |
| WhatsApp | TBD | TBD | TBD | Virtual | 3 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | TBD | Only after x2 stable |
| WhatsApp | TBD | TBD | TBD | Virtual | 4 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | TBD | Only after x3 stable |
| Telegram | TBD | TBD | TBD | Virtual | TBD | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | TBD | Future |
| Instagram | TBD | TBD | TBD | Virtual | TBD | ⬜ | ⬜ | ⬜ | ⬜ | TBD | ⬜ | ⬜ | TBD | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | TBD | Future |
| Google-heavy app | TBD | TBD | TBD | Virtual | TBD | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | TBD | TBD | TBD | TBD | ⬜ | ⬜ | ⬜ | ⬜ | TBD | Controlled test required |

---

# Device Matrix

| Android | Device/OEM | CA build | Test App x2 | Third-party x2 | FCM x2 | WhatsApp x2 | Notes |
|---|---|---|---|---|---|---|---|
| Android 16 / API 36 | pixel_7 x86_64 emulator | `5a0af170…` / Run `35691541115` | ✅ | ⬜ | ⬜ | ⬜ | Current main emulator acceptance basis |
| Android 16 | Samsung | TBD | ⬜ | ⬜ | ⬜ | ⬜ | Second reference target |
| Android 16 | OnePlus | TBD | ⬜ | ⬜ | ⬜ | ⬜ | Third validation target |
| Android 15 | Pixel/Samsung/OnePlus | TBD | ⬜ | ⬜ | ⬜ | ⬜ | Later regression |
| Android 14 | OnePlus NE2211 | historical SHA `6df51068…` / Run `35678438293` | ✅ | ⬜ | ⬜ | ⬜ | Historical D9 physical PASS; not the final current-main #55 basis |
| Android 13 | Pixel/Samsung/OnePlus | TBD | ⬜ | ⬜ | ⬜ | ⬜ | minSdk regression |

---

# CA Test App v0.1 Detailed Matrix

| Test | Instance A | Instance B | Status | Evidence / notes |
|---|---|---|---|---|
| Display name | Alice | Bob | ✅ | Run `35691541115`; guest launch + virtual identity |
| Counter | 10 | 50 | ✅ | StorageIsolationRuntimeTest; 0.168s |
| SharedPreferences | Alice/10 | Bob/50 | ✅ | StorageIsolationRuntimeTest; 0.168s |
| SQLite | Alice/10 | Bob/50 | ✅ | StorageIsolationRuntimeTest; 0.168s |
| Internal file | Alice/10 | Bob/50 | ✅ | StorageIsolationRuntimeTest; 0.168s |
| Cache | Alice/10 | Bob/50 | ✅ | StorageIsolationRuntimeTest; 0.168s |
| Provider state | A-only | B-only | ✅ | ProviderIsolationRuntimeTest; 0.280s |
| Provider routing | Alice virtual authority | Bob virtual authority | ✅ | ProviderAuthorityRoutingRuntimeTest; 0.837s |
| Notification identity | Alice | Bob | ✅ | NotificationTranslationRuntimeTest; 0.212s |
| Process death bookkeeping | lifecycle/death reconciled | lifecycle/death reconciled | ✅ | GuestProcessHostRuntimeTest; 1.235s |
| CA restart | persisted controlled state | persisted controlled state | ✅ | import/metadata/registry/storage persistence tests in Run `35691541115` |
| Emulator reboot (API 36 x86_64) | persisted controlled state | persisted controlled state | ✅ | Run `35809965282`; `adb reboot`; emulator runner independently logged `Boot completed in 28519 ms`; post-reboot guarded storage test passed |
| Device reboot | current-main physical run pending | current-main physical run pending | ⬜ | Final #55 physical-device gate |
| Delete A | deleted | untouched | ✅ | Storage + provider deletion assertions |
| Native ARM64 probe | N/A | N/A | N/A | Demonstrated APK-content evidence from Run `35809965282`: actual `app-debug.apk` and `testapp-debug.apk` ZIP listings contain zero `lib/` entries; no bundled native `.so` payload exists in these APKs, so native ARM64 execution is not demonstrated |

---

# Recording rules

When adding a result:
1. Record exact app version.
2. Record Android version.
3. Record device/OEM/model.
4. Record CA commit/build.
5. Record engine mode.
6. Record number of simultaneous instances.
7. Separate launch success from login/push/background success.
8. Record known limitations.
9. Do not infer x3/x4 compatibility from x2.
10. Do not infer another OEM's compatibility from one device.
