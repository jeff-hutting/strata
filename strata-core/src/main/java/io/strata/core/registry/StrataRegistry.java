package io.strata.core.registry;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class StrataRegistry {

    private StrataRegistry() {}

    public static <T extends Block> T registerBlock(String modId, String name, T block) {
        return Registry.register(Registries.BLOCK, Identifier.of(modId, name), block);
    }

    public static <T extends Item> T registerItem(String modId, String name, T item) {
        return Registry.register(Registries.ITEM, Identifier.of(modId, name), item);
    }

    public static <T extends Block> T registerBlockWithItem(
            String modId, String name, T block, Item.Settings itemSettings) {
        registerBlock(modId, name, block);
        registerItem(modId, name, new BlockItem(block, itemSettings));
        return block;
    }

    public static <T extends Entity> EntityType<T> registerEntityType(
            String modId, String name, EntityType<T> type) {
        return Registry.register(Registries.ENTITY_TYPE, Identifier.of(modId, name), type);
    }
}
