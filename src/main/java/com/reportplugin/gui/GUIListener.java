package com.reportplugin.gui;

import com.reportplugin.ReportPlugin;
import com.reportplugin.listeners.ChatListener;
import com.reportplugin.managers.CooldownManager;
import com.reportplugin.managers.ReportManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

public class GUIListener implements Listener {

    private final ReportPlugin plugin;
    private final ReportGUI gui;

    // Cached keys – avoid creating every click
    private final NamespacedKey keyTargetUuid;
    private final NamespacedKey keyReason;
    private final NamespacedKey keyPriority;

    public GUIListener(ReportPlugin plugin, ReportGUI gui) {
        this.plugin = plugin;
        this.gui = gui;
        this.keyTargetUuid = new NamespacedKey(plugin, "target_uuid");
        this.keyReason = new NamespacedKey(plugin, "reason");
        this.keyPriority = new NamespacedKey(plugin, "priority");
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = ChatColor.stripColor(event.getView().getTitle()).toLowerCase();
        if (!title.contains("select player") && !title.contains("select reason") && !title.contains("confirm")) {
            return;
        }

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();

        if (title.contains("select player")) {
            handlePlayerSelect(player, clicked, meta);
            return;
        }
        if (title.contains("select reason")) {
            handleReasonSelect(player, clicked, meta);
            return;
        }
        if (title.contains("confirm")) {
            handleConfirm(player, clicked);
        }
    }

    private void handlePlayerSelect(Player player, ItemStack clicked, ItemMeta meta) {
        if (clicked.getType().name().contains("BARRIER")) {
            player.closeInventory();
            return;
        }
        String uuidStr = meta.getPersistentDataContainer().get(keyTargetUuid, PersistentDataType.STRING);
        if (uuidStr == null) return;
        try {
            UUID targetUUID = UUID.fromString(uuidStr);
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
            if (isBlacklisted(target.getName())) {
                player.sendMessage(color(plugin.getConfig().getString("messages.blacklisted")));
                player.closeInventory();
                return;
            }
            gui.openReasonSelect(player, targetUUID);
        } catch (IllegalArgumentException ignored) {}
    }

    private void handleReasonSelect(Player player, ItemStack clicked, ItemMeta meta) {
        if (clicked.getType().name().contains("ARROW")) {
            gui.openPlayerSelect(player);
            return;
        }
        String reason = meta.getPersistentDataContainer().get(keyReason, PersistentDataType.STRING);
        String priority = meta.getPersistentDataContainer().get(keyPriority, PersistentDataType.STRING);
        if (priority == null) priority = "medium";
        if (reason == null) return;

        if (reason.toLowerCase().contains("other")) {
            UUID targetUUID = ReportGUI.selectedTarget.get(player.getUniqueId());
            if (targetUUID != null) {
                ChatListener.waitingForReason.put(player.getUniqueId(), targetUUID);
                ReportGUI.selectedPriority.put(player.getUniqueId(), priority);
                player.closeInventory();
                player.sendMessage(color("&eType the reason in chat now."));
                player.sendMessage(color("&7Type &ccancel &7to abort."));
            }
        } else {
            gui.openConfirm(player, reason, priority);
        }
    }

    private void handleConfirm(Player player, ItemStack clicked) {
        String type = clicked.getType().name();
        if (type.contains("RED_WOOL") || type.contains("BARRIER")) {
            player.closeInventory();
            clearTemp(player);
            player.sendMessage(color("&cReport cancelled."));
            return;
        }
        if (type.contains("LIME_WOOL")) {
            // Close first so UI feels instant
            player.closeInventory();
            submitFromGUI(player);
        }
    }

    private void submitFromGUI(Player reporter) {
        UUID targetUUID = ReportGUI.selectedTarget.get(reporter.getUniqueId());
        String reason = ReportGUI.selectedReason.get(reporter.getUniqueId());
        String priority = ReportGUI.selectedPriority.getOrDefault(reporter.getUniqueId(), "medium");
        clearTemp(reporter);

        if (targetUUID == null || reason == null) {
            reporter.sendMessage(color("&cSomething went wrong."));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String targetName = target.getName() != null ? target.getName() : "Unknown";

        if (isBlacklisted(targetName)) {
            reporter.sendMessage(color(plugin.getConfig().getString("messages.blacklisted")));
            return;
        }

        CooldownManager cm = plugin.getCooldownManager();
        ReportManager rm = plugin.getReportManager();
        UUID reporterUUID = reporter.getUniqueId();

        // Fast in-memory checks only
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

        // Instant memory ops
        String reportId = rm.createReport(
                reporterUUID, reporter.getName(),
                targetUUID, targetName, reason,
                reporter.getWorld().getName(),
                reporter.getLocation().getX(),
                reporter.getLocation().getY(),
                reporter.getLocation().getZ()
        );

        cm.applyGlobalCooldown(reporterUUID);
        cm.applySamePlayerCooldown(reporterUUID, targetUUID);

        // Instant feedback to player (before Discord network)
        String msgKey = anonymous ? "messages.anonymous-success" : "messages.success";
        reporter.sendMessage(color(plugin.getConfig().getString(msgKey)
                .replace("%target%", targetName)
                .replace("%id%", reportId)));

        if (plugin.getConfig().getBoolean("sound-on-success", true)) {
            reporter.playSound(reporter.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
        }

        // Fire-and-forget Discord (already async inside)
        plugin.getDiscordWebhook().sendReport(reporter, target, reason, reportId, priority, anonymous);

        // Staff notify (cheap)
        String staffMsg = plugin.getConfig().getString("messages.staff-notify", "")
                .replace("%reporter%", anonymous ? "Anonymous" : reporter.getName())
                .replace("%target%", targetName)
                .replace("%reason%", reason)
                .replace("%priority%", priority.toUpperCase());
        String staffPerm = plugin.getConfig().getString("staff-permission", "reportplugin.staff");
        boolean sound = plugin.getConfig().getBoolean("sound-on-report", true);
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(staffPerm)) {
                staff.sendMessage(color(staffMsg));
                if (sound) {
                    staff.playSound(staff.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                }
            }
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

    private void clearTemp(Player p) {
        UUID id = p.getUniqueId();
        ReportGUI.selectedTarget.remove(id);
        ReportGUI.selectedReason.remove(id);
        ReportGUI.selectedPriority.remove(id);
        ChatListener.waitingForReason.remove(id);
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
