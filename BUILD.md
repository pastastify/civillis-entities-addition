# 构建说明

## 环境要求

- Java Development Kit (JDK) 21 或更高版本
- Git（可选，用于版本控制）
- 互联网连接（用于下载依赖）

## 快速构建

### Windows

```powershell
.\gradlew build
```

### Linux/Mac

```bash
./gradlew build
```

## 详细步骤

### 1. 克隆项目（如果还没有）

```bash
git clone <repository-url>
cd examplemod-template-1.21.1
```

### 2. 初始化 Gradle（首次构建）

```bash
# Windows
.\gradlew

# Linux/Mac
./gradlew
```

这会自动下载 Gradle 和所有必需的依赖。

### 3. 构建模组

```bash
# 标准构建
./gradlew build

# 跳过测试的构建（更快）
./gradlew build -x test

# 清理后重新构建
./gradlew clean build
```

### 4. 查找构建产物

构建完成后，生成的 JAR 文件位于：

```
build/libs/civillis_entities-1.0.0.jar
```

## 其他有用的命令

### 运行客户端测试

```bash
./gradlew runClient
```

这会启动一个带有模组的 Minecraft 客户端用于测试。

### 运行服务器测试

```bash
./gradlew runServer
```

这会启动一个带有模组的 Minecraft 服务器用于测试。

### 生成数据

```bash
./gradlew runData
```

运行数据生成器（如果有配置）。

### 清理构建

```bash
./gradlew clean
```

删除所有构建产物。

### 刷新依赖

```bash
./gradlew --refresh-dependencies
```

重新下载所有依赖项。

## IDE 设置

### IntelliJ IDEA

```bash
./gradlew idea
```

或者：
1. 打开 IntelliJ IDEA
2. 选择 "Open" 并选择项目文件夹
3. IDEA 会自动识别 Gradle 项目
4. 等待 Gradle 同步完成

### Eclipse

```bash
./gradlew eclipse
```

## 故障排除

### 问题 1: "Java version mismatch"

**症状**: 构建失败，提示 Java 版本错误

**解决方案**:
```bash
# 检查当前 Java 版本
java -version

# 应该显示 Java 21 或更高版本
# 如果不是，请安装 JDK 21+ 并更新 JAVA_HOME 环境变量
```

### 问题 2: "Out of memory"

**症状**: 构建过程中内存不足

**解决方案**:
编辑 `gradle.properties`，增加内存：
```properties
org.gradle.jvmargs=-Xmx4G
```

### 问题 3: "Dependency resolution failed"

**症状**: 无法下载依赖

**解决方案**:
```bash
# 检查网络连接
# 清除 Gradle 缓存
./gradlew clean

# 重新构建
./gradlew build --refresh-dependencies
```

### 问题 4: "Civillis dependency not found"

**症状**: 找不到 Civillis 依赖

**解决方案**:
1. 检查 `build.gradle` 中的 Modrinth Maven 仓库配置
2. 确认 Civillis 版本号正确
3. 如果 Modrinth 上没有该版本，可能需要：
   - 手动下载 Civillis JAR 并放入 `libs/` 文件夹
   - 修改 `build.gradle` 使用本地依赖：
   ```groovy
   implementation files("libs/civillis-1.0.0.jar")
   ```

### 问题 5: Gradle 同步失败（IDE）

**症状**: IDE 中 Gradle 同步失败

**解决方案**:
1. 关闭 IDE
2. 删除 `.idea/` 文件夹（IntelliJ）或 `.project` 等文件（Eclipse）
3. 重新导入项目
4. 确保 IDE 使用 JDK 21

## 开发模式

### 启用调试

在 `build.gradle` 中添加：
```groovy
runs {
    client {
        // ... existing config ...
        property 'forge.logging.console.level', 'debug'
    }
}
```

### 热重载

对于资源文件的更改：
1. 在游戏中执行 `/reload`
2. 无需重启游戏

对于代码更改：
1. 需要重新编译
2. 重启游戏/服务器

## 发布构建

### 准备发布

1. 更新 `gradle.properties` 中的版本号
2. 更新 `CHANGELOG.md`
3. 确保所有文档都是最新的

### 构建发布版本

```bash
./gradlew build
```

发布的 JAR 文件会在 `build/libs/` 目录中。

### 验证构建

1. 在干净的 Minecraft 实例中测试
2. 确保安装了所有必需依赖
3. 测试所有功能
4. 检查日志是否有错误

## CI/CD 集成

### GitHub Actions 示例

创建 `.github/workflows/build.yml`:

```yaml
name: Build Mod

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v2
    
    - name: Set up JDK 21
      uses: actions/setup-java@v2
      with:
        java-version: '21'
        distribution: 'adopt'
    
    - name: Cache Gradle packages
      uses: actions/cache@v2
      with:
        path: ~/.gradle/caches
        key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle') }}
    
    - name: Build with Gradle
      run: ./gradlew build
    
    - name: Upload artifacts
      uses: actions/upload-artifact@v2
      with:
        name: mod-jar
        path: build/libs/*.jar
```

## 性能优化建议

### Gradle 配置

在 `gradle.properties` 中：
```properties
org.gradle.jvmargs=-Xmx4G -XX:+UseG1GC
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.daemon=true
```

### 加速开发

1. **使用守护进程**: Gradle 守护进程已默认启用
2. **并行构建**: 已在配置中启用
3. **构建缓存**: 已在配置中启用
4. **增量编译**: NeoForge ModDevGradle 插件自动处理

## 常见问题

**Q: 首次构建需要多长时间？**
A: 取决于网络速度，通常 5-15 分钟（需要下载 Minecraft、NeoForge 和所有依赖）。

**Q: 如何更新 NeoForge 版本？**
A: 修改 `gradle.properties` 中的 `neo_version`，然后重新构建。

**Q: 可以自定义 JAR 文件名吗？**
A: 可以，在 `build.gradle` 中修改 `archivesName`。

**Q: 如何添加更多依赖？**
A: 在 `build.gradle` 的 `dependencies` 块中添加，确保先添加相应的 Maven 仓库。

## 获取帮助

- [NeoForge 官方文档](https://docs.neoforged.net/)
- [Gradle 用户指南](https://docs.gradle.org/current/userguide/userguide.html)
- [Minecraft Modding Wiki](https://minecraft.wiki/w/Modding)
- 项目的 GitHub Issues

---

祝您构建顺利！🚀
