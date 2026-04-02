package com.bubbleschunkgen.terra;

import com.bubbleschunkgen.common.BubblesConstants;
import com.bubbleschunkgen.terra.platform.PlatformDetector;
import com.dfsek.terra.addons.manifest.api.AddonInitializer;
import com.dfsek.terra.api.Platform;
import com.dfsek.terra.api.addon.BaseAddon;
import com.dfsek.terra.api.inject.annotations.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Terra addon entry point for BubblesOnChunkGen.
 * Detects the underlying server platform and registers appropriate
 * chunk load/unload and water flow blocking listeners.
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

        // Set system property so standalone platform plugins can detect us
        System.setProperty(BubblesConstants.PROP_TERRA_ADDON, "true");

        // Warn if a standalone plugin was loaded before us
        if ("true".equals(System.getProperty(BubblesConstants.PROP_PLUGIN_BUKKIT))) {
            LOGGER.warn("Standalone Bukkit plugin detected. "
                    + "The Terra addon handles bubble columns - please remove the standalone plugin.");
        }
        if ("true".equals(System.getProperty(BubblesConstants.PROP_PLUGIN_FORGE))) {
            LOGGER.warn("Standalone Forge mod detected. "
                    + "The Terra addon handles bubble columns - please remove the standalone mod.");
        }
        if ("true".equals(System.getProperty(BubblesConstants.PROP_PLUGIN_FABRIC))) {
            LOGGER.warn("Standalone Fabric mod detected. "
                    + "The Terra addon handles bubble columns - please remove the standalone mod.");
        }

        // Detect platform and register listeners
        PlatformDetector.ServerPlatform detectedPlatform = PlatformDetector.detect();
        LOGGER.info("Detected server platform: {}", detectedPlatform);

        switch (detectedPlatform) {
            case BUKKIT -> {
                try {
                    new com.bubbleschunkgen.terra.platform.BukkitTerraHandler().register();
                    LOGGER.info("Registered Bukkit chunk listeners for bubble column generation.");
                } catch (Exception e) {
                    LOGGER.error("Failed to register Bukkit listeners", e);
                }
            }
            case FORGE -> {
                LOGGER.warn("Forge platform detected but Terra addon Forge listeners are not yet implemented. "
                        + "Use the standalone Forge mod instead.");
            }
            case FABRIC -> {
                LOGGER.warn("Fabric platform detected but Terra addon Fabric listeners are not yet implemented. "
                        + "Use the standalone Fabric mod instead.");
            }
            case UNKNOWN -> {
                LOGGER.error("Could not detect server platform! "
                        + "BubblesOnChunkGen requires Bukkit/Paper, Forge, or Fabric.");
            }
        }

        LOGGER.info("BubblesOnChunkGen Terra addon initialized.");
    }
}
