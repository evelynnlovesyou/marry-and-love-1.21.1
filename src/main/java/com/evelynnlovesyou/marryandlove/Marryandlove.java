package com.evelynnlovesyou.marryandlove;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.MinecraftServer;

public class Marryandlove implements ModInitializer {

    @Override
    public void onInitialize() {
        // Register marriage placeholder for Styled-chat integration
        MarriagePlaceholderProvider.register();

        // Initialize MarriageManager once server starts
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);

        // Register commands during command registration phase
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            MarryCommand.register(dispatcher);  
        });
    }

    private void onServerStarted(MinecraftServer server) {
        MarriageManager.init(server);
    }
}
