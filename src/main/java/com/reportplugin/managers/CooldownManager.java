package com.reportplugin.managers;

import com.reportplugin.ReportPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class CooldownManager {

    private final ReportPlugin plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;

    private final Map<UUID, Long> globalCooldowns = new ConcurrentHashMap<>();
    private final Map<String, Long> samePlayerCooldowns = new ConcurrentHashMap<>();

    private final AtomicBoolean saveQueued = new AtomicBoolean(false);

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

        if (dataConfig.contains("global") && dataConfig.getConfigurationSection("global") != null) {
            for (String key : dataConfig.getConfigurationSection("global").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    long expire = dataConfig.getLong("global." + key);
                    if (expire > now) globalCooldowns.put(uuid, expire);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        if (dataConfig.contains("same-player") && dataConfig.getConfigurationSection("same-player") != null) {
            for (String key : dataConfig.getConfigurationSection("same-player").getKeys(false)) {
                long expire = dataConfig.getLong("same-player." + key);
                if (expire > now) samePlayerCooldowns.put(key, expire);
            }
        }
    }

    /** Instant in-memory update. Disk write is deferred (fast path). */
    public void applyGlobalCooldown(UUID reporter) {
        int seconds = plugin.getConfig().getInt("cooldowns.global-seconds", 30);
        globalCooldowns.put(reporter, System.currentTimeMillis() + (seconds * 1000L));
        queueSave();
    }

    public void applySamePlayerCooldown(UUID reporter, UUID target) {
        int seconds = plugin.getConfig().getInt("cooldowns.same-player-seconds", 604800);
        samePlayerCooldowns.put(reporter + ":" + target, System.currentTimeMillis() + (seconds * 1000L));
        queueSave();
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
        return Math.max(0, (expire - System.currentTimeMillis()) / 1000);
    }

    public boolean hasReportedRecently(UUID reporter, UUID target) {
        String key = reporter + ":" + target;
        Long expire = samePlayerCooldowns.get(key);
        if (expire == null) return false;
        if (System.currentTimeMillis() >= expire) {
            samePlayerCooldowns.remove(key);
            return false;
        }
        return true;
    }

    public long getSamePlayerRemaining(UUID reporter, UUID target) {
        Long expire = samePlayerCooldowns.get(reporter + ":" + target);
        if (expire == null) return 0;
        return Math.max(0, (expire - System.currentTimeMillis()) / 1000);
    }

    /** Debounced async save – max once every ~2 seconds under spam */
    private void queueSave() {
        if (!saveQueued.compareAndSet(false, true)) return;
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            try {
                saveNow();
            } finally {
                saveQueued.set(false);
            }
        }, 40L); // 2 seconds
    }

    public void save() {
        // Called on disable – force immediate save on async thread if possible
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveNow);
        } else {
            saveNow();
        }
    }

    private synchronized void saveNow() {
        if (dataConfig == null) dataConfig = new YamlConfiguration();
        dataConfig.set("global", null);
        dataConfig.set("same-player", null);

        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> e : globalCooldowns.entrySet()) {
            if (e.getValue() > now) dataConfig.set("global." + e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Long> e : samePlayerCooldowns.entrySet()) {
            if (e.getValue() > now) dataConfig.set("same-player." + e.getKey(), e.getValue());
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save cooldowns.yml: " + e.getMessage());
        }
    }
}
