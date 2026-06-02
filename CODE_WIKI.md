# DataBackup - Code Wiki

> **项目名称**: DataBackup
> **项目地址**: [GitHub - XayahSuSuSu/Android-DataBackup](https://github.com/XayahSuSuSu/Android-DataBackup)
> **许可证**: GNU General Public License v3.0
> **当前稳定版**: v2.0.12 (source/) | 下一代版本: v3.0.0 (source-next/)

---

## 目录

- [1. 项目概述](#1-项目概述)
- [2. 项目整体架构](#2-项目整体架构)
- [3. 技术栈与依赖](#3-技术栈与依赖)
- [4. 模块详解](#4-模块详解)
  - [4.1 source/ — 当前稳定版 (v2.x)](#41-source--当前稳定版-v2x)
    - [4.1.1 app 模块](#411-app-模块)
    - [4.1.2 core 层模块](#412-core-层模块)
    - [4.1.3 feature 层模块](#413-feature-层模块)
    - [4.1.4 native 模块](#414-native-模块)
    - [4.1.5 build-logic 模块](#415-build-logic-模块)
  - [4.2 source-next/ — 下一代版本 (v3.x)](#42-source-next--下一代版本-v3x)
  - [4.3 dex/ — Dex 独立工具模块](#43-dex--dex-独立工具模块)
  - [4.4 build/ — 原生二进制构建](#44-build--原生二进制构建)
- [5. 模块间依赖关系](#5-模块间依赖关系)
- [6. 关键数据流](#6-关键数据流)
- [7. 项目构建与运行](#7-项目构建与运行)
- [8. CI/CD 流水线](#8-cicd-流水线)
- [9. 产品风味 (Product Flavors)](#9-产品风味-product-flavors)
- [10. 多语言支持](#10-多语言支持)

---

## 1. 项目概述

DataBackup 是一款**免费开源的 Android 数据备份应用**，基于 [speed-backup](https://github.com/YAWAsau/backup_script) 脚本发展而来。该应用需要 Root 权限，支持 Magisk / KernelSU / APatch，提供应用数据、媒体文件的完整备份与恢复功能，并支持多种云存储协议。

### 核心特性

| 特性 | 说明 |
|------|------|
| Root 支持 | 支持 Magisk、KernelSU、APatch |
| 多用户 | 支持多用户环境下的备份与恢复 |
| 云端备份 | 支持 FTP、SFTP、WebDAV、SMB 协议 |
| 数据完整性 | 100% 数据完整性保证 |
| 高性能 | 基于 zstd/tar 原生压缩，快速高效 |
| 易用性 | Material Design 3 界面，简洁直观 |

---

## 2. 项目整体架构

项目采用**多模块分层架构**，遵循 Android 官方推荐的架构模式：

```
┌─────────────────────────────────────────────────────────┐
│                     DataBackup 仓库                       │
├─────────────┬───────────────┬───────────┬───────────────┤
│  source/    │  source-next/ │   dex/    │    build/     │
│  (v2.x 稳定版)│  (v3.x 下一代)  │ (Dex工具) │ (原生二进制)   │
├─────────────┴───────────────┴───────────┴───────────────┤
│                    .github/workflows/                     │
│                    (CI/CD 流水线)                          │
└─────────────────────────────────────────────────────────┘
```

### source/ 内部架构 (v2.x 稳定版)

```
┌──────────────────────────────────────────────────────────────┐
│                        app (应用入口)                         │
│  DataBackupApplication / SplashActivity / MainActivity       │
├──────────────────────────────────────────────────────────────┤
│                    feature (功能层)                           │
│  ┌──────────┐ ┌───────┐ ┌───────┐ ┌────────────┐           │
│  │  crash   │ │ setup │ │ flavor│ │   main/*   │           │
│  │(崩溃处理) │ │(设置向导)│ │(风味) │ │(核心功能页面)│           │
│  └──────────┘ └───────┘ └───────┘ └────────────┘           │
├──────────────────────────────────────────────────────────────┤
│                     core (核心层)                             │
│  ┌──────┐┌──────┐┌────────┐┌──────────┐┌────────┐          │
│  │common││ data ││database││datastore ││ model  │          │
│  └──────┘└──────┘└────────┘└──────────┘└────────┘          │
│  ┌────────┐┌──────────┐┌────────┐┌──────┐┌────────┐       │
│  │network ││rootservice││service ││system││hiddenapi│       │
│  │        ││          ││        ││ api  ││        │       │
│  └────────┘└──────────┘└────────┘└──────┘└────────┘       │
│  ┌──────┐┌────────┐┌──────┐┌────────┐                     │
│  │  ui  ││  util  ││ work ││provider│                     │
│  └──────┘└────────┘└──────┘└────────┘                     │
├──────────────────────────────────────────────────────────────┤
│                     native (JNI 原生层)                       │
│              nativelib.cpp (C++ 高性能文件操作)                │
├──────────────────────────────────────────────────────────────┤
│                   build-logic (构建约定插件)                   │
│         13 个 ConventionPlugin 统一构建配置                    │
└──────────────────────────────────────────────────────────────┘
```

---

## 3. 技术栈与依赖

### 3.1 核心技术栈 (source/ v2.x)

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 | Kotlin | 2.0.21 |
| UI 框架 | Jetpack Compose + Material 3 | BOM 2024.12.01 |
| 依赖注入 | Hilt (Dagger) | 2.49 |
| 异步处理 | Kotlin Coroutines | 1.8.0 |
| 数据库 | Room | 2.6.1 |
| 数据存储 | DataStore Preferences | 1.1.1 |
| 网络 | Retrofit + OkHttp | 2.9.0 / 4.12.0 |
| Root | libsu | 6.0.0 |
| 导航 | Navigation Compose | 2.8.5 |
| 后台任务 | WorkManager | 2.10.0 |
| 序列化 | Gson / Kotlinx Serialization Protobuf | 2.10.1 / 1.6.0 |
| 压缩 | zip4j | 2.11.5 |
| 图片加载 | Coil Compose | 2.4.0 |
| 动画 | DotLottie Android | 0.9.2 |
| SSH | sshj | 0.38.0 |
| SMB | smbj | 0.14.0 |
| WebDAV | sardine-next | 1.0.2 |
| 加密 | BouncyCastle | 1.79 |
| 隐藏 API | Refine | 4.4.0 |
| 崩溃收集 | Firebase Crashlytics (Premium) | BOM 33.7.0 |
| 构建工具 | AGP | 8.11.1 |
| 编译 | compileSdk 35 / minSdk 24 / targetSdk 35 |

### 3.2 核心技术栈 (source-next/ v3.x)

| 类别 | 技术 | 版本 | 与 v2.x 差异 |
|------|------|------|-------------|
| 语言 | Kotlin | 2.2.0 | 升级 |
| UI 框架 | Jetpack Compose + Material 3 | BOM 2025.08.01 | 升级 |
| 依赖注入 | **Koin** | 4.1.1 | **从 Hilt 切换到 Koin** |
| 网络 | **Ktor** | 3.4.1 | **从 Retrofit 切换到 Ktor** |
| 序列化 | **Moshi** + Kotlinx Serialization JSON | 1.15.2 / 1.9.0 | **新增 Moshi** |
| 数据库 | Room | 2.7.2 | 升级 |
| 导航 | Navigation Compose | 2.9.0 | 升级 |
| 后台任务 | WorkManager | 2.10.2 | 升级 |
| 函数式 | Arrow Optics | 2.1.2 | **新增** |
| 图片加载 | Coil 3 | 3.2.0 | 升级到 Coil 3 |
| 构建工具 | AGP | 8.11.0 | 微调 |

---

## 4. 模块详解

### 4.1 source/ — 当前稳定版 (v2.x)

#### 4.1.1 app 模块

**路径**: `source/app/`
**包名**: `com.xayah.databackup`
**职责**: 应用入口模块，整合所有功能模块，配置产品风味和签名。

##### 关键类

| 类名 | 文件 | 职责 |
|------|------|------|
| `DataBackupApplication` | `DataBackupApplication.kt` | Application 类，`@HiltAndroidApp` 注解，初始化 Hilt DI 容器 |
| `SplashActivity` | `SplashActivity.kt` | 启动页 Activity，检查是否完成设置向导，决定导航到 Setup 或 Main |
| `MainActivity` | `MainActivity.kt` | 主 Activity，`@AndroidEntryPoint`，承载 Compose 导航图和底部导航栏 |

##### 产品风味配置

```
维度 "abi": arm64-v8a, armeabi-v7a, x86_64, x86
维度 "feature": foss, premium, alpha
```

- **foss**: 纯 FOSS 版本，无专有依赖 (applicationId: `com.xayah.databackup.foss`)
- **premium**: 高级版，含 Firebase Crashlytics/Analytics (applicationId: `com.xayah.databackup.premium`)
- **alpha**: 测试版，基于 foss + 额外测试功能 (applicationId: `com.xayah.databackup.alpha`)

##### 依赖关系

```
app
├── core:common, core:ui, core:model, core:database, core:data
├── core:datastore, core:util, core:work, core:hiddenapi(compileOnly)
├── core:rootservice
├── feature:crash, feature:setup
├── feature:flavor:foss, feature:flavor:premium, feature:flavor:alpha
├── feature:main:dashboard, feature:main:restore, feature:main:cloud
├── feature:main:settings, feature:main:configurations, feature:main:processing
├── feature:main:list, feature:main:details, feature:main:history, feature:main:directory
├── androidx.core.splashscreen, androidx.navigation.compose, androidx.hilt.navigation.compose
├── libsu.core, bountycastle
```

---

#### 4.1.2 core 层模块

##### core:common

**路径**: `source/core/common/`
**职责**: 通用工具类和基础定义，被所有模块依赖。

| 类/对象 | 文件 | 职责 |
|---------|------|------|
| `BuildConfigUtil` | `util/BuildConfigUtil.kt` | 获取应用构建配置信息（调试模式、版本号等） |
| `ListUtil` | `util/ListUtil.kt` | 列表工具函数（转字符串、去空项等） |

##### core:model

**路径**: `source/core/model/`
**职责**: 定义全局数据模型，被 core 和 feature 层共享。

| 类 | 文件 | 职责 |
|----|------|------|
| `PackageEntity` | `database/PackageEntity.kt` | 应用包数据库实体模型，包含包名、用户ID、数据项状态等字段 |
| `App` | `App.kt` | 外部应用模型，用于表示系统安装的应用信息 |
| `OpType` | 枚举类 | 操作类型：BACKUP / RESTORE |
| `TargetType` | 枚举类 | 目标类型：APPS / FILES |
| `CloudType` | 枚举类 | 云端类型：FTP / SFTP / WEBDAV / SMB |
| `PackageDataStates` | 数据类 | 应用数据项选择状态（APK/User/UserDe/Data/Obb/Media） |

##### core:database

**路径**: `source/core/database/`
**职责**: Room 数据库定义，包含实体、DAO 和数据库类。

| 类 | 文件 | 职责 |
|----|------|------|
| `AppDatabase` | `AppDatabase.kt` | Room 数据库主类，注册所有实体和 DAO，当前版本 7 |
| `PackageDao` | `dao/PackageDao.kt` | 应用包实体的 DAO，提供 CRUD 和查询操作 |
| `CloudDao` | `dao/CloudDao.kt` | 云端账户实体的 DAO |
| `TaskDao` | `dao/TaskDao.kt` | 备份/恢复任务实体的 DAO |
| `LabelDao` | `dao/LabelDao.kt` | 标签实体的 DAO |

数据库 Schema 版本: 1 → 7，迁移文件位于 `schemas/` 目录。

##### core:data

**路径**: `source/core/data/`
**职责**: 数据仓库层，封装数据库操作和业务逻辑。

| 类 | 文件 | 职责 |
|----|------|------|
| `AppsRepo` | `repository/AppsRepo.kt` | 应用数据仓库，处理应用相关的 CRUD 操作、数据库交互、备份/恢复状态管理 |
| `CloudRepository` | `repository/CloudRepository.kt` | 云存储数据仓库，管理云端账户的增删改查和客户端管理 |
| `TaskRepository` | `repository/TaskRepository.kt` | 任务数据仓库，管理备份/恢复任务记录 |

##### core:datastore

**路径**: `source/core/datastore/`
**职责**: 基于 DataStore Preferences 的持久化键值存储。

| 文件 | 职责 |
|------|------|
| `Boolean.kt` | Boolean 类型 DataStore 扩展函数 |
| `String.kt` | String 类型 DataStore 扩展函数 |
| `Int.kt` | Int 类型 DataStore 扩展函数 |

存储的偏好设置包括：备份目录路径、压缩格式、语言选择、是否完成设置向导等。

##### core:network

**路径**: `source/core/network/`
**职责**: 网络通信层，封装各种云存储协议的客户端实现。

| 类 | 文件 | 职责 |
|----|------|------|
| `CloudClient` | `client/CloudClient.kt` | 云客户端抽象基类，定义通用接口（连接、上传、下载、列表、删除等） |
| `FTPClient` | `client/FTPClient.kt` | FTP 协议客户端实现 |
| `SFTPClient` | `client/SFTPClient.kt` | SFTP 协议客户端实现（基于 sshj） |
| `WebDAVClient` | `client/WebDAVClient.kt` | WebDAV 协议客户端实现（基于 sardine-next） |
| `SMBClient` | `client/SMBClient.kt` | SMB 协议客户端实现（基于 smbj） |

##### core:rootservice

**路径**: `source/core/rootservice/`
**职责**: Root 权限服务层，通过 libsu 与系统 Root 服务通信。

| 类 | 文件 | 职责 |
|----|------|------|
| `RemoteRootService` | `service/RemoteRootService.kt` | 远程 Root 服务实现，通过 AIDL 提供跨进程 Root 操作 |
| `RootService` | `RootService.kt` | Root 服务代理，封装常用 Root 命令（文件操作、包管理、权限管理等） |

##### core:service

**路径**: `source/core/service/`
**职责**: 后台处理服务，执行实际的备份/恢复操作。

| 类 | 文件 | 职责 |
|----|------|------|
| `AbstractProcessingService` | `AbstractProcessingService.kt` | 处理服务抽象基类，定义备份/恢复流程框架 |
| `ProcessingServiceProxyLocalImpl` | `ProcessingServiceProxyLocalImpl.kt` | 本地处理服务实现，执行本地存储的备份/恢复 |
| `ProcessingServiceProxyCloudImpl` | `ProcessingServiceProxyCloudImpl.kt` | 云端处理服务实现，执行云端存储的备份/恢复 |

##### core:systemapi

**路径**: `source/core/systemapi/`
**职责**: Android 系统 API 封装，提供对系统服务的访问。

| 类 | 文件 | 职责 |
|----|------|------|
| `SystemProperties` | `SystemProperties.java` | 系统属性访问接口 |
| `PackageManagerWrapper` | 相关文件 | 包管理器封装，提供跨用户包查询 |
| `UserManagerWrapper` | 相关文件 | 用户管理器封装，提供多用户信息查询 |

##### core:hiddenapi

**路径**: `source/core/hiddenapi/`
**职责**: Android 隐藏 API 访问层，使用 Refine 注解将 Stub 类映射到系统隐藏类。

| 类 | 文件 | 职责 |
|----|------|------|
| `HiddenApiUtil` | `HiddenApiUtil.kt` | 隐藏 API 工具方法，绕过隐藏 API 限制 |
| `PackageManagerHidden` | Java | 包管理器隐藏 API Stub |
| `UserHandleHidden` | Java | 用户句柄隐藏 API Stub |
| `UserManagerHidden` | Java | 用户管理器隐藏 API Stub |
| `WifiManagerHidden` | Java | WiFi 管理器隐藏 API Stub |
| `ServiceManager` | Java | 系统服务管理器 |
| `SurfaceControlHidden` | Java | 显示控制隐藏 API |

##### core:ui

**路径**: `source/core/ui/`
**职责**: 共享 UI 组件库，提供可复用的 Compose 组件。

| 组件 | 文件 | 职责 |
|------|------|------|
| `Button` | `component/Button.kt` | 通用按钮组件 |
| Lottie 动画 | `assets/` | bear.lottie, loading.lottie, squirrel.lottie 动画资源 |
| 颜色/主题 | `theme/` | Material 3 主题定义 |

##### core:util

**路径**: `source/core/util/`
**职责**: 通用工具类，封装 Shell 命令和文件操作。

| 类 | 文件 | 职责 |
|----|------|------|
| `BaseUtil` | `command/BaseUtil.kt` | 基础命令工具方法 |
| `TarUtil` | `command/TarUtil.kt` | tar 压缩/解压命令封装 |
| `ZstdUtil` | `command/ZstdUtil.kt` | zstd 压缩/解压命令封装 |
| `FileUtil` | `command/FileUtil.kt` | 文件操作命令封装 |
| `PackageUtil` | `command/PackageUtil.kt` | 包管理命令封装 |

##### core:work

**路径**: `source/core/work/`
**职责**: WorkManager 后台任务定义。

| 类 | 文件 | 职责 |
|----|------|------|
| `AppsLoadWorker` | `workers/AppsLoadWorker.kt` | 应用列表加载 Worker，后台刷新应用数据 |
| `MediumLoadWorker` | `workers/MediumLoadWorker.kt` | 媒体文件加载 Worker |
| `CloudSyncWorker` | `workers/CloudSyncWorker.kt` | 云端同步 Worker |

##### core:provider

**路径**: `source/core/provider/`
**职责**: ContentProvider 定义，用于跨进程数据共享。

| 类 | 文件 | 职责 |
|----|------|------|
| `CrashProvider` | `CrashProvider.kt` | 崩溃信息 ContentProvider，提供崩溃日志给崩溃展示页面 |

---

#### 4.1.3 feature 层模块

##### feature:crash

**路径**: `source/feature/crash/`
**职责**: 全局崩溃捕获与展示。

| 类 | 文件 | 职责 |
|----|------|------|
| `CrashHandler` | `CrashHandler.kt` | 实现 `Thread.UncaughtExceptionHandler`，捕获崩溃日志写入文件，重启到崩溃展示页 |
| `MainViewModel` | `MainViewModel.kt` | 管理崩溃日志加载与展示，`loadCrashLog()` 从文件读取日志 |
| `MainActivity` | `MainActivity.kt` | 崩溃展示页 Activity，Compose 渲染崩溃信息 |

##### feature:setup

**路径**: `source/feature/setup/`
**职责**: 首次启动设置向导，引导用户完成权限授予和基础配置。

| 类 | 文件 | 职责 |
|----|------|------|
| `SetupRoutes` | `Routes.kt` | 设置向导路由常量定义 |
| `SetupUiState` | `Model.kt` | 设置向导 UI 状态模型（权限状态、根服务状态） |
| `IndexViewModel (Page1)` | `page/one/IndexViewModel.kt` | 设置第一页 ViewModel：`checkAndRequestPermissions()`, `connectRootService()` |
| `IndexViewModel (Page2)` | `page/two/IndexViewModel.kt` | 设置第二页 ViewModel：`setBackupDir()`, `finishSetup()` |
| `SetupNavHost` | `NavHost.kt` | 设置向导导航图，定义页面1→页面2的导航 |
| `MainActivity` | `MainActivity.kt` | 设置向导入口 Activity |

##### feature:main:dashboard

**路径**: `source/feature/main/dashboard/`
**职责**: 主仪表盘页面，展示备份/恢复统计和快捷入口。

| 类 | 文件 | 职责 |
|----|------|------|
| `IndexViewModel` | `IndexViewModel.kt` | 仪表盘 ViewModel，管理备份统计和页面导航 |
| `DashboardUiState` | `Model.kt` | 仪表盘 UI 状态（备份/恢复统计、存储信息） |
| `DashboardPage` | `Index.kt` | 仪表盘主页面 Composable |

##### feature:main:restore

**路径**: `source/feature/main/restore/`
**职责**: 恢复功能页面，选择恢复源和目标。

| 类 | 文件 | 职责 |
|----|------|------|
| `IndexViewModel` | `IndexViewModel.kt` | 恢复页面 ViewModel：`loadClouds()`, `selectCloud()`, `navigateToList()` |
| `RestorePage` | `Index.kt` | 恢复主页面 Composable |
| `IndexViewModel (reload)` | `reload/IndexViewModel.kt` | 重新加载恢复数据 ViewModel：`reloadBackups()` |

##### feature:main:cloud

**路径**: `source/feature/main/cloud/`
**职责**: 云端账户管理，支持添加/删除/编辑云端账户。

| 类 | 文件 | 职责 |
|----|------|------|
| `IndexViewModel` | `IndexViewModel.kt` | 云端列表 ViewModel：`deleteCloud()`, `navigateToAdd()` |
| `IndexViewModel (add)` | `add/IndexViewModel.kt` | 添加云端 ViewModel：`setCloudType()`, `testConnection()`, `saveCloud()` |
| `CloudPage` | `Index.kt` | 云端列表页面 |
| `AddCloudPage` | `add/Index.kt` | 添加云端页面 |
| `FTPSetup` | `add/FTPSetup.kt` | FTP 配置表单（主机、端口、用户名、密码、远程路径） |
| `SFTPSetup` | `add/SFTPSetup.kt` | SFTP 配置表单（含认证方式选择） |
| `WebDAVSetup` | `add/WebDAVSetup.kt` | WebDAV 配置表单（URL、用户名、密码） |
| `SMBSetup` | `add/SMBSetup.kt` | SMB 配置表单（主机、共享名、域、远程路径） |

##### feature:main:settings

**路径**: `source/feature/main/settings/`
**职责**: 设置页面集合，包含多个子页面。

| 子页面 | ViewModel 关键方法 | 功能 |
|--------|-------------------|------|
| 主设置页 | 导航到各子页面 | 设置项入口 |
| 语言设置 | `setLanguage()`, `getLanguages()` | 应用语言选择 |
| 关于页面 | `getVersion()`, `openSourceUrl()` | 版本信息、作者、翻译者、开源链接 |
| 黑名单 | `loadBlacklist()`, `removeFromBlacklist()` | 排除备份的应用/文件管理 |
| 备份设置 | 配置备份选项 | 压缩格式、备份策略等 |
| 恢复设置 | 配置恢复选项 | 恢复策略等 |

##### feature:main:configurations

**路径**: `source/feature/main/configurations/`
**职责**: 配置管理，支持备份配置的导入导出。

| 类 | 关键方法 | 职责 |
|----|---------|------|
| `IndexViewModel` | `exportConfig()`, `importConfig()`, `loadConfigs()` | 配置的导入导出和列表管理 |

##### feature:main:processing

**路径**: `source/feature/main/processing/`
**职责**: 备份/恢复执行引擎，这是最核心的功能模块。

**类继承体系**:

```
AbstractProcessingViewModel (抽象基类)
├── AbstractPackagesProcessingViewModel (应用包处理)
│   ├── BackupViewModelImpl (应用包备份)
│   └── RestoreViewModelImpl (应用包恢复)
└── AbstractMediumProcessingViewModel (媒体文件处理)
    ├── BackupViewModelImpl (媒体文件备份)
    └── RestoreViewModelImpl (媒体文件恢复)
```

| 类 | 文件 | 职责 |
|----|------|------|
| `AbstractProcessingViewModel` | `AbstractProcessingViewModel.kt` | 处理流程抽象基类，定义 `onProcessing()` 抽象方法，管理操作状态（PROCESSING/DONE/IDLE）、进度、日志 |
| `AbstractPackagesProcessingViewModel` | `AbstractPackagesProcessingViewModel.kt` | 应用包处理基类，注入 RootService、TaskRepository、ProcessingServiceProxy |
| `AbstractMediumProcessingViewModel` | `AbstractMediumProcessingViewModel.kt` | 媒体文件处理基类 |
| `BackupViewModelImpl (packages)` | `packages/backup/BackupViewModelImpl.kt` | 应用包备份实现，处理 `UpdateApps`/`SetCloudEntity`/`FinishSetup` 事件 |
| `RestoreViewModelImpl (packages)` | `packages/restore/RestoreViewModelImpl.kt` | 应用包恢复实现 |
| `BackupViewModelImpl (medium)` | `medium/backup/BackupViewModelImpl.kt` | 媒体文件备份实现 |
| `RestoreViewModelImpl (medium)` | `medium/restore/RestoreViewModelImpl.kt` | 媒体文件恢复实现 |

**处理流程**:
1. Setup 页面 → 选择存储类型（本地/云端）、选择云端账户、选择应用/文件、配置选项
2. Processing 页面 → 执行备份/恢复，展示进度和日志
3. Done 页面 → 展示完成状态和统计

##### feature:main:list

**路径**: `source/feature/main/list/`
**职责**: 应用/文件列表选择页面，支持搜索、过滤、排序、多选操作。

| 类 | 文件 | 职责 |
|----|------|------|
| `ListViewModel` | `ListViewModel.kt` | 列表主 ViewModel，管理数据初始化和页面导航：`onResume()`, `toNextPage()` |
| `ListTopBarViewModel` | `ListTopBarViewModel.kt` | 顶部栏 ViewModel：`search()`, `setUser()`（多用户切换） |
| `ListItemsViewModel` | `ListItemsViewModel.kt` | 列表项 ViewModel：`onSelectedChanged()`, `onChangeFlag()`（数据项标记切换） |
| `ListActionsViewModel` | `ListActionsViewModel.kt` | 操作栏 ViewModel：`refresh()`, `selectAll()`, `unselectAll()`, `reverseAll()`, `blockSelected()`, `deleteSelected()`, `addFiles()` |
| `ListBottomSheetViewModel` | `ListBottomSheetViewModel.kt` | 底部面板 ViewModel：`setFilters()`, `setSortByType()`, `setSortByIndex()`, `addOrRemoveLabel()`, `setDataItems()` |

**列表功能**:
- 应用列表：显示图标、名称、包名、数据标记（APK/DATA/ALL/NONE/CUSTOM）
- 文件列表：显示文件夹图标、名称、路径
- 过滤面板：来源选择、系统应用、有/无备份、已/未安装、标签、排序
- 数据项选择：APK/User/UserDe/Data/Obb/Media

##### feature:main:details

**路径**: `source/feature/main/details/`
**职责**: 应用/文件详情页面。

| 类 | 关键方法 | 职责 |
|----|---------|------|
| `DetailsViewModel` | `loadDetails()`, `updateDataItems()` | 加载详情数据和更新数据项选择 |
| `AppDetails` | Composable | 应用详情：图标、名称、包名、版本、数据项选择、备份历史 |
| `FileDetails` | Composable | 文件详情：文件名、路径、大小、备份历史 |

##### feature:main:history

**路径**: `source/feature/main/history/`
**职责**: 备份/恢复历史记录。

| 类 | 关键方法 | 职责 |
|----|---------|------|
| `HistoryViewModel` | `loadTasks()`, `deleteTask()` | 加载和删除历史任务 |
| `TaskDetailsViewModel` | `loadTaskDetails()` | 加载任务详情（执行日志、处理项目列表） |

##### feature:main:directory

**路径**: `source/feature/main/directory/`
**职责**: 备份目录管理。

| 类 | 关键方法 | 职责 |
|----|---------|------|
| `IndexViewModel` | `loadDirectory()`, `setBackupDir()` | 加载和设置备份目录 |

##### feature:flavor

**路径**: `source/feature/flavor/`
**职责**: 产品风味实现，通过 AndroidManifest.xml 和代码差异提供不同功能。

| 风味 | 路径 | 特性 |
|------|------|------|
| **foss** | `flavor/foss/` | 纯 FOSS 版本，无专有服务 |
| **premium** | `flavor/premium/` | 含 Firebase Crashlytics/Analytics，含 `google-services.json` |
| **alpha** | `flavor/alpha/` | 测试版，基于 foss + 额外测试功能 |

---

#### 4.1.4 native 模块

**路径**: `source/native/`
**职责**: JNI 原生库，提供高性能文件系统操作。

##### C++ 原生函数

| JNI 函数 | 功能 |
|----------|------|
| `NativeLib_calculateSize(path: String): Long` | 递归计算目录大小（使用 ftw 遍历） |
| `NativeLib_getUidGid(path: String): IntArray` | 获取文件的 UID 和 GID（result[0]=uid, result[1]=gid） |

##### 内嵌外部库

通过 CMake 构建的外部原生工具：
- **tar**: GNU tar 归档工具（Git 子模块: [XayahSuSuSu/tar](https://github.com/XayahSuSuSu/tar)）
- **zstd**: Zstandard 压缩工具（支持多线程、zlib/lzma/lz4 支持）

---

#### 4.1.5 build-logic 模块

**路径**: `source/build-logic/convention/`
**职责**: Gradle Convention Plugins，统一管理各模块的通用构建配置。

| Convention Plugin | 适用模块 | 配置内容 |
|------------------|---------|---------|
| `ApplicationCommonConventionPlugin` | Application 模块 | Android SDK、Kotlin、编译选项 |
| `ApplicationComposeConventionPlugin` | Application 模块 | Compose 编译器配置 |
| `ApplicationHiltConventionPlugin` | Application 模块 | Hilt DI 依赖和 KAPT |
| `ApplicationHiltWorkConventionPlugin` | Application 模块 | Hilt + WorkManager 集成 |
| `LibraryCommonConventionPlugin` | Library 模块 | Android Library 通用配置 |
| `LibraryComposeConventionPlugin` | Library 模块 | Compose 依赖 |
| `LibraryHiltConventionPlugin` | Library 模块 | Hilt DI 依赖 |
| `LibraryHiltWorkConventionPlugin` | Library 模块 | Hilt + WorkManager |
| `LibraryRoomConventionPlugin` | Library 模块 | Room 数据库依赖和 KSP |
| `LibraryProtobufConventionPlugin` | Library 模块 | Protobuf 序列化 |
| `LibraryFirebaseConventionPlugin` | Library 模块 | Firebase 依赖 |
| `LibraryTestConventionPlugin` | Library 模块 | 单元测试依赖 |
| `LibraryAndroidTestConventionPlugin` | Library 模块 | Android 仪器测试依赖 |

---

### 4.2 source-next/ — 下一代版本 (v3.x)

**路径**: `source-next/`
**职责**: 下一代重写版本，采用更现代的技术栈。

#### 与 v2.x 的主要差异

| 方面 | v2.x (source/) | v3.x (source-next/) |
|------|----------------|---------------------|
| 架构 | 多模块 (core/feature 分层) | **单模块** (所有代码在 app 模块) |
| DI 框架 | Hilt (Dagger) | **Koin** |
| 网络库 | Retrofit + OkHttp | **Ktor** |
| 序列化 | Gson + Protobuf | **Moshi + Kotlinx Serialization JSON** |
| 函数式编程 | 无 | **Arrow Optics** |
| 图片加载 | Coil 2 | **Coil 3** |
| 构建约定 | 13 个 Convention Plugin | **简化构建配置** |

#### 模块结构

```
source-next/
├── app/          # 主应用模块（所有代码）
├── hiddenapi/    # 隐藏 API Stub（与 v2.x 类似）
├── native/       # JNI 原生库（增强版）
└── .codex/       # AI 辅助开发技能定义
```

#### hiddenapi 模块 (source-next/)

与 v2.x 类似但更完整，使用 `@RefineAs` 注解映射隐藏 API：

| 类 | 职责 |
|----|------|
| `PackageManagerHidden` | 包管理器隐藏 API |
| `UserHandleHidden` | 用户句柄隐藏 API |
| `UserManagerHidden` | 用户管理器隐藏 API |
| `WifiManagerHidden` | WiFi 管理器隐藏 API |
| `AppOpsManagerHidden` | 应用操作管理器隐藏 API |
| `SurfaceControlHidden` | 显示控制隐藏 API |
| `ActivityThread` | 应用线程隐藏 API |
| `ActivityManagerHidden` | 活动管理器隐藏 API |
| `ServiceManager` | 系统服务管理器 |
| `ContextImpl` | 上下文实现隐藏 API |

#### native 模块 (source-next/)

增强版 JNI 原生库：

| JNI 函数 | 功能 | 与 v2.x 差异 |
|----------|------|-------------|
| `NativeLib_calculateTreeSize(path: String): Long` | 递归计算目录树大小（使用 FTS） | **改进**: 使用 `st_blocks * 512` 计算实际磁盘占用 |
| `NativeLib_getUidGid(path: String): IntArray` | 获取文件 UID/GID | 相同 |

外部库：
- **tar**: 增强版 tar 归档工具（tar-wrapper.cpp 封装）
- **zstd**: Zstandard 压缩（通过 zstd-jni 子模块集成）

---

### 4.3 dex/ — Dex 独立工具模块

**路径**: `dex/`
**职责**: 独立的 DEX 工具集，以 Shell 命令方式运行，通过 Root 权限执行需要系统级 API 的操作。

这是一个独立的 Gradle 项目，编译后生成 DEX 文件，被打包进 bin.zip 随应用分发，在 Root 环境下通过 `app_process` 执行。

#### 核心工具类

| 类 | 文件 | 职责 |
|----|------|------|
| `BaseUtil` | `com/xayah/dex/BaseUtil.java` | Shell 命令解析基类，提供 `getNextOption()`, `getNextArg()`, `getNextArgRequired()` |
| `HiddenApiHelper` | `com/xayah/dex/HiddenApiHelper.java` | 隐藏 API 辅助：`getContext()` 获取系统上下文，`getContentProviderExternal()` 获取 ContentProvider |
| `FakeContext` | `com/xayah/dex/FakeContext.java` | 伪造 Context 实现，用于在 Shell 环境中模拟应用上下文 |
| `SsaidUtil` | `com/xayah/dex/SsaidUtil.java` | SSAID（安全应用标识）管理：`get` 获取、`set` 设置应用的 SSAID |
| `NotificationUtil` | `com/xayah/dex/NotificationUtil.java` | Shell 环境通知发送：`notify` 命令发送通知（支持标题、进度条） |
| `NetworkUtil` | `com/xayah/dex/NetworkUtil.java` | WiFi 网络管理：`getNetworks` 获取、`saveNetworks` 保存为 JSON、`restoreNetworks` 从 JSON 恢复 |
| `CCHelper` | `com/xayah/dex/CCHelper.java` | 内容捕获辅助工具 |
| `CCUtil` | `com/xayah/dex/CCUtil.java` | 内容捕获工具 |
| `HttpUtil` | `com/xayah/dex/HttpUtil.java` | HTTP 请求工具 |
| `HiddenApiUtil` | `com/xayah/dex/HiddenApiUtil.java` | 隐藏 API 反射工具 |

#### AOSP 内部类

dex 模块还包含从 AOSP 源码中提取的内部类，用于在 Shell 环境中直接访问系统功能：

| 类 | 路径 | 职责 |
|----|------|------|
| `SettingsState` | `com/android/providers/settings/` | 系统设置状态管理（API 26+） |
| `SettingsStateApi26` | 同上 | API 26 版本实现 |
| `SettingsStateApi31` | 同上 | API 31 版本实现（Binary XML） |
| `BinaryXmlSerializer/Parser` | `com/android/internal/util/` | 二进制 XML 序列化/解析 |
| `FastXmlSerializer` | 同上 | 快速 XML 序列化 |
| `DisplayControlHidden` | `com/android/server/display/` | 显示控制隐藏 API |
| `KXmlParser/Serializer` | `com/android/org/kxml2/io/` | KXML 解析器 |
| `SystemProperties` | `android/os/` | 系统属性访问 |

#### dex/hiddenapi 模块

定义隐藏 API 的 AIDL 接口和 Stub 类：

| 类 | 职责 |
|----|------|
| `IContentProvider` | ContentProvider AIDL 接口 |
| `INotificationManager` | 通知管理器 AIDL 接口 |
| `ParceledListSlice` | 跨进程列表传输 |
| `PackageManagerHidden` | 包管理器隐藏 API |
| `UserHandleHidden` | 用户句柄隐藏 API |
| `WifiManagerHidden` | WiFi 管理器隐藏 API |
| `ServiceManager` | 系统服务管理器 |
| `SurfaceControlHidden` | 显示控制隐藏 API |
| `AppOpsManagerHidden` | 应用操作管理器隐藏 API |

---

### 4.4 build/ — 原生二进制构建

**路径**: `build/`
**职责**: 构建嵌入式原生二进制工具（zstd、busybox、tar），打包为 bin.zip 供应用使用。

#### 构建脚本: `build_bin.sh`

**用法**: `bash build_bin.sh <type> <abis>`

- `type`: `built-in` 或 `all`
- `abis`: `all` 或逗号分隔的 ABI 列表 (armeabi-v7a, arm64-v8a, x86, x86_64)

**构建组件**:

| 组件 | 版本 | 用途 |
|------|------|------|
| zstd | 1.5.6 | 高性能压缩/解压，支持多线程、zlib/lzma/lz4 |
| busybox | 1.36.1 | Unix 工具集，提供 Shell 环境下的常用命令 |
| tar | 自定义 fork | GNU tar 归档工具 |
| zlib | 1.3.1 | 压缩库（zstd 依赖） |
| xz/lzma | 5.6.2 | LZMA 压缩库（zstd 依赖） |
| lz4 | 1.9.4 | LZ4 压缩库（zstd 依赖） |
| selinux | 自定义分支 | SELinux 工具（busybox 依赖） |
| pcre | Android 分支 | 正则表达式库（busybox 依赖） |

**输出**: `built_in/<abi>/bin.zip`，包含 zstd、busybox、tar 三个可执行文件和版本号文件。

---

## 5. 模块间依赖关系

### source/ v2.x 模块依赖图

```
app
 ├── core:common ─────────────────────────────────────┐
 ├── core:ui ─────────────────────────────────────────┤
 ├── core:model ──────────────────────────────────────┤
 ├── core:database ─── core:model ────────────────────┤
 ├── core:data ─── core:database, core:model ─────────┤
 │   └── core:network ─── core:model ─────────────────┤
 ├── core:datastore ──────────────────────────────────┤
 ├── core:util ─── core:common, core:model ───────────┤
 ├── core:work ─── core:data, core:util ──────────────┤
 ├── core:hiddenapi (compileOnly) ────────────────────┤
 ├── core:rootservice ─── core:hiddenapi ─────────────┤
 │   └── core:systemapi ─── core:hiddenapi ───────────┤
 ├── core:service ─── core:rootservice, core:data ────┤
 ├── core:provider ───────────────────────────────────┤
 │                                                     │
 ├── feature:crash ─── core:ui ───────────────────────┤
 ├── feature:setup ─── core:ui, core:rootservice ─────┤
 ├── feature:flavor:* ────────────────────────────────┤
 │                                                     │
 ├── feature:main:dashboard ─── core:data, core:ui ───┤
 ├── feature:main:restore ─── core:data, core:ui ─────┤
 ├── feature:main:cloud ─── core:data, core:network ──┤
 ├── feature:main:settings ─── core:data, core:ui ────┤
 ├── feature:main:configurations ─── core:data ───────┤
 ├── feature:main:processing ─── core:service ────────┤
 ├── feature:main:list ─── core:data, core:ui ────────┤
 ├── feature:main:details ─── core:data, core:ui ─────┤
 ├── feature:main:history ─── core:data, core:ui ─────┤
 └── feature:main:directory ─── core:data, core:ui ───┘
```

### 依赖层次总结

```
Layer 1 (最底层): core:common, core:model, core:hiddenapi
Layer 2: core:database, core:datastore, core:ui, core:network
Layer 3: core:data, core:util, core:rootservice, core:systemapi
Layer 4: core:service, core:work, core:provider
Layer 5: feature:* (所有功能模块)
Layer 6: app (应用入口)
```

---

## 6. 关键数据流

### 6.1 备份流程

```
用户操作
  │
  ▼
DashboardPage → 选择"备份"
  │
  ▼
ListPage → 选择应用/文件
  │  ListViewModel: onResume() → 刷新列表
  │  ListItemsViewModel: onSelectedChanged() → 切换选中
  │  ListActionsViewModel: selectAll() / filter() → 批量操作
  │
  ▼
ProcessingSetupPage → 配置备份选项
  │  BackupViewModelImpl: UpdateApps → 加载已激活应用
  │  BackupViewModelImpl: SetCloudEntity → 设置云端实体
  │  BackupViewModelImpl: FinishSetup → 完成设置
  │
  ▼
ProcessingPage → 执行备份
  │  AbstractPackagesProcessingViewModel: onProcessing()
  │    ├── ProcessingServiceProxyLocalImpl / CloudImpl
  │    │     ├── RootService → 执行 Root 命令
  │    │     ├── TarUtil → tar 归档
  │    │     ├── ZstdUtil → zstd 压缩
  │    │     └── CloudClient → 云端上传 (如选择云端)
  │    └── TaskRepository → 记录任务
  │
  ▼
DonePage → 展示结果
```

### 6.2 恢复流程

```
用户操作
  │
  ▼
DashboardPage → 选择"恢复"
  │
  ▼
RestorePage → 选择恢复源 (本地/云端)
  │  IndexViewModel: loadClouds() → 加载云端账户
  │
  ▼
ListPage → 选择要恢复的应用/文件
  │
  ▼
ProcessingSetupPage → 配置恢复选项
  │
  ▼
ProcessingPage → 执行恢复
  │  AbstractPackagesProcessingViewModel: onProcessing()
  │    ├── ProcessingServiceProxy → 执行恢复操作
  │    └── TaskRepository → 记录任务
  │
  ▼
DonePage → 展示结果
```

### 6.3 云端同步流程

```
CloudClient (抽象)
  ├── FTPClient  → FTP 服务器
  ├── SFTPClient → SFTP 服务器 (sshj)
  ├── WebDAVClient → WebDAV 服务器 (sardine-next)
  └── SMBClient → SMB 共享 (smbj)

操作: connect() → upload() / download() → list() → disconnect()
```

---

## 7. 项目构建与运行

### 7.1 环境要求

| 要求 | 版本 |
|------|------|
| JDK | 17 |
| Android SDK | compileSdk 35 |
| NDK | r25c (构建 bin.zip 时) |
| Gradle | 8.x (通过 wrapper) |
| Kotlin | 2.0.21 (v2.x) / 2.2.0 (v3.x) |

### 7.2 构建命令

```bash
# 进入 source 目录
cd source/

# 构建 Debug 版本
./gradlew assembleDebug

# 构建 Release 版本 (需签名配置)
./gradlew assembleRelease

# 构建指定风味的 APK
./gradlew assembleArm64-v8aFossRelease
./gradlew assembleArm64-v8aPremiumRelease
./gradlew assembleArm64-v8aAlphaRelease

# 安装到设备
./gradlew :app:installDebug

# 运行 Lint 检查
./gradlew lint

# 运行单元测试
./gradlew test

# 运行仪器测试
./gradlew connectedAndroidTest

# 检查 build-logic
./gradlew check -p build-logic
```

### 7.3 构建原生二进制

```bash
# 构建所有 ABI 的 bin.zip
bash build/build_bin.sh all all

# 构建指定 ABI
bash build/build_bin.sh built-in arm64-v8a,x86_64
```

### 7.4 source-next/ 构建

```bash
cd source-next/

# 构建 Debug 版本
./gradlew assembleDebug

# 安装到设备
./gradlew :app:installDebug
```

---

## 8. CI/CD 流水线

### 8.1 Build 工作流 (`.github/workflows/build.yaml`)

- **触发**: 手动触发 (`workflow_dispatch`)
- **参数**: Alpha / Premium / Foss 构建开关
- **环境**: ubuntu-latest, JDK 17
- **步骤**: Checkout → 验证 Gradle Wrapper → 设置 JDK → 构建 APK → 上传 Artifact
- **超时**: 60 分钟
- **输出**: APK 文件

### 8.2 Lint 工作流 (`.github/workflows/lint.yaml`)

- **触发**: push/PR 到 `source/**` 路径
- **环境**: ubuntu-latest, JDK 17
- **步骤**: Checkout → 验证 Wrapper → 运行 `./gradlew lint test` → 上传 Lint 报告

### 8.3 Bin 工作流 (`.github/workflows/bin.yaml`)

- **触发**: push/PR 到 `build/**` 路径，或手动触发
- **环境**: F-Droid 构建服务器 Docker 镜像 (`registry.gitlab.com/fdroid/fdroidserver:buildserver-bookworm`)
- **步骤**: Checkout (含子模块) → 执行 `build_bin.sh` → 上传 bin.zip

### 8.4 Release 工作流 (`.github/workflows/release-please.yml`)

- **触发**: 手动触发
- **工具**: `googleapis/release-please-action@v4`
- **功能**: 自动生成 Release Notes PR，跳过 GitHub Release 创建

---

## 9. 产品风味 (Product Flavors)

### 维度 1: ABI (处理器架构)

| 风味 | ABI | versionCode 偏移 |
|------|-----|------------------|
| arm64-v8a | arm64-v8a | +4 |
| armeabi-v7a | armeabi-v7a | +3 |
| x86_64 | x86_64 | +2 |
| x86 | x86 | +1 |

### 维度 2: Feature (功能特性)

| 风味 | Application ID | 特性 |
|------|---------------|------|
| foss | com.xayah.databackup.foss | 纯 FOSS，无专有依赖，可上 F-Droid/IzzyOnDroid |
| premium | com.xayah.databackup.premium | 含 Firebase Crashlytics/Analytics |
| alpha | com.xayah.databackup.alpha | 测试版，独立 versionCode，基于 foss |

### APK 命名格式

```
DataBackup-{versionName}-{abi}-{feature}-{buildType}.apk
```

示例: `DataBackup-2.0.12-arm64-v8a-foss-release.apk`

---

## 10. 多语言支持

项目支持 30+ 种语言，翻译通过 [Weblate](https://hosted.weblate.org/engage/databackup/) 平台协作完成。

已支持的语言包括（但不限于）:
- 中文（简体 zh-CN、繁体 zh-TW、zh-HK）
- 英语 (en)
- 阿拉伯语 (ar)、孟加拉语 (bn)、波斯语 (fa)
- 欧洲语言: 德语 (de)、法语 (fr)、西班牙语 (es)、意大利语 (it)、葡萄牙语 (pt/pt-BR)、荷兰语 (nl)、瑞典语 (sv)、芬兰语 (fi)
- 东欧语言: 俄语 (ru)、乌克兰语 (uk)、波兰语 (pl)、捷克语 (cs)、匈牙利语 (hu)、罗马尼亚语 (ro)
- 亚洲语言: 韩语 (ko)、越南语 (vi)、泰米尔语 (ta)、马来亚拉姆语 (ml)
- 其他: 土耳其语 (tr)、克罗地亚语 (hr)、塞尔维亚语 (sr)、乌兹别克语 (uz)

---

## 附录: Git 子模块

| 路径 | 仓库 | 用途 |
|------|------|------|
| `source/native/src/main/jni/external/tar/tar` | [XayahSuSuSu/tar](https://github.com/XayahSuSuSu/tar) | v2.x tar 归档工具源码 |
| `source-next/native/src/main/jni/external/tar/tar` | [XayahSuSuSu/tar](https://github.com/XayahSuSuSu/tar) | v3.x tar 归档工具源码 |
| `source-next/native/src/main/jni/external/zstd/zstd-jni` | [luben/zstd-jni](https://github.com/luben/zstd-jni) | v3.x zstd JNI 绑定 |
