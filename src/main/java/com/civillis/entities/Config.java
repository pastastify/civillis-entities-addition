package com.civillis.entities;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    
    // Main Toggle
    public static final ModConfigSpec.BooleanValue ENABLE_CEA;
    
    // Marker Processing Control
    public static final ModConfigSpec.BooleanValue MANUAL_PLACEMENT_MODE;
    
    // Entity Weights (format: "entity_id:weight")
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ENTITY_WEIGHTS;
    
    // Particle Effects
    public static final ModConfigSpec.BooleanValue ENABLE_PARTICLE_EFFECTS;
    public static final ModConfigSpec.IntValue PARTICLE_DURATION_TICKS;
    public static final ModConfigSpec.ConfigValue<String> INITIAL_PARTICLE_TYPE;
    public static final ModConfigSpec.ConfigValue<String> CONTINUOUS_PARTICLE_TYPE;
    public static final ModConfigSpec.IntValue CONTINUOUS_PARTICLE_INTERVAL;
    
    // Debug Mode (controlled by command, not config)
    public static volatile boolean ENABLE_DEBUG_MODE = false;
    
    // Logging Control
    public static final ModConfigSpec.BooleanValue ENABLE_VERBOSE_LOGGING;
    
    // Heatmap Decay Configuration
    public static final ModConfigSpec.BooleanValue USE_PERCENTAGE_DECAY;
    public static final ModConfigSpec.IntValue HEAT_DECAY_FIXED;
    public static final ModConfigSpec.DoubleValue HEAT_DECAY_RATE;
    
    // Level Calculation Configuration
    public static final ModConfigSpec.BooleanValue USE_LINEAR_LEVEL_CALCULATION;
    public static final ModConfigSpec.DoubleValue LEVEL_CURVE_EXPONENT;
    
    // Default entity weight
    private static final double DEFAULT_ENTITY_WEIGHT = 1.0;
    
    /**
     * Get entity weight from config
     * @param entityType Entity type string (e.g., "minecraft:villager")
     * @return Weight value, or DEFAULT_ENTITY_WEIGHT if not configured
     */
    public static double getEntityWeight(String entityType) {
        List<? extends String> weightsList = ENTITY_WEIGHTS.get();
        
        // Search in entityWeights list
        for (String entry : weightsList) {
            String[] parts = entry.split(":");
            if (parts.length >= 2 && parts[0].equals(entityType)) {
                try {
                    return Double.parseDouble(parts[1]);
                } catch (NumberFormatException e) {
                    CivillisEntitiesAddon.LOGGER.warn("Invalid weight format for {}: {}, using default", entityType, parts[1]);
                    return DEFAULT_ENTITY_WEIGHT;
                }
            }
        }
        
        return DEFAULT_ENTITY_WEIGHT;
    }
    
    static {
        BUILDER.comment("Civillis Entities Addon (CEA) Configuration")
               .comment("Heatmap-based settlement tracking system for Civillis")
               .push("general");
        
        ENABLE_CEA = BUILDER
            .comment("Master switch to enable/disable entire CEA mod functionality")
            .comment("When disabled: No sampling, no marker processing, no particle effects")
            .comment("This has HIGHEST priority - overrides all other settings when false")
            .define("enableCEA", true);
        
        BUILDER.pop();
        
        BUILDER.push("marker_processing");
        
        MANUAL_PLACEMENT_MODE = BUILDER
            .comment("Manual Placement Mode - for admin/custom map creation")
            .comment("When disabled (default): Automatic mode - heat decays normally, markers auto-create/update/delete")
            .comment("When enabled: Manual mode - stops all automatic marker management and heat decay")
            .comment("Use with /ce_create_marker and /ce_remove_marker for full manual control")
            .comment("Markers become permanent and only change via admin commands")
            .comment("Note: This is automatically disabled if enableCEA is false")
            .define("manualPlacementMode", false);
        
        BUILDER.pop();
        
        BUILDER.push("entities");
        
        ENTITY_WEIGHTS = BUILDER
            .comment("List of civilization entities with weights for heatmap system")
            .comment("Format: 'namespace:entity_type:weight' (e.g., 'minecraft:villager:1.0')")
            .comment("If weight is omitted, default value 1.0 will be used")
            .comment("Only entities in this list will be tracked and contribute to settlement markers")
            .comment("Examples:")
            .comment("  ['minecraft:villager'] - uses default weight 1.0")
            .comment("  ['minecraft:villager:1.5', 'minecraft:iron_golem:3.0'] - custom weights")
            .defineList("entityWeights", 
                (java.util.List<String>) (java.util.List<?>) java.util.Collections.emptyList(),
                obj -> obj instanceof String && ((String) obj).contains(":"));
        
        BUILDER.pop();
        
        BUILDER.push("particles");
        
        ENABLE_PARTICLE_EFFECTS = BUILDER
            .comment("Enable particle effects when scanning entities with Civillis tools")
            .comment("When enabled, civilization entities will show particles when detected")
            .define("enableParticleEffects", true);
        
        PARTICLE_DURATION_TICKS = BUILDER
            .comment("Duration of particle effect (in ticks, 20 ticks = 1 second)")
            .comment("How long particles continue to appear after detection")
            .defineInRange("particleDurationTicks", 100, 20, 600);
        
        INITIAL_PARTICLE_TYPE = BUILDER
            .comment("Particle type for initial burst effect")
            .comment("Available types: HAPPY_VILLAGER, GLOW, HEART, FLAME, SMOKE, etc.")
            .comment("See Minecraft wiki for full list of particle types")
            .define("initialParticleType", "HAPPY_VILLAGER");
        
        CONTINUOUS_PARTICLE_TYPE = BUILDER
            .comment("Particle type for continuous effect")
            .comment("Available types: GLOW, SPELL, INSTANT_SPELL, WITCH, etc.")
            .define("continuousParticleType", "GLOW");
        
        CONTINUOUS_PARTICLE_INTERVAL = BUILDER
            .comment("Interval between continuous particle spawns (in ticks)")
            .comment("Lower values = more frequent particles (more performance impact)")
            .defineInRange("continuousParticleInterval", 5, 1, 20);
        
        BUILDER.pop();
        
        BUILDER.push("debug");
        
        ENABLE_VERBOSE_LOGGING = BUILDER
            .comment("Enable verbose logging for CEA operations")
            .comment("When disabled, only warnings and errors will be logged")
            .comment("When enabled, detailed info about sampling, processing, and entity tracking will be logged")
            .define("enableVerboseLogging", false);
        
        BUILDER.pop();
        
        BUILDER.push("heatmap_decay");
        
        USE_PERCENTAGE_DECAY = BUILDER
            .comment("Heatmap decay mode selector")
            .comment("false = Fixed-value decay (default): Subtract a fixed amount each sampling")
            .comment("true = Percentage decay: Reduce heat by a percentage each sampling")
            .comment("Fixed decay is more stable for low-heat areas")
            .comment("Percentage decay encourages spreading out settlements")
            .define("usePercentageDecay", false);
        
        HEAT_DECAY_FIXED = BUILDER
            .comment("Fixed decay amount per sampling period (used when usePercentageDecay = false)")
            .comment("Higher values make heat harder to accumulate, requiring more entities or frequent sampling")
            .comment("Lower values make heat easier to maintain, markers generate and upgrade more easily")
            .comment("Recommended range: 1-5 (default: 1)")
            .defineInRange("heatDecayFixed", 1, 0, 20);
        
        HEAT_DECAY_RATE = BUILDER
            .comment("Percentage decay rate per sampling period (used when usePercentageDecay = true)")
            .comment("Value between 0.0 and 1.0 (e.g., 0.05 = 5% decay)")
            .comment("Higher values make heat harder to accumulate in high-heat areas")
            .comment("Lower values make heat easier to maintain across all levels")
            .comment("Recommended range: 0.05-0.20 (default: 0.05)")
            .defineInRange("heatDecayRate", 0.05, 0.0, 1.0);
        
        BUILDER.pop();
        
        BUILDER.push("level_calculation");
        
        USE_LINEAR_LEVEL_CALCULATION = BUILDER
            .comment("Level calculation mode selector")
            .comment("false = Curve-based calculation (default): Higher levels require more heat (slower progression)")
            .comment("true = Linear calculation: level = heat / threshold (simple and predictable)")
            .comment("Curve mode makes it harder to reach high levels, encouraging more settlement activity")
            .define("useLinearLevelCalculation", false);
        
        LEVEL_CURVE_EXPONENT = BUILDER
            .comment("Exponent for curve-based level calculation (used when useLinearLevelCalculation = false)")
            .comment("Formula: level = (heat / threshold) ^ (1/exponent)")
            .comment("Higher values = slower progression at high levels (more challenging)")
            .comment("Lower values = faster progression (easier to reach high levels)")
            .comment("Recommended range: 2.0-3.5 (default: 2.5 for balanced difficulty)")
            .comment("Examples: 2.0 = square root (easy), 2.5 = moderate, 3.0 = cube root (hard)")
            .defineInRange("levelCurveExponent", 2.5, 1.0, 5.0);
        
        BUILDER.pop();
        
        SPEC = BUILDER.build();
    }
    
    public static final ModConfigSpec SPEC;
}
