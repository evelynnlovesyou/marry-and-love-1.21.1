package io.github.evelynnlovesyou.marryandlove;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.evelynnlovesyou.marryandlove.config.ConfigReader;
import io.github.evelynnlovesyou.marryandlove.config.LangReader;
import io.github.evelynnlovesyou.marryandlove.manager.CommandRegisterManager;
import io.github.evelynnlovesyou.marryandlove.manager.MarriageManager;
import io.github.evelynnlovesyou.marryandlove.manager.PermissionManager;

public class MarryAndLove implements ModInitializer {
	public static final String MOD_ID = "marry-and-love";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		String version = FabricLoader.getInstance()
				.getModContainer(MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");

		LOGGER.info("Initialising {} v{}", MOD_ID, version);
		ConfigReader.init();
		LangReader.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			CommandRegisterManager.register(dispatcher);
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> PermissionManager.init());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			MarriageManager.ensureLoaded(handler.getPlayer());
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			MarriageManager.handleDisconnect(handler.getPlayer());
		});
	}
}