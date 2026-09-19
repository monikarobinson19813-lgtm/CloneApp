# CloneApp (CA) Compatibility Matrix

_Last updated: 2026-09-19_

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

# App Matrix

| App | App version | Android | OEM/device | Engine | Instances | Launch | Login | Prefs/DB/Files | Notifications | FCM | Camera | Mic | Contacts | Media | Background | Reboot | Update | Integrity / secure-env behavior | Tier | Result / notes |
|---|---|---|---|---|---:|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| CA Test App | current repo build | TBD | TBD | Virtual | 2 | ⬜ | N/A | ⬜ | ⬜ | N/A | N/A | N/A | N/A | N/A | ⬜ | ⬜ | ⬜ | N/A | TBD | v0.1 target: Alice=10 / Bob=50 |
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
| Android 16 | Pixel reference device | TBD | ⬜ | ⬜ | ⬜ | ⬜ | First reference target |
| Android 16 | Samsung | TBD | ⬜ | ⬜ | ⬜ | ⬜ | Second reference target |
| Android 16 | OnePlus | TBD | ⬜ | ⬜ | ⬜ | ⬜ | Third validation target |
| Android 15 | Pixel/Samsung/OnePlus | TBD | ⬜ | ⬜ | ⬜ | ⬜ | Later regression |
| Android 14 | Pixel/Samsung/OnePlus | TBD | ⬜ | ⬜ | ⬜ | ⬜ | Later regression |
| Android 13 | Pixel/Samsung/OnePlus | TBD | ⬜ | ⬜ | ⬜ | ⬜ | minSdk regression |

---

# CA Test App v0.1 Detailed Matrix

| Test | Instance A | Instance B | Status | Evidence / notes |
|---|---|---|---|---|
| Display name | Alice | Bob | ⬜ | |
| Counter | 10 | 50 | ⬜ | |
| SharedPreferences | Alice/10 | Bob/50 | ⬜ | |
| SQLite | Alice/10 | Bob/50 | ⬜ | |
| Internal file | Alice/10 | Bob/50 | ⬜ | |
| Cache | A marker | B marker | ⬜ | |
| Provider state | A-only | B-only | ⬜ | |
| Notification identity | Alice | Bob | ⬜ | |
| Process kill recovery | restore | restore | ⬜ | |
| CA restart | restore | restore | ⬜ | |
| Device reboot | restore | restore | ⬜ | |
| Delete A | deleted | untouched | ⬜ | |
| Native library probe | pass | pass | ⬜ | |

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
