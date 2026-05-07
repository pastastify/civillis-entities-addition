# Civillis Entities Addition (CEA)

A NeoForge addon mod for [Civillis](https://github.com/MaoxnZ/Civillis) that tracks entity activity via heatmap system and generates settlement markers with automatic civilization value contribution.

## ✨ Features

- 🔥 **Heatmap Sampling System**: Periodically scans entity positions and accumulates regional heat values
- 🏘️ **Automatic Settlement Generation**: Creates invisible Marker entities when heat reaches threshold
- 📊 **Level System**: Markers are graded 1-10 based on heat values (higher = more activity)
- 💾 **Persistent Storage**: Heat data and markers auto-save and restore after restart
- 🔄 **Smart Decay**: Heat gradually decays in inactive areas, marker levels decrease over time
- ⚙️ **Configurable Entities**: Support custom entity types to track (default: villagers)
- 🔗 **Civillis Integration**: Seamlessly passes marker data to Civillis via Mixin for civilization value calculation

## 📦 Requirements

- Minecraft 1.21.1
- NeoForge 21.1.228+
- **Civillis mod (REQUIRED)** - CEA is an addon, must install the main mod first

## 🔧 How It Works

### Heatmap System

CEA uses time-based sampling to track entity activity:

1. **Periodic Sampling**: Scans all loaded chunks for target entities every 60 seconds (default)
2. **Heat Accumulation**: Each entity adds heat to its chunk (configurable weight, default 1.0)
3. **Smart Decay**: Heat decreases by fixed value 1 (default) or percentage 5% (optional) each sampling
4. **Level Calculation**: Marker level (1-10) calculated from heat, supports linear and curve modes

**Level Calculation Modes**:

**Default: Curve-based (slower progression at higher levels)**
- Formula: `level = (heat / threshold) ^ (1/exponent) × 10`
- Default exponent: 2.5 (medium difficulty)
- Examples (threshold=5):
  - Heat 5 → Level 1
  - Heat 30 → Level 3
  - Heat 60 → Level 4
  - Heat 100 → Level 5
  - Heat 200 → Level 7
  - Heat 500 → Level 10
- Pros: Higher levels require more heat, encourages sustained activity
- Cons: Reaching mid-high levels takes significant time

**Optional: Linear calculation**
- Formula: `level = heat / threshold`
- Examples (threshold=5):
  - Heat 5 → Level 1
  - Heat 25 → Level 5
  - Heat 50 → Level 10
- Pros: Simple and predictable
- Cons: High levels relatively easy to achieve

**Configuration**:
Edit `config/civillis_entities-common.toml`:
```toml
[level_calculation]
useLinearLevelCalculation = false  # false = curve, true = linear
levelCurveExponent = 2.5  # Curve exponent (recommended: 2.0-3.5)
```

**Smart Decay Mechanism**:

**Default: Fixed-value decay**
- Heat decreases by fixed amount each sampling (default 1)
- Example: Heat 50 → 49; Heat 3 → 2
- Pros: Low-heat areas decay slowly, won't stagnate
- Cons: High-heat areas decay relatively slower

**Optional: Percentage decay**
- Heat decreases by percentage of current value (default 5%)
- Example: Heat 50 → decay 2.5 → 47.5; Heat 5 → decay 0.25 → 4.75
- Pros: High-heat areas decay faster, encourages分散 settlements
- Cons: Low-heat areas decay slower

**Configuration**:
```toml
[heatmap_decay]
usePercentageDecay = false  # false = fixed, true = percentage
heatDecayFixed = 1  # Fixed decay value (recommended: 1-5)
heatDecayRate = 0.05  # Percentage decay rate (recommended: 0.05-0.20)
```

### Marker Entity Generation

When chunk heat reaches threshold:
- Automatically creates an invisible Marker entity
- Marker positioned at chunk center (10 blocks above ground)
- Tagged with `civillis_settlement_marker`
- Stores level and heat values in NBT data

### Civillis Integration

CEA passes Marker data to Civillis via Mixin:
- Civillis automatically reads CEA Marker data during civilization calculation
- Higher marker levels contribute more (linear weight: level × 10)
- Examples: L1 = 10 weight, L5 = 50 weight, L10 = 100 weight
- Weight added to block civilization score, then normalized

This ensures seamless CEA and Civillis collaboration without extra configuration.

## 🎮 Usage

### Commands

CEA provides the following management commands (OP required):

#### `/ce_sample` - Manual Sampling
Immediately scans all loaded chunks for entities and accumulates heat.

**Use cases**:
- Quick testing of heatmap system
- Manually increase heat near villagers
- Skip waiting time during debugging

**Example**:
```
/ce_sample
```

#### `/ce_process` - Process Settlements
Creates, updates, or removes Marker entities based on current heat values.

**Use cases**:
- Immediately generate/update markers without waiting for daily processing
- Clean up overlapping markers
- Debug level changes

**Example**:
```
/ce_process
```

#### `/ce_status` - View Status
Displays heatmap statistics and all marker positions/levels.

**Output includes**:
- Total chunk count
- Marker count per dimension
- Coordinates and level of each marker
- Countdown to next sampling

**Example**:
```
/ce_status
```

#### `/ce_debug` - Toggle Debug Mode
Enables or disables debug features.

**When enabled**:
- Shows END_ROD particles on entities during sampling
- Displays particle digit numbers above markers
- HUD shows countdown to next sampling

**When disabled**:
- All visual effects hidden
- Runs silently in background

**Example**:
```
/ce_debug
```

#### `/ce_create_marker <x> <z> <level>` - Create Marker (Admin)
Manually creates a marker at specified coordinates.

**Example**:
```
/ce_create_marker 0 0 5
```

#### `/ce_remove_marker <x> <z>` - Remove Marker (Admin)
Manually removes a marker at specified coordinates.

**Example**:
```
/ce_remove_marker 0 0
```

### Configuration

Edit `config/civillis_entities-common.toml`:

```toml
# Master switch for CEA mod
general.enableCEA = true  # Disabling stops all functionality

# Marker processing mode
marker_processing.manualPlacementMode = false  # Manual mode for admin map creation

# Entity weight configuration
entities.entityWeights = []  # Format: ['namespace:entity_type:weight']
                              # Default weight: 1.0 if omitted
                              # Only listed entities are tracked

# Particle effects
particles.enableParticleEffects = true
particles.particleDurationTicks = 100
particles.initialParticleType = "HAPPY_VILLAGER"
particles.continuousParticleType = "GLOW"
particles.continuousParticleInterval = 5

# Logging control
debug.enableVerboseLogging = false  # false = warnings/errors only

# Heatmap decay settings
heatmap_decay.usePercentageDecay = false  # false = fixed, true = percentage
heatmap_decay.heatDecayFixed = 1  # Fixed decay (recommended: 1-5)
heatmap_decay.heatDecayRate = 0.05  # Percentage decay (recommended: 0.05-0.20)

# Level calculation
level_calculation.useLinearLevelCalculation = false  # false = curve, true = linear
level_calculation.levelCurveExponent = 2.5  # Curve exponent (recommended: 2.0-3.5)
```

**Key Configuration Notes**:

**Master Switch**:
```toml
# Disable entire CEA mod (stops sampling, marker processing, mixin injection)
general.enableCEA = false

# Re-enable
general.enableCEA = true
```

**Manual Placement Mode**:
```toml
# When enabled: Stops automatic marker management and heat decay
# Use /ce_create_marker and /ce_remove_marker for full manual control
marker_processing.manualPlacementMode = true
```

**Custom Entity Tracking**:
```toml
# Only track specified entity types
# Must manually add 'minecraft:villager' to track villagers
entities.entityWeights = ["minecraft:villager", "minecraft:iron_golem"]

# Example: Track only villagers (uses default weight 1.0)
entities.entityWeights = ["minecraft:villager"]

# Example: Track multiple entities with different weights
entities.entityWeights = [
    "minecraft:villager:1.5",
    "minecraft:wandering_trader:2.0",
    "minecraft:iron_golem:3.0"
]
```

**Logging Control**:
```toml
# Enable verbose logging - shows all sampling, processing, entity tracking info
debug.enableVerboseLogging = true

# Disable verbose logging - only warnings and errors
debug.enableVerboseLogging = false
```

## ❓ FAQ

### Q1: Why do marker levels drop after re-entering the world?

A: This is due to the heat decay mechanism. Heat decreases each sampling (default fixed 1 or 5%). If you're inactive in an area for a long time, heat gradually drops, causing marker levels to decrease.

**Solutions**:
- Keep villagers active in the area
- Use `/ce_sample` to manually increase heat
- Adjust `heatDecayFixed` or `heatDecayRate` in config

### Q2: How can I see marker positions and levels?

A: Use `/ce_debug` to enable debug mode, then:
- Particle digit numbers appear above markers
- Use `/ce_status` to view all marker coordinates and levels
- HUD shows countdown to next sampling

### Q3: Can't see particles on villagers during sampling?

A: Ensure debug mode is enabled:
```
/ce_debug
```
Sampling particles are hidden when debug mode is off.

### Q4: How to make CEA track other entities (like cows, sheep)?

A: Edit config file `config/civillis_entities-common.toml`:
```toml
entities.entityWeights = ["minecraft:cow", "minecraft:sheep"]
```

**Notes**:
- Only specific entity IDs supported, no wildcards (e.g., `minecraft:*`)
- Must manually add `'minecraft:villager'` to track villagers
- Each entity must be listed separately
- Can set different weights: `["minecraft:villager:1.5", "minecraft:cow:0.5"]`

### Q5: How does CEA integrate with Civillis?

A: CEA automatically passes marker data to Civillis via Mixin:
1. CEA generates marker entities and stores level information
2. Civillis reads CEA marker weights during civilization calculation
3. Marker weight = level × 10 (linear, L1=10, L5=50, L10=100)
4. Weight added to block civilization score, then normalized
5. Fully automated, no extra configuration needed

### Q6: How to disable CEA mod?

A: Edit config:
```toml
general.enableCEA = false
```
This stops all functionality: sampling, marker processing, mixin injection. Restart game to take effect.

### Q7: Too much log output?

A: Disable verbose logging:
```toml
debug.enableVerboseLogging = false
```
Only warnings and errors will be shown.

### Q8: Do markers disappear?

A: Yes, markers are removed when:
- Chunk heat stays below threshold, markers downgrade level by level (L10 → L9 → ... → L1)
- Level 1 marker with low heat is automatically deleted
- This cleans up abandoned areas with no activity
- Only markers with sufficient heat are restored after restart

## 🛠️ Development Guide

### Building the Project

```bash
./gradlew build
```

Built files located in `build/libs/` directory.

### Adding Dependencies

In `build.gradle`:

```groovy
repositories {
    maven {
        name = "Modrinth"
        url = "https://api.modrinth.com/maven"
    }
}

dependencies {
    implementation "maven.modrinth:civillis:[1.21.1,)"
}
```

## 💡 Performance Optimization Tips

1. **Adjust sampling interval**: Default 60 seconds, can modify `SAMPLING_INTERVAL_MS` in code
2. **Limit entity types**: Only track necessary entities to avoid scanning too many types
3. **Disable particle effects**: `particles.enableParticleEffects = false` reduces client performance impact
4. **Disable verbose logging**: `debug.enableVerboseLogging = false` reduces log output
5. **Use manual mode**: Enable `marker_processing.manualPlacementMode = true` when automatic management not needed

## ✅ Compatibility

- ✅ Fully compatible with original Civillis features
- ✅ Coexists with other spawn rate modification mods
- ✅ Auto-saves and restores heat data
- ⚠️ Civillis base mod required

## 🐛 Issue Reporting

Please report bugs or suggestions on GitHub Issues.

## 📄 License

MIT License

## 🙏 Credits

- Thanks to MaoxnZ for creating the [Civillis](https://github.com/MaoxnZ/Civillis) mod
- Thanks to NeoForge team for the modding framework
