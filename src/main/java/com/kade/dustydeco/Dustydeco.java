package com.kade.dustydeco;

import com.kade.dustydeco.block.ModBlocks;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Dustydeco implements ModInitializer {
    public static final String MOD_ID = "dustydeco";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Things working well");
        ModBlocks.registerModBlocks();
    }
}
