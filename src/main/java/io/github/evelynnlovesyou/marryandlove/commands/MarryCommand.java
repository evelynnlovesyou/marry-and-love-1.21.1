package io.github.evelynnlovesyou.marryandlove.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import io.github.evelynnlovesyou.marryandlove.config.LangReader;
import io.github.evelynnlovesyou.marryandlove.manager.MarriageManager;
import io.github.evelynnlovesyou.marryandlove.manager.PermissionManager;
import io.github.evelynnlovesyou.marryandlove.utils.MessageFormatter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
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
                        var registryAccess = context.getSource().registryAccess();
                        String targetName = StringArgumentType.getString(context, "player");
                        ServerPlayer target = context.getSource().getServer().getPlayerList().getPlayerByName(targetName);
                        if (target == null) {
                            context.getSource().sendFailure(MessageFormatter.format(LangReader.MARRY_TARGET_OFFLINE, registryAccess));
                            return 0;
                        }
                        if (!PermissionManager.canUseMarryCommand(player)) {
                            context.getSource().sendFailure(MessageFormatter.format(LangReader.MARRY_NO_PERMISSION, registryAccess));
                            return 0;
                        }

                        if (player.getUUID().equals(target.getUUID())) {
                            context.getSource().sendFailure(MessageFormatter.format(LangReader.CANNOT_MARRY_SELF, registryAccess));
                            return 0;
                        }

                        if (!canMarry(player)) {
                            context.getSource().sendFailure(MessageFormatter.format(LangReader.ALREADY_MARRIED_SELF, registryAccess));
                            return 0;
                        }

                        if (!canMarry(target)) {
                            context.getSource().sendFailure(MessageFormatter.format(LangReader.ALREADY_MARRIED_TARGET, registryAccess));
                            return 0;
                        }

                        if (!MarriageManager.propose(player, target)) {
                            context.getSource().sendFailure(MessageFormatter.format(LangReader.MARRY_FAILED, registryAccess));
                            return 0;
                        }
                        String playerName = player.getName().getString();

                        context.getSource().sendSuccess(
                            () -> MessageFormatter.format(LangReader.MARRY_PROPOSAL_SENT, Map.of("player", target.getName().getString()), registryAccess),
                            false
                        );
                        target.sendSystemMessage(
                            MessageFormatter.format(LangReader.MARRY_PROPOSAL_RECEIVED, Map.of("player", playerName), target.registryAccess())
                        );
                        return 1;
                    })
                ) 
                .then(Commands.literal("accept") // /marry accept
                    .executes(context -> {
                        ServerPlayer accepter = context.getSource().getPlayerOrException();
                        var registryAccess = context.getSource().registryAccess();

                        if (!PermissionManager.canUseMarryCommand(accepter)) {
                            context.getSource().sendFailure(MessageFormatter.format(LangReader.MARRY_NO_PERMISSION, registryAccess));
                            return 0;
                        }

                        MarriageManager.ProposalResult proposalResult = MarriageManager.getLatestProposal(accepter);
                        UUID proposerUuid = proposalResult.getProposerId();
                        if (proposerUuid == null) {
                            context.getSource().sendFailure(MessageFormatter.format(
                                proposalResult.isExpired() ? LangReader.MARRY_RECEIVED_PROPOSAL_EXPIRED : LangReader.MARRY_NO_PENDING_PROPOSAL,
                                registryAccess
                            ));
                            return 0;
                        }

                        ServerPlayer proposer = accepter.server.getPlayerList().getPlayer(proposerUuid);
                        if (proposer == null) {
                            MarriageManager.clearProposal(accepter);
                            context.getSource().sendFailure(MessageFormatter.format(LangReader.MARRY_PROPOSER_OFFLINE, registryAccess));
                            return 0;
                        }

                        // Re-check marriage state at accept time
                        if (!canMarry(accepter)) {
                            context.getSource().sendFailure(MessageFormatter.format(LangReader.ALREADY_MARRIED_SELF, registryAccess));
                            return 0;
                        }

                        if (!canMarry(proposer)) {
                            context.getSource().sendFailure(MessageFormatter.format(LangReader.ALREADY_MARRIED_TARGET, registryAccess));
                            return 0;
                        }

                        if (!MarriageManager.marry(proposer, accepter)) {
                            context.getSource().sendFailure(MessageFormatter.format(LangReader.MARRY_FAILED, registryAccess));
                            return 0;
                        }

                        String accepterName = accepter.getName().getString();
                        String proposerName = proposer.getName().getString();

                        context.getSource().sendSuccess(
                            () -> MessageFormatter.format(LangReader.MARRY_SUCCESS_SENDER, Map.of("player", proposerName), registryAccess),
                            false
                        );
                        proposer.sendSystemMessage(
                            MessageFormatter.format(LangReader.MARRY_SUCCESS_TARGET, Map.of("player", accepterName), proposer.registryAccess())
                        );
                        return 1;
                    })
                )
                .then(Commands.literal("deny") // /marry deny
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        var registryAccess = context.getSource().registryAccess();
                        if (!PermissionManager.canUseMarryCommand(player)) {
                            context.getSource().sendFailure(MessageFormatter.format(LangReader.MARRY_NO_PERMISSION, registryAccess));
                            return 0;
                        }
                        MarriageManager.ProposalResult proposalResult = MarriageManager.getLatestProposal(player);
                        UUID proposerUuid = proposalResult.getProposerId();
                        if (proposerUuid == null) {
                            context.getSource().sendFailure(MessageFormatter.format(
                                proposalResult.isExpired() ? LangReader.MARRY_RECEIVED_PROPOSAL_EXPIRED : LangReader.MARRY_NO_PENDING_PROPOSAL,
                                registryAccess
                            ));
                            return 0;
                        }
                        ServerPlayer proposer = player.server.getPlayerList().getPlayer(proposerUuid);
                        MarriageManager.clearProposal(player);
                        if (proposer == null) {
                            context.getSource().sendFailure(MessageFormatter.format(LangReader.MARRY_PROPOSER_OFFLINE, registryAccess));
                            return 0;
                        }
                        context.getSource().sendSuccess(
                            () -> MessageFormatter.format(LangReader.MARRY_DENY_SUCCESS, Map.of("player", proposer.getName().getString()), registryAccess),
                            false
                        );
                        proposer.sendSystemMessage(
                            MessageFormatter.format(LangReader.MARRY_DENY_TARGET, Map.of("player", player.getName().getString()), proposer.registryAccess())
                        );
                        return 1;
                    })
                )
                .then(Commands.literal("divorce") // /marry divorce
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        var registryAccess = context.getSource().registryAccess();
                        if (!PermissionManager.canUseMarryCommand(player)) {
                            context.getSource().sendFailure(MessageFormatter.format(LangReader.MARRY_NO_PERMISSION, registryAccess));
                            return 0;
                        }

                        UUID spouseUuid = MarriageManager.getSpouse(player);
                        if (MarriageManager.divorce(player)) {
                            context.getSource().sendSuccess(
                                () -> MessageFormatter.format(LangReader.DIVORCE_SUCCESS, registryAccess),
                                false
                            );

                            if (spouseUuid != null) {
                                ServerPlayer spouse = player.server.getPlayerList().getPlayer(spouseUuid);
                                if (spouse != null) {
                                    spouse.sendSystemMessage(
                                        MessageFormatter.format(
                                            LangReader.DIVORCE_SPOUSE_NOTIFIED,
                                            Map.of("player", player.getName().getString()),
                                            spouse.registryAccess()
                                        )
                                    );
                                } else {
                                    MarriageManager.queueDivorceNotification(player.server, spouseUuid, player.getUUID());
                                }
                            }
                        } else {
                            context.getSource().sendFailure(MessageFormatter.format(LangReader.DIVORCE_FAILED, registryAccess));
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

}
