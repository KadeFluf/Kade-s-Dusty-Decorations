package com.kade.dustydeco.block;

import com.kade.dustydeco.Dustydeco;
import com.kade.dustydeco.block.custom.ShadedLamp;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class ModBlocks {
    public static final Block SHADED_LAMP = registerBlock("shaded_lamp",
            new ShadedLamp(BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .strength(0.5f, 1.0f)
                    .lightLevel(state -> state.getValue(ShadedLamp.LIT) ? 12 : 0)));

    private static Block registerBlock(String name, Block block) {
        registerBlockItem(name, block);
        return Registry.register(BuiltInRegistries.BLOCK, new ResourceLocation(Dustydeco.MOD_ID, name), block);
    }

    private static Item registerBlockItem(String name, Block block) {
        return Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(Dustydeco.MOD_ID, name),
                new BlockItem(block, new Item.Properties()));
    }

    public static void registerModBlocks() {
        Dustydeco.LOGGER.info("Registering blocks for " + Dustydeco.MOD_ID);
    }
}
