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
                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");

                    if (source.getUuid().equals(target.getUuid())) {
                        source.sendMessage(Text.literal("You cannot marry yourself."), false);
                        return 1;
                    }

                    if (MarriageManager.isMarried(source.getUuid())) {
                        source.sendMessage(Text.literal("You are already married."), false);
                        return 1;
                    }

                    if (MarriageManager.hasPendingRequest(target.getUuid()) &&
                        MarriageManager.getSpouse(target.getUuid()) == null) { 
                        // Just in case, but your MarriageManager does expiration
                        boolean accepted = MarriageManager.acceptRequest(target.getUuid());
                        if (accepted) {
                            source.sendMessage(Text.literal("You are now married to " + target.getName().getString() + "!"), false);
                            target.sendMessage(Text.literal("You are now married to " + source.getName().getString() + "!"), false);
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

                    if (!MarriageManager.hasPendingRequest(denier.getUuid())) {
                        denier.sendMessage(Text.literal("No proposal to deny."), false);
                        return 1;
                    }

                    MarriageManager.denyRequest(denier.getUuid());

                    denier.sendMessage(Text.literal("You denied the proposal."), false);
                    return 1;
                }))
            .then(CommandManager.literal("tp")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();

                    boolean teleported = MarriageManager.teleportToSpouse(player);
                    if (!teleported) {
                        player.sendMessage(Text.literal("Cannot teleport to your spouse right now. You might not be married, your spouse may be offline, or cooldown active."), false);
                        return 1;
                    }

                    player.sendMessage(Text.literal("Teleported to your spouse."), false);
                    return 1;
                }))
            .then(CommandManager.literal("divorce")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    if (!MarriageManager.isMarried(player.getUuid())) {
                        player.sendMessage(Text.literal("You are not married."), false);
                        return 1;
                    }
                    MarriageManager.divorce(player.getUuid());
                    player.sendMessage(Text.literal("You are now divorced."), false);
                    return 1;
                }))
        );
    }
}
