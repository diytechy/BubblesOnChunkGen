package com.bubbleschunkgen.terra;

import com.bubbleschunkgen.terra.platform.CampLootTable;
import com.bubbleschunkgen.terra.platform.DedicationLootTable;
import com.bubbleschunkgen.terra.platform.PlatformDetector;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import com.dfsek.terra.addons.manifest.api.AddonInitializer;
import com.dfsek.terra.api.Platform;
import com.dfsek.terra.api.addon.BaseAddon;
import com.dfsek.terra.api.event.events.config.pack.ConfigPackPostLoadEvent;
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

        registerLootTables();

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
     * every loot table this addon provides into each pack as it loads; a
     * {@code .tesf} structure invokes one by its {@code bubbles-chunk-gen:<name>}
     * key:
     * <ul>
     *   <li>{@code dedication} — river-bottom easter-egg chest (emeralds + diamonds;
     *       the written book is added on top by the per-platform
     *       {@link com.bubbleschunkgen.terra.platform.DedicationChestListener}).</li>
     *   <li>{@code abandoned_camp_barrel} / {@code abandoned_camp_common_chest} /
     *       {@code abandoned_camp_secret_chest} — the 26.3 Abandoned Camp tables
     *       (see {@link CampLootTable}).</li>
     * </ul>
     */
    private void registerLootTables() {
        // name -> factory; LinkedHashMap keeps a stable registration/log order.
        Map<String, Function<Platform, LootTable>> tables = new LinkedHashMap<>();
        tables.put("dedication", DedicationLootTable::new);
        tables.put("abandoned_camp_barrel", CampLootTable::barrel);
        tables.put("abandoned_camp_common_chest", CampLootTable::commonChest);
        tables.put("abandoned_camp_secret_chest", CampLootTable::secretChest);

        platform.getEventManager()
                .getHandler(FunctionalEventHandler.class)
                .register(addon, ConfigPackPostLoadEvent.class)
                .then(event -> {
                    var pack = event.getPack().getRegistryKey();
                    try {
                        var registry = event.getPack().getOrCreateRegistry(LootTable.class);
                        // Plain loop (not forEach): registry.register throws a checked
                        // DuplicateEntryException, which a Consumer lambda cannot propagate.
                        for (var entry : tables.entrySet()) {
                            var key = addon.key(entry.getKey());
                            if (!registry.contains(key)) {
                                registry.register(key, entry.getValue().apply(platform));
                                LOGGER.info("Registered loot table {} into pack {}", key, pack);
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.error("Failed to register loot tables into pack {}", pack, e);
                    }
                })
                // ConfigPackPostLoadEvent is a PackEvent, and Terra only dispatches PackEvents
                // to a handler if the handler is global OR the pack declares this addon as a
                // dependency (FunctionalEventHandlerImpl#handle). CHIMERA invokes our loot table
                // without declaring us, so we must register globally to fire for every pack.
                .global();
    }
}
