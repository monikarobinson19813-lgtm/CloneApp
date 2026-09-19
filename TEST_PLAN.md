# CloneApp CA v0.1 Test Plan

## Controlled values

Instance A:
- Name: Alice
- Counter: 10

Instance B:
- Name: Bob
- Counter: 50

## Isolation tests

| Test | A | B | Pass condition |
|---|---|---|---|
| SharedPreferences | Alice / 10 | Bob / 50 | no cross-read |
| SQLite | Alice / 10 | Bob / 50 | separate rows/db view |
| Internal file | Alice / 10 | Bob / 50 | separate contents |
| Cache | A marker | B marker | separate contents |
| UID model | record real + virtual identity | record real + virtual identity | expected model documented |
| ContentProvider | A data | B data | no authority/state collision |
| Notifications | Alice | Bob | distinguishable and non-overwriting |
| Process kill | restore A | restore B | both survive |
| CA restart | restore A | restore B | both survive |
| Device reboot | restore A | restore B | both survive |
| Delete A | deleted | intact | B untouched |

## Compatibility sequence after GREEN

1. Simple third-party app x2.
2. Firebase/FCM controlled app x2.
3. WhatsApp x2.
4. WhatsApp x3 only if x2 is stable.
5. WhatsApp x4 only if x3 is stable.
