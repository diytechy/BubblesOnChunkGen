package com.bubbleschunkgen.terra.platform;

public class PlatformDetector {

    public enum ServerPlatform {
        BUKKIT, FORGE, FABRIC, UNKNOWN
    }

    public static ServerPlatform detect() {
        if (classExists("org.bukkit.Bukkit")) {
            return ServerPlatform.BUKKIT;
        }
        if (classExists("net.neoforged.fml.ModList") || classExists("net.minecraftforge.fml.ModList")) {
            return ServerPlatform.FORGE;
        }
        if (classExists("net.fabricmc.loader.api.FabricLoader")) {
            return ServerPlatform.FABRIC;
        }
        return ServerPlatform.UNKNOWN;
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className, false, PlatformDetector.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
