package com.civillis.entities.command;

import com.civillis.entities.Config;
import com.civillis.entities.cea.CEAHeatmap;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * Command to manually remove a settlement marker at specified chunk coordinates
 * Usage: /ce_remove_marker <chunkX> <chunkZ>
 */
public class CEARemoveMarkerCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ce")
            .then(Commands.literal("remove")
                .then(Commands.literal("marker")
                    .requires(source -> source.hasPermission(2)) // Requires operator level 2
                    .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                        .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                            .executes(CEARemoveMarkerCommand::execute))))));
    }
    
    private static int execute(CommandContext<CommandSourceStack> context) {
        // Check if CEA master switch is enabled
        if (!Config.ENABLE_CEA.getAsBoolean()) {
            context.getSource().sendFailure(
                Component.literal("§cCEA mod is disabled! Enable it in config first.")
            );
            return 0;
        }
        
        int chunkX = IntegerArgumentType.getInteger(context, "chunkX");
        int chunkZ = IntegerArgumentType.getInteger(context, "chunkZ");
        
        ServerLevel serverLevel = context.getSource().getLevel();
        
        // Remove marker at specified chunk
        boolean success = CEAHeatmap.removeManualMarker(serverLevel, chunkX, chunkZ);
        
        if (success) {
            context.getSource().sendSuccess(
                () -> Component.literal(String.format("§aSuccessfully removed marker at chunk [%d, %d]", 
                    chunkX, chunkZ)),
                true
            );
        } else {
            context.getSource().sendFailure(
                Component.literal(String.format("§cNo marker found at chunk [%d, %d]", chunkX, chunkZ))
            );
        }
        
        return success ? 1 : 0;
    }
}
