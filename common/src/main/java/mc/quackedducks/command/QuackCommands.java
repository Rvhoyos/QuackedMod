package mc.quackedducks.command;

import com.mojang.brigadier.CommandDispatcher;
import mc.quackedducks.QuackMod;
import mc.quackedducks.QuackServerConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/** The /quack command tree. Gamemaster permission required. */
public class QuackCommands {
    /** Hangs /quack reload and /quack config off the server dispatcher. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("quack")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("reload")
                        .executes(context -> {
                            QuackServerConfig.reloadAndBroadcast(context.getSource().getServer());
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "QuackedMod configuration reloaded. Spawn settings apply on restart."), true);
                            return 1;
                        }))
                .then(Commands.literal("config")
                        .executes(context -> {
                            if (context.getSource().getEntity() instanceof ServerPlayer player) {
                                QuackMod.openConfigGui(player);
                                return 1;
                            }
                            return 0;
                        })));
    }
}
