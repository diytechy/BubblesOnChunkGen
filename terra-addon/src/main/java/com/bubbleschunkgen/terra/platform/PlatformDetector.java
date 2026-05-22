package com.bubbleschunkgen.terra.platform;

public class PlatformDetector {

    public enum ServerPlatform {
        BUKKIT, FORGE, FABRIC, MINESTOM, ALLAY, UNKNOWN
    }

    public static ServerPlatform detect() {
        if (classExists("org.allaymc.api.server.Server")) {
            return ServerPlatform.ALLAY;
        }
        if (classExists("net.minestom.server.MinecraftServer")) {
            return ServerPlatform.MINESTOM;
        }
        if (classExists("net.neoforged.fml.ModList") || classExists("net.minecraftforge.fml.ModList")) {
            return ServerPlatform.FORGE;
        }
        if (classExists("net.fabricmc.loader.api.FabricLoader")) {
            return ServerPlatform.FABRIC;
        }
        if (classExists("org.bukkit.Bukkit")) {
            return ServerPlatform.BUKKIT;
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
