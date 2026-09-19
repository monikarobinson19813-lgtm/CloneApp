# CloneApp (CA) Feature Inventory

_Last updated: 2026-09-19_

This file tracks what CA is expected to support and the current implementation state.

## Status legend

- ✅ DONE
- 🟡 PARTIAL
- 🔵 IN PROGRESS
- ⏳ NEXT
- ⬜ BACKLOG
- 🧪 NEEDS TEST
- ⛔ BLOCKED / UNSUPPORTED

---

# 1. Core Platform

| Feature | Version target | Status | Notes |
|---|---:|---|---|
| Android app shell | v0.1 | ✅ DONE | `com.cloneapp.ca` |
| CA Test App | v0.1 | ✅ DONE | Controlled isolation probe |
| Core engine interface | v0.1 | ✅ DONE | `VirtualEngine` |
| Capability reporting | v0.1 | ✅ DONE | Defaults to false until proven |
| Instance registry | v0.1 | ✅ DONE | Metadata persistence |
| Virtual user IDs | v0.1 | 🟡 PARTIAL | IDs exist; guest runtime not wired |
| Per-instance storage root | v0.1 | 🟡 PARTIAL | Host dirs exist; real IO redirection not implemented |
| GitHub CI | v0.1 | ✅ DONE | Produces CloneApp + Test App APKs |
| Physical-device validation workflow | v0.1 | ⏳ NEXT | Pixel/Samsung first |

# 2. Guest App Import

| Feature | Version target | Status | Notes |
|---|---:|---|---|
| Select/import APK | v0.1 | ⏳ NEXT | First real engine feature |
| Parse package name | v0.1 | ⏳ NEXT | |
| Parse manifest/components | v0.1 | ⏳ NEXT | |
| Parse requested permissions | v0.1 | ⏳ NEXT | |
| Parse native libraries/ABI | v0.1 | ⬜ BACKLOG | |
| Detect split APK requirements | v0.2+ | ⬜ BACKLOG | |
| App Bundle/split handling | v0.2+ | ⬜ BACKLOG | |
| Detect `REQUIRE_SECURE_ENV` | v0.3+ | ⬜ BACKLOG | Virtual engine must respect it |
| Import from installed apps | v0.3+ | ⬜ BACKLOG | Subject to Android access rules |

# 3. Virtual Package / Component Model

| Feature | Version target | Status |
|---|---:|---|
| Virtual Package Manager | v0.1 | ⬜ BACKLOG |
| Virtual component registry | v0.1 | ⬜ BACKLOG |
| Intent resolution | v0.1 | ⬜ BACKLOG |
| Activity resolution | v0.1 | ⬜ BACKLOG |
| Service resolution | v0.1 | ⬜ BACKLOG |
| Provider resolution | v0.1 | ⬜ BACKLOG |
| Broadcast receiver handling | v0.2 | ⬜ BACKLOG |
| Deep-link routing | v0.3+ | ⬜ BACKLOG |

# 4. Process / Runtime Virtualization

| Feature | Version target | Status |
|---|---:|---|
| Stub process host | v0.1 | ⬜ BACKLOG |
| Guest process identity | v0.1 | ⬜ BACKLOG |
| Virtual Activity Manager | v0.1 | ⬜ BACKLOG |
| Virtual Service Manager | v0.1 | ⬜ BACKLOG |
| Process restart/recovery | v0.1 | ⬜ BACKLOG |
| Multi-process guest apps | v0.2+ | ⬜ BACKLOG |
| Background process policy | v0.5 | ⬜ BACKLOG |

# 5. Data Isolation

| Feature | Version target | Status | Notes |
|---|---:|---|---|
| SharedPreferences isolation | v0.1 | ⬜ BACKLOG | Must be proven Alice/Bob |
| SQLite isolation | v0.1 | ⬜ BACKLOG | |
| Internal files isolation | v0.1 | ⬜ BACKLOG | |
| Cache isolation | v0.1 | ⬜ BACKLOG | |
| ContentProvider isolation | v0.1 | ⬜ BACKLOG | |
| Native filesystem redirection | v0.1 | ⬜ BACKLOG | Hard requirement for real isolation |
| Per-instance app files | v0.1 | ⬜ BACKLOG | |
| Delete one instance safely | v0.1 | ⬜ BACKLOG | Must not affect siblings |
| Scoped-storage behavior | v0.3+ | ⬜ BACKLOG | |
| Media/file picker behavior | v0.4+ | ⬜ BACKLOG | |

# 6. Notifications / Background / Push

| Feature | Version target | Status |
|---|---:|---|
| Basic CA Test notification | v0.1 | ✅ DONE |
| Per-instance notification translation | v0.1 | ⬜ BACKLOG |
| Unique notification IDs/channels | v0.1 | ⬜ BACKLOG |
| Background worker/service support | v0.1–0.5 | ⬜ BACKLOG |
| Doze survival | v0.5 | ⬜ BACKLOG |
| Battery optimization handling | v0.5 | ⬜ BACKLOG |
| Firebase/FCM controlled test | v0.3–0.5 | ⬜ BACKLOG |
| Independent FCM identity per clone | v0.5 | ⬜ BACKLOG |

# 7. Permissions

| Feature | Version target | Status |
|---|---:|---|
| Read guest requested permissions | v0.1 | ⬜ BACKLOG |
| Instance permission display | v0.3 | ⬜ BACKLOG |
| Camera | v0.4 | ⬜ BACKLOG |
| Microphone | v0.4 | ⬜ BACKLOG |
| Contacts | v0.4 | ⬜ BACKLOG |
| Location | v0.4+ | ⬜ BACKLOG |
| Files/media | v0.4+ | ⬜ BACKLOG |
| Per-instance permission broker | v0.4+ | ⬜ BACKLOG |
| Contacts modes (shared/selected/none) | future | ⬜ BACKLOG |

# 8. Google Services / Integrity / Keystore

| Feature | Version target | Status | Rule |
|---|---:|---|---|
| Google Play Services compatibility | v0.3+ | ⬜ BACKLOG | Test, do not assume |
| Google Sign-In | v0.3+ | ⬜ BACKLOG | Controlled test first |
| FCM | v0.3+ | ⬜ BACKLOG | Controlled test first |
| Android Keystore behavior | v0.4+ | ⬜ BACKLOG | Document actual isolation |
| Play Integrity behavior | v0.4+ | ⬜ BACKLOG | No bypass |
| `REQUIRE_SECURE_ENV` compliance | v0.3+ | ⬜ BACKLOG | Do not virtualize opted-out apps |

# 9. Instance Manager / UX

| Feature | Version target | Status |
|---|---:|---|
| My Apps dashboard | v0.3 | 🟡 PARTIAL |
| App + instance count | v0.3 | ⬜ BACKLOG |
| Add App | v0.3 | ⬜ BACKLOG |
| Create Instance | v0.3 | 🟡 PARTIAL |
| Open | v0.3 | ⬜ BACKLOG |
| Stop | v0.3 | ⬜ BACKLOG |
| Restart | v0.3 | ⬜ BACKLOG |
| Rename | v0.3 | ⬜ BACKLOG |
| Settings | v0.3 | ⬜ BACKLOG |
| Permissions | v0.3 | ⬜ BACKLOG |
| Storage | v0.3 | ⬜ BACKLOG |
| Delete | v0.3 | 🟡 PARTIAL | Metadata/storage helper exists; runtime cleanup not proven |
| Status display | v0.3 | ⬜ BACKLOG |
| Instance color | later | ⬜ BACKLOG |
| Instance icon badge | later | ⬜ BACKLOG |
| Quick switch | later | ⬜ BACKLOG |
| Search/sort | later | ⬜ BACKLOG |
| Favorites | later | ⬜ BACKLOG |

# 10. Lifecycle / Reliability

| Feature | Version target | Status |
|---|---:|---|
| CA restart persistence | v0.1 | 🧪 NEEDS TEST |
| Guest process death recovery | v0.1 | ⬜ BACKLOG |
| Device reboot persistence | v0.1 | ⬜ BACKLOG |
| Network change handling | v0.2+ | ⬜ BACKLOG |
| Crash recovery | v0.7 | ⬜ BACKLOG |
| Update recovery | v0.7 | ⬜ BACKLOG |
| Watchdog/restart policy | v0.7 | ⬜ BACKLOG |

# 11. Updates / Migration

| Feature | Version target | Status |
|---|---:|---|
| Guest app version detection | v0.2+ | ⬜ BACKLOG |
| Guest app update | v0.7 | ⬜ BACKLOG |
| Data migration | v0.7 | ⬜ BACKLOG |
| Failed-update recovery | v0.7 | ⬜ BACKLOG |
| Split APK update handling | v0.7 | ⬜ BACKLOG |

# 12. Security

| Feature | Version target | Status |
|---|---:|---|
| CA PIN | v0.8 | ⬜ BACKLOG |
| Biometric unlock | v0.8 | ⬜ BACKLOG |
| Lock individual instance | v0.8 | ⬜ BACKLOG |
| Sensitive logging review | v0.8 | ⬜ BACKLOG |
| Local encryption where appropriate | v0.8 | ⬜ BACKLOG |
| Secure storage review | v0.8 | ⬜ BACKLOG |
| Threat model | v0.8 | ⬜ BACKLOG |
| Native/Profile fallback security level | future | ⬜ BACKLOG |

# 13. Backup / Export / Recovery

| Feature | Version target | Status |
|---|---:|---|
| Export instance | future | ⬜ BACKLOG |
| Import instance | future | ⬜ BACKLOG |
| Local backup | future | ⬜ BACKLOG |
| Restore | future | ⬜ BACKLOG |
| Cloud backup | future | ⬜ BACKLOG | Not required for core architecture |

# 14. Compatibility

| Feature | Version target | Status |
|---|---:|---|
| Compatibility matrix file | now | ✅ DONE |
| Compatibility tiering | v0.9 | 🟡 PARTIAL |
| Pixel validation | v0.1+ | ⬜ BACKLOG |
| Samsung validation | v0.1+ | ⬜ BACKLOG |
| OnePlus validation | v0.2+ | ⬜ BACKLOG |
| Xiaomi validation | v0.9 | ⬜ BACKLOG |
| Oppo validation | v0.9 | ⬜ BACKLOG |
| Vivo validation | v0.9 | ⬜ BACKLOG |
| Motorola validation | v0.9 | ⬜ BACKLOG |

# 15. Reference App Sequence

| App/test target | Planned stage | Status |
|---|---:|---|
| CA Test App x2 | v0.1 | 🔵 CURRENT GOAL |
| Simple third-party app x2 | v0.2 | ⬜ BACKLOG |
| Firebase/FCM controlled app x2 | v0.3–0.5 | ⬜ BACKLOG |
| WhatsApp x2 | v0.4 | ⬜ BACKLOG |
| WhatsApp x3 | v0.6 | ⬜ BACKLOG |
| WhatsApp x4 | v0.6 | ⬜ BACKLOG |
| Telegram multi-instance | later compatibility | ⬜ BACKLOG |
| Instagram multi-instance | later compatibility | ⬜ BACKLOG |
| Google-heavy apps | later compatibility | ⬜ BACKLOG |

# 16. Native/Profile Engine

| Feature | Version target | Status |
|---|---:|---|
| Android Managed/Work Profile research | completed architecture phase | ✅ DONE |
| Native/Profile engine implementation | future | ⬜ BACKLOG |
| Engine selector | future | ⬜ BACKLOG |
| Fallback for secure/incompatible apps | future | ⬜ BACKLOG |

---

# Out of scope / prohibited implementation directions

CA will not:
- bypass Play Integrity;
- bypass anti-tamper or app security restrictions;
- ignore `REQUIRE_SECURE_ENV`;
- depend on root for normal operation;
- rely on package resigning as the primary arbitrary-app architecture;
- claim isolation/compatibility that has not passed the relevant tests.
