# CA v0.1 Virtual Engine Architecture

## Decision

Virtualization-first proof of concept, while preserving a future Native/Profile engine as a second compatibility backend.

## Planned v0.1 components

### VirtualPackageManager
Owns imported guest package metadata, manifest parsing, components, permissions requested by the guest and virtual installation state.

### VirtualUserManager
Assigns CA virtual user IDs. The same guest package can have multiple virtual users.

### VirtualProcessManager
Maps guest application/process requests onto CA-owned Android processes/stubs while preserving virtual identity.

### VirtualActivityManager
Resolves guest activities and launches them via host stub components.

### VirtualServiceManager
Tracks guest service lifecycle separately per virtual user.

### VirtualProviderManager
Prevents ContentProvider authority/data collisions and routes provider access to the appropriate virtual user.

### StorageRedirector
Maps guest-private filesystem accesses into per-virtual-user roots. Host-side directories alone are not sufficient; guest framework/native IO must actually be redirected before this capability can be marked supported.

### VirtualNotificationManager
Rewrites notification IDs/channels/titles where required so two guest instances cannot overwrite one another and the user can identify the source instance.

### NativeCompatibilityLayer
Later v0.1 probe for ARM64 guest `.so` loading and path behavior.

## Non-goals

- bypassing Play Integrity;
- bypassing app anti-tamper/security mechanisms;
- ignoring `REQUIRE_SECURE_ENV`;
- hiding virtualization from applications;
- root-only hooks;
- WhatsApp testing before the controlled POC is green.

## Abandon/reassess gate

Re-open the architecture decision if the controlled Test App cannot be made stable on current stock Android without relying on obsolete target SDK behavior, root, or fragile hidden-API patches that cannot survive current Android releases.
