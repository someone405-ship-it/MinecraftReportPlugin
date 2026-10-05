# Elite Minecraft Report Plugin

Advanced player reporting system for Paper / Spigot 1.21+  
**Pure Discord Webhook** • Fancy embeds • Full GUI • Anti-spam • Staff tools

**Repository:** https://github.com/someone405-ship-it/MinecraftReportPlugin

---

## Features

### Player
- `/report` → Opens GUI with online player heads
- `/report <player> <reason>` → Classic command
- Predefined reason categories + **custom reason via chat** (Other)
- Cannot report yourself
- Offline player support

### Anti-Spam
- Global cooldown (default 45 seconds)
- Same player can only be reported **once per week** by the same reporter
- Max reports per hour limit
- Admins bypass all limits

### Discord (Webhook only)
- Username: **Reported Users**
- Avatar: Reported player's head
- 3D body render of the reported player
- Reporter head as author icon
- Unique Report ID
- Exact location + world
- Discord timestamps
- Optional role ping

### Staff
- `/reports` — List open reports
- `/reportview <id>` — Full details
- `/reportclose <id>` — Close a report
- `/reportreload` — Reload config
- In-game notifications + sound when a report arrives

---

## Installation

1. Build with Maven (`mvn clean package`) or download the JAR
2. Put the JAR in `plugins/`
3. Restart the server
4. Edit `plugins/MinecraftReportPlugin/config.yml` (webhook is already set)
5. Restart / `/reportreload`

---

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/report` | Open GUI | `reportplugin.report` |
| `/report <player> <reason>` | Direct report | `reportplugin.report` |
| `/reports` | List open reports | `reportplugin.staff` |
| `/reportview <id>` | View report | `reportplugin.staff` |
| `/reportclose <id>` | Close report | `reportplugin.staff` |
| `/reportreload` | Reload config | `reportplugin.admin` |

---

## Recent Improvements & Bugfixes

- Fixed fragile GUI title matching
- Proper "Other" reason flow (type in chat, supports cancel)
- Cleaned Discord embeds (pure webhook, no DiscordSRV leftovers)
- Better null-safety for offline players
- Improved staff notifications
- More reliable cooldown + report storage

---

## License

MIT
