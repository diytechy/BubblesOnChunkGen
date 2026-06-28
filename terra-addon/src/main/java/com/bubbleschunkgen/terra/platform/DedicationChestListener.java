package com.bubbleschunkgen.terra.platform;

import com.dfsek.terra.api.Platform;
import com.dfsek.terra.api.addon.BaseAddon;
import com.dfsek.terra.api.event.events.world.generation.LootPopulateEvent;
import com.dfsek.terra.api.event.functional.FunctionalEventHandler;
import com.dfsek.terra.api.registry.key.Keyed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base for the per-platform dedication-book injectors.
 *
 * <p>The dedication chest and its emeralds/diamonds are placed by world
 * generation (a {@code .tesf} structure + a Terra loot table). Terra loot tables
 * cannot encode a written book's title/author/pages, so once Terra populates a
 * loot structure whose registry id contains {@value #STRUCTURE_MARKER}, the
 * matching subclass injects the dedication book through the platform's native
 * container API.
 *
 * <p>Only the platform API used by {@link #register()} is referenced here, so the
 * native-API subclasses load only on their own platform.
 */
public abstract class DedicationChestListener {

    protected static final Logger LOGGER = LoggerFactory.getLogger("BubblesOnChunkGen-Terra");

    /** A loot structure is treated as the dedication chest if its id contains this. */
    private static final String STRUCTURE_MARKER = "dedication";

    private final Platform platform;
    private final BaseAddon addon;

    protected DedicationChestListener(Platform platform, BaseAddon addon) {
        this.platform = platform;
        this.addon = addon;
    }

    public void register() {
        platform.getEventManager()
                .getHandler(FunctionalEventHandler.class)
                .register(addon, LootPopulateEvent.class)
                .then(event -> {
                    if (isDedicationStructure(event)) injectBook(event);
                })
                // LootPopulateEvent is a PackEvent: without global() Terra only dispatches it
                // to handlers whose addon the pack declares as a dependency. CHIMERA does not
                // declare us, so register globally or the book is never injected.
                .global();
        LOGGER.info("[Bubbles] Registered dedication-chest loot listener ({}).", platformName());
    }

    /** Inject the dedication written book into the just-populated container. */
    protected abstract void injectBook(LootPopulateEvent event);

    /** Short platform label for logging. */
    protected abstract String platformName();

    private static boolean isDedicationStructure(LootPopulateEvent event) {
        return event.getStructure() instanceof Keyed<?> keyed
                && keyed.getRegistryKey().getID().toLowerCase().contains(STRUCTURE_MARKER);
    }
}
