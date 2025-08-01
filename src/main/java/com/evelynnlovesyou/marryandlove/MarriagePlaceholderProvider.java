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

    // This is the ID for our placeholder. Gotta keep it unique so papi knows what’s what <3
    public static final Identifier MARRIAGE_STATUS = Identifier.of("marryandlove", "marriage_status");

    // Register the placeholder so papi can find and use it :)
    public static void register() {
        Placeholders.register(MARRIAGE_STATUS, MarriagePlaceholderProvider::provideMarriageStatus);
    }

    // The heart of the placeholder — what text to show when someone uses it <3 <3
    private static PlaceholderResult provideMarriageStatus(PlaceholderContext ctx, String arg) {
        ServerPlayerEntity player = ctx.player();
        if (player == null) {
            // No player here, so just return empty text. Sad face :(
            return PlaceholderResult.value(Text.empty());
        }

        // Check if player is married by looking up their spouse’s UUID <3
        UUID spouseId = MarriageManager.getSpouse(player.getUuid());
        if (spouseId == null) {
            // No spouse found, so return empty gray text to stay subtle :)
            return PlaceholderResult.value(Text.literal("").formatted(Formatting.GRAY));
        }

        ServerPlayerEntity spouse = null;
        // Try to find the spouse player if they are online :)
        if (player.getServer() != null && player.getServer().getPlayerManager() != null) {
            spouse = player.getServer().getPlayerManager().getPlayer(spouseId);
        }

        // Get spouse name if online, otherwise say “Offline” (boo, no hugs :( )
        String spouseName = (spouse != null) ? spouse.getName().getString() : "Offline";

        // Build the little heart bracket text with a hover showing who they’re married to <3
        Text displayText = Text.literal("[")
                .formatted(Formatting.GRAY)
                .append(Text.literal("♥").formatted(Formatting.RED))
                .append(Text.literal("]").formatted(Formatting.GRAY))
                .styled(style -> style.withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Text.literal("Married to: " + spouseName).formatted(Formatting.GOLD)
                )));

        // Return the heart badge to show wherever the placeholder is used :)
        return PlaceholderResult.value(displayText);
    }

}
