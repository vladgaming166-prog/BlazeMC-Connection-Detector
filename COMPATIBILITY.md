# Compatibility

BlazeMC Connection Detector is a native Velocity plugin. It uses only the public Velocity API.

## Tested

| Component | Version | Notes |
| --- | --- | --- |
| Velocity API | 3.5.0-SNAPSHOT | Compile and annotation-processor dependency |
| Java | 21 (`--release 21`) | Intended runtime Java 21–25 |
| LimboAuth | 1.1.14 | Optional reflection adapter |
| LimboAPI | 1.1.27-SNAPSHOT | Optional; not loaded or modified by this plugin |

Automated tests run against the plugin's own authentication engine, SQLite storage, Mojang HTTP client (local mock server), payload parser, and LimboAuth bypass policy. They do not start a live Velocity process and do not perform a real Microsoft session.

## Velocity 3.x

Public APIs this plugin depends on:

- `PreLoginEvent` / `PreLoginComponentResult.forceOnlineMode()` / `forceOfflineMode()`
- `EventTask.resumeWhenComplete(CompletableFuture)` for asynchronous PreLogin lookups
- `GameProfileRequestEvent` and `GameProfile.withId(UUID)` (restore only; never invents a profile)
- `LoginEvent`, `PostLoginEvent`, `DisconnectEvent`
- `Player.isOnlineMode()`, `Player.getUniqueId()`, `Player.getGameProfile()`
- `SimpleCommand`, `@Plugin`, `@Subscribe`, `PostOrder`

`forceOnlineMode()` / `forceOfflineMode()` exist in the Velocity 3.4 and 3.5 public javadocs and were introduced for hybrid offline proxies in the 3.1 line.

`LoginEvent.getServerIdHash()` is Velocity 3.5+. The plugin reads it only when the method exists. On older 3.x proxies, `Player.isOnlineMode()` after `forceOnlineMode()` is the session evidence.

This plugin does **not** claim support for:

- Velocity 3.0.x
- Velocity 4.x
- Paper, Bukkit, BungeeCord, or Waterfall as the plugin host

One JAR is used. Separate build variants are unnecessary for the public 3.1–3.5 API surface above. If a future Velocity release removes `forceOnlineMode()`, that build is unsupported until the plugin is updated.

## LimboAuth / LimboAPI

Inspected artifacts:

- [LimboAuth 1.1.14 `AuthListener`](https://github.com/Elytrium/LimboAuth/blob/1.1.14/src/main/java/net/elytrium/limboauth/listener/AuthListener.java)
- [LimboAuth 1.1.14 `LimboAuth#cacheAuthUser` / `#needAuth`](https://github.com/Elytrium/LimboAuth/blob/1.1.14/src/main/java/net/elytrium/limboauth/LimboAuth.java)
- `PreAuthorizationEvent` / `PreRegisterEvent` with `TaskEvent.Result.BYPASS`

Integration method: optional adapter, no compile dependency, no LimboAuth database access.

LimboAuth 1.1.14 already skips cracked auth when `player.isOnlineMode()` is true and the stored hash is empty. This plugin still calls `cacheAuthUser` and `BYPASS` so Premium players are not sent to the auth limbo if `ONLINE-MODE-NEED-AUTH` is enabled.

Exact conflict: LimboAuth's `FORCE-OFFLINE-UUID` rewrites Premium UUIDs at `GameProfileRequestEvent` `FIRST`. Safest resolution: set `FORCE-OFFLINE-UUID: false`. This plugin also restores the authenticated Mojang UUID at `LAST` if another plugin changed it. It does not generate a new identity.

## PremiumOrCracked

Unload PremiumOrCracked. Both plugins implement selective online-mode. Two listeners at different `PostOrder` values will produce undefined Premium/Cracked classification.

## Forwarding

Designed for Velocity **modern** forwarding to Paper. The UUID Velocity forwards is the UUID this plugin protected:

- Premium: Mojang UUID from the authenticated session
- Cracked: Velocity's offline UUID (unchanged)

Paper `proxies.velocity.online-mode` should remain `false` because the proxy itself is offline-mode; individual Premium connections are still authenticated by Velocity before forwarding. The forwarded UUID is the authenticated one.
