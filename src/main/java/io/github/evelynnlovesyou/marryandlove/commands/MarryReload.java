package io.github.evelynnlovesyou.marryandlove.commands;

import com.mojang.brigadier.CommandDispatcher;

import io.github.evelynnlovesyou.marryandlove.config.ConfigReader;
import io.github.evelynnlovesyou.marryandlove.config.LangReader;
import io.github.evelynnlovesyou.marryandlove.manager.MarriageManager;
import io.github.evelynnlovesyou.marryandlove.manager.PermissionManager;
import io.github.evelynnlovesyou.marryandlove.utils.MessageFormatter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
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
                        var registryAccess = context.getSource().registryAccess();
                        context.getSource().sendSystemMessage(MessageFormatter.format("&eReloading...", registryAccess));
                        ConfigReader.init();
                        LangReader.init();
                        MarriageManager.invalidateTimeoutCache();
                        context.getSource().sendSuccess(
                            () -> MessageFormatter.format("&aReloaded", registryAccess), 
                            false
                        );
                        return 1;
                    })
                )
        );
        return 0;
    }
}
