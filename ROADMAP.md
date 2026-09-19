# CloneApp (CA) Roadmap

_Last updated: 2026-09-19_

This file is the authoritative high-level development roadmap for CloneApp.

## Product goal

CloneApp is an Android multi-instance platform that lets a user run multiple isolated instances of supported apps from one CA dashboard.

Core principles:
- stock Android first;
- no root requirement for the core product;
- no package resigning/rewrite as the primary architecture;
- virtualization-first for the main multi-instance engine;
- preserve a future native Android Profile/Work Profile engine as a compatibility/security fallback;
- do not bypass Play Integrity, anti-tamper systems, or `REQUIRE_SECURE_ENV`;
- incremental implementation: implement → commit → build → test → GREEN → next.

## Status legend

- ✅ DONE — implemented and accepted for this stage.
- 🟡 PARTIAL — foundation exists but the feature is not complete.
- 🔵 IN PROGRESS — current engineering focus.
- ⏳ NEXT — next planned work.
- ⬜ BACKLOG — planned later.
- 🧪 TEST — implementation exists but needs physical-device validation.
- ⛔ BLOCKED — waiting on a technical dependency or decision.

---

# Current position

**Current milestone:** v0.1 — Virtual Engine POC

**Current engineering focus:** guest APK import + package/manifest discovery.

**Latest CI checkpoint:** Android Build #3 — GREEN.

**Important:** a GREEN CI build currently means the Android project compiles and APK artifacts are produced. It does not mean guest-app virtualization is working.

---

# v0.1 — Virtual Engine POC

## Objective

Run the exact same unmodified `com.cloneapp.testapp` APK as two independent CA virtual users:

- Instance A: Alice / counter 10
- Instance B: Bob / counter 50

The two instances must remain isolated across:
- SharedPreferences;
- SQLite;
- private files;
- cache;
- providers;
- notifications;
- process death;
- CA restart;
- device reboot.

## Workstream

| Item | Status |
|---|---|
| Repository + Android project | ✅ DONE |
| GitHub Actions build pipeline | ✅ DONE |
| CloneApp host scaffold | ✅ DONE |
| CA Test App | ✅ DONE |
| VirtualEngine contract | ✅ DONE |
| Instance metadata registry | ✅ DONE |
| Virtual user IDs | 🟡 PARTIAL |
| Per-instance host storage roots | 🟡 PARTIAL |
| Guest APK import | ⏳ NEXT |
| Guest manifest/package parser | ⏳ NEXT |
| Virtual Package Manager | ⬜ BACKLOG |
| Stub/guest process hosting | ⬜ BACKLOG |
| Virtual Activity Manager | ⬜ BACKLOG |
| Virtual Service Manager | ⬜ BACKLOG |
| Virtual Provider Manager | ⬜ BACKLOG |
| Filesystem/IO redirection | ⬜ BACKLOG |
| Notification translation | ⬜ BACKLOG |
| Native `.so` guest loading probe | ⬜ BACKLOG |
| Alice/Bob simultaneous launch | ⬜ BACKLOG |
| Alice/Bob isolation test | ⬜ BACKLOG |
| Process-kill recovery | ⬜ BACKLOG |
| Device reboot persistence | ⬜ BACKLOG |
| Delete A without touching B | ⬜ BACKLOG |
| Pixel physical validation | ⬜ BACKLOG |
| Samsung physical validation | ⬜ BACKLOG |

## v0.1 exit criteria

v0.1 is GREEN only when all relevant tests in `TEST_PLAN.md` pass on a real device.

---

# v0.2 — Real App x2

## Objective

Run a simple third-party app twice using the CA Virtual Engine.

Test:
- launch;
- login;
- local state;
- network;
- WebView if used;
- files;
- notifications;
- background behavior;
- process recreation;
- reboot;
- app update.

No WhatsApp yet.

---

# v0.3 — Instance Manager

## Objective

Turn the prototype into a usable multi-instance manager.

Planned controls:
- Add App;
- Create Instance;
- Open;
- Stop;
- Restart;
- Rename;
- Settings;
- Permissions;
- Storage;
- Delete;
- instance status.

Planned dashboard:
- My Apps;
- app icon/name;
- number of instances;
- instance list;
- compatibility status.

---

# v0.4 — WhatsApp x2

## Objective

Test current WhatsApp in two CA virtual instances.

Validation areas:
- registration/login;
- messages;
- FCM/push;
- notifications;
- background behavior;
- contacts;
- camera;
- microphone;
- calls;
- media/files;
- linked devices;
- reboot;
- app updates;
- Keystore behavior;
- integrity/security restrictions.

No bypasses. If WhatsApp or Android blocks the environment, document the limitation.

---

# v0.5 — Notifications + Background Reliability

Focus:
- FCM reliability;
- Doze;
- battery optimization;
- background service/job behavior;
- per-instance notification identity;
- restart/recovery.

---

# v0.6 — Multiple Instances

Objective:
- x3;
- x4;
- higher instance counts where technically sustainable.

Only advance from x2 → x3 → x4 after the previous count is stable.

Benchmark:
- RAM;
- CPU;
- storage;
- battery;
- startup time;
- background pressure;
- crash/recovery behavior.

---

# v0.7 — Updates + Recovery

Focus:
- guest app updates;
- state migration;
- interrupted update recovery;
- engine crash recovery;
- data consistency;
- rollback strategy where practical.

---

# v0.8 — Security

Focus:
- CA PIN;
- biometric lock;
- instance lock;
- local encryption where appropriate;
- storage hardening;
- sensitive-log reduction;
- clipboard/file cleanup policy;
- secure delete behavior where Android allows;
- audit of virtual-isolation boundary vs native-isolation boundary.

---

# v0.9 — Compatibility

Focus:
- OEM matrix;
- app matrix;
- Android version matrix;
- known limitations;
- compatibility tiers.

Initial OEM order:
1. Pixel;
2. Samsung;
3. OnePlus;
4. Xiaomi;
5. Oppo;
6. Vivo;
7. Motorola.

---

# v1.0 — Production Release Gate

v1.0 requires:
- stable instance lifecycle;
- repeatable GREEN regression suite;
- documented compatibility;
- recovery from common failures;
- security review;
- production UI;
- update strategy;
- release signing;
- crash/telemetry strategy that preserves user privacy.

---

# Architecture track

CA is intentionally hybrid:

```text
CloneApp
│
├── CA Virtual Engine
│   └── primary path for many instances
│
└── CA Native Engine
    └── future Android Profile/Work Profile fallback
```

The Native Engine is not part of v0.1. It becomes relevant for apps that are unsuitable for virtualization, including apps that require a secure environment or depend heavily on OS-level identity/integrity behavior.

---

# Development discipline

1. Implement one bounded capability.
2. Commit to GitHub.
3. Let GitHub Actions build automatically.
4. Do not continuously poll builds during normal development.
5. Check build state at important checkpoints or when physical testing is required.
6. RED → inspect logs → fix → push.
7. GREEN → test on device where applicable.
8. Mark feature DONE only after its required test passes.
