package com.bubbleschunkgen.terra.platform;

import com.dfsek.terra.api.Platform;
import com.dfsek.terra.api.inventory.Inventory;
import com.dfsek.terra.api.inventory.ItemStack;
import com.dfsek.terra.api.structure.LootTable;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Items for the dedication chest (emeralds + diamonds), registered into each
 * pack's loot-table registry by {@link com.bubbleschunkgen.terra.BubblesTerraAddon}
 * under the key {@code bubbles-chunk-gen:dedication}. A {@code .tesf} structure
 * places the chest and calls {@code loot(x, y, z, "bubbles-chunk-gen:dedication")}.
 *
 * <p>The items are created through Terra's cross-platform item handle, so they
 * appear on every platform. The custom written book cannot be expressed through
 * Terra's item API, so it is added separately by {@link DedicationChestListener}
 * on the {@code LootPopulateEvent} that invoking this table fires.
 */
public class DedicationLootTable implements LootTable {

    private final Platform platform;

    public DedicationLootTable(Platform platform) {
        this.platform = platform;
    }

    @Override
    public void fillInventory(Inventory inventory, RandomGenerator random) {
        // Slot 0 is reserved for the dedication book (added on LootPopulateEvent).
        int slot = 1;
        for (ItemStack stack : getLoot(random)) {
            if (slot >= inventory.getSize()) break;
            inventory.setItem(slot++, stack);
        }
    }

    @Override
    public List<ItemStack> getLoot(RandomGenerator random) {
        List<ItemStack> loot = new ArrayList<>();
        loot.add(platform.getItemHandle().createItem("minecraft:emerald").newItemStack(6));
        loot.add(platform.getItemHandle().createItem("minecraft:diamond").newItemStack(7));
        return loot;
    }
}
