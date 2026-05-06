# 更新日志

## [1.0.0] - 2026-05-02

### 新增功能

#### 核心系统
- ✅ 实体扫描系统 (`EntityCivilizationScanner`)
  - **两种扫描模式**:
    - `GLOBAL` 模式：扫描所有实体，无论是否在文明区域内
    - `CIVILIZED_AREA` 模式：只扫描文明区域内的实体
  - 支持扫描模式切换（通过配置文件或 KubeJS）
  - 实现距离衰减算法
  - 自动归一化文明值分数
  - **总开关控制**：可以完全启用/禁用实体扫描功能
  
- ✅ 实体权重系统 (`EntityWeightData`, `EntityWeightRegistry`)
  - 支持正负权重值
  - 可配置实体是否参与计算
  - 数据包加载和热重载支持

- ✅ 事件处理系统 (`EntityScanEventHandler`)
  - 数据包重载监听器
  - 可选的实时实体跟踪
  - 与 Civillis 的集成接口

#### 配置系统
- ✅ 完整的 NeoForge 配置系统 (`Config.java`)
  - **通用设置**:
    - `enableEntityScanning`: 总开关，完全启用/禁用实体扫描
    - `scanMode`: 扫描模式选择（GLOBAL / CIVILIZED_AREA）
    - `enableRealTimeScanning`: 实时跟踪开关
  - 扫描参数（半径、高度）
  - 实体权重（玩家、村民、被动生物、敌对生物）
  - 分数组合（方块和实体权重比例）
  - 性能优化选项（缓存、实时扫描）

#### 扩展接口
- ✅ KubeJS 集成 (`KubeJSIntegration.java`)
  - `CivillisEntities` API 对象
  - `setEntityWeight()` - 设置实体权重
  - `getEntityWeight()` - 获取实体权重
  - `removeEntityWeight()` - 移除自定义权重
  - `reloadWeights()` - 重新加载权重
  - **`setScanMode()` - 设置扫描模式（GLOBAL / CIVILIZED_AREA）**
  - **`getScanMode()` - 获取当前扫描模式**
  
- ✅ 数据包支持
  - 标准 Minecraft 数据包格式
  - JSON 配置文件
  - `/reload` 命令支持

#### 文档
- ✅ README.md - 完整的使用指南和功能说明
- ✅ DATAPACK_GUIDE.md - 数据包配置详细教程
- ✅ QUICKSTART.md - 5分钟快速上手指南
- ✅ PROJECT_SUMMARY.md - 项目技术总结
- ✅ 中英文语言文件 (en_us.json, zh_cn.json)
- ✅ KubeJS 示例脚本
- ✅ 示例数据包配置（牛、村民、僵尸）

### 技术实现

#### 扫描算法
```
扫描区域定义 → 获取实体列表 → 计算权重和距离衰减 → 归一化输出
```

#### 默认实体分类
- **高文明值**: 玩家 (+5.0), 村民 (+3.0)
- **中等文明值**: 农场动物 (+1.0), 宠物 (+2.0)
- **负文明值**: 敌对生物 (-2.0), Boss (-8.0 ~ -10.0)

#### 性能优化
- 可选的缓存系统
- 可配置的扫描范围
- 实时扫描开关
- 距离衰减减少远距离实体计算

### 兼容性
- ✅ Minecraft 1.21.1
- ✅ NeoForge 21.1.228+
- ✅ Civillis 1.0.0+
- ✅ KubeJS 2101.7.1+ (可选)

### 已知限制
- ⚠️ 需要 Civillis 模组才能运行
- ⚠️ 当前通过 API 提供集成，可能需要 Mixin 才能完全整合到 Civillis 的生成逻辑中
- ⚠️ KubeJS 集成为可选功能，需要单独安装 KubeJS

### 文件结构
```
civillis_entities/
├── src/main/java/com/civillis/entities/
│   ├── CivillisEntitiesAddon.java
│   ├── Config.java
│   ├── EntityWeightData.java
│   ├── scanner/EntityCivilizationScanner.java
│   ├── registry/EntityWeightRegistry.java
│   ├── event/EntityScanEventHandler.java
│   └── integration/KubeJSIntegration.java
├── src/main/resources/
│   ├── META-INF/neoforge.mods.toml
│   ├── assets/civillis_entities/lang/
│   │   ├── en_us.json
│   │   └── zh_cn.json
│   ├── data/civillis_entities/entity_weights/
│   │   ├── cow.json
│   │   ├── villager.json
│   │   └── zombie.json
│   └── kubejs/server_scripts/civillis_entities.js
├── README.md
├── DATAPACK_GUIDE.md
├── QUICKSTART.md
├── PROJECT_SUMMARY.md
└── CHANGELOG.md
```

### 构建信息
- Gradle 版本: 根据项目配置
- Java 版本: 21
- Parchment 映射: 1.21.1 / 2024.11.17

---

## 未来计划

### 1.1.0 (计划中)
- [ ] Mixin 集成到 Civillis 的核心扫描方法
- [ ] 调试可视化工具（显示实体贡献）
- [ ] 更多预设配置
- [ ] 统计和日志系统

### 1.2.0 (计划中)
- [ ] GUI 配置界面
- [ ] 动态权重调整（基于时间、生物群系等）
- [ ] 实体分组系统
- [ ] 更好的性能优化

### 长期目标
- [ ] Fabric 版本支持
- [ ] Forge 版本支持
- [ ] 官方 Civillis API 集成
- [ ] 更多模组兼容性配置

---

**发布说明**: 这是首个公开发布版本，包含了完整的基础功能。欢迎反馈和建议！
