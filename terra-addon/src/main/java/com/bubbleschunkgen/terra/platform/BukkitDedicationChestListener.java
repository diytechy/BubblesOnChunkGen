package com.bubbleschunkgen.terra.platform;

import com.dfsek.terra.api.Platform;
import com.dfsek.terra.api.addon.BaseAddon;
import com.dfsek.terra.api.event.events.world.generation.LootPopulateEvent;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

/**
 * Bukkit dedication-book injector. Terra's Bukkit container exposes the native
 * {@link org.bukkit.inventory.Inventory} via {@code getInventory().getHandle()}.
 */
public class BukkitDedicationChestListener extends DedicationChestListener {

    public BukkitDedicationChestListener(Platform platform, BaseAddon addon) {
        super(platform, addon);
    }

    @Override
    protected String platformName() {
        return "Bukkit";
    }

    @Override
    protected void injectBook(LootPopulateEvent event) {
        Object handle = event.getContainer().getInventory().getHandle();
        if (!(handle instanceof org.bukkit.inventory.Inventory inventory)) return;

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle("CHIMERA");
        meta.setAuthor("CHIMERA");
        meta.addPages(net.kyori.adventure.text.Component.text("CHIMERA\n\nDedicated to Finnian and Armin"));
        book.setItemMeta(meta);

        inventory.setItem(0, book);
    }
}
