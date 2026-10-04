# Username Changer

A server-side Fabric mod for Minecraft 26.1 through 26.3, including dedicated servers and single-player/LAN worlds. Connecting clients need no mods or resource packs. For a single-player or LAN world, install the mod in the host's Fabric instance so its integrated server can load it.

## Installation

Use Java 25+, Fabric Loader 0.19.5+, and the Fabric API release for your Minecraft version. Put `usernamechanger-1.0.2.jar` in the server's (or single-player host's) `mods` directory. Replace the old jar rather than keeping both versions. LuckPerms is optional; install its Fabric build for your server version to use permission nodes.

The supplied `logo.png` is packaged as the mod icon in the release and sources jars.

## Commands

| Command | Effect |
| --- | --- |
| `/usernamechange <player> <nickname>` | Set or replace a nickname. |
| `/usernamereset <player>` | Restore the account name. |
| `/usernamechanger reload` | Reload configuration, import missing cached players, and refresh command visibility. |

`<player>` accepts an account name, nickname, or UUID of a known player, including offline players imported from `usercache.json`. Nicknames use 1-16 ASCII letters, digits, or underscores. Spaces, colors, and formatting codes are not supported because vanilla player profiles have these limits.

Names are checked without regard to case. Nicknames cannot take another known account name or stored nickname. If a new player joins with an account name already used as a nickname, that nickname is suspended and the real account takes priority. Change or reset the suspended nickname to resolve it.

Change/reset suggestions show online players by their current nickname (or account name if unnamed), plus known offline players without a nickname. Offline nicknamed players are hidden from suggestions but remain targetable by their current nickname, account name, or UUID. Generic online-player suggestions contain only current display names, not old aliases or renamed account names.

### Bedrock / Geyser

Geyser/Floodgate account names, including configurable prefixes, spaces, and names longer than 16 characters, are preserved under their original server UUID. No Floodgate API dependency or client mod is required. Quote account names containing spaces, for example `/usernamechange ".Bedrock Player" Comet`. Suggestions insert quotes when needed. Nicknames retain the 1-16 character restriction; use the nickname for vanilla command targets whose parsers cannot accept the original account name. Authentication and account linking remain Geyser/Floodgate's responsibility.

## What Changes

- Vanilla chat display names, join/leave messages, and other messages using player display names.
- Tab names and overhead nametags, including live updates to tracking players.
- Command name suggestions and vanilla entity/player targets such as `/msg Comet`, `/tp Comet`, and `/execute as Comet`.
- Vanilla account-profile arguments, such as `/op Comet` or `/ban Comet`, resolve to the real account UUID. These commands retain their normal permission checks.

Real account names and UUIDs still work. Authentication, player data, inventories, skins, signed chat sessions, permissions, and server scoreboard ownership keep the original identity. Client team membership is translated so team colors, prefixes, suffixes, and visibility rules still apply. Tab names use vanilla's live team formatting.

Selector filters such as `@a[name=AccountName]`, scoreboard score-holder strings, and arbitrary text in command blocks retain their vanilla meaning. Third-party mods that format chat themselves, provide custom argument parsers, or replace player-profile packets may need integration. Nicknames are presentation, not anonymity: account identities remain available in logs and chat reporting.

## Configuration And Translation

The server creates `config/usernamechanger.json` on startup:

```json
{
  "schemaVersion": 1,
  "requiredOpLevel": 2,
  "allowNonOperators": false,
  "useLuckPerms": true,
  "messages": {
    "changed": "{player} is now known as {nickname}."
  }
}
```

The generated file contains all message keys. Translate the `messages` strings in this file, keeping placeholders such as `{player}` and `{nickname}`, then run `/usernamechanger reload`. Text is resolved on the server, so no client language files are required. Missing message keys use English defaults. This selects one language for the server, not a language per client.

Listed operators of any level and the single-player/LAN world owner always have access, including when cheats are disabled for the owner. This takes precedence over LuckPerms denials. LAN guests are not world owners. `requiredOpLevel` accepts 1 through 4 (default 2) for the remaining vanilla command-source permission fallback, including command blocks. `allowNonOperators: true` gives every player access to changing/resetting **any known player's** nickname. Reload remains restricted. Invalid configuration is rejected, and an unsuccessful reload retains the previous settings.

### LuckPerms

| Node | Access |
| --- | --- |
| `usernamechanger.change` | Set/reset any known player's nickname. |
| `usernamechanger.reload` | Reload configuration. |

Example: `/lp group moderator permission set usernamechanger.change true`.

For players who are neither operators nor the world owner, `useLuckPerms: true` makes an explicit LuckPerms allow or deny take precedence over the config fallback. Unset permissions fall back to configuration. Group inheritance, wildcards, and active contexts use LuckPerms' cached permission API. These permission checks fail closed if LuckPerms is present but unavailable. Set `useLuckPerms: false` to use only owner/OP/config checks. Rejoin or reload this mod if your permission manager does not refresh the client's command tree after a grant.

## Persistence

On startup and `/usernamechanger reload`, the mod reads `usercache.json` from the server/host game directory (alongside `mods`, not inside the world). Missing UUIDs are imported into the world's nickname store so cached offline players are immediately available to rename. Existing records and nicknames take priority over potentially stale cache names; joining updates the real account name. Cache expiry dates do not prevent this local import, and no account lookup is performed. The cache itself is never modified. Invalid entries are skipped with a warning; a missing or unreadable cache does not prevent startup. Only players still present in the cache or already known to the mod can be imported.

Nicknames and last-seen account names are stored by UUID in `<world>/usernamechanger.json`. Every change is written before success is reported, using a flushed temporary file and an atomic replacement when the filesystem supports it. Back up this file with the world. Do not edit it while the server is running. Malformed storage stops mod startup instead of overwriting the existing file.

Invalid or conflicting saved nicknames are suspended with a warning instead of preventing startup. The original records remain intact; affected players use their account names until the conflict is resolved. Use `/usernamereset <UUID>` or assign a valid nickname to repair a record. Loading alone never rewrites the file. Version 1.0.2 also fixes restart failures caused by prefixed Bedrock account names stored by earlier versions.

## Building And Testing

Set `JAVA_HOME` to a Java 25 JDK, then run:

```powershell
.\gradlew.bat build
.\gradlew.bat runGametest
```

Release jars are in `build/libs`. The `-sources.jar` is for development, not installation. Game tests run in isolated worlds under `build/gametest-run` and are excluded from release jars.

| Minecraft | Fabric API |
| --- | --- |
| 26.1 | 0.145.1+26.1 |
| 26.1.1 | 0.145.4+26.1.1 |
| 26.1.2 | 0.155.3+26.1.2 |
| 26.2 | 0.161.0+26.2 |
| 26.3 (default) | 0.161.0+26.3 |

To check another version, pass both properties, for example:

```powershell
.\gradlew.bat build runGametest '-Pminecraft_version=26.1' '-Pfabric_api_version=0.145.1+26.1'
```

CI runs this matrix. Unit tests cover restart persistence, collisions, failed writes, configuration, and LuckPerms API decisions; Fabric Loader tests apply every mixin in both server and client environments. Headless game tests exercise command visibility/execution for operators and a simulated world owner with cheats disabled, guest denial, identity preservation, team packets, tracking-viewer refresh, reconnect, and reset using embedded connections. A final visual check with vanilla clients and a live LuckPerms installation is still recommended; embedded tests do not render nametags or authenticate signed chat clients.

Bedrock regression tests use simulated Floodgate-style profiles and embedded server connections to check quoting, offline suggestions, identity preservation, and storage reload. A live Geyser/Bedrock client visual check is still needed to verify its translated tab list and nametags.

## Porting

Storage and config live in their own packages. Minecraft-facing behavior is concentrated in `mixin/` and `compat/PlayerPresentation.java`; optional permissions are in `permissions/`. For another release, update the two version properties, run both test suites, and inspect changed packet/tracker signatures before widening `fabric.mod.json`. Versions before 26.1 require a separate build/mapping setup and potentially Java/API changes.

## License

Licensed under the MIT License; see [LICENSE](LICENSE). Copyright (c) 2026 Admin.
