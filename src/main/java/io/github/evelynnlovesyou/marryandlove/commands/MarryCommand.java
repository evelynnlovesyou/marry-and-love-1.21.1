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
public class MarryCommand {
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

                        MarriageManager.ProposalResult proposalResult = MarriageManager.getLatestProposal(accepter);
                        UUID proposerUuid = proposalResult.getProposerId();
                        if (proposerUuid == null) {
                            context.getSource().sendFailure(Component.literal(
                                proposalResult.isExpired() ? LangReader.MARRY_PROPOSAL_EXPIRED : LangReader.MARRY_NO_PENDING_PROPOSAL
                            ));
                            return 0;
                        }

                        ServerPlayer proposer = accepter.server.getPlayerList().getPlayer(proposerUuid);
                        if (proposer == null) {
                            MarriageManager.clearProposal(accepter);
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
                        MarriageManager.ProposalResult proposalResult = MarriageManager.getLatestProposal(player);
                        UUID proposerUuid = proposalResult.getProposerId();
                        if (proposerUuid == null) {
                            context.getSource().sendFailure(Component.literal(
                                proposalResult.isExpired() ? LangReader.MARRY_PROPOSAL_EXPIRED : LangReader.MARRY_NO_PENDING_PROPOSAL
                            ));
                            return 0;
                        }
                        ServerPlayer proposer = player.server.getPlayerList().getPlayer(proposerUuid);
                        MarriageManager.clearProposal(player);
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

    private static String formatPlayerMessage(String template, String playerName) {
        return template.replace("%player%", playerName);
    }
}
