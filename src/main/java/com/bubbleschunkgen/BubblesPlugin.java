package com.bubbleschunkgen;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class BubblesPlugin extends JavaPlugin {

    private boolean debug = false;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new ChunkGenListener(this), this);
        getLogger().info("BubblesOnChunkGen enabled - will trigger bubble columns on new chunks.");
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
}
