# Civillis Entities Addition (CEA)

一个为 [Civillis](https://github.com/MaoxnZ/Civillis) 模组制作的附属模组，通过热力图系统追踪实体活动并生成定居点标记。

## 功能特性

- **热力图采样系统**: 定期扫描实体位置，积累区域热量值
- **自动定居点生成**: 当热量达到阈值时，自动生成不可见的 Marker 实体
- **等级系统**: Marker 根据热量值分为 1-10 级，等级越高代表活动越频繁
- **粒子数字显示**: 在调试模式下，Marker 上方会显示由粒子组成的数字表示等级
- **持久化存储**: 热力数据和 Marker 会自动保存，重启后恢复
- **智能衰减**: 长时间无活动的区域热量会逐渐衰减，Marker 等级会降低
- **可配置实体**: 支持自定义要追踪的实体类型（默认：村民）
- **与 Civillis 集成**: 通过 Mixin 自动将 Marker 数据传递给 Civillis 本体计算文明值

## 安装要求

- Minecraft 1.21.1
- NeoForge 21.1.228+
- **Civillis 模组（必需）** - CEA 是 Civillis 的附属模组，必须先安装本体

## 工作原理

### 热力图系统

CEA 使用基于时间的采样机制来追踪实体活动：

1. **定期采样**: 每隔一定时间（默认 60 秒），扫描所有已加载区块中的目标实体
2. **热量积累**: 每个实体会为其所在区块增加热量值（权重可配置，默认 1.0）
3. **智能衰减**: 每次采样时，所有区块的热量会减少固定值 1（默认）或百分比 5%（可选）
4. **等级计算**: 根据热量值计算 Marker 等级（1-10 级），支持线性和曲线两种模式

**等级计算模式详解**：

**默认模式：曲线计算（越往上越慢）**
- 公式：`等级 = (热量 / 阈值) ^ (1/指数) × 10`
- 默认指数为 2.5（中等难度）
- 例如（阈值=5）：
  - 热量 5 → 等级 1
  - 热量 30 → 等级 3
  - 热量 60 → 等级 4
  - 热量 100 → 等级 5
  - 热量 200 → 等级 7
  - 热量 500 → 等级 10
- 优点：高等级需要更多热量，鼓励持续活动和多个定居点
- 缺点：达到中高等级需要大量时间和实体

**可选模式：线性计算**
- 公式：`等级 = 热量 / 阈值`
- 例如（阈值=5）：
  - 热量 5 → 等级 1
  - 热量 25 → 等级 5
  - 热量 50 → 等级 10
- 优点：简单直观，容易预测
- 缺点：高等级相对容易达到

**配置方法**：
修改 `config/civillis_entities-common.toml` 文件：
```toml
[level_calculation]
useLinearLevelCalculation = false  # false = 曲线计算, true = 线性计算
levelCurveExponent = 2.5  # 曲线指数（推荐范围：2.0-3.5）
```

**曲线指数调整建议**：
- **更激进的曲线**（如指数改为 3.0-3.5）会使 L4-L5 更难达到，需要更多热量和实体活动
- **更温和的曲线**（如指数改为 2.0）会使 L4-L5 更容易达到，适合小型服务器

**智能衰减机制详解**：

**默认模式：固定值衰减**
- 每次采样时，所有区块的热量减少固定值（默认 1）
- 例如：热量 50 → 新热量 49；热量 3 → 新热量 2
- 优点：低热量区域也能缓慢衰减，不会完全停滞
- 缺点：高热量区域衰减速度相对较慢

**可选模式：百分比衰减**
- 每次采样时，所有区块的热量减少当前值的百分比（默认 5%）
- 例如：热量 50 → 衰减 2.5 → 新热量 47.5；热量 5 → 衰减 0.25 → 新热量 4.75
- 优点：高热量区域衰减更快，鼓励分散定居点
- 缺点：低热量区域衰减较慢

**配置方法**：
修改 `config/civillis_entities-common.toml` 文件：
```toml
[heatmap_decay]
usePercentageDecay = false  # false = 固定值衰减, true = 百分比衰减
heatDecayFixed = 1  # 固定衰减值（推荐范围：1-5）
heatDecayRate = 0.05  # 百分比衰减率（推荐范围：0.05-0.20）
```

**衰减值调整建议**：
- **更激进的衰减**（如固定值改为 5 或百分比改为 20%）会使热量更难积累到阈值，需要更多实体或更频繁的采样才能生成 Marker
- **更温和的衰减**（如固定值改为 0.5 或百分比改为 2%）会使热量更容易保持，Marker 更容易生成和维持高等级

### Marker 实体生成

当区块热量达到阈值时：
- 自动生成一个不可见的 Marker 实体
- Marker 存储在该区块中心位置（地面以上 10 格）
- Marker 带有标签 `civillis_settlement_marker`
- 通过 NBT 数据记录等级和热量值

### 与 Civillis 集成

CEA 通过 Mixin 技术将 Marker 数据传递给 Civillis：
- Civillis 在计算文明值时会自动读取 CEA 的 Marker 数据
- Marker 等级越高，对文明值的贡献越大（线性权重：等级 × 10）
- 例如：L1 = 10权重, L5 = 50权重, L10 = 100权重
- 权重会被添加到方块的文明值中，然后统一归一化

这样确保了 CEA 和 Civillis 的无缝协作，无需额外配置。

## 使用方法

### 指令系统

CEA 提供以下管理指令（需要 OP 权限）：

#### `/ce_sample` - 手动采样
立即扫描所有已加载区块中的实体并积累热量。

**用途**：
- 快速测试热力图系统
- 在村民附近手动增加热量
- 调试时跳过等待时间

**示例**：
```
/ce_sample
```

#### `/ce_process` - 处理定居点
根据当前热量值创建、更新或删除 Marker 实体。

**用途**：
- 立即生成/更新 Marker，无需等到每日处理
- 清理重叠的 Marker
- 调试等级变化

**示例**：
```
/ce_process
```

#### `/ce_status` - 查看状态
显示当前热力图的统计信息和所有 Marker 的位置、等级。

**输出内容**：
- 总区块数
- 每个维度的 Marker 数量
- 每个 Marker 的坐标和等级
- 下次采样倒计时

**示例**：
```
/ce_status
```

#### `/ce_debug` - 切换调试模式
开启或关闭调试功能。

**调试模式开启时**：
- 采样时在实体头上显示 END_ROD 粒子
- Marker 上方显示由粒子组成的等级数字
- HUD 显示下次采样倒计时

**调试模式关闭时**：
- 所有视觉效果隐藏
- 后台静默运行

**示例**：
```
/ce_debug
```

### 配置文件

修改 `config/civillis_entities-common.toml` 文件：

```toml
# CEA 模组总开关
general.enableCEA = true  # 禁用后将停止所有功能（采样、Marker处理、Mixin注入）

# Marker 处理模式
marker_processing.manualPlacementMode = false  # 手动放置模式（用于管理员自定义地图）

# 实体权重配置
entities.entityWeights = []  # 格式: ['namespace:entity_type:weight']，例如：['minecraft:villager:1.0']
                              # 如果省略权重，使用默认值 1.0
                              # 只有列表中的实体才会被追踪

# 粒子效果配置
particles.enableParticleEffects = true  # 启用粒子效果
particles.particleDurationTicks = 100  # 粒子持续时间（刻）
particles.initialParticleType = "HAPPY_VILLAGER"  # 初始粒子类型
particles.continuousParticleType = "GLOW"  # 持续粒子类型
particles.continuousParticleInterval = 5  # 持续粒子间隔（刻）

# 日志控制
debug.enableVerboseLogging = false  # 启用详细日志（false = 只显示警告和错误）

# 热力图衰减配置
heatmap_decay.usePercentageDecay = false  # 衰减模式：false = 固定值, true = 百分比
heatmap_decay.heatDecayFixed = 1  # 固定衰减值（推荐范围：1-5）
heatmap_decay.heatDecayRate = 0.05  # 百分比衰减率（推荐范围：0.05-0.20）

# 等级计算配置
level_calculation.useLinearLevelCalculation = false  # 等级计算模式：false = 曲线, true = 线性
level_calculation.levelCurveExponent = 2.5  # 曲线指数（推荐范围：2.0-3.5）
```

#### 关键配置说明

**模组总开关**：
```toml
# 禁用整个 CEA 模组（包括采样、Marker处理、Mixin注入）
general.enableCEA = false

# 重新启用
general.enableCEA = true
```

**手动放置模式**：
```toml
# 开启后：停止自动 Marker 管理和热量衰减
# 只能使用 /ce_create_marker 和 /ce_remove_marker 命令手动管理
marker_processing.manualPlacementMode = true
```

**自定义实体追踪**：
```toml
# 只追踪列表中指定的实体类型
# 如果要追踪村民，必须手动添加 'minecraft:villager'
entities.entityWeights = ["minecraft:villager", "minecraft:iron_golem"]

# 示例：只追踪村民（使用默认权重 1.0）
entities.entityWeights = ["minecraft:villager"]

# 示例：追踪多种实体并设置不同权重
entities.entityWeights = [
    "minecraft:villager:1.5",
    "minecraft:wandering_trader:2.0",
    "minecraft:iron_golem:3.0"
]
```

**日志控制**：
```toml
# 开启详细日志 - 显示所有采样、处理、实体追踪信息
debug.enableVerboseLogging = true

# 关闭详细日志 - 只显示警告和错误
debug.enableVerboseLogging = false
```

## 常见问题

### Q1: 为什么 Marker 等级在重新进入世界后下降了？

A: 这是因为热量衰减机制。每次采样时，所有区块的热量会衰减（默认固定值 1 或百分比 5%）。如果你长时间没有在该区域活动，热量会逐渐降低，导致 Marker 等级下降。

**解决方案**：
- 保持村民在该区域活动
- 使用 `/ce_sample` 手动增加热量
- 调整配置文件中的 `heatDecayFixed` 或 `heatDecayRate`

### Q2: 如何看到 Marker 的位置和等级？

A: 使用 `/ce_debug` 开启调试模式，然后：
- Marker 上方会显示由粒子组成的数字（等级）
- 使用 `/ce_status` 查看所有 Marker 的坐标和等级
- HUD 左上角会显示下次采样倒计时

### Q3: 采样时看不到村民头上的粒子效果？

A: 确保调试模式已开启：
```
/ce_debug
```
调试模式关闭时，采样粒子会被隐藏。

### Q4: 如何让 CEA 追踪其他实体（比如牛、羊）？

A: 修改配置文件 `config/civillis_entities-common.toml`：
```toml
entities.entityWeights = ["minecraft:cow", "minecraft:sheep"]
```

**注意**：
- 只支持具体的实体 ID，不支持通配符（如 `minecraft:*`）
- 如果要追踪村民，必须手动添加 `'minecraft:villager'`
- 每个实体都需要单独列出
- 可以为不同实体设置不同权重，例如：`["minecraft:villager:1.5", "minecraft:cow:0.5"]`

### Q5: CEA 和 Civillis 本体如何协作？

A: CEA 通过 Mixin 技术自动将 Marker 数据传递给 Civillis：
1. CEA 生成 Marker 实体并存储等级信息
2. Civillis 在计算文明值时读取 CEA 的 Marker 权重
3. Marker 权重 = 等级 × 10（线性计算，L1=10, L5=50, L10=100）
4. 权重会被添加到方块的文明值中，然后统一归一化
5. 无需额外配置，完全自动化

### Q6: 如何禁用 CEA 模组？

A: 修改配置文件：
```toml
general.enableCEA = false
```
这将停止所有功能：采样、Marker处理、Mixin注入。重启游戏后生效。

### Q7: 日志太多怎么办？

A: 关闭详细日志：
```toml
debug.enableVerboseLogging = false
```
这样只会显示警告和错误信息。

### Q8: Marker 会消失吗？

A: 是的，Marker 会在以下情况下被删除：
- 当区块热量持续低于阈值时，Marker 会逐级降级（L10 → L9 → ... → L1）
- 当 Level 1 的 Marker 热量仍然低于阈值时，会被自动删除
- 这样可以清理长时间无人活动的废弃区域
- 重启游戏后，只有热量足够的 Marker 才会恢复

## 开发指南

### 构建项目

```bash
./gradlew build
```

构建后的文件位于 `build/libs/` 目录。

### 添加依赖

在 `build.gradle` 中添加：

```groovy
repositories {
    maven {
        name = "Modrinth"
        url = "https://api.modrinth.com/maven"
    }
}

dependencies {
    implementation fg.deobf("maven.modrinth:civillis:1.21.1-1.0.0")
}
```

## 性能优化建议

1. **调整采样间隔**: 默认 60 秒，可在代码中修改 `SAMPLING_INTERVAL_MS`
2. **限制实体类型**: 只追踪必要的实体，避免扫描过多类型
3. **关闭粒子效果**: `particles.enableParticleEffects = false` 可减少客户端性能开销
4. **关闭详细日志**: `debug.enableVerboseLogging = false` 减少日志输出
5. **使用手动模式**: 在不需要自动管理的场景下，开启 `marker_processing.manualPlacementMode = true`

## 兼容性

- ✅ 完全兼容原版 Civillis 的所有功能
- ✅ 支持与其他修改生成率的模组共存
- ✅ 自动保存和恢复热力数据
- ⚠️ 必须安装 Civillis 本体模组

## 问题反馈

如有问题或建议，请在 GitHub Issues 中反馈。

## 许可证

MIT License

## 致谢

- 感谢 MaoxnZ 创造的 [Civillis](https://github.com/MaoxnZ/Civillis) 模组
- 感谢 NeoForge 团队提供的模组开发框架
