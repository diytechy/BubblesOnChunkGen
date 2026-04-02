package com.bubbleschunkgen.bukkit;

import com.bubbleschunkgen.common.BubblesConstants;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class BukkitPlugin extends JavaPlugin {

    private boolean debug = false;

    @Override
    public void onEnable() {
        // Conflict detection: if Terra addon is already active, disable this plugin
        if ("true".equals(System.getProperty(BubblesConstants.PROP_TERRA_ADDON))) {
            getLogger().warning("BubblesOnChunkGen Terra addon is active - disabling standalone plugin. "
                    + "Remove this plugin JAR if using Terra.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        System.setProperty(BubblesConstants.PROP_PLUGIN_BUKKIT, "true");

        getServer().getPluginManager().registerEvents(new BukkitChunkListener(this), this);
        getLogger().info("BubblesOnChunkGen (Bukkit) enabled - will trigger bubble columns on new chunks.");
    }

    @Override
    public void onDisable() {
        System.clearProperty(BubblesConstants.PROP_PLUGIN_BUKKIT);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("bubblesdebug")) {
            debug = !debug;
            sender.sendMessage("BubblesOnChunkGen debug " + (debug ? "ENABLED" : "DISABLED"));
            return true;
        }
        return false;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }
}
