package com.bubbleschunkgen.terra.platform;

import com.dfsek.terra.api.Platform;
import com.dfsek.terra.api.addon.BaseAddon;
import com.dfsek.terra.api.event.events.world.generation.LootPopulateEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;

import java.util.List;

/**
 * Dedication-book injector for the vanilla-based mod platforms (Fabric and
 * NeoForge). Terra's mod container is the vanilla container block entity itself
 * (it implements Terra's {@code Container} via mixin), so the event's container
 * is a {@link BaseContainerBlockEntity} we can write the book into directly.
 */
public class ModDedicationChestListener extends DedicationChestListener {

    private final String platformName;

    public ModDedicationChestListener(Platform platform, BaseAddon addon, String platformName) {
        super(platform, addon);
        this.platformName = platformName;
    }

    @Override
    protected String platformName() {
        return platformName;
    }

    @Override
    protected void injectBook(LootPopulateEvent event) {
        if (!(event.getContainer() instanceof BaseContainerBlockEntity container)) return;

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(
                        Filterable.passThrough("CHIMERA"),
                        "CHIMERA",
                        0,
                        List.of(Filterable.passThrough(
                                Component.literal("CHIMERA\n\nDedicated to Finnian and Armin"))),
                        true));

        container.setItem(0, book);
    }
}
