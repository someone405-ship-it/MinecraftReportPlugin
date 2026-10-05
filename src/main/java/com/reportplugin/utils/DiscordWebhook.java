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
        return sendReport(reporter, target, reason, "N/A");
    }

    public boolean sendReport(Player reporter, OfflinePlayer target, String reason, String reportId) {
        String webhookUrl = plugin.getConfig().getString("webhook-url");
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            plugin.getLogger().warning("Webhook URL is not configured!");
            return false;
        }

        CompletableFuture.runAsync(() -> {
            try {
                JsonObject payload = buildUltraFancyPayload(reporter, target, reason, reportId);
                send(webhookUrl, payload);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to send Discord webhook: " + e.getMessage());
                e.printStackTrace();
            }
        });
        return true;
    }

    private JsonObject buildUltraFancyPayload(Player reporter, OfflinePlayer target, String reason, String reportId) {
        String reporterName = reporter.getName();
        UUID reporterUUID = reporter.getUniqueId();
        String targetName = target.getName() != null ? target.getName() : "Unknown";
        UUID targetUUID = target.getUniqueId();

        String reporterHead = "https://crafatar.com/avatars/" + reporterUUID + "?size=64&overlay";
        String targetHead = "https://crafatar.com/avatars/" + targetUUID + "?size=128&overlay";
        String targetBody = "https://crafatar.com/renders/body/" + targetUUID + "?scale=6&overlay";

        String serverName = plugin.getConfig().getString("server-name", "Minecraft Server");
        int color = plugin.getConfig().getInt("embed.color", 16711680);
        String title = plugin.getConfig().getString("embed.title", "🚨 PLAYER REPORT RECEIVED");
        String footer = plugin.getConfig().getString("embed.footer", "Elite Report System");

        long unix = Instant.now().getEpochSecond();

        // ========== MAIN EMBED ==========
        JsonObject main = new JsonObject();
        main.addProperty("title", title);
        main.addProperty("color", color);
        main.addProperty("timestamp", Instant.now().toString());

        main.addProperty("description",
                "```diff\n- A new player report has been submitted\n+ Staff action may be required\n```\n\n" +
                "> **Status:** 🔴 **OPEN**\n" +
                "> **Priority:** High");

        // Author = Reporter
        JsonObject author = new JsonObject();
        author.addProperty("name", "Reported by " + reporterName);
        author.addProperty("icon_url", reporterHead);
        author.addProperty("url", "https://namemc.com/profile/" + reporterUUID);
        main.add("author", author);

        // Thumbnail = reported head
        JsonObject thumb = new JsonObject();
        thumb.addProperty("url", targetHead);
        main.add("thumbnail", thumb);

        // Big image = body render of reported player
        if (plugin.getConfig().getBoolean("embed.show-body-render", true)) {
            JsonObject image = new JsonObject();
            image.addProperty("url", targetBody);
            main.add("image", image);
        }

        JsonArray fields = new JsonArray();

        fields.add(createField("👤 Reporter",
                "```yaml\nName: " + reporterName + "\nUUID: " + reporterUUID + "\n```", true));

        fields.add(createField("🎯 Reported Player",
                "```yaml\nName: " + targetName + "\nUUID: " + targetUUID + "\n```", true));

        fields.add(createField("📝 Reason",
                "```fix\n" + reason + "\n```", false));

        fields.add(createField("🆔 Report ID", "`" + reportId + "`", true));
        fields.add(createField("🌐 Server", "`" + serverName + "`", true));
        fields.add(createField("📍 Location",
                "`" + reporter.getWorld().getName() + "`\nX: `" +
                        String.format("%.0f", reporter.getLocation().getX()) + "` " +
                        "Y: `" + String.format("%.0f", reporter.getLocation().getY()) + "` " +
                        "Z: `" + String.format("%.0f", reporter.getLocation().getZ()) + "`", true));

        fields.add(createField("⏱️ Submitted",
                "<t:" + unix + ":F>\n(<t:" + unix + ":R>)", true));

        fields.add(createField("📊 Quick Stats",
                "Reporter total (hour): check `/reports`", true));

        main.add("fields", fields);

        JsonObject footerObj = new JsonObject();
        footerObj.addProperty("text", footer);
        footerObj.addProperty("icon_url", targetHead);
        main.add("footer", footerObj);

        // ========== SECOND EMBED (Actions / Info) ==========
        JsonObject actions = new JsonObject();
        actions.addProperty("title", "📋 Staff Actions (DiscordSRV)");
        actions.addProperty("color", 3447003);
        actions.addProperty("description",
                "**Ban / Mute from Discord:**\n" +
                "• `!ban " + targetName + " reason`\n" +
                "• `!tempban " + targetName + " 7d reason`\n" +
                "• `!mute " + targetName + " reason`\n" +
                "• `!kick " + targetName + " reason`\n\n" +
                "**In-game commands:**\n" +
                "• `/reports` — List open reports\n" +
                "• `/reportview " + reportId + "`\n" +
                "• `/reportclose " + reportId + "`");

        // ========== PAYLOAD ==========
        JsonObject payload = new JsonObject();
        payload.addProperty("username", "Reported Users");          // Fixed human-like name as requested
        payload.addProperty("avatar_url", targetHead);             // Still uses reported player's head

        // Optional role ping
        String roleId = plugin.getConfig().getString("embed.mention-role-id", "");
        if (roleId != null && !roleId.isEmpty()) {
            payload.addProperty("content", "<@&" + roleId + "> New report submitted!");
        }

        JsonArray embeds = new JsonArray();
        embeds.add(main);
        embeds.add(actions);
        payload.add("embeds", embeds);

        return payload;
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
        connection.setRequestProperty("User-Agent", "MinecraftEliteReportPlugin/2.0");
        connection.setDoOutput(true);

        String json = gson.toJson(payload);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = json.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
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
