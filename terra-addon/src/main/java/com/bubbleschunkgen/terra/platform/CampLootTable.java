package com.bubbleschunkgen.terra.platform;

import com.dfsek.terra.api.Platform;
import com.dfsek.terra.api.inventory.Inventory;
import com.dfsek.terra.api.inventory.Item;
import com.dfsek.terra.api.inventory.ItemStack;
import com.dfsek.terra.api.structure.LootTable;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Faithful re-implementation of the vanilla 26.3 "Abandoned Camp" loot tables
 * (barrel / common chest / secret chest), registered into each pack by
 * {@link com.bubbleschunkgen.terra.BubblesTerraAddon} under the keys
 * {@code bubbles-chunk-gen:abandoned_camp_barrel},
 * {@code bubbles-chunk-gen:abandoned_camp_common_chest} and
 * {@code bubbles-chunk-gen:abandoned_camp_secret_chest}. The reconstructed
 * {@code abandoned_camp_*.tesf} structures place the container and call
 * {@code loot(x, y, z, "bubbles-chunk-gen:abandoned_camp_*")}.
 *
 * <p>Each pool rolls {@code rollsMin..rollsMax} (inclusive) times; every roll picks
 * one entry uniformly (all vanilla weights are 1, so duplicate entries — e.g. the
 * map appearing twice in the common pool — reproduce the vanilla weighting) and
 * yields a stack of {@code min..max}. Items are built through Terra's cross-platform
 * {@link com.dfsek.terra.api.handle.ItemHandle#createItem(String)}, which only
 * resolves a plain material id. Every barrel and common-chest item is a plain item,
 * so those two are exact; the secret chest's potions cannot carry their effect
 * through Terra's item API (matching the dedication book's limitation) and resolve
 * to plain potions — the secret chest is not placed by any current camp variant.
 */
public class CampLootTable implements LootTable {

    /** A single weighted-by-duplication loot entry yielding {@code min..max} of {@code id}. */
    public record Entry(String id, int min, int max) { }

    /** A loot pool rolled {@code rollsMin..rollsMax} (inclusive) times. */
    public record Pool(int rollsMin, int rollsMax, List<Entry> entries) { }

    private final Platform platform;
    private final List<Pool> pools;

    public CampLootTable(Platform platform, List<Pool> pools) {
        this.platform = platform;
        this.pools = pools;
    }

    private static Entry e(String id, int min, int max) {
        return new Entry("minecraft:" + id, min, max);
    }

    private static Entry e(String id, int n) {
        return e(id, n, n);
    }

    // --- vanilla tables (data/minecraft/loot_table/.../abandoned_camp_*.json) ---

    public static CampLootTable barrel(Platform p) {
        return new CampLootTable(p, List.of(new Pool(4, 8, List.of(
                e("arrow", 1, 3), e("bone", 2, 4), e("bowl", 1, 2), e("bread", 1, 3),
                e("coal", 2, 4), e("cobweb", 1), e("fishing_rod", 1), e("glass_bottle", 1, 3),
                e("leather", 1, 3), e("bundle", 1), e("rabbit_hide", 1, 4), e("string", 1, 2),
                e("wheat", 1, 4), e("white_candle", 1, 3), e("wooden_axe", 1)))));
    }

    public static CampLootTable commonChest(Platform p) {
        return new CampLootTable(p, List.of(
                new Pool(4, 6, List.of(
                        e("arrow", 4), e("map", 1), e("bone", 2, 4), e("cobweb", 1),
                        e("compass", 1), e("map", 1, 2), e("firework_rocket", 2, 4),
                        e("fishing_rod", 1), e("flint_and_steel", 1), e("glass_bottle", 1, 4),
                        e("lead", 1, 3), e("leather", 1, 4), e("bundle", 1), e("rabbit_hide", 1, 4),
                        e("saddle", 1), e("white_candle", 1, 3))),
                new Pool(2, 2, List.of(
                        e("bow", 1), e("bucket", 1), e("copper_axe", 1), e("copper_boots", 1),
                        e("copper_chestplate", 1), e("copper_leggings", 1), e("copper_spear", 1),
                        e("copper_sword", 1), e("spyglass", 1), e("shears", 1)))));
    }

    public static CampLootTable secretChest(Platform p) {
        return new CampLootTable(p, List.of(
                new Pool(2, 2, List.of(
                        e("diamond", 1), e("potion", 1), e("potion", 1), e("potion", 1), e("potion", 1))),
                new Pool(4, 6, List.of(
                        e("map", 1), e("copper_ingot", 1, 2), e("gold_ingot", 1, 2), e("iron_ingot", 1))),
                new Pool(0, 1, List.of(
                        e("iron_axe", 1), e("iron_boots", 1), e("iron_leggings", 1), e("iron_spear", 1)))));
    }

    @Override
    public List<ItemStack> getLoot(RandomGenerator random) {
        List<ItemStack> out = new ArrayList<>();
        for (Pool pool : pools) {
            int rolls = pool.rollsMin() + span(random, pool.rollsMin(), pool.rollsMax());
            for (int i = 0; i < rolls; i++) {
                Entry entry = pool.entries().get(random.nextInt(pool.entries().size()));
                int count = entry.min() + span(random, entry.min(), entry.max());
                ItemStack stack = stackOf(entry.id(), count);
                if (stack != null) {
                    out.add(stack);
                }
            }
        }
        return out;
    }

    @Override
    public void fillInventory(Inventory inventory, RandomGenerator random) {
        List<ItemStack> loot = getLoot(random);
        int size = inventory.getSize();
        // Fisher-Yates shuffle of slot indices so loot scatters like a vanilla chest.
        int[] slots = new int[size];
        for (int i = 0; i < size; i++) {
            slots[i] = i;
        }
        for (int i = size - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int t = slots[i];
            slots[i] = slots[j];
            slots[j] = t;
        }
        for (int i = 0; i < loot.size() && i < size; i++) {
            inventory.setItem(slots[i], loot.get(i));
        }
    }

    /** Inclusive uniform offset in {@code [0, max-min]}, or 0 when the range is empty. */
    private static int span(RandomGenerator random, int min, int max) {
        return max > min ? random.nextInt(max - min + 1) : 0;
    }

    /** Build a stack, tolerating ids the running server does not know (returns null). */
    private ItemStack stackOf(String id, int count) {
        try {
            Item item = platform.getItemHandle().createItem(id);
            return item == null ? null : item.newItemStack(count);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
