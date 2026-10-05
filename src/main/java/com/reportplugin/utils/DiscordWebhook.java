package com.reportplugin.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.reportplugin.ReportPlugin;
import org.bukkit.Bukkit;
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
        String webhookUrl = plugin.getConfig().getString("webhook-url");
        if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.contains("YOUR_WEBHOOK")) {
            plugin.getLogger().warning("Webhook URL is not configured properly!");
            return false;
        }

        // Build the fancy embed asynchronously so we don't block the main thread
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject payload = buildPayload(reporter, target, reason);
                send(webhookUrl, payload);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to send Discord webhook: " + e.getMessage());
                e.printStackTrace();
            }
        });

        return true; // We assume success since it's async; real error is logged
    }

    private JsonObject buildPayload(Player reporter, OfflinePlayer target, String reason) {
        String reporterName = reporter.getName();
        UUID reporterUUID = reporter.getUniqueId();
        String targetName = target.getName() != null ? target.getName() : "Unknown";
        UUID targetUUID = target.getUniqueId();

        // Avatar URLs using Crafatar (high quality Minecraft skins)
        String reporterAvatar = "https://crafatar.com/avatars/" + reporterUUID + "?size=128&overlay";
        String targetAvatar = "https://crafatar.com/avatars/" + targetUUID + "?size=128&overlay";
        String reporterHead = "https://crafatar.com/avatars/" + reporterUUID + "?size=64&overlay";

        String serverName = plugin.getConfig().getString("server-name", "Minecraft Server");
        int color = plugin.getConfig().getInt("embed.color", 15158332);
        String title = plugin.getConfig().getString("embed.title", "🚨 New Player Report");
        String footer = plugin.getConfig().getString("embed.footer", "Minecraft Report System");

        // Main embed
        JsonObject embed = new JsonObject();
        embed.addProperty("title", title);
        embed.addProperty("color", color);
        embed.addProperty("timestamp", Instant.now().toString());

        // Description with markdown
        String description = "**A player has been reported on the server.**\n\n" +
                "> Please review this report carefully.";
        embed.addProperty("description", description);

        // Thumbnail = reported player's head
        JsonObject thumbnail = new JsonObject();
        thumbnail.addProperty("url", targetAvatar);
        embed.add("thumbnail", thumbnail);

        // Author = reporter with their avatar
        JsonObject author = new JsonObject();
        author.addProperty("name", "Reported by " + reporterName);
        author.addProperty("icon_url", reporterHead);
        embed.add("author", author);

        // Fields
        JsonArray fields = new JsonArray();

        fields.add(createField("👤 Reporter", "`" + reporterName + "`\nUUID: `" + reporterUUID + "`", true));
        fields.add(createField("🎯 Reported Player", "`" + targetName + "`\nUUID: `" + targetUUID + "`", true));
        fields.add(createField("📝 Reason", reason, false));
        fields.add(createField("🌐 Server", serverName, true));
        fields.add(createField("📍 World", reporter.getWorld().getName(), true));
        fields.add(createField("🕒 Location", String.format("X: %.0f  Y: %.0f  Z: %.0f",
                reporter.getLocation().getX(),
                reporter.getLocation().getY(),
                reporter.getLocation().getZ()), true));

        embed.add("fields", fields);

        // Footer
        JsonObject footerObj = new JsonObject();
        footerObj.addProperty("text", footer);
        footerObj.addProperty("icon_url", "https://crafatar.com/avatars/8667ba71-b85a-4004-af54-457a9734eed7?size=32");
        embed.add("footer", footerObj);

        // Full payload
        JsonObject payload = new JsonObject();
        payload.addProperty("username", "Report System");
        payload.addProperty("avatar_url", "https://crafatar.com/avatars/8667ba71-b85a-4004-af54-457a9734eed7?size=64&overlay");

        JsonArray embeds = new JsonArray();
        embeds.add(embed);
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
        connection.setRequestProperty("User-Agent", "MinecraftReportPlugin/1.0");
        connection.setDoOutput(true);

        String json = gson.toJson(payload);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = json.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            plugin.getLogger().warning("Discord webhook returned HTTP " + responseCode);
        } else {
            plugin.getLogger().info("Report successfully sent to Discord.");
        }

        connection.disconnect();
    }
}
