package com.gargara.flanestate;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlanEstate implements ModInitializer {
    public static final String MOD_ID = "flanestate";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("FlanEstate Mod Initialized!");
        SignEventHandler.register();
        RentManager.register();
    }
}
