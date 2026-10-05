package com.reportplugin;

import com.reportplugin.commands.ReportCommand;
import com.reportplugin.commands.StaffCommands;
import com.reportplugin.gui.GUIListener;
import com.reportplugin.gui.ReportGUI;
import com.reportplugin.listeners.ChatListener;
import com.reportplugin.managers.CooldownManager;
import com.reportplugin.managers.ReportManager;
import com.reportplugin.utils.DiscordWebhook;
import org.bukkit.plugin.java.JavaPlugin;

public class ReportPlugin extends JavaPlugin {

    private static ReportPlugin instance;
    private CooldownManager cooldownManager;
    private ReportManager reportManager;
    private DiscordWebhook discordWebhook;
    private ReportGUI reportGUI;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.cooldownManager = new CooldownManager(this);
        this.reportManager = new ReportManager(this);
        this.discordWebhook = new DiscordWebhook(this);
        this.reportGUI = new ReportGUI(this);

        // Commands
        ReportCommand reportCmd = new ReportCommand(this);
        getCommand("report").setExecutor(reportCmd);
        getCommand("report").setTabCompleter(reportCmd);

        StaffCommands staff = new StaffCommands(this);
        getCommand("reports").setExecutor(staff);
        getCommand("reportview").setExecutor(staff);
        getCommand("reportclose").setExecutor(staff);
        getCommand("reportreload").setExecutor(staff);

        // Listeners
        getServer().getPluginManager().registerEvents(new GUIListener(this, reportGUI), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        getLogger().info("================================================");
        getLogger().info("  Elite Minecraft Report Plugin v" + getDescription().getVersion());
        getLogger().info("  Features:");
        getLogger().info("  • Pure Discord Webhook (Reported Users)");
        getLogger().info("  • Full GUI with player heads + categories");
        getLogger().info("  • Custom reason via chat (Other)");
        getLogger().info("  • Global + weekly same-player cooldowns");
        getLogger().info("  • Hourly report limits");
        getLogger().info("  • Report IDs + persistent storage");
        getLogger().info("  • Staff tools (/reports, /reportview, /reportclose)");
        getLogger().info("================================================");
    }

    @Override
    public void onDisable() {
        if (cooldownManager != null) cooldownManager.save();
        if (reportManager != null) reportManager.save();
        getLogger().info("Elite Report Plugin disabled. Data saved.");
    }

    public static ReportPlugin getInstance() {
        return instance;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public ReportManager getReportManager() {
        return reportManager;
    }

    public DiscordWebhook getDiscordWebhook() {
        return discordWebhook;
    }

    public ReportGUI getReportGUI() {
        return reportGUI;
    }

    public void reloadPlugin() {
        reloadConfig();
        cooldownManager.load();
        reportManager.load();
        getLogger().info("Configuration and data reloaded.");
    }
}
