package com.reportplugin.commands;

import com.reportplugin.ReportPlugin;
import com.reportplugin.managers.CooldownManager;
import com.reportplugin.utils.DiscordWebhook;
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
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Player reporter = (Player) sender;

        if (!reporter.hasPermission("reportplugin.report")) {
            reporter.sendMessage(color(plugin.getConfig().getString("messages.no-permission")));
            return true;
        }

        if (args.length < 2) {
            reporter.sendMessage(color(plugin.getConfig().getString("messages.usage")));
            return true;
        }

        String targetName = args[0];
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

        int minLength = plugin.getConfig().getInt("min-reason-length", 5);
        if (reason.length() < minLength) {
            reporter.sendMessage(color(plugin.getConfig().getString("messages.reason-too-short")));
            return true;
        }

        // Resolve target player (online or offline)
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline() && !plugin.getConfig().getBoolean("allow-offline-reports", true)) {
            reporter.sendMessage(color(plugin.getConfig().getString("messages.player-not-found").replace("%target%", targetName)));
            return true;
        }

        // Cannot report self
        if (target.getUniqueId().equals(reporter.getUniqueId())) {
            reporter.sendMessage(color(plugin.getConfig().getString("messages.cannot-report-self")));
            return true;
        }

        CooldownManager cooldownManager = plugin.getCooldownManager();
        UUID reporterUUID = reporter.getUniqueId();
        UUID targetUUID = target.getUniqueId();

        // Check global cooldown
        if (!reporter.hasPermission("reportplugin.admin") && cooldownManager.isOnGlobalCooldown(reporterUUID)) {
            long remaining = cooldownManager.getGlobalRemaining(reporterUUID);
            reporter.sendMessage(color(plugin.getConfig().getString("messages.global-cooldown")
                    .replace("%time%", formatTime(remaining))));
            return true;
        }

        // Check same-player weekly limit
        if (!reporter.hasPermission("reportplugin.admin") && cooldownManager.hasReportedRecently(reporterUUID, targetUUID)) {
            long remaining = cooldownManager.getSamePlayerRemaining(reporterUUID, targetUUID);
            reporter.sendMessage(color(plugin.getConfig().getString("messages.same-player-cooldown")
                    .replace("%target%", target.getName() != null ? target.getName() : targetName)
                    .replace("%time%", formatTime(remaining))));
            return true;
        }

        // Send to Discord
        DiscordWebhook webhook = plugin.getDiscordWebhook();
        boolean success = webhook.sendReport(reporter, target, reason);

        if (success) {
            // Apply cooldowns
            cooldownManager.applyGlobalCooldown(reporterUUID);
            cooldownManager.applySamePlayerCooldown(reporterUUID, targetUUID);

            reporter.sendMessage(color(plugin.getConfig().getString("messages.success")
                    .replace("%target%", target.getName() != null ? target.getName() : targetName)));
        } else {
            reporter.sendMessage(color(plugin.getConfig().getString("messages.webhook-error")));
        }

        return true;
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
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        return ChatColor.translateAlternateColorCodes('&', prefix + message);
    }

    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + " second" + (seconds != 1 ? "s" : "");
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            return minutes + " minute" + (minutes != 1 ? "s" : "");
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + "h " + minutes + "m";
        } else {
            long days = seconds / 86400;
            long hours = (seconds % 86400) / 3600;
            return days + " day" + (days != 1 ? "s" : "") + " " + hours + "h";
        }
    }
}
