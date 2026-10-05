package com.reportplugin.gui;

import com.reportplugin.ReportPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class ReportGUI {

    private final ReportPlugin plugin;

    public static final Map<UUID, UUID> selectedTarget = new HashMap<>();
    public static final Map<UUID, String> selectedReason = new HashMap<>();
    public static final Map<UUID, String> selectedPriority = new HashMap<>();

    public ReportGUI(ReportPlugin plugin) {
        this.plugin = plugin;
    }

    public void openPlayerSelect(Player player) {
        String title = color(plugin.getConfig().getString("gui.title-player-select", "&8Select a player"));
        Inventory inv = Bukkit.createInventory(null, 54, title);

        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(player.getUniqueId())) continue;
            if (slot >= 45) break;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(online);
            meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + online.getName());
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Click to report",
                    ChatColor.DARK_GRAY + "Ping: " + online.getPing() + "ms",
                    ChatColor.DARK_GRAY + online.getUniqueId().toString().substring(0, 13) + "..."
            ));
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "target_uuid"),
                    PersistentDataType.STRING,
                    online.getUniqueId().toString()
            );
            head.setItemMeta(meta);
            inv.setItem(slot++, head);
        }

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "✖ Cancel");
        close.setItemMeta(closeMeta);
        inv.setItem(49, close);

        player.openInventory(inv);
    }

    public void openReasonSelect(Player player, UUID targetUUID) {
        selectedTarget.put(player.getUniqueId(), targetUUID);

        String title = color(plugin.getConfig().getString("gui.title-reason-select", "&8Select a reason"));
        Inventory inv = Bukkit.createInventory(null, 36, title);

        Material[] icons = {
                Material.TNT, Material.DIAMOND_SWORD, Material.WITHER_SKELETON_SKULL,
                Material.PAPER, Material.GOLD_INGOT, Material.COMMAND_BLOCK,
                Material.PAINTING, Material.WRITABLE_BOOK
        };

        List<Map<?, ?>> reasonList = plugin.getConfig().getMapList("reasons");
        if (reasonList == null || reasonList.isEmpty()) {
            // Fallback for old string list format
            List<String> old = plugin.getConfig().getStringList("reasons");
            for (int i = 0; i < old.size() && i < 8; i++) {
                addReasonItem(inv, 10 + i, icons[i % icons.length], old.get(i), "medium");
            }
        } else {
            int i = 0;
            for (Map<?, ?> entry : reasonList) {
                if (i >= 8) break;
                String name = String.valueOf(entry.get("name"));
                String priority = entry.containsKey("priority") ? String.valueOf(entry.get("priority")) : "medium";
                addReasonItem(inv, 10 + i, icons[i % icons.length], name, priority);
                i++;
            }
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← Back");
        back.setItemMeta(backMeta);
        inv.setItem(31, back);

        player.openInventory(inv);
    }

    private void addReasonItem(Inventory inv, int slot, Material mat, String name, String priority) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + name);

        String pColor = switch (priority.toLowerCase()) {
            case "low" -> ChatColor.GREEN + "LOW";
            case "high" -> ChatColor.GOLD + "HIGH";
            case "critical" -> ChatColor.RED + "CRITICAL";
            default -> ChatColor.YELLOW + "MEDIUM";
        };

        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Suggested priority: " + pColor,
                ChatColor.DARK_GRAY + "Click to select"
        ));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "reason"), PersistentDataType.STRING, name);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "priority"), PersistentDataType.STRING, priority);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    public void openConfirm(Player player, String reason, String priority) {
        selectedReason.put(player.getUniqueId(), reason);
        selectedPriority.put(player.getUniqueId(), priority != null ? priority : "medium");

        UUID targetUUID = selectedTarget.get(player.getUniqueId());
        if (targetUUID == null) return;

        String targetName = Bukkit.getOfflinePlayer(targetUUID).getName();
        if (targetName == null) targetName = "Unknown";

        String title = color(plugin.getConfig().getString("gui.title-confirm", "&8Confirm Report"));
        Inventory inv = Bukkit.createInventory(null, 27, title);

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Report Summary");
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Target: " + ChatColor.YELLOW + targetName);
        lore.add(ChatColor.GRAY + "Reason: " + ChatColor.WHITE + reason);
        lore.add(ChatColor.GRAY + "Priority: " + ChatColor.RED + priority.toUpperCase());
        lore.add("");
        lore.add(ChatColor.GREEN + "✔ Green wool = Confirm");
        lore.add(ChatColor.RED + "✘ Red wool = Cancel");
        infoMeta.setLore(lore);
        info.setItemMeta(infoMeta);
        inv.setItem(13, info);

        ItemStack confirm = new ItemStack(Material.LIME_WOOL);
        ItemMeta cMeta = confirm.getItemMeta();
        cMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "✔ CONFIRM");
        confirm.setItemMeta(cMeta);
        inv.setItem(11, confirm);

        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "✘ CANCEL");
        cancel.setItemMeta(cancelMeta);
        inv.setItem(15, cancel);

        player.openInventory(inv);
    }

    // Compatibility overload
    public void openConfirm(Player player, String reason) {
        openConfirm(player, reason, "medium");
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
