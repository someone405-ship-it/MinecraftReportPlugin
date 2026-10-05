# Elite Minecraft Report Plugin

**The most advanced free player reporting system** for Paper / Spigot 1.21+

Ultra-fancy Discord embeds • Full GUI with player heads • Body renders • Report IDs • Weekly limits • Staff tools

![Minecraft](https://img.shields.io/badge/Minecraft-1.21+-brightgreen)
![Paper](https://img.shields.io/badge/Paper-Recommended-blue)
![Version](https://img.shields.io/badge/Version-2.0-orange)

**Repository:** https://github.com/someone405-ship-it/MinecraftReportPlugin

---

## ✨ Features (v2.0)

### Player Side
- **`/report`** → Beautiful GUI with online player heads
- **`/report <player> <reason>`** → Classic command still works
- Predefined reason categories (Griefing, Hacking, Toxicity, etc.)
- Custom reason support
- Cannot report yourself
- Offline player support

### Anti-Spam & Limits
- Global cooldown (default 45s)
- **Same player can only be reported once per week** by the same reporter
- Max reports per hour limit
- All limits bypassable by admins

### Discord (Ultra Fancy)
- Multi-embed messages
- Reporter avatar as author icon
- Reported player **3D body render** as large image
- Head thumbnail
- Code-block formatted fields
- Discord relative timestamps (`<t:...:R>`)
- Unique Report ID
- Location + world of the reporter
- Optional role ping
- Professional red styling + footer

### Staff Tools
- `/reports` — List all open reports
- `/reportview <id>` — Full details of a report
- `/reportclose <id>` — Mark as handled
- `/reportreload` — Reload config + data
- In-game notification + sound when a report arrives

### Technical
- Fully asynchronous webhook sending (zero lag)
- Persistent storage (`reports.yml` + `cooldowns.yml`)
- Unique Report IDs (`RPT-YYYYMMDD-0001`)
- Tab completion
- Fully configurable messages, colors, cooldowns, reasons, GUI titles

---

## 📥 Installation

1. Build or download the JAR
2. Put it in `plugins/`
3. Restart server
4. Edit `plugins/MinecraftReportPlugin/config.yml` (webhook is already set)
5. Restart / reload

### Building
```bash
git clone https://github.com/someone405-ship-it/MinecraftReportPlugin.git
cd MinecraftReportPlugin
mvn clean package
# JAR → target/MinecraftReportPlugin-1.0.0.jar
```

---

## ⌨️ Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/report` | Open GUI | `reportplugin.report` |
| `/report <player> <reason>` | Direct report | `reportplugin.report` |
| `/reports` | List open reports | `reportplugin.staff` |
| `/reportview <id>` | View report details | `reportplugin.staff` |
| `/reportclose <id>` | Close a report | `reportplugin.staff` |
| `/reportreload` | Reload config | `reportplugin.admin` |

---

## 🖼️ Discord Preview

The embed includes:
- Author with reporter head
- Large 3D body render of the reported player
- YAML-style code blocks for names & UUIDs
- Reason in a highlighted code block
- Report ID, server, exact coordinates
- Live Discord timestamps
- Second embed with staff command reminders

---

## ⚠️ About Discord Buttons

True interactive buttons (Accept / Ban / Mute / etc.) require a full Discord **bot** (JDA) with a bot token and event listeners.  
This version uses a pure **webhook** for maximum simplicity and zero extra setup.  
A future version with optional bot support is planned.

---

## License

MIT – free to use, modify and distribute.

Enjoy the most advanced free report system available!
