package com.bubbleschunkgen.terra.platform;

import com.dfsek.terra.api.Platform;
import com.dfsek.terra.api.addon.BaseAddon;
import com.dfsek.terra.api.block.entity.Container;
import com.dfsek.terra.api.event.events.world.generation.LootPopulateEvent;
import com.dfsek.terra.api.event.functional.FunctionalEventHandler;
import com.dfsek.terra.api.registry.key.Keyed;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adds the CHIMERA dedication written book to chests filled by a Terra loot
 * structure whose registry id contains {@value #STRUCTURE_MARKER}.
 *
 * <p>The chest and its emeralds/diamonds are placed by world generation (a
 * {@code .tesf} structure + a Terra loot table). Terra loot tables cannot encode
 * a written book's title/author/pages, so this listener injects the book through
 * the native Bukkit inventory once Terra populates the container.
 *
 * <p>Bukkit only: the book item is created with the Bukkit API, so this class is
 * instantiated by {@link com.bubbleschunkgen.terra.BubblesTerraAddon} only when
 * the detected platform is Bukkit/Paper. Other platforms can mirror this with
 * their own native injector.
 */
public class BukkitDedicationChestListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("BubblesOnChunkGen-Terra");

    /** A loot structure is treated as the dedication chest if its id contains this. */
    private static final String STRUCTURE_MARKER = "dedication";

    private final Platform platform;
    private final BaseAddon addon;

    public BukkitDedicationChestListener(Platform platform, BaseAddon addon) {
        this.platform = platform;
        this.addon = addon;
    }

    public void register() {
        platform.getEventManager()
                .getHandler(FunctionalEventHandler.class)
                .register(addon, LootPopulateEvent.class)
                .then(this::onLootPopulate)
                .failThrough();
        LOGGER.info("[Bubbles] Registered dedication-chest loot listener (Bukkit).");
    }

    private void onLootPopulate(LootPopulateEvent event) {
        if (!isDedicationStructure(event)) return;

        Object handle = event.getContainer().getInventory().getHandle();
        if (!(handle instanceof org.bukkit.inventory.Inventory inventory)) return;

        inventory.setItem(0, dedicationBook());
    }

    private static boolean isDedicationStructure(LootPopulateEvent event) {
        return event.getStructure() instanceof Keyed<?> keyed
                && keyed.getRegistryKey().getID().toLowerCase().contains(STRUCTURE_MARKER);
    }

    private static ItemStack dedicationBook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle("CHIMERA");
        meta.setAuthor("CHIMERA");
        meta.addPages(net.kyori.adventure.text.Component.text("CHIMERA\n\nDedicated to Finnian and Armin"));
        book.setItemMeta(meta);
        return book;
    }
}
