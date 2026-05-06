package com.civillis.entities.cea;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

/**
 * Particle-based digit renderer for settlement levels
 * Creates particle patterns that form numbers (0-9)
 * Digits are rendered horizontally (parallel to ground)
 */
public class ParticleDigitRenderer {
    
    // Digit templates: 5x7 grid (width x height)
    // 1 = particle, 0 = empty
    private static final int[][] DIGIT_TEMPLATES = {
        // 0
        {
            0,1,1,1,0,
            1,0,0,0,1,
            1,0,0,0,1,
            1,0,0,0,1,
            1,0,0,0,1,
            1,0,0,0,1,
            0,1,1,1,0
        },
        // 1
        {
            0,0,1,0,0,
            0,1,1,0,0,
            0,0,1,0,0,
            0,0,1,0,0,
            0,0,1,0,0,
            0,0,1,0,0,
            0,1,1,1,0
        },
        // 2
        {
            0,1,1,1,0,
            1,0,0,0,1,
            0,0,0,0,1,
            0,0,0,1,0,
            0,0,1,0,0,
            0,1,0,0,0,
            1,1,1,1,1
        },
        // 3
        {
            0,1,1,1,0,
            1,0,0,0,1,
            0,0,0,0,1,
            0,0,1,1,0,
            0,0,0,0,1,
            1,0,0,0,1,
            0,1,1,1,0
        },
        // 4
        {
            0,0,0,1,0,
            0,0,1,1,0,
            0,1,0,1,0,
            1,0,0,1,0,
            1,1,1,1,1,
            0,0,0,1,0,
            0,0,0,1,0
        },
        // 5
        {
            1,1,1,1,1,
            1,0,0,0,0,
            1,1,1,1,0,
            0,0,0,0,1,
            0,0,0,0,1,
            1,0,0,0,1,
            0,1,1,1,0
        },
        // 6
        {
            0,1,1,1,0,
            1,0,0,0,0,
            1,0,0,0,0,
            1,1,1,1,0,
            1,0,0,0,1,
            1,0,0,0,1,
            0,1,1,1,0
        },
        // 7
        {
            1,1,1,1,1,
            0,0,0,0,1,
            0,0,0,1,0,
            0,0,1,0,0,
            0,1,0,0,0,
            0,1,0,0,0,
            0,1,0,0,0
        },
        // 8
        {
            0,1,1,1,0,
            1,0,0,0,1,
            1,0,0,0,1,
            0,1,1,1,0,
            1,0,0,0,1,
            1,0,0,0,1,
            0,1,1,1,0
        },
        // 9
        {
            0,1,1,1,0,
            1,0,0,0,1,
            1,0,0,0,1,
            0,1,1,1,1,
            0,0,0,0,1,
            0,0,0,1,0,
            0,1,1,0,0
        }
    };
    
    private static final int DIGIT_WIDTH = 5;
    private static final int DIGIT_HEIGHT = 7;
    private static final double PARTICLE_SPACING = 0.3; // Space between particles
    
    /**
     * Render a level number using particles
     * @param level Settlement level (1-10)
     * @param centerX Center X position
     * @param centerY Center Y position (height above ground)
     * @param centerZ Center Z position
     * @param serverLevel Server level to send particles
     */
    public static void renderLevel(int level, double centerX, double centerY, double centerZ, ServerLevel serverLevel) {
        if (level <= 0 || level > 10) return;
        
        // Use end rod particles (bright white) for better visibility
        ParticleOptions particleType = ParticleTypes.END_ROD;
        
        if (level == 10) {
            // Render "10" - two digits side by side
            renderSingleDigit(1, centerX - DIGIT_WIDTH * PARTICLE_SPACING - 0.3, centerY, centerZ, particleType, serverLevel);
            renderSingleDigit(0, centerX + DIGIT_WIDTH * PARTICLE_SPACING + 0.3, centerY, centerZ, particleType, serverLevel);
            // Add black marker below (horizontal offset for visibility)
            // Use SMOKE particle (darker and more visible) with multiple particles for clarity
            double markerZ = centerZ + DIGIT_HEIGHT * PARTICLE_SPACING + 0.3;
            serverLevel.sendParticles(ParticleTypes.SMOKE, centerX, centerY, markerZ, 3, 0.05, 0, 0.05, 0);
        } else {
            // Render single digit
            renderSingleDigit(level, centerX, centerY, centerZ, particleType, serverLevel);
            // Add black marker below (horizontal offset for visibility)
            // Use SMOKE particle (darker and more visible) with multiple particles for clarity
            double markerZ = centerZ + DIGIT_HEIGHT * PARTICLE_SPACING + 0.3;
            serverLevel.sendParticles(ParticleTypes.SMOKE, centerX, centerY, markerZ, 3, 0.05, 0, 0.05, 0);
        }
    }
    
    /**
     * Render a single digit (0-9)
     * Renders horizontally (parallel to ground) on XZ plane
     */
    private static void renderSingleDigit(int digit, double centerX, double centerY, double centerZ, 
                                          ParticleOptions particleType, ServerLevel serverLevel) {
        if (digit < 0 || digit > 9) return;
        
        int[] template = DIGIT_TEMPLATES[digit];
        
        // Calculate offset to center the digit
        // Render on XZ plane (horizontal, parallel to ground)
        double offsetX = centerX - (DIGIT_WIDTH * PARTICLE_SPACING) / 2.0;
        double offsetY = centerY;
        double offsetZ = centerZ - (DIGIT_HEIGHT * PARTICLE_SPACING) / 2.0;
        
        // Spawn particles based on template
        for (int row = 0; row < DIGIT_HEIGHT; row++) {
            for (int col = 0; col < DIGIT_WIDTH; col++) {
                int index = row * DIGIT_WIDTH + col;
                if (template[index] == 1) {
                    double x = offsetX + col * PARTICLE_SPACING;
                    double y = offsetY;  // Keep same height
                    double z = offsetZ + row * PARTICLE_SPACING;
                    
                    // Spawn particle with very small spread to maintain shape
                    serverLevel.sendParticles(
                        particleType,
                        x, y, z,
                        1,  // count
                        0.02, 0.02, 0.02,  // minimal spread
                        0.0  // speed (stationary)
                    );
                }
            }
        }
    }
}
