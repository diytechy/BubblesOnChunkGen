package com.bubbleschunkgen.forge;

import com.bubbleschunkgen.common.BubblesConstants;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("bubbleschunkgen")
public class ForgeMod {

    private static final Logger LOGGER = LoggerFactory.getLogger("BubblesOnChunkGen");
    private final ForgeChunkHandler chunkHandler;

    public ForgeMod(IEventBus modBus) {
        // Conflict detection: if Terra addon is already active, warn and skip
        if ("true".equals(System.getProperty(BubblesConstants.PROP_TERRA_ADDON))) {
            LOGGER.warn("BubblesOnChunkGen Terra addon is active - disabling standalone mod. "
                    + "Remove this mod JAR if using Terra.");
            chunkHandler = null;
            return;
        }

        System.setProperty(BubblesConstants.PROP_PLUGIN_FORGE, "true");

        chunkHandler = new ForgeChunkHandler();
        modBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        if (chunkHandler != null) {
            chunkHandler.register();
            LOGGER.info("BubblesOnChunkGen (Forge) enabled - will trigger bubble columns on new chunks.");
        }
    }
}
