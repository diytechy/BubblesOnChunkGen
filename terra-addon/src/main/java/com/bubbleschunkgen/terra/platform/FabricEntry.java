package com.bubbleschunkgen.terra.platform;

import net.fabricmc.api.DedicatedServerModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric mod entry point. Intentionally a no-op — its only purpose is to make
 * Fabric Loader discover this JAR as a mod so the {@code bubbleschunkgen.mixins.json}
 * mixin config is registered before Terra's addon loader fires
 * {@link com.bubbleschunkgen.terra.BubblesTerraAddon#initialize()}.
 *
 * All chunk-handling and event-registration logic lives on the Terra-addon side
 * (FabricTerraHandler) and is driven from Terra's lifecycle.
 *
 * Safe to load on a server without Terra: the mixin guards on
 * {@link com.bubbleschunkgen.common.FlowBlocker#getGlobalInstance()} being null,
 * and no listeners are registered here.
 */
public class FabricEntry implements DedicatedServerModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("BubblesOnChunkGen-Terra");

    @Override
    public void onInitializeServer() {
        LOGGER.debug("BubblesOnChunkGen Fabric entry loaded (mixin registered, waiting for Terra).");
    }
}
