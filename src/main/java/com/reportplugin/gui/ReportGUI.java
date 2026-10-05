package com.reportplugin.gui;

import com.reportplugin.ReportPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class ReportGUI {

    private final ReportPlugin plugin;
    public static final String PLAYER_SELECT = "report_player_select";
    public static final String REASON_SELECT = "report_reason_select";
    public static final String CONFIRM = "report_confirm";

    // Temporary storage while player is in GUI flow
    public static final java.util.Map<UUID, UUID> selectedTarget = new java.util.HashMap<>();
    public static final java.util.Map<UUID, String> selectedReason = new java.util.HashMap<>();

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
            meta.setDisplayName(ChatColor.YELLOW + online.getName());
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Click to report this player",
                    ChatColor.DARK_GRAY + "UUID: " + online.getUniqueId()
            ));
            // store UUID
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "target_uuid"),
                    PersistentDataType.STRING,
                    online.getUniqueId().toString()
            );
            head.setItemMeta(meta);
            inv.setItem(slot++, head);
        }

        // Close button
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Cancel");
        close.setItemMeta(closeMeta);
        inv.setItem(49, close);

        player.openInventory(inv);
    }

    public void openReasonSelect(Player player, UUID targetUUID) {
        selectedTarget.put(player.getUniqueId(), targetUUID);

        String title = color(plugin.getConfig().getString("gui.title-reason-select", "&8Select a reason"));
        Inventory inv = Bukkit.createInventory(null, 27, title);

        List<String> reasons = plugin.getConfig().getStringList("reasons");
        Material[] icons = {
                Material.TNT, Material.DIAMOND_SWORD, Material.WITHER_SKELETON_SKULL,
                Material.PAPER, Material.GOLD_INGOT, Material.COMMAND_BLOCK,
                Material.PAINTING, Material.WRITABLE_BOOK
        };

        for (int i = 0; i < reasons.size() && i < 8; i++) {
            ItemStack item = new ItemStack(icons[i % icons.length]);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.AQUA + reasons.get(i));
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Click to select this reason"));
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "reason"),
                    PersistentDataType.STRING,
                    reasons.get(i)
            );
            item.setItemMeta(meta);
            inv.setItem(10 + i, item);
        }

        // Back / Cancel
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← Back");
        back.setItemMeta(backMeta);
        inv.setItem(22, back);

        player.openInventory(inv);
    }

    public void openConfirm(Player player, String reason) {
        selectedReason.put(player.getUniqueId(), reason);
        UUID targetUUID = selectedTarget.get(player.getUniqueId());
        if (targetUUID == null) return;

        String targetName = Bukkit.getOfflinePlayer(targetUUID).getName();
        if (targetName == null) targetName = "Unknown";

        String title = color(plugin.getConfig().getString("gui.title-confirm", "&8Confirm Report"));
        Inventory inv = Bukkit.createInventory(null, 27, title);

        // Info item
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + "Report Summary");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Target: " + ChatColor.YELLOW + targetName);
        lore.add(ChatColor.GRAY + "Reason: " + ChatColor.WHITE + reason);
        lore.add("");
        lore.add(ChatColor.GREEN + "Click the green wool to confirm");
        lore.add(ChatColor.RED + "Click the red wool to cancel");
        infoMeta.setLore(lore);
        info.setItemMeta(infoMeta);
        inv.setItem(13, info);

        // Confirm
        ItemStack confirm = new ItemStack(Material.LIME_WOOL);
        ItemMeta cMeta = confirm.getItemMeta();
        cMeta.setDisplayName(ChatColor.GREEN + "✔ CONFIRM REPORT");
        confirm.setItemMeta(cMeta);
        inv.setItem(11, confirm);

        // Cancel
        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.RED + "✘ CANCEL");
        cancel.setItemMeta(cancelMeta);
        inv.setItem(15, cancel);

        player.openInventory(inv);
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
