package io.github.evelynnlovesyou.marryandlove.manager;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import io.github.evelynnlovesyou.marryandlove.commands.MarryCommand;
import io.github.evelynnlovesyou.marryandlove.commands.MarryReload;

public class CommandRegisterManager {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (MarryCommand.register(dispatcher) != 0) {
            // handle error
        }
        if (MarryReload.register(dispatcher) != 0) {
            // handle error
        }
    }
}
