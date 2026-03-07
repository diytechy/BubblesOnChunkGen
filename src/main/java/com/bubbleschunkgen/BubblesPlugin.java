package com.bubbleschunkgen;

import org.bukkit.plugin.java.JavaPlugin;

public class BubblesPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new ChunkGenListener(this), this);
        getLogger().info("BubblesOnChunkGen enabled - will trigger bubble columns on new chunks.");
    }
}
