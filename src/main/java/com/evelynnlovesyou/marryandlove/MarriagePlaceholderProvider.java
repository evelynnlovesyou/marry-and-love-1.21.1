package com.evelynnlovesyou.marryandlove;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;

import java.util.UUID;

public class MarriagePlaceholderProvider {

    public static void register() {
        // This method depends on how Styled-chat expects placeholder registration.
        // Replace this call with actual Styled-chat placeholder registration,
        // here just showing the placeholder logic:
    }

    public static Text getMarriageStatus(ServerPlayerEntity player) {
        UUID spouseId = MarriageManager.getSpouse(player.getUuid());
        if (spouseId == null) return Text.empty();

        ServerPlayerEntity spouse = player.getServer().getPlayerManager().getPlayer(spouseId);
        String spouseName = (spouse != null) ? spouse.getName().getString() : "Offline";

        // Create the display text with hover event
        return Text.literal(" [♥ " + spouseName + "]")
            .styled(style -> style.withHoverEvent(
                new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Married to: " + spouseName))
            ));
    }
}
