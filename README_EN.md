<p align="center">
<img src="https://cdn.jsdelivr.net/gh/Dalict/JoinSuite@main/assets/icon-256.png" alt="joinsuite-logo" width="15%"/>
</p>

<h1 align="center">JoinSuite</h1>

<p align="center">All-in-one Minecraft join/leave management: sounds, holograms, messages & announcements.</p>

<p align="center">English | <a href="README.md">中文</a></p>

<div align="center">
    <img src="https://img.shields.io/github/v/release/Dalict/JoinSuite?color=blue&label=release" alt="Release"/>
    <img src="https://img.shields.io/github/last-commit/Dalict/JoinSuite" alt="GitHub last commit"/>
    <img src="https://img.shields.io/github/commit-activity/w/Dalict/JoinSuite" alt="GitHub commit activity"/>
    <img src="https://img.shields.io/github/contributors/Dalict/JoinSuite" alt="GitHub contributors"/>
    <br>
    <img src="https://img.shields.io/github/languages/code-size/Dalict/JoinSuite" alt="GitHub code size in bytes"/>
    <img src="https://img.shields.io/endpoint?url=https://ghloc.vercel.app/api/Dalict/JoinSuite/badge?filter=.java$&label=lines%20of%20code&color=blue" alt="GitHub lines of code"/>
    <img src="https://img.shields.io/github/license/Dalict/JoinSuite" alt="License"/>
    <br>
    <img src="https://img.shields.io/badge/Spigot%20%2F%20Paper-1.19.4%2B-857e00" alt="Server"/>
    <img src="https://img.shields.io/badge/Java-17%2B-orange?logo=openjdk" alt="Java"/>
    <img src="https://img.shields.io/badge/MC-1.19.4%2B-green?logo=minecraft" alt="Minecraft"/>
</div>

---

## Introduction
A lightweight **Spigot / Paper-family** (Paper / Purpur / Pufferfish / Spigot) 1.19.4+ plugin that manages **sounds, holograms, custom messages and welcome announcements** for player joins and leaves — all permission-group based with independent module toggles. Successor to [JoinLeaveSound](https://github.com/Dalict/JoinLeaveSound).

---

## Screenshots

**Join hologram** (above the head by default, TextDisplay always faces the viewer):

![Join hologram](Join-hologram-rendering.png)

**Leave hologram** (near the feet by default):

![Leave hologram](leave-hologram-rendering.png)

---

## Features
- **Modular**: sound / hologram / message / announcement modules, each with its own toggle and permission node (only sound and holograms enabled by default)
- **Permission groups**: newbie > custom permission groups (top to bottom) > default group; per-feature fallback (features not configured on a layer fall through to the next)
- **Holograms** (1.19.4+ TextDisplay)
  - Always facing the viewer — every player sees the text oriented toward themselves
  - Multi-line: independent text, height and duration per line, optional translucent background
  - True-color hex `&#RRGGBB` + legacy `&` codes
  - Never written to world save; discarded on chunk unload, **zero residue**; legacy leftovers swept on startup / chunk load
  - One hologram set per player: a new hologram instantly replaces the old one — join/leave never displayed at the same time, no stacking
- **Custom messages**: replace vanilla join/leave messages (disabled by default), broadcast server-wide
- **Join announcement** (disabled by default): visible to the joining player only — title / subtitle / action bar / chat message, **each with its own independent text** (empty = hidden, multiple can show at once), with fade-in, stay and fade-out for titles
- **Mute list**: SQLite / MySQL storage (`database.yml`), stores case-preserved names + UUIDs; renames synced automatically; legacy `muted-players.yml` migrated automatically
- **PlaceholderAPI soft-depend**: any PAPI placeholder usable in texts; provides `%joinsuite_group%`, `%joinsuite_muted%`, `%joinsuite_time%`
- **LuckPerms friendly**: all permission nodes (including group nodes) auto-registered, tab-completable in LP
- **Config auto-update**: new keys are merged in on upgrade; user edits are never lost (old file backed up as `*.old`)
- Multi-language (`lang/zh_CN.yml`, `lang/en_US.yml`), OP bypass, debug mode, sound cooldowns
- Zero bundled dependencies (~70KB jar), nothing downloaded at startup

---

## Installation
1. Drop `JoinSuite-x.x.x.jar` into your server's `plugins/` folder.
2. Restart the server.
3. Customize the files under `plugins/JoinSuite/`, then `/joinsuite reload`.

> Requires Spigot / Paper-family 1.19.4+, Java 17+. Paper-family servers use the native Adventure API; plain Spigot automatically falls back to legacy APIs with full functionality.

---

## Configuration Files

| File | Purpose |
|------|---------|
| `config.yml` | Global settings (cooldowns, language, time format, OP bypass, debug) |
| `groups.yml` | Module toggles, permission groups, all effects for newbie/default/custom groups |
| `database.yml` | Mute list storage (SQLite / MySQL) |
| `lang/zh_CN.yml` | Simplified Chinese messages |
| `lang/en_US.yml` | English messages |
| `muted.db` | SQLite database file (auto-generated) |

---

## Commands & Permissions

| Command | Alias | Permission | Description |
|---------|-------|------------|-------------|
| `/joinsuite reload` | `/jsuite reload` | `joinsuite.reload` | Reload all configs and messages |
| `/joinsuite mute <player>` | `/jsuite mute <player>` | `joinsuite.mute` | Mute a player (offline / never-joined works) |
| `/joinsuite unmute <player>` | `/jsuite unmute <player>` | `joinsuite.mute` | Unmute a player |
| `/joinsuite list` | `/jsuite list` | `joinsuite.list` | Show the mute list |

Tab completion: `mute` completes online players, `unmute` completes muted names (case preserved).

Module permission nodes (granted to everyone by default; negate per player in LP):

| Node | Controls |
|------|----------|
| `joinsuite.module.sound` | Sound module |
| `joinsuite.module.hologram` | Hologram module |
| `joinsuite.module.message` | Custom message module |
| `joinsuite.module.announce` | Join announcement module |

---

## Group System (`groups.yml`)

### Match priority (highest to lowest)
1. **Newbie** (first join and `newbie.enabled: true`)
2. **Custom permission groups** (top to bottom, first match wins)
3. **Default group** (when `enable-default: true`)
4. **Per-feature fallback**: features missing on a layer fall through to the next

### Config example (excerpt)
```yaml
modules:
  sound:
    enabled: true
    permission: "joinsuite.module.sound"
  message:
    enabled: false
    permission: "joinsuite.module.message"

newbie:
  enabled: true
  join-hologram:
    enabled: true
    duration: 15
    background: true
    lines:
      - text: "&e{player} &f在这个位置首次加入这里！"
        height: 2.8
        duration: 15
      - text: "&7{time}"
        height: 2.5
        duration: 15
  join-announce:
    enabled: true
    title: "&eWelcome &f{player}"  # empty "" = hidden
    subtitle: ""                  # empty "" = hidden
    actionbar: ""
    message: ""
    text: "&e欢迎新玩家 &f{player}"
    fade-in: 10          # title/subtitle only (ticks)
    stay: 60
    fade-out: 20

groups:
  vip:
    permission: "joinsuite.group.vip"
    join-sound:
      sound: ENTITY_PLAYER_LEVELUP
      volume: 1.0
      pitch: 1.2
    join-hologram:
      enabled: true
      duration: 15
      lines:
        - text: "&#FFAA00VIP &f{player} &7加入了游戏"
          height: 2.8
          duration: 15
```

### Granting permissions (LuckPerms)
```
/lp user Dalict permission set joinsuite.group.admin true
/lp group vip permission set joinsuite.group.vip true
```

> All group nodes are auto-registered and **tab-completable in LP**. Note: group nodes default to denied — **ops do not get them automatically**; grant explicitly.

---

## Hologram Notes

- **height**: offset above the player's feet, in blocks (~`2.4` above head, ~`0.4` near feet); join defaults to above the head, leave to near the feet
- **Per-line duration**: falls back to the section's top-level `duration` (15s by default)
- **Exact position**: holograms spawn at the player's exact decimal coordinates, not snapped to blocks
- **No residue**: entities are never saved to disk, discarded on chunk unload, legacy leftovers swept on startup
- **One set per player**: spawning a new hologram instantly clears the player's old one (rejoin within 15s of leaving removes the leave hologram immediately)

---

## Mute List (`database.yml`)

```yaml
type: sqlite          # sqlite or mysql
table-prefix: "joinsuite_"
mysql:
  host: "localhost"
  port: 3306
  database: "minecraft"
  username: "root"
  password: ""
  use-ssl: false
```

- Muted players trigger none of the plugin's effects (vanilla join message kept)
- Muting players who have never joined works — effective from their very first join
- Stores UUID + case-preserved names; UUIDs filled in and renames synced on login
- Legacy `muted-players.yml` migrated automatically on first start

---

## Config Auto-Update
Every config file carries a `config-version`. When the plugin update changes the structure, missing keys are merged in with defaults on startup, the old file is backed up as `*.old`, and **all user-modified values are kept** — no reconfiguration needed.

---

## Localization
Ships with `zh_CN` / `en_US`. Change `language` in `config.yml` and reload; falls back to `zh_CN` if the file is missing.

---

## FAQ

**Q: Which servers are supported?**
A: Spigot / Paper-family 1.19.4+. Paper-family uses Adventure; plain Spigot falls back to legacy APIs automatically — all features work.

**Q: Can holograms leak into the world or the save?**
A: No. Entities are marked non-persistent, discarded on chunk unload, with a tag-sweep fallback.

**Q: What happens when a muted player joins?**
A: None of the plugin's effects trigger (sound/hologram/message/announcement); the vanilla join message is kept.

**Q: Announcement vs. message module?**
A: Messages = server-wide join/leave broadcast in chat; announcements = welcome info visible only to the joining player (four display targets).

**Q: Why doesn't my OP account match a custom group?**
A: Group nodes are registered as denied-by-default (to enable LP completion), so ops no longer pass automatically. Grant via LP, or reorder groups for testing.

**Q: Does the plugin download libraries at startup?**
A: No. Zero bundled dependencies; database drivers come with the server itself.

**Q: Will upgrades overwrite my config edits?**
A: No. Auto-update only fills in missing keys; existing values are preserved and the old file is backed up as `*.old`.

---

## Building
```bash
mvn package
```
The jar is built at `target/JoinSuite-x.x.x.jar` (version segments containing 4 are skipped).

---

## License
MIT License, see [LICENSE](LICENSE).

## Support & Feedback
- Issues: [GitHub Issues](https://github.com/Dalict/JoinSuite/issues)
- Repository: [https://github.com/Dalict/JoinSuite](https://github.com/Dalict/JoinSuite)
