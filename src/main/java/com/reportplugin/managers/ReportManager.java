package com.reportplugin.managers;

import com.reportplugin.ReportPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ReportManager {

    private final ReportPlugin plugin;
    private final File dataFile;
    private FileConfiguration data;
    private final AtomicInteger counter = new AtomicInteger(0);

    public static class Report {
        public final String id;
        public final UUID reporterUUID;
        public final String reporterName;
        public final UUID targetUUID;
        public final String targetName;
        public final String reason;
        public final long timestamp;
        public final String world;
        public final double x, y, z;
        public boolean closed;

        public Report(String id, UUID reporterUUID, String reporterName, UUID targetUUID, String targetName,
                      String reason, long timestamp, String world, double x, double y, double z) {
            this.id = id;
            this.reporterUUID = reporterUUID;
            this.reporterName = reporterName;
            this.targetUUID = targetUUID;
            this.targetName = targetName;
            this.reason = reason;
            this.timestamp = timestamp;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.closed = false;
        }
    }

    private final Map<String, Report> reports = new LinkedHashMap<>();

    public ReportManager(ReportPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "reports.yml");
        load();
    }

    public void load() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create reports.yml");
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        reports.clear();

        if (data.contains("counter")) {
            counter.set(data.getInt("counter"));
        }

        if (data.contains("reports")) {
            for (String key : data.getConfigurationSection("reports").getKeys(false)) {
                String path = "reports." + key;
                try {
                    Report r = new Report(
                            key,
                            UUID.fromString(data.getString(path + ".reporterUUID")),
                            data.getString(path + ".reporterName"),
                            UUID.fromString(data.getString(path + ".targetUUID")),
                            data.getString(path + ".targetName"),
                            data.getString(path + ".reason"),
                            data.getLong(path + ".timestamp"),
                            data.getString(path + ".world"),
                            data.getDouble(path + ".x"),
                            data.getDouble(path + ".y"),
                            data.getDouble(path + ".z")
                    );
                    r.closed = data.getBoolean(path + ".closed", false);
                    reports.put(key, r);
                } catch (Exception ignored) {}
            }
        }
        plugin.getLogger().info("Loaded " + reports.size() + " reports from disk.");
    }

    public void save() {
        data.set("counter", counter.get());
        data.set("reports", null);

        for (Report r : reports.values()) {
            String path = "reports." + r.id;
            data.set(path + ".reporterUUID", r.reporterUUID.toString());
            data.set(path + ".reporterName", r.reporterName);
            data.set(path + ".targetUUID", r.targetUUID.toString());
            data.set(path + ".targetName", r.targetName);
            data.set(path + ".reason", r.reason);
            data.set(path + ".timestamp", r.timestamp);
            data.set(path + ".world", r.world);
            data.set(path + ".x", r.x);
            data.set(path + ".y", r.y);
            data.set(path + ".z", r.z);
            data.set(path + ".closed", r.closed);
        }

        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save reports.yml: " + e.getMessage());
        }
    }

    public String createReport(UUID reporterUUID, String reporterName, UUID targetUUID, String targetName,
                               String reason, String world, double x, double y, double z) {
        int num = counter.incrementAndGet();
        String id = String.format("RPT-%s-%04d",
                DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.systemDefault()).format(Instant.now()),
                num);

        Report report = new Report(id, reporterUUID, reporterName, targetUUID, targetName,
                reason, System.currentTimeMillis(), world, x, y, z);
        reports.put(id, report);
        save();
        return id;
    }

    public Report getReport(String id) {
        return reports.get(id);
    }

    public List<Report> getOpenReports() {
        List<Report> open = new ArrayList<>();
        for (Report r : reports.values()) {
            if (!r.closed) open.add(r);
        }
        // newest first
        open.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        return open;
    }

    public boolean closeReport(String id) {
        Report r = reports.get(id);
        if (r == null || r.closed) return false;
        r.closed = true;
        save();
        return true;
    }

    public int getReportsByReporterLastHour(UUID reporter) {
        long oneHourAgo = System.currentTimeMillis() - 3600_000L;
        int count = 0;
        for (Report r : reports.values()) {
            if (r.reporterUUID.equals(reporter) && r.timestamp >= oneHourAgo) {
                count++;
            }
        }
        return count;
    }
}
