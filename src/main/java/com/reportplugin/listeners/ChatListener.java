package com.reportplugin.listeners;

import com.reportplugin.ReportPlugin;
import com.reportplugin.gui.ReportGUI;
import com.reportplugin.managers.CooldownManager;
import com.reportplugin.managers.ReportManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ChatListener implements Listener {

    private final ReportPlugin plugin;

    public static final Map<UUID, UUID> waitingForReason = new HashMap<>();

    public ChatListener(ReportPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID reporterUUID = player.getUniqueId();

        if (!waitingForReason.containsKey(reporterUUID)) return;

        event.setCancelled(true);
        String message = event.getMessage().trim();

        if (message.equalsIgnoreCase("cancel")) {
            waitingForReason.remove(reporterUUID);
            ReportGUI.selectedPriority.remove(reporterUUID);
            player.sendMessage(color("&cReport cancelled."));
            return;
        }

        int minLen = plugin.getConfig().getInt("min-reason-length", 5);
        if (message.length() < minLen) {
            player.sendMessage(color("&cReason too short (min " + minLen + " characters). Type again or type &ecancel&c."));
            return;
        }

        UUID targetUUID = waitingForReason.remove(reporterUUID);
        String priority = ReportGUI.selectedPriority.getOrDefault(reporterUUID, "medium");
        ReportGUI.selectedPriority.remove(reporterUUID);

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String targetName = target.getName() != null ? target.getName() : "Unknown";

        Bukkit.getScheduler().runTask(plugin, () -> submitCustomReason(player, target, targetName, message, priority));
    }

    private void submitCustomReason(Player reporter, OfflinePlayer target, String targetName, String reason, String priority) {
        if (isBlacklisted(targetName)) {
            reporter.sendMessage(color(plugin.getConfig().getString("messages.blacklisted")));
            return;
        }

        CooldownManager cm = plugin.getCooldownManager();
        ReportManager rm = plugin.getReportManager();
        UUID reporterUUID = reporter.getUniqueId();
        UUID targetUUID = target.getUniqueId();

        if (!reporter.hasPermission("reportplugin.admin")) {
            if (cm.isOnGlobalCooldown(reporterUUID)) {
                reporter.sendMessage(color(plugin.getConfig().getString("messages.global-cooldown")
                        .replace("%time%", formatTime(cm.getGlobalRemaining(reporterUUID)))));
                return;
            }
            if (cm.hasReportedRecently(reporterUUID, targetUUID)) {
                reporter.sendMessage(color(plugin.getConfig().getString("messages.same-player-cooldown")
                        .replace("%target%", targetName)
                        .replace("%time%", formatTime(cm.getSamePlayerRemaining(reporterUUID, targetUUID)))));
                return;
            }
            int maxHourly = plugin.getConfig().getInt("cooldowns.max-reports-per-hour", 6);
            if (rm.getReportsByReporterLastHour(reporterUUID) >= maxHourly) {
                reporter.sendMessage(color(plugin.getConfig().getString("messages.hourly-limit")
                        .replace("%max%", String.valueOf(maxHourly))));
                return;
            }
        }

        boolean anonymous = plugin.getConfig().getBoolean("features.anonymous-reporting", false);

        String reportId = rm.createReport(
                reporterUUID, reporter.getName(),
                targetUUID, targetName,
                reason,
                reporter.getWorld().getName(),
                reporter.getLocation().getX(),
                reporter.getLocation().getY(),
                reporter.getLocation().getZ()
        );

        boolean ok = plugin.getDiscordWebhook().sendReport(reporter, target, reason, reportId, priority, anonymous);

        if (ok) {
            cm.applyGlobalCooldown(reporterUUID);
            cm.applySamePlayerCooldown(reporterUUID, targetUUID);

            String msgKey = anonymous ? "messages.anonymous-success" : "messages.success";
            reporter.sendMessage(color(plugin.getConfig().getString(msgKey)
                    .replace("%target%", targetName)
                    .replace("%id%", reportId)));

            if (plugin.getConfig().getBoolean("sound-on-success", true)) {
                reporter.playSound(reporter.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
            }

            String staffMsg = plugin.getConfig().getString("messages.staff-notify", "")
                    .replace("%reporter%", anonymous ? "Anonymous" : reporter.getName())
                    .replace("%target%", targetName)
                    .replace("%reason%", reason)
                    .replace("%priority%", priority.toUpperCase());
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission(plugin.getConfig().getString("staff-permission", "reportplugin.staff"))) {
                    staff.sendMessage(color(staffMsg));
                    if (plugin.getConfig().getBoolean("sound-on-report", true)) {
                        staff.playSound(staff.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                    }
                }
            }
        } else {
            reporter.sendMessage(color(plugin.getConfig().getString("messages.webhook-error")));
        }
    }

    private boolean isBlacklisted(String name) {
        if (name == null) return false;
        List<String> list = plugin.getConfig().getStringList("features.blacklist");
        for (String s : list) {
            if (s.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private String color(String msg) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        return ChatColor.translateAlternateColorCodes('&', prefix + msg);
    }

    private String formatTime(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        if (seconds < 86400) return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        return (seconds / 86400) + "d " + ((seconds % 86400) / 3600) + "h";
    }
}
