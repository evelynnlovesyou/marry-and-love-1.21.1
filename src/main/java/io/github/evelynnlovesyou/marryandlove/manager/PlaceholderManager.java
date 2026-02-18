package io.github.evelynnlovesyou.marryandlove.manager;


import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import com.mojang.authlib.GameProfile;
import io.github.evelynnlovesyou.marryandlove.config.LangReader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.Optional;
import java.util.UUID;
import io.github.evelynnlovesyou.marryandlove.MarryAndLove;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlaceholderManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarryAndLove.MOD_ID);
    public static final ResourceLocation MARRIAGE_STATUS = ResourceLocation.fromNamespaceAndPath("marryandlove", "marriage_status");

    public static void init()
    {
        if (initPlaceholderApi() == 0) {
            LOGGER.info("Placeholder API detected, registering placeholders...");
        } else {
            LOGGER.warn("Placeholder API not detected, skipping placeholder registration.");
            return;
        }
        Placeholders.register(MARRIAGE_STATUS, PlaceholderManager::provideMarriageStatus);
    }

    private static int initPlaceholderApi() {
        try {
            Class.forName("eu.pb4.placeholders.api.Placeholders"); // Check if Placeholder API is present
        } catch (ClassNotFoundException e) {
            LOGGER.warn("Placeholder API not found, placeholders will be unavailable.");
            return 1;
        }  
        return 0;
    }

    private static PlaceholderResult provideMarriageStatus(PlaceholderContext ctx, String arg){
        ServerPlayer player = ctx.player();
        if (player == null) {
            return PlaceholderResult.value(Component.empty());
        }

        UUID spouseId = MarriageManager.getSpouse(player);
        if (spouseId == null) {
            return PlaceholderResult.value(Component.empty());
        }

        ServerPlayer spouse = null;
        if (player.server != null) {
            spouse = player.server.getPlayerList().getPlayer(spouseId);
        }

        String spouseName = getSpouseName(player, spouseId, spouse);

        MutableComponent displayText = createMarriageBadgeComponent()
            .withStyle(style -> style.withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Component.literal(formatPlayerMessage(LangReader.MARRIAGE_STATUS_HOVER, spouseName))
                )));

        return PlaceholderResult.value(displayText);
    }

    private static String getSpouseName(ServerPlayer player, UUID spouseId, ServerPlayer spouse) {
        if (spouse != null) {
            return spouse.getName().getString();
        }

        if (player.server != null) {
            Optional<GameProfile> cachedProfile = player.server.getProfileCache().get(spouseId);
            if (cachedProfile.isPresent()) {
                return cachedProfile.get().getName();
            }
        }

        return "Unknown";
    }

    private static MutableComponent createMarriageBadgeComponent() {
        return Component.literal(LangReader.MARRIAGE_STATUS_BADGE);
    }

    private static String formatPlayerMessage(String template, String playerName) {
        return template.replace("%player%", playerName);
    }
}
