package com.bubbleschunkgen.fabric;

import com.bubbleschunkgen.common.BubblesConstants;
import net.fabricmc.api.DedicatedServerModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FabricMod implements DedicatedServerModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("BubblesOnChunkGen");

    @Override
    public void onInitializeServer() {
        // Conflict detection: if Terra addon is already active, warn and skip
        if ("true".equals(System.getProperty(BubblesConstants.PROP_TERRA_ADDON))) {
            LOGGER.warn("BubblesOnChunkGen Terra addon is active - disabling standalone mod. "
                    + "Remove this mod JAR if using Terra.");
            return;
        }

        System.setProperty(BubblesConstants.PROP_PLUGIN_FABRIC, "true");

        FabricChunkHandler handler = new FabricChunkHandler();
        handler.register();
        LOGGER.info("BubblesOnChunkGen (Fabric) enabled - will trigger bubble columns on new chunks.");
    }
}
