package com.evelynnlovesyou.marryandlove;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.command.CommandRegistryAccess;

import java.util.UUID;

import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;
import net.minecraft.command.argument.EntityArgumentType;

public class MarryCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("marry")
            .then(argument("target", EntityArgumentType.player())
                .executes(ctx -> {
                    ServerPlayerEntity sender = ctx.getSource().getPlayer();
                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                    MarriageData.getInstance().sendProposal(sender, target);
                    return 1;
                }))
            .then(literal("accept")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    MarriageData.getInstance().acceptProposal(player);
                    return 1;
                }))
            .then(literal("tp")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    MarriageData.getInstance().teleportToPartner(player);
                    return 1;
                }))
            .then(literal("divorce")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    MarriageData.getInstance().divorce(player);
                    return 1;
                }))
        );
    }
}
