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
 * Command to immediately process settlement markers from heatmap
 */
public class CEAProcessCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ce")
            .then(Commands.literal("process")
                .requires(source -> source.hasPermission(2))
                .executes(CEAProcessCommand::execute)));
    }
    
    private static int execute(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        // Check if CEA master switch is enabled
        if (!Config.ENABLE_CEA.getAsBoolean()) {
            context.getSource().sendFailure(
                Component.literal("§cCEA mod is disabled! Enable it in config first.")
            );
            return 0;
        }
        
        // No longer requires debug mode - this is a management command that should always work
        // Debug mode only controls visual effects (particles, HUD), not core settlement functionality
        
        ServerLevel level = context.getSource().getLevel();
        
        // Trigger immediate settlement processing
        CEAHeatmap.triggerManualSettlementProcessing(level);
        
        context.getSource().sendSuccess(
            () -> Component.literal("§aSettlement processing complete! Use /ce_status to view marker levels."),
            true
        );
        
        return 1;
    }
}
