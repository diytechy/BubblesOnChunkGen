package com.bubbleschunkgen.terra;

import com.bubbleschunkgen.terra.platform.PlatformDetector;
import com.dfsek.terra.addons.manifest.api.AddonInitializer;
import com.dfsek.terra.api.Platform;
import com.dfsek.terra.api.addon.BaseAddon;
import com.dfsek.terra.api.inject.annotations.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Terra addon entry point for BubblesOnChunkGen.
 * Detects the underlying server platform and registers the matching handler.
 *
 * On Fabric and NeoForge this JAR is also loaded as a mod (see fabric.mod.json
 * and META-INF/neoforge.mods.toml) so that {@link com.bubbleschunkgen.terra.mixin.FlowableFluidMixin}
 * is registered before Terra calls into us. That entry-point side does nothing
 * else; CHIMERA detection and chunk handling are wired up here, from Terra.
 */
public class BubblesTerraAddon implements AddonInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("BubblesOnChunkGen-Terra");

    @Inject
    private Platform platform;

    @Inject
    private BaseAddon addon;

    @Override
    public void initialize() {
        LOGGER.info("Initializing BubblesOnChunkGen Terra addon...");

        PlatformDetector.ServerPlatform detectedPlatform = PlatformDetector.detect();
        LOGGER.info("Detected server platform: {}", detectedPlatform);

        try {
            switch (detectedPlatform) {
                case BUKKIT -> {
                    new com.bubbleschunkgen.terra.platform.BukkitTerraHandler().register();
                    // The dedication chest itself is placed + loot-filled by world
                    // generation; this listener only injects the custom written book,
                    // which Terra loot tables cannot express.
                    new com.bubbleschunkgen.terra.platform.BukkitDedicationChestListener(platform, addon).register();
                    LOGGER.info("Registered Bukkit chunk listeners for bubble column generation.");
                }
                case FABRIC -> {
                    new com.bubbleschunkgen.terra.platform.FabricTerraHandler().register();
                    new com.bubbleschunkgen.terra.platform.ModDedicationChestListener(platform, addon, "Fabric").register();
                    LOGGER.info("Registered Fabric chunk listeners for bubble column generation.");
                }
                case FORGE -> {
                    new com.bubbleschunkgen.terra.platform.ForgeTerraHandler().register();
                    new com.bubbleschunkgen.terra.platform.ModDedicationChestListener(platform, addon, "NeoForge").register();
                    LOGGER.info("Registered NeoForge chunk listeners for bubble column generation.");
                }
                case MINESTOM -> {
                    new com.bubbleschunkgen.terra.platform.MinestomTerraHandler().register();
                    LOGGER.info("Registered Minestom chunk listeners for bubble column generation.");
                }
                case ALLAY -> {
                    new com.bubbleschunkgen.terra.platform.AllayTerraHandler().register();
                    LOGGER.info("Registered Allay chunk listeners for bubble column generation.");
                }
                case UNKNOWN -> LOGGER.error("Could not detect server platform! "
                        + "BubblesOnChunkGen Terra addon requires Bukkit/Paper, Fabric, NeoForge, Minestom, or Allay.");
            }
        } catch (Throwable t) {
            LOGGER.error("Failed to register {} listeners", detectedPlatform, t);
        }

        LOGGER.info("BubblesOnChunkGen Terra addon initialized.");
    }
}
