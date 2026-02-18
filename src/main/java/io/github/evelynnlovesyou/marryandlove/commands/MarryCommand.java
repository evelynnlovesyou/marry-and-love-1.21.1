package io.github.evelynnlovesyou.marryandlove.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import io.github.evelynnlovesyou.marryandlove.config.ConfigReader;
import io.github.evelynnlovesyou.marryandlove.config.LangReader;
import io.github.evelynnlovesyou.marryandlove.manager.MarriageManager;
import io.github.evelynnlovesyou.marryandlove.manager.PermissionManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
public class MarryCommand {
    // target -> proposers (most recent at the front)
    private static final Map<UUID, Deque<PendingProposal>> PENDING_PROPOSALS = new HashMap<>();

    public static int register(CommandDispatcher<CommandSourceStack> dispatcher) { //register command
        dispatcher.register(
            Commands.literal("marry") // /marry
                .requires(source -> {
                     if (!(source.getEntity() instanceof ServerPlayer player)) {
                            return source.hasPermission(4);
                    }
                    return PermissionManager.canUseMarryCommand(player);
            })
                .then(Commands.argument("player", StringArgumentType.word()) // /marry <player>
                    .suggests((context, builder) -> {
                        return SharedSuggestionProvider.suggest(
                            context.getSource().getOnlinePlayerNames(), 
                            builder
                        );
                    })
                .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        String targetName = StringArgumentType.getString(context, "player");
                        ServerPlayer target = context.getSource().getServer().getPlayerList().getPlayerByName(targetName);
                        if (target == null) {
                            context.getSource().sendFailure(Component.literal(LangReader.MARRY_TARGET_OFFLINE));
                            return 0;
                        }
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

                        context.getSource().sendSuccess(
                            () -> Component.literal(formatPlayerMessage(LangReader.MARRY_PROPOSAL_SENT, target.getName().getString())),
                            false
                        );
                        target.sendSystemMessage(
                            Component.literal(formatPlayerMessage(LangReader.MARRY_PROPOSAL_RECEIVED, playerName))
                        );
                        return 1;
                    })
                ) 
                .then(Commands.literal("accept") // /marry accept
                    .executes(context -> {
                        ServerPlayer accepter = context.getSource().getPlayerOrException();

                        if (!PermissionManager.canUseMarryCommand(accepter)) {
                            context.getSource().sendFailure(Component.literal(LangReader.MARRY_NO_PERMISSION));
                            return 0;
                        }

                        UUID proposerUuid = popMostRecentProposal(accepter.getUUID());
                        if (proposerUuid == null) {
                            context.getSource().sendFailure(Component.literal(LangReader.MARRY_NO_PENDING_PROPOSAL));
                            return 0;
                        }

                        ServerPlayer proposer = accepter.server.getPlayerList().getPlayer(proposerUuid);
                        if (proposer == null) {
                            context.getSource().sendFailure(Component.literal(LangReader.MARRY_PROPOSER_OFFLINE));
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
                            () -> Component.literal(formatPlayerMessage(LangReader.MARRY_SUCCESS_SENDER, proposerName)),
                            false
                        );
                        proposer.sendSystemMessage(
                            Component.literal(formatPlayerMessage(LangReader.MARRY_SUCCESS_TARGET, accepterName))
                        );
                        return 1;
                    })
                )
                .then(Commands.literal("deny") // /marry deny
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        if (!PermissionManager.canUseMarryCommand(player)) {
                            context.getSource().sendFailure(Component.literal(LangReader.MARRY_NO_PERMISSION));
                            return 0;
                        }
                        UUID proposerUuid = popMostRecentProposal(player.getUUID());
                        if (proposerUuid == null) {
                            context.getSource().sendFailure(Component.literal(LangReader.MARRY_NO_PENDING_PROPOSAL));
                            return 0;
                        }
                        ServerPlayer proposer = player.server.getPlayerList().getPlayer(proposerUuid);
                        if (proposer == null) {
                            context.getSource().sendFailure(Component.literal(LangReader.MARRY_PROPOSER_OFFLINE));
                            return 0;
                        }
                        context.getSource().sendSuccess(
                            () -> Component.literal(formatPlayerMessage(LangReader.MARRY_DENY_SUCCESS, proposer.getName().getString())),
                            false
                        );
                        proposer.sendSystemMessage(
                            Component.literal(formatPlayerMessage(LangReader.MARRY_DENY_TARGET, player.getName().getString()))
                        );
                        return 1;
                    })
                )
                .then(Commands.literal("divorce") // /marry divorce
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        if (!PermissionManager.canUseMarryCommand(player)) {
                            context.getSource().sendFailure(Component.literal(LangReader.MARRY_NO_PERMISSION));
                            return 0;
                        }
                        if (MarriageManager.divorce(player)) {
                            context.getSource().sendSuccess(
                                () -> Component.literal(LangReader.DIVORCE_SUCCESS),
                                false
                            );
                        } else {
                            context.getSource().sendFailure(Component.literal(LangReader.DIVORCE_FAILED));
                        }
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
        pruneExpiredProposals(target);
        Deque<PendingProposal> proposals = PENDING_PROPOSALS.computeIfAbsent(target, k -> new ArrayDeque<>());
        // prevents duplicate entries from same proposer, keep most recent on top
        proposals.removeIf(proposal -> proposal.proposerId().equals(proposer));
        proposals.push(new PendingProposal(proposer, System.currentTimeMillis()));
    }

    private static String formatPlayerMessage(String template, String playerName) {
        return template.replace("%player%", playerName);
    }

    private static UUID popMostRecentProposal(UUID target) {
        pruneExpiredProposals(target);
        Deque<PendingProposal> proposals = PENDING_PROPOSALS.get(target);
        if (proposals == null || proposals.isEmpty()) return null;

        UUID proposer = proposals.pop().proposerId();
        if (proposals.isEmpty()) {
            PENDING_PROPOSALS.remove(target);
        }
        return proposer;
    }

    private static void pruneExpiredProposals(UUID target) {
        Deque<PendingProposal> proposals = PENDING_PROPOSALS.get(target);
        if (proposals == null || proposals.isEmpty()) {
            return;
        }

        if (ConfigReader.PROPOSAL_TIMEOUT_SECONDS < 0) {
            return;
        }

        long timeoutMs = Math.max(0L, (long) ConfigReader.PROPOSAL_TIMEOUT_SECONDS * 1000L);
        if (timeoutMs == 0L) {
            return;
        }

        long now = System.currentTimeMillis();
        proposals.removeIf(proposal -> now - proposal.createdAt() > timeoutMs);
        if (proposals.isEmpty()) {
            PENDING_PROPOSALS.remove(target);
        }
    }

    public static void handleDisconnect(UUID playerId) {
        if (playerId == null) {
            return;
        }

        PENDING_PROPOSALS.remove(playerId);

        Iterator<Map.Entry<UUID, Deque<PendingProposal>>> iterator = PENDING_PROPOSALS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Deque<PendingProposal>> entry = iterator.next();
            Deque<PendingProposal> proposals = entry.getValue();
            proposals.removeIf(proposal -> playerId.equals(proposal.proposerId()));
            if (proposals.isEmpty()) {
                iterator.remove();
            }
        }
    }

    private record PendingProposal(UUID proposerId, long createdAt) {
    }
}
