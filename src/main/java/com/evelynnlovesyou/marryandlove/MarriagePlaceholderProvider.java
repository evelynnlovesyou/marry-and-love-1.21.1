package com.evelynnlovesyou.marryandlove;

import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.UUID;

public class MarriagePlaceholderProvider {

    public static final Identifier MARRIAGE_STATUS = Identifier.of("marryandlove", "marriage_status");

    public static void register() {
        Placeholders.register(MARRIAGE_STATUS, MarriagePlaceholderProvider::provideMarriageStatus);
    }

    // Updated method signature to match BiFunction<PlaceholderContext, String[], PlaceholderResult>
    private static PlaceholderResult provideMarriageStatus(PlaceholderContext ctx, String arg) {
        ServerPlayerEntity player = ctx.player();
        if (player == null) {
            return PlaceholderResult.value(Text.empty());
        }

        UUID spouseId = MarriageManager.getSpouse(player.getUuid());
        if (spouseId == null) {
            return PlaceholderResult.value(Text.literal("").formatted(Formatting.GRAY));
        }

        ServerPlayerEntity spouse = null;
        if (player.getServer() != null && player.getServer().getPlayerManager() != null) {
            spouse = player.getServer().getPlayerManager().getPlayer(spouseId);
        }

        String spouseName = (spouse != null) ? spouse.getName().getString() : "Offline";

        Text displayText = Text.literal("[")
                .formatted(Formatting.GRAY)
                .append(Text.literal("♥").formatted(Formatting.RED))
                .append(Text.literal("]").formatted(Formatting.GRAY)).styled(style ->
                style.withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Text.literal("♥ Married to: " + spouseName + " ♥").formatted(Formatting.GOLD)
                ))
        );

        return PlaceholderResult.value(displayText);
    }

}
