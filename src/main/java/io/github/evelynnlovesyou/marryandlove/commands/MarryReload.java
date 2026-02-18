package io.github.evelynnlovesyou.marryandlove.commands;

import com.mojang.brigadier.CommandDispatcher;

import io.github.evelynnlovesyou.marryandlove.config.ConfigReader;
import io.github.evelynnlovesyou.marryandlove.config.LangReader;
import io.github.evelynnlovesyou.marryandlove.manager.PermissionManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;


public class MarryReload {
    public static int register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("marryandlove") // /marryandlove
                .requires(source -> {
                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                        return true;
                    }
                    return PermissionManager.canUseReloadCommand(player);
                })
                .then(Commands.literal("reload") // /marryandlove reload
                    .executes(context -> {
                        context.getSource().sendSystemMessage(Component.literal("Reloading..."));
                        ConfigReader.init();
                        LangReader.init();
                        context.getSource().sendSuccess(
                            () -> Component.literal("Reloaded"), 
                            false
                        );
                        return 1;
                    })
                )
        );
        return 0;
    }
}
