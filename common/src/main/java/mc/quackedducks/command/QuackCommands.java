package mc.quackedducks.command;

import com.mojang.brigadier.CommandDispatcher;
import mc.quackedducks.QuackMod;
import mc.quackedducks.config.QuackConfig;
import net.minecraft.resources.Identifier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

public class QuackCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("quack")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("reload")
                        .executes(context -> {
                            QuackConfig.load();
                            context.getSource()
                                    .sendSuccess(() -> Component.literal("QuackedMod configuration reloaded."), true);
                            return 1;
                        }))
                .then(Commands.literal("config")
                        .executes(context -> {
                            if (context.getSource()
                                    .getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                                QuackMod.openConfigGui(player);
                                return 1;
                            }
                            return 0;
                        })));
    }
}
