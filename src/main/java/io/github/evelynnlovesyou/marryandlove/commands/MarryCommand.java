package io.github.evelynnlovesyou.marryandlove.commands;

import com.mojang.brigadier.CommandDispatcher;

import io.github.evelynnlovesyou.marryandlove.config.LangReader;
import io.github.evelynnlovesyou.marryandlove.manager.MarriageManager;
import io.github.evelynnlovesyou.marryandlove.manager.PermissionManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
public class MarryCommand {
    // target -> proposers (most recent at the front)
    private static final Map<UUID, Deque<UUID>> PENDING_PROPOSALS = new HashMap<>();

    public static int register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("marry")
                .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.literal("accept")
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();

                        if (!PermissionManager.canUseMarryCommand(player)) {
                            context.getSource().sendFailure(Component.literal(LangReader.MARRY_NO_PERMISSION));
                            return 0;
                        }

                        MarriageManager.ProposalResult result = MarriageManager.getLatestProposal(player);
                        if (result.getProposerId() == null) {
                            String message = result.isExpired() ? LangReader.MARRY_PROPOSAL_EXPIRED : LangReader.MARRY_NO_PENDING_PROPOSAL;
                            context.getSource().sendFailure(Component.literal(message));
                            return 0;
                        }

                        UUID proposerId = result.getProposerId();
                        ServerPlayer proposer = player.server.getPlayerList().getPlayer(proposerId);
                        if (proposer == null || !canMarry(proposer)) {
                            MarriageManager.clearProposal(player);
                            context.getSource().sendFailure(Component.literal(LangReader.MARRY_PROPOSAL_EXPIRED));
                            return 0;
                        }

                        if (!MarriageManager.marry(proposer, player)) {
                            context.getSource().sendFailure(Component.literal(LangReader.MARRY_FAILED));
                            return 0;
                        }

                        String proposerName = proposer.getName().getString();
                        String playerName = player.getName().getString();

                        context.getSource().sendSuccess(() -> Component.literal(String.format(LangReader.MARRY_SUCCESS_SENDER, proposerName)), false);
                        proposer.sendSystemMessage(Component.literal(String.format(LangReader.MARRY_SUCCESS_TARGET, playerName)));
                        return 1;
                    })
                )
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        ServerPlayer target = EntityArgument.getPlayer(context, "player");

                        if (!PermissionManager.canUseMarryCommand(player)) {
                            context.getSource().sendFailure(Component.literal(LangReader.MARRY_NO_PERMISSION));
                            return 0;
                        }

                        if (player.getUUID().equals(target.getUUID())) {
                            context.getSource().sendFailure(Component.literal(LangReader.CANNOT_MARRY_SELF));
                            return 0;
                        }

                        if (!canMarry(player)) {
                            context.getSource().sendFailure(Component.literal(LangReader.ALREADY_MARRIED_SELF));
                            return 0;
                        }

                        if (!canMarry(target)) {
                            context.getSource().sendFailure(Component.literal(LangReader.ALREADY_MARRIED_TARGET));
                            return 0;
                        }

                        addProposal(player.getUUID(), target.getUUID());
                        if (!MarriageManager.propose(player, target)) {
                            context.getSource().sendFailure(Component.literal(LangReader.MARRY_FAILED));
                            return 0;
                        }
                        String playerName = player.getName().getString();
                        String targetName = target.getName().getString();

                        context.getSource().sendSuccess(
                            () -> Component.literal("Marriage proposal sent to " + targetName + "."),
                            false
                        );
                        target.sendSystemMessage(
                            Component.literal(playerName + " proposed to you! Use /marry accept to accept.")
                        );
                        return 1;
                    })
                )
                .then(Commands.literal("accept")
                    .executes(context -> {
                        ServerPlayer accepter = context.getSource().getPlayerOrException();

                        if (!PermissionManager.canUseMarryCommand(accepter)) {
                            context.getSource().sendFailure(Component.literal(LangReader.MARRY_NO_PERMISSION));
                            return 0;
                        }

                        UUID proposerUuid = popMostRecentProposal(accepter.getUUID());
                        if (proposerUuid == null) {
                            context.getSource().sendFailure(Component.literal("You have no pending marriage proposals."));
                            return 0;
                        }

                        ServerPlayer proposer = accepter.server.getPlayerList().getPlayer(proposerUuid);
                        if (proposer == null) {
                            context.getSource().sendFailure(Component.literal("The player who proposed is offline."));
                            return 0;
                        }

                        // Re-check marriage state at accept time
                        if (!canMarry(accepter)) {
                            context.getSource().sendFailure(Component.literal(LangReader.ALREADY_MARRIED_SELF));
                            return 0;
                        }

                        if (!canMarry(proposer)) {
                            context.getSource().sendFailure(Component.literal(LangReader.ALREADY_MARRIED_TARGET));
                            return 0;
                        }

                        if (!MarriageManager.marry(proposer, accepter)) {
                            context.getSource().sendFailure(Component.literal(LangReader.MARRY_FAILED));
                            return 0;
                        }

                        String accepterName = accepter.getName().getString();
                        String proposerName = proposer.getName().getString();

                        context.getSource().sendSuccess(
                            () -> Component.literal(String.format(LangReader.MARRY_SUCCESS_SENDER, proposerName)),
                            false
                        );
                        proposer.sendSystemMessage(
                            Component.literal(String.format(LangReader.MARRY_SUCCESS_TARGET, accepterName))
                        );
                        return 1;
                    })
                )
        );
        return 0;
    }

    private static boolean canMarry(ServerPlayer player) {
        return MarriageManager.canMarry(player);
    }

    private static void addProposal(UUID proposer, UUID target) {
        Deque<UUID> proposals = PENDING_PROPOSALS.computeIfAbsent(target, k -> new ArrayDeque<>());
        // prevent duplicate entries from same proposer, keep most recent on top
        proposals.remove(proposer);
        proposals.push(proposer);
    }

    private static UUID popMostRecentProposal(UUID target) {
        Deque<UUID> proposals = PENDING_PROPOSALS.get(target);
        if (proposals == null || proposals.isEmpty()) return null;

        UUID proposer = proposals.pop();
        if (proposals.isEmpty()) {
            PENDING_PROPOSALS.remove(target);
        }
        return proposer;
    }
}
