package com.evelynnlovesyou.marryandlove;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;

public class MarryCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("marry")
            .then(CommandManager.argument("target", EntityArgumentType.player())
                .executes(ctx -> {
                    ServerPlayerEntity source = ctx.getSource().getPlayer();
                    if (source == null) {
                        ctx.getSource().sendFeedback(() -> Text.literal("This command can only be run by a player."), false);
                        return 1;
                    }
                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");

                    if (source.getUuid().equals(target.getUuid())) {
                        source.sendMessage(Text.literal("You cannot marry yourself."), false);
                        return 1;
                    }

                    if (MarriageManager.isMarried(source.getUuid())) {
                        source.sendMessage(Text.literal("You are already married, divorce first!"), false);
                        return 1;
                    }
                    if (MarriageManager.hasPendingRequest(target.getUuid()) &&
                        MarriageManager.getSpouse(target.getUuid()) == null) { 
                        // Just in case, but your MarriageManager does expiration
                        boolean accepted = MarriageManager.acceptRequest(target.getUuid());
                        if (accepted) {
                            source.sendMessage(Text.literal("You are now married to " + target.getName().getString() + "!"), false);
                            target.sendMessage(Text.literal("You are now married to " + source.getName().getString() + "!"), false);

                            // Broadcast to all players
                            String broadcast = source.getName().getString() + " and " + target.getName().getString() + " are now married!";
                            for (ServerPlayerEntity player : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
                                player.sendMessage(Text.literal(broadcast), false);
                            }
                        } else {
                            source.sendMessage(Text.literal("Failed to accept the marriage request."), false);
                        }
                    } else {
                        boolean sent = MarriageManager.sendRequest(source.getUuid(), target.getUuid());
                        if (sent) {
                            target.sendMessage(Text.literal(source.getName().getString() + " has proposed to you! Type /marry accept or /marry deny."), false);
                            source.sendMessage(Text.literal("You proposed to " + target.getName().getString() + ". Waiting for response..."), false);
                        } else {
                            source.sendMessage(Text.literal("That player already has a pending proposal."), false);
                        }
                    }

                    return 1;
                }))
            .then(CommandManager.literal("accept")
                .executes(ctx -> {
                    ServerPlayerEntity accepter = ctx.getSource().getPlayer();
                    if (accepter == null) {
                        ctx.getSource().sendFeedback(() -> Text.literal("This command can only be run by a player."), false);
                        return 1;
                    }
                    boolean accepted = MarriageManager.acceptRequest(accepter.getUuid());

                    if (!accepted) {
                        accepter.sendMessage(Text.literal("No valid proposal to accept."), false);
                        return 1;
                    }

                    UUID proposerId = MarriageManager.getSpouse(accepter.getUuid());
                    if (proposerId == null) {
                        accepter.sendMessage(Text.literal("Unexpected error: spouse not found."), false);
                        return 1;
                    }

                    ServerPlayerEntity proposer = ctx.getSource().getServer().getPlayerManager().getPlayer(proposerId);
                    if (proposer != null) {
                        proposer.sendMessage(Text.literal(accepter.getName().getString() + " accepted your proposal!"), false);
                    }
                    accepter.sendMessage(Text.literal("You are now married to " + (proposer != null ? proposer.getName().getString() : "your spouse") + "!"), false);

                    return 1;
                }))
            .then(CommandManager.literal("deny")
                .executes(ctx -> {
                    ServerPlayerEntity denier = ctx.getSource().getPlayer();
                    if (denier == null) {
                        ctx.getSource().sendFeedback(() -> Text.literal("This command can only be run by a player."), false);
                        return 1;
                    }
                    if (!MarriageManager.hasPendingRequest(denier.getUuid())) {
                        denier.sendMessage(Text.literal("No proposal to deny."), false);
                        return 1;
                    }
                    // Get proposer UUID before denying
                    UUID proposerId = MarriageManager.getPendingRequester(denier.getUuid());
                    if (proposerId != null) {
                        ServerPlayerEntity proposer = ctx.getSource().getServer().getPlayerManager().getPlayer(proposerId);
                        if (proposer != null) {
                            proposer.sendMessage(Text.literal(denier.getName().getString() + " denied your proposal."), false);
                            MarriageManager.denyRequest(denier.getUuid());
                            denier.sendMessage(Text.literal("You denied the proposal sent to you by " + proposer.getName().getString()), false);
                        } else {
                            MarriageManager.denyRequest(denier.getUuid());
                            denier.sendMessage(Text.literal("You denied the proposal sent to you."), false);
                        }
                    } else {
                        MarriageManager.denyRequest(denier.getUuid());
                        denier.sendMessage(Text.literal("You denied the proposal sent to you."), false);
                    }
                    return 1;
                }))
            .then(CommandManager.literal("tp")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    if (player == null) {
                        ctx.getSource().sendFeedback(() -> Text.literal("This command can only be run by a player."), false);
                        return 1;
                    }
                    boolean teleported = MarriageManager.teleportToSpouse(player);
                    if (!teleported) {
                        player.sendMessage(Text.literal("Cannot teleport to your spouse right now. You might not be married, your spouse may be offline, or cooldown active."), false);
                        return 1;
                    }
                    player.sendMessage(Text.literal("Teleported to your spouse."), false);

                    // Notify the spouse
                    UUID spouseId = MarriageManager.getSpouse(player.getUuid());
                    if (spouseId != null) {
                        ServerPlayerEntity spouse = ctx.getSource().getServer().getPlayerManager().getPlayer(spouseId);
                        if (spouse != null && !spouse.getUuid().equals(player.getUuid())) {
                            spouse.sendMessage(Text.literal(player.getName().getString() + " teleported to you."), false);
                        }
                    }
                    return 1;
                }))
            .then(CommandManager.literal("divorce")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    if (player == null) {
                        ctx.getSource().sendFeedback(() -> Text.literal("This command can only be run by a player."), false);
                        return 1;
                    }
                    if (!MarriageManager.isMarried(player.getUuid())) {
                        player.sendMessage(Text.literal("You are not married."), false);
                        return 1;
                    }
                    UUID spouseId = MarriageManager.getSpouse(player.getUuid());
                    MarriageManager.divorce(player.getUuid());
                    player.sendMessage(Text.literal("You are now divorced."), false);

                    // Notify the spouse if online
                    if (spouseId != null) {
                        ServerPlayerEntity spouse = ctx.getSource().getServer().getPlayerManager().getPlayer(spouseId);
                        if (spouse != null && !spouse.getUuid().equals(player.getUuid())) {
                            spouse.sendMessage(Text.literal(player.getName().getString() + " has divorced you."), false);
                        }
                    }
                    return 1;
                }))
        );
    }
}
