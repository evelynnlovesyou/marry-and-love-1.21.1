package com.evelynnlovesyou.marryandlove;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.sound.SoundCategory;



import java.util.UUID;

public class MarryCommand {

    // Register all the /marry commands here — proposals, accepts, denies, teleports, divorces <3
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("marry")
                // The basic marry command where you propose to someone
                .then(CommandManager.argument("target", EntityArgumentType.player())
                        .executes(ctx -> {
                            ServerPlayerEntity source = ctx.getSource().getPlayer();
                            if (source == null) {
                                // Command ran by console or something? Nope, only players allowed here :)
                                ctx.getSource().sendFeedback(() -> Text.literal("This command can only be run by a player."), false);
                                return 1;
                            }

                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");

                            // No marrying yourself, that’s just sad :/
                            if (source.getUuid().equals(target.getUuid())) {
                                source.sendMessage(Text.literal("You cannot marry yourself."), false);
                                return 1;
                            }

                            // If you’re already married, don’t get greedy — divorce first! <3
                            if (MarriageManager.isMarried(source.getUuid())) {
                                source.sendMessage(Text.literal("You are already married, divorce first!"), false);
                                return 1;
                            }

                            // Check if target already has a pending request and isn’t married yet
                            if (MarriageManager.hasPendingRequest(target.getUuid()) &&
                                    MarriageManager.getSpouse(target.getUuid()) == null) {

                                // Accept the proposal if possible
                                boolean accepted = MarriageManager.acceptRequest(target.getUuid());
                                if (accepted) {
                                    // Yay! Marriage successful :)
                                    source.sendMessage(Text.literal("You are now married to " + target.getName().getString() + "!"), false);
                                    target.sendMessage(Text.literal("You are now married to " + source.getName().getString() + "!"), false);
                                    // Play custom marriage sound! How cute! :)
                                    ServerWorld world = (ServerWorld) source.getWorld();
                                    world.playSound(
                                            source,
                                            source.getX(), source.getY(), source.getZ(),
                                            MALSoundEvent.MARRY,
                                            SoundCategory.PLAYERS,
                                            1.0f, 1.0f
                                    );
                                    world.playSound(
                                            target,
                                            target.getX(), target.getY(), target.getZ(),
                                            MALSoundEvent.MARRY,
                                            SoundCategory.PLAYERS,
                                            1.0f, 1.0f
                                    );
                                    // Broadcast to all players about the happy couple!
                                    String broadcast = source.getName().getString() + " and " + target.getName().getString() + " are now married!";
                                    for (ServerPlayerEntity player : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
                                        player.sendMessage(Text.literal(broadcast), false);
                                    }
                                } else {
                                    // Something went wrong accepting the request :(
                                    source.sendMessage(Text.literal("Failed to accept the marriage request."), false);
                                }
                            } else {
                                // No pending request? Send a new proposal then <3
                                boolean sent = MarriageManager.sendRequest(source.getUuid(), target.getUuid());
                                if (sent) {
                                    // Tell the target they got proposed to
                                    target.sendMessage(Text.literal(source.getName().getString() + " has proposed to you! Type /marry accept or /marry deny."), false);
                                    source.sendMessage(Text.literal("You proposed to " + target.getName().getString() + ". Waiting for response..."), false);
                                } else {
                                    // Target already has a pending proposal, so chill for now :)
                                    source.sendMessage(Text.literal("That player already has a pending proposal."), false);
                                }
                            }

                            return 1;
                        }))
                // Accept the marriage proposal command
                .then(CommandManager.literal("accept")
                        .executes(ctx -> {
                            ServerPlayerEntity accepter = ctx.getSource().getPlayer();
                            if (accepter == null) {
                                // Console can’t accept proposals, sorry :)
                                ctx.getSource().sendFeedback(() -> Text.literal("This command can only be run by a player."), false);
                                return 1;
                            }

                            boolean accepted = MarriageManager.acceptRequest(accepter.getUuid());

                            if (!accepted) {
                                // No proposal waiting? Nope, no marrying now :(
                                accepter.sendMessage(Text.literal("No valid proposal to accept."), false);
                                return 1;
                            }

                            // Get the proposer’s UUID now that we accepted
                            UUID proposerId = MarriageManager.getSpouse(accepter.getUuid());
                            if (proposerId == null) {
                                // Uh oh, something weird happened. Spouse missing!
                                accepter.sendMessage(Text.literal("Unexpected error: spouse not found."), false);
                                return 1;
                            }

                            // Get proposer player to send them a message too
                            ServerPlayerEntity proposer = ctx.getSource().getServer().getPlayerManager().getPlayer(proposerId);
                            if (proposer != null) {
                                proposer.sendMessage(Text.literal(accepter.getName().getString() + " accepted your proposal!"), false);
                            }
                            // Tell accepter they’re now married — yay love <3
                            accepter.sendMessage(Text.literal("You are now married to " + (proposer != null ? proposer.getName().getString() : "your spouse") + "!"), false);

                            return 1;
                        }))
                // Deny the marriage proposal command
                .then(CommandManager.literal("deny")
                        .executes(ctx -> {
                            ServerPlayerEntity denier = ctx.getSource().getPlayer();
                            if (denier == null) {
                                // Console can’t deny proposals either, sorry :)
                                ctx.getSource().sendFeedback(() -> Text.literal("This command can only be run by a player."), false);
                                return 1;
                            }

                            if (!MarriageManager.hasPendingRequest(denier.getUuid())) {
                                // No proposal to deny — nothing to do here
                                denier.sendMessage(Text.literal("No proposal to deny."), false);
                                return 1;
                            }

                            // Find who proposed to you
                            UUID proposerId = MarriageManager.getPendingRequester(denier.getUuid());
                            if (proposerId != null) {
                                ServerPlayerEntity proposer = ctx.getSource().getServer().getPlayerManager().getPlayer(proposerId);
                                if (proposer != null) {
                                    // Let the proposer know you said no :(
                                    proposer.sendMessage(Text.literal(denier.getName().getString() + " denied your proposal."), false);
                                    MarriageManager.denyRequest(denier.getUuid());
                                    denier.sendMessage(Text.literal("You denied the proposal sent to you by " + proposer.getName().getString()), false);
                                } else {
                                    // Proposer offline but still denying — gotta keep it clean <3
                                    MarriageManager.denyRequest(denier.getUuid());
                                    denier.sendMessage(Text.literal("You denied the proposal sent to you."), false);
                                }
                            } else {
                                // No proposer found but we clear the request anyway — no hard feelings :)
                                MarriageManager.denyRequest(denier.getUuid());
                                denier.sendMessage(Text.literal("You denied the proposal sent to you."), false);
                            }
                            return 1;
                        }))
                // Teleport to your spouse if married and allowed <3
                .then(CommandManager.literal("tp")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                            if (player == null) {
                                // Sorry, console can’t TP to spouse
                                ctx.getSource().sendFeedback(() -> Text.literal("This command can only be run by a player."), false);
                                return 1;
                            }

                            boolean teleported = MarriageManager.teleportToSpouse(player);
                            if (!teleported) {
                                // Can’t TP now — either not married, spouse offline, or cooldown active :(
                                player.sendMessage(Text.literal("Cannot teleport to your spouse right now. You might not be married, your spouse may be offline, or cooldown active."), false);
                                return 1;
                            }

                            // Success! Tell player they teleported
                            player.sendMessage(Text.literal("Teleported to your spouse."), false);

                            // If spouse online and not the same player, tell them you just TP’d
                            UUID spouseId = MarriageManager.getSpouse(player.getUuid());
                            if (spouseId != null) {
                                ServerPlayerEntity spouse = ctx.getSource().getServer().getPlayerManager().getPlayer(spouseId);
                                if (spouse != null && !spouse.getUuid().equals(player.getUuid())) {
                                    spouse.sendMessage(Text.literal(player.getName().getString() + " teleported to you."), false);
                                }
                            }

                            return 1;
                        }))
                // Divorce command — break up the happy couple :(
                .then(CommandManager.literal("divorce")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                            if (player == null) {
                                // Console can’t divorce, sorry!
                                ctx.getSource().sendFeedback(() -> Text.literal("This command can only be run by a player."), false);
                                return 1;
                            }

                            if (!MarriageManager.isMarried(player.getUuid())) {
                                // Not married? No divorce needed :)
                                player.sendMessage(Text.literal("You are not married."), false);
                                return 1;
                            }

                            UUID spouseId = MarriageManager.getSpouse(player.getUuid());

                            // Perform the divorce, clearing both sides <3
                            MarriageManager.divorce(player.getUuid());

                            // Tell player they are divorced now — sad but necessary
                            player.sendMessage(Text.literal("You are now divorced."), false);

                            // Let spouse know too if they’re online and not the same player
                            if (spouseId != null) {
                                ServerPlayerEntity spouse = ctx.getSource().getServer().getPlayerManager().getPlayer(spouseId);
                                if (spouse != null && !spouse.getUuid().equals(player.getUuid())) {
                                    spouse.sendMessage(Text.literal(player.getName().getString() + " has divorced you."), false);
                                }
                            }

                            return 1;
                        }))
                .then(CommandManager.literal("kiss")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                            if (player == null) {
                                ctx.getSource().sendFeedback(() -> Text.literal("This command can only be run by a player."), false);
                                return 1;
                            }

                            if (!MarriageManager.isMarried(player.getUuid())) {
                                player.sendMessage(Text.literal("You are not married, find a partner first!"), false);
                                return 1;
                            }

                            UUID spouseId = MarriageManager.getSpouse(player.getUuid());
                            if (spouseId != null) {
                                ServerPlayerEntity spouse = ctx.getSource().getServer().getPlayerManager().getPlayer(spouseId);
                                if (spouse != null && !spouse.getUuid().equals(player.getUuid())) {
                                    double distance = player.squaredDistanceTo(spouse);
                                    if (distance > 9) { // 3 blocks squared = 9
                                        player.sendMessage(Text.literal("You must be within 3 blocks of your spouse to kiss them."), false);
                                        return 1;
                                    }

                                    player.sendMessage(Text.literal("You gave " + spouse.getName().getString() + " a kiss!"), false);
                                    spouse.sendMessage(Text.literal(player.getName().getString() + " gave you a kiss!"), false);

                                    ServerWorld world = (ServerWorld) player.getWorld();

                                    // Spawn heart particles at both players <33
                                    world.spawnParticles(ParticleTypes.HEART,
                                            player.getX(), player.getY() + 1.0, player.getZ(),
                                            5, 0.5, 0.5, 0.5, 0.01);
                                    world.spawnParticles(ParticleTypes.HEART,
                                            spouse.getX(), spouse.getY() + 1.0, spouse.getZ(),
                                            5, 0.5, 0.5, 0.5, 0.01);

                                    // Play custom kiss sound, mwah!
                                    world.playSound(
                                            player,
                                            player.getX(), player.getY(), player.getZ(),
                                            MALSoundEvent.KISS,
                                            SoundCategory.PLAYERS,
                                            1.0f, 1.0f
                                    );
                                    world.playSound(
                                            spouse,
                                            spouse.getX(), spouse.getY(), spouse.getZ(),
                                            MALSoundEvent.KISS,
                                            SoundCategory.PLAYERS,
                                            1.0f, 1.0f
                                    );
                                }
                            }

                            return 1;
                        }))

        );
    }
}
