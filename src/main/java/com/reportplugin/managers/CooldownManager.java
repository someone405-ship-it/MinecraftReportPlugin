package com.reportplugin.managers;

import com.reportplugin.ReportPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {

    private final ReportPlugin plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;

    // Global cooldown: reporterUUID -> expire timestamp (ms)
    private final Map<UUID, Long> globalCooldowns = new HashMap<>();

    // Same-player: "reporterUUID:targetUUID" -> expire timestamp (ms)
    private final Map<String, Long> samePlayerCooldowns = new HashMap<>();

    public CooldownManager(ReportPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "cooldowns.yml");
        load();
    }

    public void load() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create cooldowns.yml: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        globalCooldowns.clear();
        samePlayerCooldowns.clear();

        long now = System.currentTimeMillis();

        if (dataConfig.contains("global")) {
            for (String key : dataConfig.getConfigurationSection("global").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    long expire = dataConfig.getLong("global." + key);
                    if (expire > now) {
                        globalCooldowns.put(uuid, expire);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }

        if (dataConfig.contains("same-player")) {
            for (String key : dataConfig.getConfigurationSection("same-player").getKeys(false)) {
                long expire = dataConfig.getLong("same-player." + key);
                if (expire > now) {
                    samePlayerCooldowns.put(key, expire);
                }
            }
        }

        plugin.getLogger().info("Loaded " + globalCooldowns.size() + " global cooldowns and " +
                samePlayerCooldowns.size() + " same-player cooldowns.");
    }

    public void save() {
        dataConfig.set("global", null);
        dataConfig.set("same-player", null);

        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, Long> entry : globalCooldowns.entrySet()) {
            if (entry.getValue() > now) {
                dataConfig.set("global." + entry.getKey().toString(), entry.getValue());
            }
        }

        for (Map.Entry<String, Long> entry : samePlayerCooldowns.entrySet()) {
            if (entry.getValue() > now) {
                dataConfig.set("same-player." + entry.getKey(), entry.getValue());
            }
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save cooldowns.yml: " + e.getMessage());
        }
    }

    public boolean isOnGlobalCooldown(UUID reporter) {
        Long expire = globalCooldowns.get(reporter);
        if (expire == null) return false;
        if (System.currentTimeMillis() >= expire) {
            globalCooldowns.remove(reporter);
            return false;
        }
        return true;
    }

    public long getGlobalRemaining(UUID reporter) {
        Long expire = globalCooldowns.get(reporter);
        if (expire == null) return 0;
        long remaining = (expire - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }

    public void applyGlobalCooldown(UUID reporter) {
        int seconds = plugin.getConfig().getInt("cooldowns.global-seconds", 60);
        globalCooldowns.put(reporter, System.currentTimeMillis() + (seconds * 1000L));
        save();
    }

    public boolean hasReportedRecently(UUID reporter, UUID target) {
        String key = reporter.toString() + ":" + target.toString();
        Long expire = samePlayerCooldowns.get(key);
        if (expire == null) return false;
        if (System.currentTimeMillis() >= expire) {
            samePlayerCooldowns.remove(key);
            return false;
        }
        return true;
    }

    public long getSamePlayerRemaining(UUID reporter, UUID target) {
        String key = reporter.toString() + ":" + target.toString();
        Long expire = samePlayerCooldowns.get(key);
        if (expire == null) return 0;
        long remaining = (expire - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }

    public void applySamePlayerCooldown(UUID reporter, UUID target) {
        int seconds = plugin.getConfig().getInt("cooldowns.same-player-seconds", 604800); // 1 week default
        String key = reporter.toString() + ":" + target.toString();
        samePlayerCooldowns.put(key, System.currentTimeMillis() + (seconds * 1000L));
        save();
    }
}
