package com.evelynnlovesyou.marryandlove;

import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.PlaceholderProvider;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public class MarriagePlaceholderProvider implements PlaceholderProvider {
    @Override
    public PlaceholderResult provide(PlaceholderContext ctx, String key) {
        if (!(ctx.getEntity() instanceof ServerPlayerEntity player)) return PlaceholderResult.invalid("Not a player");

        UUID partner = MarriageData.getInstance().getPartner(player.getUuid());
        if (key.equals("partner")) {
            if (partner != null) {
                return PlaceholderResult.value(MarriageData.getInstance().getPlayerName(partner));
            } else {
                return PlaceholderResult.value("Single");
            }
        }

        return PlaceholderResult.invalid("Invalid key");
    }
}
