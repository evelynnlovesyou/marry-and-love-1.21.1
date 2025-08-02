package com.evelynnlovesyou.marryandlove;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

public class Marryandlove implements ModInitializer {

    @Override
    public void onInitialize() {
        // Register marriage commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> MarryCommand.register(dispatcher));

        // Initialize MarriageManager when server starts
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);

        // Register placeholder provider with papi
        MarriagePlaceholderProvider.register();
        MALSoundEvent.registerSounds();
    }

    private void onServerStarted(MinecraftServer server) {
        MarriageManager.init(server);
    }
}
