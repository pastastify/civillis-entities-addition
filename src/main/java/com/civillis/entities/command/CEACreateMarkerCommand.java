package com.civillis.entities.command;

import com.civillis.entities.Config;
import com.civillis.entities.cea.CEAHeatmap;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * Command to manually create a settlement marker at specified chunk coordinates
 * Usage: /ce_create_marker <chunkX> <chunkZ> <level>
 */
public class CEACreateMarkerCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ce")
            .then(Commands.literal("create")
                .then(Commands.literal("marker")
                    .requires(source -> source.hasPermission(2)) // Requires operator level 2
                    .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                        .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                            .then(Commands.argument("level", IntegerArgumentType.integer(1, 10))
                                .executes(CEACreateMarkerCommand::execute)))))));
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
        int level = IntegerArgumentType.getInteger(context, "level");
        
        ServerLevel serverLevel = context.getSource().getLevel();
        
        // Calculate heat value from level (heat = level * threshold)
        int heatValue = level * 5; // HEAT_THRESHOLD = 5
        
        // Create or update marker at specified chunk
        boolean success = CEAHeatmap.createManualMarker(serverLevel, chunkX, chunkZ, level, heatValue);
        
        if (success) {
            context.getSource().sendSuccess(
                () -> Component.literal(String.format("§aSuccessfully created L%d marker at chunk [%d, %d]", 
                    level, chunkX, chunkZ)),
                true
            );
        } else {
            context.getSource().sendFailure(
                Component.literal(String.format("§cFailed to create marker at chunk [%d, %d]", chunkX, chunkZ))
            );
        }
        
        return success ? 1 : 0;
    }
}
