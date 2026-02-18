package io.github.evelynnlovesyou.marryandlove.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

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
import java.util.Map;
public class MarryCommand {
    // target -> proposers (most recent at the front)
    private static final Map<UUID, Deque<UUID>> PENDING_PROPOSALS = new HashMap<>();

    public static int register(CommandDispatcher<CommandSourceStack> dispatcher) { //register command
        dispatcher.register(
            Commands.literal("marry") // /marry <player>
                .requires(source -> {
                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                        return false;
                    }
                    return PermissionManager.canUseMarryCommand(player);
            })
                .then(Commands.argument("player", StringArgumentType.word())
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
                            () -> Component.literal(String.format(LangReader.MARRY_PROPOSAL_SENT, target.getName().getString())),
                            false
                        );
                        target.sendSystemMessage(
                            Component.literal(String.format(LangReader.MARRY_PROPOSAL_RECEIVED, playerName))
                        );
                        return 1;
                    })
                ) // /marry accept
                .then(Commands.literal("accept")
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
