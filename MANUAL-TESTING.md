# Manual integration checklist

Automated tests use mocked HTTP and a testable authentication engine. They **do not** prove a live Microsoft/Mojang session. Run this checklist on `mc.blazemc.ro` (or a staging Velocity 3.5 proxy) before calling the upgrade finished in production.

Do not record passwords, access tokens, Microsoft credentials, or forwarding secrets.

## Setup

1. Install `blazemc-connection-detector-1.1.0.jar` on Velocity.
2. Remove PremiumOrCracked.
3. Keep LimboAuth 1.1.14 / LimboAPI 1.1.27-SNAPSHOT.
4. Confirm Velocity `online-mode = false` and `player-info-forwarding-mode = "modern"`.
5. Confirm Paper Velocity forwarding is enabled with the same secret.
6. Confirm LimboAuth `FORCE-OFFLINE-UUID: false` and `ONLINE-MODE-NEED-AUTH: false`.

## A. Genuine Premium account (required)

Use a real Minecraft Java account signed in with Microsoft.

1. Do **not** run `/bcd premium add` for this account.
2. Connect with the official launcher (or another legitimate Microsoft-authenticated launcher).
3. Expected proxy logs:
   - `Automatic Premium profile detected: <name>`
   - `Forcing Mojang session authentication.`
   - `Premium session successfully verified.`
   - `Premium UUID verified: <mojang-uuid>`
4. You must **not** see `/register` or a LimboAuth password prompt.
5. Run `/bcd diagnostics <player>`:
   - Account type `PREMIUM`
   - UUID source `MOJANG`
   - Online authentication `true`
   - Session verification `true`
   - Authenticated UUID = expected Mojang UUID = backend forwarding UUID
6. On Paper, the player UUID must equal the Mojang UUID from step 5.
7. Disconnect and reconnect. The same Mojang UUID must be reused.

## B. Username change

1. Change the Microsoft account username on minecraft.net.
2. Connect with the new name.
3. `/bcd diagnostics` must still show the **same** Mojang UUID.
4. `/bcd lookup <oldName>` / `/bcd lookup <newName>` must not create a second Premium UUID.

## C. Cracked impersonation of a Premium name

1. From TLauncher or another cracked client, connect using the Premium username (for example `Blaz3Craft`).
2. Expected: connection rejected at the proxy.
3. Logs: `Premium impersonation attempt rejected.`
4. The client must never reach BlazeSMP, never receive the offline UUID, and never open LimboAuth `/register` for that name.

## D. Genuine Cracked player

1. Connect with a username that has **no** Minecraft Java profile.
2. Expected log: `Cracked profile confirmed: <name>`
3. LimboAuth `/register` or `/login` must still work.
4. The offline UUID must match `UUID.nameUUIDFromBytes("OfflinePlayer:<exactName>")`.
5. Existing Cracked inventory/balance/ranks for that UUID must still load.

## E. Historical Cracked `Blaz3Craft`

1. Before the first Premium login, note the old offline UUID from Paper playerdata.
2. Complete checklist A with the real `Blaz3Craft` Premium account.
3. `/bcd lookup Blaz3Craft` must show **two** identity records if the old Cracked row existed:
   - `CRACKED` + offline UUID (untouched)
   - `PREMIUM` + Mojang UUID
4. Paper must load Premium playerdata for the Mojang UUID, not the old offline UUID.
5. The old world files under the offline UUID must still exist.

## F. Mojang API outage / rate limit

1. After a successful Premium login, block `api.mojang.com` or wait for a 429.
2. Reconnect with the same Premium account. Online authentication must still be forced from the local cache.
3. Connect with a **new** unknown username while the API is down. Strict mode must reject with the retry message, not treat the name as Cracked.

## G. UUID consistency command

On the proxy:

```
bcd diagnostics <premiumPlayer>
```

On Paper, compare the player UUID. They must be identical. If they differ, forwarding is misconfigured; this plugin does not rewrite Paper identities.
