package com.reportplugin.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.reportplugin.ReportPlugin;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DiscordWebhook {

    private final ReportPlugin plugin;
    private final Gson gson = new Gson();

    public DiscordWebhook(ReportPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean sendReport(Player reporter, OfflinePlayer target, String reason) {
        return sendReport(reporter, target, reason, "N/A", "medium", false);
    }

    public boolean sendReport(Player reporter, OfflinePlayer target, String reason, String reportId) {
        return sendReport(reporter, target, reason, reportId, "medium", false);
    }

    public boolean sendReport(Player reporter, OfflinePlayer target, String reason, String reportId, String priority, boolean anonymous) {
        String webhookUrl = plugin.getConfig().getString("webhook-url");
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            plugin.getLogger().warning("Webhook URL is not configured!");
            return false;
        }

        CompletableFuture.runAsync(() -> {
            try {
                JsonObject payload = buildUltimateEmbed(reporter, target, reason, reportId, priority, anonymous);
                send(webhookUrl, payload);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to send Discord webhook: " + e.getMessage());
                e.printStackTrace();
            }
        });
        return true;
    }

    private JsonObject buildUltimateEmbed(Player reporter, OfflinePlayer target, String reason,
                                          String reportId, String priority, boolean anonymous) {

        String reporterName = anonymous ? "Anonymous" : reporter.getName();
        UUID reporterUUID = reporter.getUniqueId();
        String targetName = target.getName() != null ? target.getName() : "Unknown";
        UUID targetUUID = target.getUniqueId();

        String reporterHead = anonymous
                ? "https://crafatar.com/avatars/8667ba71-b85a-4004-af54-457a9734eed7?size=64&overlay"
                : "https://crafatar.com/avatars/" + reporterUUID + "?size=64&overlay";

        String targetHead = "https://crafatar.com/avatars/" + targetUUID + "?size=128&overlay";
        String targetBody = "https://crafatar.com/renders/body/" + targetUUID + "?scale=7&overlay&default=MHF_Steve";

        String serverName = plugin.getConfig().getString("server-name", "Minecraft Server");
        String title = plugin.getConfig().getString("embed.title", "🚨 NEW PLAYER REPORT");
        String footer = plugin.getConfig().getString("embed.footer", "Reported Users");

        // Priority color
        int color = plugin.getConfig().getInt("embed.colors." + priority.toLowerCase(),
                plugin.getConfig().getInt("embed.color", 16711680));

        String priorityEmoji = switch (priority.toLowerCase()) {
            case "low" -> "🟢 LOW";
            case "medium" -> "🟡 MEDIUM";
            case "high" -> "🟠 HIGH";
            case "critical" -> "🔴 CRITICAL";
            default -> "⚪ UNKNOWN";
        };

        long unix = Instant.now().getEpochSecond();

        // ========== MAIN EMBED ==========
        JsonObject main = new JsonObject();
        main.addProperty("title", title);
        main.addProperty("color", color);
        main.addProperty("timestamp", Instant.now().toString());

        String desc = "```ansi\n\u001b[2;31m■\u001b[0m REPORT RECEIVED\n```\n" +
                "> **Priority:** " + priorityEmoji + "\n" +
                "> **Status:** 🔴 OPEN\n" +
                "> **ID:** `" + reportId + "`";
        main.addProperty("description", desc);

        // Author
        JsonObject author = new JsonObject();
        author.addProperty("name", anonymous ? "Anonymous Report" : "Reported by " + reporterName);
        author.addProperty("icon_url", reporterHead);
        if (!anonymous) {
            author.addProperty("url", "https://namemc.com/profile/" + reporterUUID);
        }
        main.add("author", author);

        // Thumbnail
        JsonObject thumb = new JsonObject();
        thumb.addProperty("url", targetHead);
        main.add("thumbnail", thumb);

        // Body render
        if (plugin.getConfig().getBoolean("embed.show-body-render", true)) {
            JsonObject image = new JsonObject();
            image.addProperty("url", targetBody);
            main.add("image", image);
        }

        JsonArray fields = new JsonArray();

        // Reporter
        if (anonymous) {
            fields.add(createField("👤 Reporter", "```\nAnonymous\n```", true));
        } else {
            fields.add(createField("👤 Reporter",
                    "```yaml\nName: " + reporterName + "\nUUID: " + shortUUID(reporterUUID) + "\n```", true));
        }

        // Target
        fields.add(createField("🎯 Reported",
                "```yaml\nName: " + targetName + "\nUUID: " + shortUUID(targetUUID) + "\n```", true));

        // Reason
        fields.add(createField("📝 Reason", "```fix\n" + reason + "\n```", false));

        // Meta
        fields.add(createField("🌐 Server", "`" + serverName + "`", true));
        fields.add(createField("📍 World", "`" + reporter.getWorld().getName() + "`", true));
        fields.add(createField("🧭 Coords",
                "`" + fmt(reporter.getLocation().getX()) + " " +
                        fmt(reporter.getLocation().getY()) + " " +
                        fmt(reporter.getLocation().getZ()) + "`", true));

        fields.add(createField("⏱️ When", "<t:" + unix + ":F>\n<t:" + unix + ":R>", true));
        fields.add(createField("🎮 Gamemode", "`" + reporter.getGameMode().name() + "`", true));
        fields.add(createField("📶 Ping", "`" + reporter.getPing() + "ms`", true));

        main.add("fields", fields);

        // Footer
        JsonObject footerObj = new JsonObject();
        footerObj.addProperty("text", footer + " • " + priority.toUpperCase());
        footerObj.addProperty("icon_url", targetHead);
        main.add("footer", footerObj);

        // ========== SECOND EMBED (Staff help) ==========
        JsonObject help = new JsonObject();
        help.addProperty("title", "📋 Staff Tools");
        help.addProperty("color", 3447003);
        help.addProperty("description",
                "**In-game commands**\n" +
                "• `/reports` — Open reports list\n" +
                "• `/reportview " + reportId + "` — Full details\n" +
                "• `/reportclose " + reportId + "` — Mark handled\n\n" +
                "*Webhook edition — buttons require the Action Bot*");

        // ========== PAYLOAD ==========
        JsonObject payload = new JsonObject();
        payload.addProperty("username", "Reported Users");
        payload.addProperty("avatar_url", targetHead);

        String roleId = plugin.getConfig().getString("embed.mention-role-id", "");
        if (roleId != null && !roleId.isEmpty()) {
            payload.addProperty("content", "<@&" + roleId + "> **New " + priority.toUpperCase() + " report**");
        }

        JsonArray embeds = new JsonArray();
        embeds.add(main);
        embeds.add(help);
        payload.add("embeds", embeds);

        return payload;
    }

    private String shortUUID(UUID uuid) {
        String s = uuid.toString();
        return s.substring(0, 8) + "..." + s.substring(s.length() - 4);
    }

    private String fmt(double d) {
        return String.format("%.0f", d);
    }

    private JsonObject createField(String name, String value, boolean inline) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value);
        field.addProperty("inline", inline);
        return field;
    }

    private void send(String webhookUrl, JsonObject payload) throws Exception {
        URL url = new URL(webhookUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("User-Agent", "EliteReportPlugin/3.0");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(gson.toJson(payload).getBytes(StandardCharsets.UTF_8));
        }

        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            plugin.getLogger().warning("Discord webhook returned HTTP " + code);
        } else {
            plugin.getLogger().info("Report sent to Discord successfully.");
        }
        connection.disconnect();
    }
}
