package com.reportplugin;

import com.reportplugin.commands.ReportCommand;
import com.reportplugin.managers.CooldownManager;
import com.reportplugin.utils.DiscordWebhook;
import org.bukkit.plugin.java.JavaPlugin;

public class ReportPlugin extends JavaPlugin {

    private static ReportPlugin instance;
    private CooldownManager cooldownManager;
    private DiscordWebhook discordWebhook;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();

        // Initialize managers
        this.cooldownManager = new CooldownManager(this);
        this.discordWebhook = new DiscordWebhook(this);

        // Register command
        getCommand("report").setExecutor(new ReportCommand(this));
        getCommand("report").setTabCompleter(new ReportCommand(this));

        getLogger().info("========================================");
        getLogger().info(" Minecraft Report Plugin v" + getDescription().getVersion());
        getLogger().info(" Advanced Discord Webhook Reporting");
        getLogger().info(" Features: Avatars, Cooldowns, Weekly Limits");
        getLogger().info("========================================");
    }

    @Override
    public void onDisable() {
        if (cooldownManager != null) {
            cooldownManager.save();
        }
        getLogger().info("Minecraft Report Plugin disabled.");
    }

    public static ReportPlugin getInstance() {
        return instance;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public DiscordWebhook getDiscordWebhook() {
        return discordWebhook;
    }

    public void reload() {
        reloadConfig();
        cooldownManager.load();
        getLogger().info("Configuration reloaded.");
    }
}
