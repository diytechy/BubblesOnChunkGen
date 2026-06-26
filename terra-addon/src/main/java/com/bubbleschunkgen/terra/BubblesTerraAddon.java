package com.bubbleschunkgen.terra;

import com.bubbleschunkgen.terra.platform.DedicationLootTable;
import com.bubbleschunkgen.terra.platform.PlatformDetector;
import com.dfsek.terra.addons.manifest.api.AddonInitializer;
import com.dfsek.terra.api.Platform;
import com.dfsek.terra.api.addon.BaseAddon;
import com.dfsek.terra.api.event.events.config.pack.ConfigPackPreLoadEvent;
import com.dfsek.terra.api.event.functional.FunctionalEventHandler;
import com.dfsek.terra.api.inject.annotations.Inject;
import com.dfsek.terra.api.structure.LootTable;
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

        registerDedicationLootTable();

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

    /**
     * Terra's loot-table registry is created but never populated by the core
     * addons, so {@code loot(...)} calls would otherwise find nothing. Register
     * the dedication chest's loot table (emeralds + diamonds) under
     * {@code bubbles-chunk-gen:dedication} into every pack as it loads; a
     * {@code .tesf} structure invokes it by that key. The dedication written
     * book is added on top by the per-platform {@code DedicationChestListener}.
     */
    private void registerDedicationLootTable() {
        platform.getEventManager()
                .getHandler(FunctionalEventHandler.class)
                .register(addon, ConfigPackPreLoadEvent.class)
                .then(event -> {
                    try {
                        event.getPack().getOrCreateRegistry(LootTable.class)
                                .register(addon.key("dedication"), new DedicationLootTable(platform));
                    } catch (Exception e) {
                        // Already registered for this pack, or registry unavailable - ignore.
                    }
                });
    }
}
