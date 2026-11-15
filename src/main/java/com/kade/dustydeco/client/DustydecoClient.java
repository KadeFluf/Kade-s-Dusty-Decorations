package com.kade.dustydeco.client;

import com.kade.dustydeco.ObjModelLoader;
import com.kade.dustydeco.block.ModBlocks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.RenderType;

public class DustydecoClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ObjModelLoader.init();
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.SHADED_LAMP, RenderType.cutout());
    }
}
