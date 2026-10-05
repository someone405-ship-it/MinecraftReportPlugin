package com.reportplugin.commands;

import com.reportplugin.ReportPlugin;
import com.reportplugin.managers.CooldownManager;
import com.reportplugin.managers.ReportManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ReportCommand implements CommandExecutor, TabCompleter {

    private final ReportPlugin plugin;

    public ReportCommand(ReportPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player reporter)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        if (!reporter.hasPermission("reportplugin.report")) {
            reporter.sendMessage(color(plugin.getConfig().getString("messages.no-permission")));
            return true;
        }

        if (args.length == 0) {
            if (plugin.getConfig().getBoolean("gui.enabled", true)) {
                plugin.getReportGUI().openPlayerSelect(reporter);
            } else {
                reporter.sendMessage(color(plugin.getConfig().getString("messages.usage")));
            }
            return true;
        }

        if (args.length < 2) {
            reporter.sendMessage(color(plugin.getConfig().getString("messages.usage")));
            return true;
        }

        String targetName = args[0];
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

        if (reason.length() < plugin.getConfig().getInt("min-reason-length", 5)) {
            reporter.sendMessage(color(plugin.getConfig().getString("messages.reason-too-short")));
            return true;
        }

        if (isBlacklisted(targetName)) {
            reporter.sendMessage(color(plugin.getConfig().getString("messages.blacklisted")));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline() && !plugin.getConfig().getBoolean("allow-offline-reports", true)) {
            reporter.sendMessage(color(plugin.getConfig().getString("messages.player-not-found").replace("%target%", targetName)));
            return true;
        }

        if (target.getUniqueId().equals(reporter.getUniqueId())) {
            reporter.sendMessage(color(plugin.getConfig().getString("messages.cannot-report-self")));
            return true;
        }

        CooldownManager cm = plugin.getCooldownManager();
        ReportManager rm = plugin.getReportManager();
        UUID reporterUUID = reporter.getUniqueId();
        UUID targetUUID = target.getUniqueId();
        String resolvedName = target.getName() != null ? target.getName() : targetName;

        if (!reporter.hasPermission("reportplugin.admin")) {
            if (cm.isOnGlobalCooldown(reporterUUID)) {
                reporter.sendMessage(color(plugin.getConfig().getString("messages.global-cooldown")
                        .replace("%time%", formatTime(cm.getGlobalRemaining(reporterUUID)))));
                return true;
            }
            if (cm.hasReportedRecently(reporterUUID, targetUUID)) {
                reporter.sendMessage(color(plugin.getConfig().getString("messages.same-player-cooldown")
                        .replace("%target%", resolvedName)
                        .replace("%time%", formatTime(cm.getSamePlayerRemaining(reporterUUID, targetUUID)))));
                return true;
            }
            int maxHourly = plugin.getConfig().getInt("cooldowns.max-reports-per-hour", 6);
            if (rm.getReportsByReporterLastHour(reporterUUID) >= maxHourly) {
                reporter.sendMessage(color(plugin.getConfig().getString("messages.hourly-limit")
                        .replace("%max%", String.valueOf(maxHourly))));
                return true;
            }
        }

        boolean anonymous = plugin.getConfig().getBoolean("features.anonymous-reporting", false);
        String priority = "medium";

        // Instant memory ops
        String reportId = rm.createReport(
                reporterUUID, reporter.getName(),
                targetUUID, resolvedName, reason,
                reporter.getWorld().getName(),
                reporter.getLocation().getX(),
                reporter.getLocation().getY(),
                reporter.getLocation().getZ()
        );

        cm.applyGlobalCooldown(reporterUUID);
        cm.applySamePlayerCooldown(reporterUUID, targetUUID);

        // Instant feedback
        String msgKey = anonymous ? "messages.anonymous-success" : "messages.success";
        reporter.sendMessage(color(plugin.getConfig().getString(msgKey)
                .replace("%target%", resolvedName)
                .replace("%id%", reportId)));

        if (plugin.getConfig().getBoolean("sound-on-success", true)) {
            reporter.playSound(reporter.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
        }

        // Async Discord
        plugin.getDiscordWebhook().sendReport(reporter, target, reason, reportId, priority, anonymous);

        String staffMsg = plugin.getConfig().getString("messages.staff-notify", "")
                .replace("%reporter%", anonymous ? "Anonymous" : reporter.getName())
                .replace("%target%", resolvedName)
                .replace("%reason%", reason)
                .replace("%priority%", priority.toUpperCase());
        String staffPerm = plugin.getConfig().getString("staff-permission", "reportplugin.staff");
        boolean sound = plugin.getConfig().getBoolean("sound-on-report", true);
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(staffPerm)) {
                staff.sendMessage(color(staffMsg));
                if (sound) staff.playSound(staff.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            }
        }

        return true;
    }

    private boolean isBlacklisted(String name) {
        if (name == null) return false;
        for (String s : plugin.getConfig().getStringList("features.blacklist")) {
            if (s.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.prefix", "") + message);
    }

    private String formatTime(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        if (seconds < 86400) return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        return (seconds / 86400) + "d " + ((seconds % 86400) / 3600) + "h";
    }
}
