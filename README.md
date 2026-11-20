# BlazeMC Connection Detector

Native **Velocity** plugin for **BlazeMC Network**. It identifies Minecraft Premium and Cracked connections on an offline-mode proxy without allowing Premium username impersonation and without merging player data.

This is **not** a Paper or Bukkit plugin.

| Field | Value |
| --- | --- |
| Plugin name | BlazeMC Connection Detector |
| Plugin ID | `blazemc-connection-detector` |
| Author | BlazeMC Network |
| Version | 1.0.0 |
| Primary target | Velocity `3.5.0-SNAPSHOT` |
| Compilation | Java 21 (`--release 21`) |
| Intended runtime | Java 21–25 |
| Backend | Paper with **modern** player forwarding |
| Proxy | `online-mode = false` |

Built JAR:

```
build/libs/blazemc-connection-detector-1.0.0.jar
```

## What it does

The proxy stays in offline mode so cracked players can join. Administrators reserve real Premium usernames. Only those reserved names are forced through Velocity's Mojang/Microsoft session authentication.

- A username is **never** treated as Premium just because it exists on Mojang, a client claimed it, or a UUID was sent.
- A valid Mojang session is mandatory for reserved Premium names.
- Failed Premium authentication is **never** silently downgraded to cracked login.
- Existing cracked profiles, including a historical `Blaz3Craft` offline UUID, are left untouched.
- Premium and cracked identities are stored as separate UUID records. Nothing is copied or merged.

## Tested versions

These are the versions this plugin was designed and compiled against. Do not treat this as a compatibility claim for every historical or future Velocity build.

| Component | Tested / targeted version |
| --- | --- |
| Velocity API | `3.5.0-SNAPSHOT` |
| Velocity public APIs used | `PreLoginEvent.PreLoginComponentResult.forceOnlineMode()`, `forceOfflineMode()`, `GameProfileRequestEvent`, `LoginEvent`, `Player.isOnlineMode()` |
| LimboAuth | `1.1.14` (optional, reflection adapter) |
| LimboAPI | `1.1.27-SNAPSHOT` (optional, not modified) |
| Java | compiled with JDK 21, `--release 21` |

Runtime-compatible with other **Velocity 3.1+** builds that expose `PreLoginComponentResult.forceOnlineMode()` and `forceOfflineMode()`. `LoginEvent.getServerIdHash()` is used reflectively when present (Velocity 3.5+) and is not required.

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

The shadowed plugin JAR is written to `build/libs/blazemc-connection-detector-1.0.0.jar`. Automated tests run as part of `build`.

## Install on the Velocity proxy

1. Stop Velocity.
2. **Remove** `PremiumOrCracked` (or any other plugin that also calls `forceOnlineMode()` / rewrites UUIDs). Running two selective-auth plugins will fight over the same events.
3. Keep **LimboAuth** and **LimboAPI** if you use them for cracked `/register` and `/login`.
4. Copy `build/libs/blazemc-connection-detector-1.0.0.jar` into the Velocity `plugins/` folder.
5. Confirm `velocity.toml` (or your Velocity config) has:
   - `online-mode = false`
   - modern player forwarding to Paper
6. Start Velocity. The plugin creates `plugins/blazemc-connection-detector/config.yml` and `plugins/blazemc-connection-detector/database/identities.db`.
7. Grant permissions (LuckPerms Velocity, or equivalent):

```
blazemcconnection.admin
blazemcconnection.debug
blazemcconnection.premium.manage
blazemcconnection.reload
```

`blazemcconnection.admin` implies the other three.

### Recommended LimboAuth settings

Inspected LimboAuth `1.1.14` source (`AuthListener`, `LimboAuth#needAuth`, `LimboAuth#cacheAuthUser`, `PreAuthorizationEvent` / `PreRegisterEvent` `Result.BYPASS`).

Keep LimboAuth for cracked password auth. Do **not** let it rewrite Premium UUIDs:

```yaml
# LimboAuth config (names follow LimboAuth 1.1.14)
ONLINE-MODE-NEED-AUTH: false
FORCE-OFFLINE-UUID: false
```

This plugin:

- Forces **offline mode** for unreserved names at `PostOrder.LAST`, so LimboAuth cannot upgrade an unreserved Mojang username to online-mode.
- Forces **online mode** for reserved Premium names at `PostOrder.LAST`.
- Restores the Mojang UUID at `GameProfileRequestEvent` `LAST` if another plugin rewrote it.
- Calls `cacheAuthUser(Player)` and sets LimboAuth event result `BYPASS` only after a verified Premium session, so authenticated Premium players are not sent to `/register` or `/login`.
- Never reads or writes LimboAuth's database, password hashes, or internal classes.

If LimboAuth is absent, cracked players still join in offline mode; they simply will not get LimboAuth's login limbo.

## Register your real Premium account

From the Velocity console (or an in-game source with `blazemcconnection.premium.manage`):

```
bcd premium add Blaz3Craft
```

The plugin queries `https://api.mojang.com/users/profiles/minecraft/Blaz3Craft`, stores the **canonical username** and **Mojang UUID**, and reserves that name.

Then connect with the genuine Microsoft/Minecraft account named `Blaz3Craft`.

Expected console logs:

```
[BlazeMC Connection Detector] PREMIUM connection verified: Blaz3Craft
[BlazeMC Connection Detector] UUID verification successful.
```

If a cracked client uses that username:

- Velocity is told to require a Mojang session.
- The connection is rejected. It is never accepted as cracked.

If the Mojang API is down while you run `add`/`refresh`/`rename`, the command fails closed. An API error is **not** treated as proof that the account is cracked. Already-reserved accounts keep working from the local SQLite cache.

## Verify that Premium and Cracked UUIDs stay separate

Your existing cracked `Blaz3Craft` profile on Paper/BlazeSMP used an offline UUID. That data is not moved.

1. Before registering Premium, note the cracked UUID from Paper (`ops.json`, world playerdata, or an economy plugin). Offline UUIDs are `UUID.nameUUIDFromBytes("OfflinePlayer:Blaz3Craft")`.
2. Register Premium with `/bcd premium add Blaz3Craft`.
3. Connect cracked **without** using the reserved name, or inspect stored records:
   ```
   bcd lookup Blaz3Craft
   ```
4. Connect with the real Premium account, then:
   ```
   bcd debug Blaz3Craft
   ```

You should see **two** identity records:

| Record | Account type | UUID source | UUID |
| --- | --- | --- | --- |
| Historical cracked profile | `CRACKED` | `OFFLINE` | offline UUID (unchanged) |
| Authenticated Premium | `PREMIUM` | `MOJANG_SESSION` | real Mojang UUID |

The backend forwarding identity for the Premium connection is the **Mojang UUID**. Paper therefore loads the Premium playerdata, not the cracked one. Inventories, economy, permissions, and statistics stay on their original UUIDs. This plugin never migrates or overwrites them.

## Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/bcd` | any | Plugin information and help |
| `/bcd reload` | `blazemcconnection.reload` | Reload `config.yml` |
| `/bcd status` | `blazemcconnection.admin` or `debug` | Auth system and LimboAuth adapter status |
| `/bcd debug <player>` | `blazemcconnection.debug` | Live identity diagnostics |
| `/bcd lookup <username>` | `debug` or `admin` | Stored identity information |
| `/bcd premium add <username>` | `blazemcconnection.premium.manage` | Reserve a Premium username via Mojang API |
| `/bcd premium remove <username>` | `premium.manage` | Remove reservation after `confirm` |
| `/bcd premium list` | `premium.manage` | List reserved Premium accounts |
| `/bcd premium refresh <username>` | `premium.manage` | Refresh cached Mojang profile |
| `/bcd premium rename <old> <new>` | `premium.manage` | Update username, keep the same Mojang UUID |

`/bcd premium remove` requires a second `confirm` within 30 seconds. It only removes the reservation. Identity records and history are never deleted.

## Authentication flow

1. `PreLoginEvent` (`PostOrder.LAST`)
   - Reserved Premium username → `forceOnlineMode()`
   - Any other username → `forceOfflineMode()`
   - Client-sent UUID is ignored
2. Velocity performs Mojang session auth only for the reserved connection
3. `GameProfileRequestEvent` (`LAST`) restores the Mojang UUID if another plugin replaced it. Offline UUIDs are never generated for reserved Premium players.
4. `LoginEvent`
   - Reserved name without `player.isOnlineMode()` and session evidence → kick
   - Authenticated UUID ≠ stored Mojang UUID → kick
   - Unreserved name → cracked, original offline UUID kept
5. Optional LimboAuth adapter bypasses cracked login **only** after step 4 succeeded as Premium

## Storage

SQLite file `database/identities.db` (configurable) with:

- `premium_accounts` — reserved canonical username + Mojang UUID
- `identity_records` — separate Premium and Cracked UUID rows
- `connection_history` — authentication outcomes

Passwords, Microsoft credentials, access tokens, and session tokens are never stored.

## Configuration

See the generated `config.yml`. Every documented key is implemented:

- `authentication.premium.*` — selective online-mode and UUID checks
- `authentication.cracked.*` — offline preservation and LimboAuth usage
- `mojang.*` — timeout, retry, cache, fail-closed
- `database.*` — SQLite path
- `limboauth.*` — `auto` / `enabled` / `disabled`
- `logging.*` and `messages.*`

`general.language` currently ships English only (`en`).

## Security properties

- Reserved Premium usernames cannot complete the cracked path
- Offline UUID spoofing cannot unlock a reserved name
- Duplicate username or UUID reservations are rejected
- Concurrent reserved logins are denied (with a handshake timeout so a failed attempt cannot block the real account)
- Mojang API outages fail closed for **new** reservations; cached reservations stay enforced
- Database writes use transactions, prepared statements, WAL, and a process lock

## Tests

`./gradlew test` covers:

1. Legitimate reserved Premium session
2. Unreserved cracked username
3. Unauthorized use of a reserved Premium username
4. Premium name presenting an offline UUID
5. Incorrect Premium UUID
6. Mojang API outage during registration
7. Duplicate Premium registration
8. Username change with the same Mojang UUID
9. SQLite persistence after restart
10. Concurrent reserved connection attempts
11. LimboAuth bypass only for verified Premium sessions
12. Existing cracked profiles remaining untouched after Premium reservation
