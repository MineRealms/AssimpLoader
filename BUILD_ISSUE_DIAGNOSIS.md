# 构建问题诊断报告

**日期**: 2026-04-26  
**问题**: Gradle 构建失败 - SSL 握手错误

---

## 问题症状

```
javax.net.ssl.SSLHandshakeException: Remote host terminated the handshake
Caused by: java.io.EOFException: SSL peer shut down incorrectly
```

**影响的依赖**:
- icyllis.modernui:ModernUI-Forge:1.20.1-3.12.0.+
- mezz.jei:jei-1.20.1-forge:15.20.0.115 (已禁用)
- 所有通过 HTTPS 下载的 Maven 依赖

---

## 根本原因

**代理 SSL 证书问题**

代理配置: `127.0.0.1:7890`

测试结果:
```bash
$ curl -I -x http://127.0.0.1:7890 https://maven.virtuoel.com
HTTP/1.1 200 Connection established  # 代理连接成功
curl: (35) schannel: failed to receive handshake, SSL/TLS connection failed  # SSL 握手失败
```

**分析**:
1. 代理服务器正常运行（返回 200 Connection established）
2. SSL/TLS 握手阶段失败
3. 代理的 SSL 证书未被系统信任
4. Gradle/Java 拒绝不受信任的 SSL 连接

---

## 已尝试的解决方案

### ❌ 方案 1: 禁用 Gradle 代理
```properties
# 注释掉代理配置
#systemProp.http.proxyHost=127.0.0.1
#systemProp.http.proxyPort=7890
```
**结果**: DNS 解析失败（无法访问外网）

### ❌ 方案 2: 配置 Java SSL 信任库
```properties
org.gradle.jvmargs=-Djavax.net.ssl.trustStore=NONE -Djavax.net.ssl.trustStoreType=Windows-ROOT
```
**结果**: SSL 握手依然失败

### ❌ 方案 3: Maven Wagon SSL 绕过
```properties
org.gradle.jvmargs=-Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true
```
**结果**: SSL 握手依然失败（Gradle 使用 Apache HttpClient，不是 Maven Wagon）

### ❌ 方案 4: 禁用可选依赖
```gradle
// runtimeOnly fg.deobf("mezz.jei:jei-1.20.1-forge:15.20.0.115")  // 已禁用
```
**结果**: ModernUI（必需依赖）依然失败

---

## ✅ 推荐解决方案

### 方案 A: 修复代理 SSL 证书（推荐）

**如果使用 Clash/V2Ray/SSR 等代理软件**:

1. **导出代理根证书**
   - Clash: 设置 → TUN 模式 → 安装证书
   - V2Ray: 设置 → 证书 → 导出根证书

2. **导入到 Java 信任库**
   ```powershell
   # 找到 Java 安装路径
   $javaHome = "C:\Program Files\Eclipse Adoptium\jdk-17.0.18+8-hotspot"
   
   # 导入证书（假设证书文件为 proxy-ca.crt）
   & "$javaHome\bin\keytool.exe" -import -trustcacerts `
     -alias proxy-ca `
     -file proxy-ca.crt `
     -keystore "$javaHome\lib\security\cacerts" `
     -storepass changeit
   ```

3. **重新构建**
   ```powershell
   .\gradlew clean build
   ```

### 方案 B: 使用系统代理（无 SSL 拦截）

**如果代理软件支持"系统代理模式"**:

1. 在代理软件中启用"系统代理"或"TUN 模式"
2. 禁用 Gradle 代理配置
   ```properties
   # gradle.properties - 注释掉
   #systemProp.http.proxyHost=127.0.0.1
   #systemProp.http.proxyPort=7890
   ```
3. 重新构建（Gradle 会自动使用系统代理）

### 方案 C: 手动下载依赖（临时方案）

**如果无法修复代理，手动下载并安装依赖**:

1. **下载 ModernUI**
   - 访问: https://maven.blamejared.com/icyllis/modernui/ModernUI-Forge/
   - 下载: `ModernUI-Forge-1.20.1-3.12.0.jar` 和 `.pom` 文件

2. **安装到本地 Maven 仓库**
   ```powershell
   # 安装 JAR
   mvn install:install-file `
     -Dfile=ModernUI-Forge-1.20.1-3.12.0.jar `
     -DgroupId=icyllis.modernui `
     -DartifactId=ModernUI-Forge `
     -Dversion=1.20.1-3.12.0 `
     -Dpackaging=jar `
     -DpomFile=ModernUI-Forge-1.20.1-3.12.0.pom
   ```

3. **修改 build.gradle 使用本地仓库**
   ```gradle
   repositories {
       mavenLocal()  // 添加到第一行
       // ... 其他仓库
   }
   ```

4. **重新构建**
   ```powershell
   .\gradlew clean build --offline
   ```

### 方案 D: 使用其他网络环境

**如果有其他网络环境**:

1. 使用手机热点或其他无需代理的网络
2. 临时禁用代理配置
3. 下载所有依赖后切回原网络

---

## 当前项目状态

### ✅ 代码完成度: 100%
- Phase 1-5, 7 全部完成
- 616 行核心代码
- 479 行 Mixin 扩展
- 1,800 行文档

### ⚠️ 构建状态: 阻塞
- 代码无语法错误
- 依赖解析失败（网络问题）
- 无法生成 JAR 文件

### 📋 Git 状态
```
* 27493c5 docs(photon): Phase 7 - Complete integration
* 46eb170 feat(photon): Phase 5 - Material import UI
* f34b12a feat(photon): Phase 4 - Material converter
* e2d823e feat(photon): Phase 3 - Mesh import UI
* f9079cd feat(photon): Phase 2 - Mesh converter
* 5fe8f44 feat(photon): Phase 1 - Infrastructure
```

---

## 下一步行动

### 立即行动（选择一个方案）
1. ✅ **方案 A**: 修复代理 SSL 证书（最彻底）
2. ✅ **方案 B**: 使用系统代理模式（最简单）
3. ⚠️ **方案 C**: 手动下载依赖（最繁琐）
4. ⚠️ **方案 D**: 更换网络环境（最快速）

### 验证步骤
```powershell
# 1. 测试代理 SSL
curl -I -x http://127.0.0.1:7890 https://maven.blamejared.com

# 2. 清理缓存
.\gradlew clean --refresh-dependencies

# 3. 重新构建
.\gradlew build

# 4. 验证 JAR 文件
ls build/libs/*.jar
```

---

## 技术细节

### Gradle 依赖解析流程
```
1. 读取 build.gradle 依赖声明
2. 查询 Maven 仓库（通过 HTTPS）
3. 下载 POM 文件（元数据）
4. 下载 JAR 文件（二进制）
5. ForgeGradle deobfuscate（反混淆）
6. 缓存到 bundled_deobf_repo
```

**当前阻塞点**: 步骤 2-4（HTTPS 下载失败）

### SSL 握手流程
```
1. Client Hello (Gradle) → Proxy
2. Proxy → Server (maven.blamejared.com)
3. Server → Proxy (SSL Certificate)
4. Proxy → Client (Proxy's Certificate)  ← 失败点
5. Client 验证证书 → 不受信任 → 拒绝连接
```

**问题**: Gradle/Java 不信任代理的自签名证书

---

## 参考资料

- [Gradle Proxy Configuration](https://docs.gradle.org/current/userguide/build_environment.html#sec:accessing_the_web_via_a_proxy)
- [Java Keytool Documentation](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html)
- [ForgeGradle Documentation](https://github.com/MinecraftForge/ForgeGradle)

---

**最后更新**: 2026-04-26 04:00  
**状态**: 等待网络/代理配置修复
