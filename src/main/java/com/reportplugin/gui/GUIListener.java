package com.reportplugin.gui;

import com.reportplugin.ReportPlugin;
import com.reportplugin.managers.CooldownManager;
import com.reportplugin.managers.ReportManager;
import com.reportplugin.utils.DiscordWebhook;
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

import java.util.UUID;

public class GUIListener implements Listener {

    private final ReportPlugin plugin;
    private final ReportGUI gui;

    public GUIListener(ReportPlugin plugin, ReportGUI gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String title = ChatColor.stripColor(event.getView().getTitle());

        if (!title.contains("Select a player") && !title.contains("Select a reason") && !title.contains("Confirm Report")) {
            return;
        }

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();

        // PLAYER SELECT
        if (title.contains("Select a player")) {
            if (clicked.getType().name().contains("BARRIER")) {
                player.closeInventory();
                return;
            }

            String uuidStr = meta.getPersistentDataContainer().get(
                    new NamespacedKey(plugin, "target_uuid"),
                    PersistentDataType.STRING
            );
            if (uuidStr != null) {
                try {
                    UUID targetUUID = UUID.fromString(uuidStr);
                    gui.openReasonSelect(player, targetUUID);
                } catch (IllegalArgumentException ignored) {}
            }
            return;
        }

        // REASON SELECT
        if (title.contains("Select a reason")) {
            if (clicked.getType().name().contains("ARROW")) {
                gui.openPlayerSelect(player);
                return;
            }

            String reason = meta.getPersistentDataContainer().get(
                    new NamespacedKey(plugin, "reason"),
                    PersistentDataType.STRING
            );
            if (reason != null) {
                if (reason.toLowerCase().contains("other")) {
                    player.closeInventory();
                    player.sendMessage(color("&ePlease type the reason in chat now (or type &ccancel&e):"));
                    gui.openConfirm(player, "Other - (player will specify)");
                } else {
                    gui.openConfirm(player, reason);
                }
            }
            return;
        }

        // CONFIRM
        if (title.contains("Confirm Report")) {
            if (clicked.getType().name().contains("RED_WOOL") || clicked.getType().name().contains("BARRIER")) {
                player.closeInventory();
                clearTemp(player);
                player.sendMessage(color("&cReport cancelled."));
                return;
            }

            if (clicked.getType().name().contains("LIME_WOOL")) {
                player.closeInventory();
                submitFromGUI(player);
            }
        }
    }

    private void submitFromGUI(Player reporter) {
        UUID targetUUID = ReportGUI.selectedTarget.get(reporter.getUniqueId());
        String reason = ReportGUI.selectedReason.get(reporter.getUniqueId());
        clearTemp(reporter);

        if (targetUUID == null || reason == null) {
            reporter.sendMessage(color("&cSomething went wrong. Please try again."));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        String targetName = target.getName() != null ? target.getName() : "Unknown";

        CooldownManager cm = plugin.getCooldownManager();
        ReportManager rm = plugin.getReportManager();

        if (!reporter.hasPermission("reportplugin.admin")) {
            if (cm.isOnGlobalCooldown(reporter.getUniqueId())) {
                long rem = cm.getGlobalRemaining(reporter.getUniqueId());
                reporter.sendMessage(color(plugin.getConfig().getString("messages.global-cooldown")
                        .replace("%time%", formatTime(rem))));
                return;
            }
            if (cm.hasReportedRecently(reporter.getUniqueId(), targetUUID)) {
                long rem = cm.getSamePlayerRemaining(reporter.getUniqueId(), targetUUID);
                reporter.sendMessage(color(plugin.getConfig().getString("messages.same-player-cooldown")
                        .replace("%target%", targetName).replace("%time%", formatTime(rem))));
                return;
            }
            int maxHourly = plugin.getConfig().getInt("cooldowns.max-reports-per-hour", 5);
            if (rm.getReportsByReporterLastHour(reporter.getUniqueId()) >= maxHourly) {
                reporter.sendMessage(color(plugin.getConfig().getString("messages.hourly-limit")
                        .replace("%max%", String.valueOf(maxHourly))));
                return;
            }
        }

        String reportId = rm.createReport(
                reporter.getUniqueId(), reporter.getName(),
                targetUUID, targetName,
                reason,
                reporter.getWorld().getName(),
                reporter.getLocation().getX(),
                reporter.getLocation().getY(),
                reporter.getLocation().getZ()
        );

        DiscordWebhook webhook = plugin.getDiscordWebhook();
        boolean ok = webhook.sendReport(reporter, target, reason, reportId);

        if (ok) {
            cm.applyGlobalCooldown(reporter.getUniqueId());
            cm.applySamePlayerCooldown(reporter.getUniqueId(), targetUUID);

            reporter.sendMessage(color(plugin.getConfig().getString("messages.success")
                    .replace("%target%", targetName)
                    .replace("%id%", reportId)));

            String staffMsg = plugin.getConfig().getString("messages.staff-notify", "")
                    .replace("%reporter%", reporter.getName())
                    .replace("%target%", targetName)
                    .replace("%reason%", reason);
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

    private void clearTemp(Player p) {
        ReportGUI.selectedTarget.remove(p.getUniqueId());
        ReportGUI.selectedReason.remove(p.getUniqueId());
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
