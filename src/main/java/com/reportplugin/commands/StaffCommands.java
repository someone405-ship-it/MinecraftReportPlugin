package com.reportplugin.commands;

import com.reportplugin.ReportPlugin;
import com.reportplugin.managers.ReportManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class StaffCommands implements CommandExecutor {

    private final ReportPlugin plugin;

    public StaffCommands(ReportPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();

        if (cmd.equals("reportreload")) {
            if (!sender.hasPermission("reportplugin.admin")) {
                sender.sendMessage(color("&cNo permission."));
                return true;
            }
            plugin.reloadPlugin();
            sender.sendMessage(color("&aConfiguration and data reloaded successfully."));
            return true;
        }

        if (!sender.hasPermission("reportplugin.staff")) {
            sender.sendMessage(color("&cNo permission."));
            return true;
        }

        ReportManager rm = plugin.getReportManager();

        if (cmd.equals("reports")) {
            List<ReportManager.Report> open = rm.getOpenReports();
            if (open.isEmpty()) {
                sender.sendMessage(color(plugin.getConfig().getString("messages.no-reports")));
                return true;
            }

            sender.sendMessage(color("&8&m--------------------------------"));
            sender.sendMessage(color("&c&lOpen Reports &7(" + open.size() + ")"));
            sender.sendMessage(color("&8&m--------------------------------"));

            int shown = 0;
            for (ReportManager.Report r : open) {
                if (shown++ >= 15) {
                    sender.sendMessage(color("&7... and " + (open.size() - 15) + " more"));
                    break;
                }
                String time = DateTimeFormatter.ofPattern("MM/dd HH:mm")
                        .withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochMilli(r.timestamp));
                sender.sendMessage(color("&e" + r.id + " &7| &f" + r.reporterName + " &7→ &c" + r.targetName +
                        " &8| &7" + time));
                sender.sendMessage(color("  &7Reason: &f" + truncate(r.reason, 50)));
            }
            sender.sendMessage(color("&8&m--------------------------------"));
            sender.sendMessage(color("&7Use &e/reportview <id> &7for details"));
            return true;
        }

        if (cmd.equals("reportview")) {
            if (args.length < 1) {
                sender.sendMessage(color("&cUsage: /reportview <id>"));
                return true;
            }
            ReportManager.Report r = rm.getReport(args[0]);
            if (r == null) {
                sender.sendMessage(color("&cReport not found."));
                return true;
            }

            String time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(r.timestamp));

            sender.sendMessage(color("&8&m--------------------------------"));
            sender.sendMessage(color("&c&lReport Details"));
            sender.sendMessage(color("&eID: &f" + r.id));
            sender.sendMessage(color("&eStatus: " + (r.closed ? "&aCLOSED" : "&cOPEN")));
            sender.sendMessage(color("&eReporter: &f" + r.reporterName + " &7(" + r.reporterUUID + ")"));
            sender.sendMessage(color("&eTarget: &c" + r.targetName + " &7(" + r.targetUUID + ")"));
            sender.sendMessage(color("&eReason: &f" + r.reason));
            sender.sendMessage(color("&eWorld: &f" + r.world));
            sender.sendMessage(color("&eLocation: &f" + String.format("%.0f, %.0f, %.0f", r.x, r.y, r.z)));
            sender.sendMessage(color("&eTime: &f" + time));
            sender.sendMessage(color("&8&m--------------------------------"));
            return true;
        }

        if (cmd.equals("reportclose")) {
            if (args.length < 1) {
                sender.sendMessage(color("&cUsage: /reportclose <id>"));
                return true;
            }
            boolean ok = rm.closeReport(args[0]);
            if (ok) {
                sender.sendMessage(color(plugin.getConfig().getString("messages.report-closed")
                        .replace("%id%", args[0])));
            } else {
                sender.sendMessage(color("&cCould not close report (not found or already closed)."));
            }
            return true;
        }

        return true;
    }

    private String color(String msg) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        return ChatColor.translateAlternateColorCodes('&', prefix + msg);
    }

    private String truncate(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max - 3) + "...";
    }
}
