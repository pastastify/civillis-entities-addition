package com.civillis.entities.command;

import com.civillis.entities.Config;
import com.civillis.entities.cea.CEAHeatmap;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * Command to toggle debug mode
 * Debug mode controls particle effects and HUD display
 */
public class ToggleDebugCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ce")
            .then(Commands.literal("debug")
                .requires(source -> source.hasPermission(2))
                .executes(ToggleDebugCommand::execute)));
    }
    
    private static int execute(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        
        // Toggle debug mode
        Config.ENABLE_DEBUG_MODE = !Config.ENABLE_DEBUG_MODE;
        
        String modeText = Config.ENABLE_DEBUG_MODE ? "§aENABLED" : "§cDISABLED";
        context.getSource().sendSuccess(
            () -> Component.literal("§eDebug mode " + modeText),
            true
        );
        
        String effectText = Config.ENABLE_DEBUG_MODE ? "§ashown" : "§7hidden";
        context.getSource().sendSuccess(
            () -> Component.literal("§eParticle effects are now " + effectText),
            true
        );
        
        return 1;
    }
}
