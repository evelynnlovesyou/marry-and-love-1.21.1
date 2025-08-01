package com.evelynnlovesyou.marryandlove;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import eu.pb4.placeholders.api.Placeholders;

public class Marryandlove implements ModInitializer {
    public static final String MOD_ID = "marryandlove";

    @Override
    public void onInitialize() {
        MarriageData.init();

        // Register /marry command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            MarryCommand.register(dispatcher);
        });

        // Register placeholders
        Placeholders.register(MOD_ID, new MarriagePlaceholderProvider());
    }
}
