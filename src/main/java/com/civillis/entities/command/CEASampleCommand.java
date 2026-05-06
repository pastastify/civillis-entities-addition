package com.civillis.entities.command;

import com.civillis.entities.Config;
import com.civillis.entities.cea.CEAHeatmap;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * Command to manually trigger settlement heatmap sampling
 * Usage: /settlement_sample [count]
 * - count: Number of times to sample (default: 1, max: 100)
 */
public class CEASampleCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ce")
            .then(Commands.literal("sample")
                .requires(source -> source.hasPermission(2)) // Requires operator level 2
                .executes(CEASampleCommand::execute) // Default: 1 time
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                    .executes(context -> executeWithCount(context)))));
    }
    
    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return executeWithCount(context, 1); // Default to 1 sample
    }
    
    private static int executeWithCount(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int count = IntegerArgumentType.getInteger(context, "count");
        return executeWithCount(context, count);
    }
    
    private static int executeWithCount(CommandContext<CommandSourceStack> context, int count) throws CommandSyntaxException {
        // Check if CEA master switch is enabled
        if (!Config.ENABLE_CEA.getAsBoolean()) {
            context.getSource().sendFailure(
                Component.literal("§cCEA mod is disabled! Enable it in config first.")
            );
            return 0;
        }
        
        // Check if manual placement mode is enabled
        if (Config.MANUAL_PLACEMENT_MODE.getAsBoolean()) {
            context.getSource().sendFailure(
                Component.literal("§cManual placement mode is enabled! Disable it to use sampling commands.")
            );
            return 0;
        }
        
        ServerLevel level = context.getSource().getLevel();
        
        // Trigger manual sampling multiple times
        for (int i = 0; i < count; i++) {
            CEAHeatmap.triggerManualSampling(level);
        }
        
        context.getSource().sendSuccess(
            () -> Component.literal(String.format("§aSettlement heatmap sampling triggered %d time(s)! Check console for details.", count)),
            true
        );
        
        return 1;
    }
}
