package com.bubbleschunkgen.terra.platform;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge mod entry point. Intentionally a no-op — its only purpose is to make
 * NeoForge discover this JAR as a mod so the {@code bubbleschunkgen.mixins.json}
 * mixin config is registered before Terra's addon loader fires
 * {@link com.bubbleschunkgen.terra.BubblesTerraAddon#initialize()}.
 *
 * All chunk-handling and event-registration logic lives on the Terra-addon side
 * (ForgeTerraHandler) and is driven from Terra's lifecycle.
 *
 * Safe to load on a server without Terra: the mixin guards on
 * {@link com.bubbleschunkgen.common.FlowBlocker#getGlobalInstance()} being null,
 * and no listeners are registered here.
 */
@Mod("bubbleschunkgen")
public class ForgeEntry {

    private static final Logger LOGGER = LoggerFactory.getLogger("BubblesOnChunkGen-Terra");

    public ForgeEntry(IEventBus modBus) {
        LOGGER.debug("BubblesOnChunkGen NeoForge entry loaded (mixin registered, waiting for Terra).");
    }
}
