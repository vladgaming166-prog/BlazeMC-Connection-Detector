# BlazeMC Connection Detector

Native **Velocity** plugin for **BlazeMC Network**. It automatically detects genuine Minecraft Java Premium sessions, keeps Cracked players on LimboAuth, and never merges or overwrites player identities.

This is **not** a Paper or Bukkit plugin.

| Field | Value |
| --- | --- |
| Plugin name | BlazeMC Connection Detector |
| Plugin ID | `blazemc-connection-detector` |
| Author | BlazeMC Network |
| Version | 1.1.0 |
| Primary target | Velocity `3.5.0-SNAPSHOT` |
| Compilation | Java 21 (`--release 21`) |
| Intended runtime | Java 21–25 |
| Backend | Paper with **modern** player forwarding |
| Proxy | `online-mode = false` |

Download the ready-to-install plugin JAR:

- Branch copy: [`dist/blazemc-connection-detector-1.1.0.jar`](https://github.com/vladgaming166-prog/BlazeMC-Connection-Detector/raw/cursor/blazemc-connection-detector-72e8/dist/blazemc-connection-detector-1.1.0.jar)

Or build it yourself:

```
build/libs/blazemc-connection-detector-1.1.0.jar
```

## What it does

The proxy stays in offline mode so Cracked players can join. Every connecting username is looked up against the official Mojang Java profile API. If a real Minecraft profile exists, Velocity is forced into **online authentication**. Ownership is proven only after Mojang/Microsoft session verification succeeds.

- `/bcd premium add` is **optional**. Genuine Premium players are detected automatically.
- A username is never treated as Premium just because a launcher, a claimed UUID, or a client said so.
- A valid Mojang session is mandatory for any username that has a Java Premium profile.
- Failed Premium authentication is **never** silently downgraded to Cracked login.
- HTTP 403/429/5xx, timeouts, and malformed responses are **never** treated as “this name is Cracked”.
- Existing Cracked profiles, including a historical `Blaz3Craft` offline UUID, are left untouched.
- Premium and Cracked identities are stored as separate UUID records. Nothing is copied, merged, or deleted.

## Tested versions

These are the versions this plugin was designed and compiled against. Do not treat this as a compatibility claim for every historical or future Velocity build.

| Component | Tested / targeted version |
| --- | --- |
| Velocity API | `3.5.0-SNAPSHOT` |
| Velocity public APIs used | `PreLoginEvent.PreLoginComponentResult.forceOnlineMode()`, `forceOfflineMode()`, `EventTask.resumeWhenComplete()`, `GameProfileRequestEvent`, `LoginEvent`, `PostLoginEvent`, `Player.isOnlineMode()` |
| LimboAuth | `1.1.14` (optional, reflection adapter) |
| LimboAPI | `1.1.27-SNAPSHOT` (optional, not modified) |
| Java | compiled with JDK 21, `--release 21` |

Runtime-compatible with other **Velocity 3.1+** builds that expose `PreLoginComponentResult.forceOnlineMode()` and `forceOfflineMode()`. `LoginEvent.getServerIdHash()` is used when present (Velocity 3.5+) and is not required.

**Not compatible with:**

- Velocity 3.0.x (no `forceOnlineMode()`)
- Velocity 4.x (different API line; not tested)
- Paper/Bukkit as the plugin loader

## Build

Unix:

```bash
./gradlew clean build
```

Windows:

```bat
gradlew.bat clean build
```

The shadowed plugin JAR is written to `build/libs/blazemc-connection-detector-1.1.0.jar`. Automated tests run as part of `build`.

Minimum Java to **run** the plugin: **Java 21**. BlazeMC's Java 25 runtime is supported.

## Install on the Velocity proxy

1. Stop Velocity.
2. **Remove** `PremiumOrCracked` (or any other plugin that also calls `forceOnlineMode()` / rewrites UUIDs). Running two selective-auth plugins will fight over the same events.
3. Keep **LimboAuth** and **LimboAPI** if you use them for Cracked `/register` and `/login`.
4. Copy `build/libs/blazemc-connection-detector-1.1.0.jar` into the Velocity `plugins/` folder.
5. Confirm `velocity.toml` has:
   - `online-mode = false`
   - modern player forwarding to Paper (`player-info-forwarding-mode = "modern"`)
   - a forwarding secret that is **not** logged and matches Paper
6. Start Velocity. The plugin creates `plugins/blazemc-connection-detector/config.yml` and `plugins/blazemc-connection-detector/database/identities.db`.
7. Grant permissions (LuckPerms Velocity, or equivalent):

```
blazemcconnection.admin
blazemcconnection.debug
blazemcconnection.premium.manage
blazemcconnection.reload
```

`blazemcconnection.admin` implies the other three.

### Paper modern forwarding

Do **not** let this plugin overwrite backend files. On each Paper server, `config/paper-global.yml` (or the equivalent Paper config for your build) must use Velocity modern forwarding and the **same** secret as Velocity:

```yaml
proxies:
  velocity:
    enabled: true
    online-mode: false
    secret: "<same value as Velocity forwarding.secret>"
```

The secret must never appear in this plugin's logs. After a verified Premium login, Velocity's player UUID and Paper's player UUID must be identical. Check with `/bcd diagnostics <player>` on the proxy and the player's UUID in Paper (`/data get entity @s UUID` or a UUID plugin).

### Recommended LimboAuth settings

Inspected LimboAuth `1.1.14` source (`AuthListener`, `LimboAuth#needAuth`, `LimboAuth#cacheAuthUser`, `PreAuthorizationEvent` / `PreRegisterEvent` `Result.BYPASS`).

Keep LimboAuth for Cracked password auth. Do **not** let it rewrite Premium UUIDs:

```yaml
# LimboAuth config (names follow LimboAuth 1.1.14)
ONLINE-MODE-NEED-AUTH: false
FORCE-OFFLINE-UUID: false
```

This plugin:

- Looks up every username asynchronously during `PreLoginEvent` before choosing online vs offline mode.
- Forces **online mode** for automatic or reserved Premium names at `PostOrder.LAST`.
- Forces **offline mode** only after a definitive Mojang `204`/`404` (no Java profile).
- Restores the Mojang UUID at `GameProfileRequestEvent` `LAST` if another plugin rewrote it.
- Calls `cacheAuthUser(Player)` and sets LimboAuth event result `BYPASS` only after a verified Premium session, so authenticated Premium players are not sent to `/register` or `/login`.
- Never reads or writes LimboAuth's database, password hashes, or internal classes.

If LimboAuth is absent, Cracked players still join in offline mode; they simply will not get LimboAuth's login limbo.

If LimboAuth `FORCE-OFFLINE-UUID` remains `true`, this plugin restores the Mojang UUID at `LAST`, but you should still disable that option. That is the safest resolution.

## Automatic Premium authentication

Administrators do **not** need to register every Premium player.

Connection flow:

1. Incoming username is normalized and validated.
2. If the name is already in the local Premium cache, online authentication is forced immediately (safe during Mojang outages).
3. Otherwise the official lookup `https://api.mojang.com/users/profiles/minecraft/{username}` runs off the Velocity event loop.
   - `200` with `id` + `name` → `PREMIUM_CANDIDATE`
   - `204` or `404` → `CRACKED_CONFIRMED`
   - `403`, `429`, `5xx`, timeout, malformed body → `MOJANG_API_UNAVAILABLE` (fail-closed)
4. Premium candidates call `PreLoginEvent.PreLoginComponentResult.forceOnlineMode()`. Velocity performs the real Mojang session. This plugin does not implement encryption or fake auth.
5. After login, the authenticated UUID must match the expected Mojang UUID. `Player.isOnlineMode()` and, on Velocity 3.5+, `LoginEvent.getServerIdHash()` must show that an online session occurred.
6. Success stores the Mojang UUID in SQLite so later outages still protect that username.
7. Failure **rejects** the connection. The client is never sent to Survival, never given an offline UUID, and never allowed to `/register` as that Premium name.

`/bcd premium add <username>` remains available as an optional reservation.

## Historical Cracked `Blaz3Craft` profile

The old Cracked profile stays in `identity_records` with its offline UUID. Once Mojang confirms `Blaz3Craft` is Premium:

- The Premium Mojang UUID is used for the authenticated connection.
- The old Cracked UUID is not forwarded.
- A Cracked client using `Blaz3Craft` is rejected.
- Nothing is deleted or migrated.

To keep using the **old Cracked inventory** later, connect with a **different username** that has no Mojang Java profile (or restore the offline UUID playerdata on Paper under that other username). This plugin will not rename or copy that data for you.

## Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/bcd` | any | Plugin information and help |
| `/bcd reload` | `blazemcconnection.reload` | Reload `config.yml` |
| `/bcd status` | `blazemcconnection.admin` or `debug` | Auth system and LimboAuth adapter status |
| `/bcd debug <player>` | `blazemcconnection.debug` | Live identity diagnostics |
| `/bcd lookup <username>` | `debug` or `admin` | Stored identity information |
| `/bcd check <username>` | `debug` or `admin` | Live Mojang profile classification |
| `/bcd cache refresh <username>` | `debug` or `admin` | Invalidate and refresh the profile cache |
| `/bcd diagnostics <player>` | `blazemcconnection.debug` | Full authentication diagnostics |
| `/bcd premium add <username>` | `blazemcconnection.premium.manage` | Optional Premium reservation via Mojang API |
| `/bcd premium remove <username>` | `premium.manage` | Remove reservation after `confirm` |
| `/bcd premium list` | `premium.manage` | List cached/reserved Premium accounts |
| `/bcd premium refresh <username>` | `premium.manage` | Refresh cached Mojang profile |
| `/bcd premium rename <old> <new>` | `premium.manage` | Update username, keep the same Mojang UUID |

`/bcd premium remove` requires a second `confirm` within 30 seconds. It only removes the reservation. Identity records and history are never deleted.

`/bcd diagnostics` reports username, account type, expected Mojang UUID, authenticated UUID, UUID source, online authentication, session verification, Premium profile status, LimboAuth status, backend forwarding UUID, and the final login decision. Passwords, access tokens, Microsoft credentials, and session secrets are never shown.

## Authentication flow

1. `PreLoginEvent` (`PostOrder.LAST`, async `EventTask`)
   - Cached or looked-up Premium username → `forceOnlineMode()`
   - Definitive Mojang not-found → `forceOfflineMode()`
   - API unavailable → deny (strict mode)
   - Client-sent UUID is ignored
2. Velocity performs Mojang session auth only for Premium candidates
3. `GameProfileRequestEvent` (`LAST`) restores the Mojang UUID if another plugin replaced it. Offline UUIDs are never generated for Premium players.
4. `LoginEvent`
   - Premium candidate without an online session → kick
   - Authenticated UUID ≠ expected Mojang UUID → kick
   - Cracked confirmed → original offline UUID kept
5. `PostLoginEvent` notifies LimboAuth to skip `/register` for verified Premium players
6. Optional LimboAuth adapter bypasses Cracked login **only** after step 4 succeeded as Premium

## Storage

SQLite file `database/identities.db` (configurable) with:

- `premium_accounts` — canonical username + Mojang UUID (manual reservations **and** automatically verified Premium identities)
- `identity_records` — separate Premium and Cracked UUID rows
- `connection_history` — authentication outcomes

Passwords, Microsoft credentials, access tokens, and session tokens are never stored.

## Configuration

See the generated `config.yml`. Every documented key is implemented.

New 1.1.0 keys:

- `premium-detection.*` — automatic detection, fail-closed, UUID verification
- `mojang.positive-cache-seconds` / `negative-cache-seconds` (strict mode forces negative cache to `0`)
- `cracked.require-definitive-profile-not-found`
- `uuid.verify-backend-forwarding`, `prevent-premium-offline-uuid`, `preserve-existing-cracked-identities`, `never-auto-migrate`

Legacy `authentication.premium.*` and `authentication.cracked.*` keys still parse so existing `config.yml` files keep working.

`general.language` currently ships English only (`en`).

## Security properties

- Automatically detected Premium usernames cannot complete the Cracked path
- Offline UUID spoofing cannot unlock a Premium name
- Duplicate username or UUID reservations are rejected
- Concurrent Premium logins are denied (with a handshake timeout so a failed attempt cannot block the real account)
- Mojang API outages fail closed for **unknown** names; cached Premium identities stay forced online
- Database writes use transactions, prepared statements, WAL, and a process lock

## Tests

`./gradlew test` covers mocked HTTP and a Velocity-independent authentication engine, including:

1. Automatic Premium detection without manual registration
2. Newly joining Premium players receiving the Mojang UUID
3. Cached Premium usernames forcing online authentication
4. Cracked launchers using a Premium username being rejected
5. Invalid Premium UUIDs being rejected
6. Failed Premium sessions never falling back to offline mode
7. Definitive Cracked usernames staying offline
8. HTTP 429 / 5xx / timeout never becoming Cracked
9. Strict fail-closed handling of unknown profiles
10. Username changes preserving the Mojang UUID
11. Backend forwarding UUID matching the authenticated UUID
12. Existing Cracked UUIDs remaining unchanged
13. Premium and Cracked profiles never merging
14. Concurrent connections
15. Cached Premium protection during API outages
16. LimboAuth not replacing authenticated Premium UUIDs
17. Database records surviving restart
18. Verified Premium connections never using an offline UUID

These tests **do not** perform a live Microsoft/Mojang login. Use [MANUAL-TESTING.md](MANUAL-TESTING.md) with a real Premium account and a Cracked client.

## Logging

Examples:

```
[BlazeMC Connection Detector] Automatic Premium profile detected: Blaz3Craft
[BlazeMC Connection Detector] Forcing Mojang session authentication.
[BlazeMC Connection Detector] Premium session successfully verified.
[BlazeMC Connection Detector] Premium UUID verified: 11111111-1111-4111-8111-111111111111
[BlazeMC Connection Detector] Cracked profile confirmed: ExamplePlayer
[BlazeMC Connection Detector] Premium impersonation attempt rejected.
[BlazeMC Connection Detector] Mojang API unavailable. Connection rejected safely.
```
