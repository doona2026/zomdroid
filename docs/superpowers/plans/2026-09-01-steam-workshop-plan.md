# Zomdroid Steam Workshop 集成实现计划

关联设计：`docs/superpowers/specs/2026-09-01-steam-workshop-design.md`

## 执行约束

- 所有生产代码只修改 `zomdroid/`，不修改源项目。
- 不引入 Compose、独立 Activity 或源项目的实验室代理。
- 每个任务完成后先运行该任务的窄验证，再进入下一任务。
- 先添加/迁移测试，再添加对应实现；测试不能依赖真实 Steam 网络。
- 真实 Steam 下载仅在单元和 MockWebServer 测试通过后进行。
- 保留现有 `SteamGameDownloader`、手动 ZIP 导入和 InstallerService 行为，除非某项接入明确需要兼容修改。

## 阶段 1 执行边界

本次请求中的“阶段 1”包含下列全部任务：先完成任务 1–3 的基线、依赖和 proto 前置工作，再完成任务 4–6 的官方核心下载接入。任务 7 及之后属于后续交付阶段，本次不得实现。

## 基线与依赖

### 1. 建立基线

文件：无生产文件修改。

动作：在 `zomdroid` 目录运行 `./gradlew.bat :app:assembleDebug`，记录当前构建结果、Git 状态和现有 APK 输出路径。

验证：基线构建成功；若失败，先记录并将失败归因与 Workshop 改动隔离。

### 2. 接入 Kotlin/Serialization/Coroutines/OkHttp/Protobuf 编译能力

文件：

- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`

动作：加入 Kotlin Android 和 Kotlin Serialization 插件；增加 Coroutines Android、Serialization JSON、OkHttp BOM、Okio、Protobuf 编译插件/运行时等最小依赖；保持 Java/Kotlin target 11。将源项目所需版本与现有 JavaSteam、protobuf-java、zstd-jni 做依赖约束，避免替换现有游戏下载依赖。

验证：运行 `./gradlew.bat :app:dependencies` 和 `./gradlew.bat :app:dependencyInsight --dependency protobuf --configuration debugRuntimeClasspath`；确认无版本冲突、无 Compose 依赖意外引入。

### 3. 迁移许可证归属和 proto 定义

文件：

- `app/src/main/proto/content_manifest.proto`
- `app/src/main/proto/steam_messages.proto`
- `NOTICE.md`
- `THIRD_PARTY_LICENSES/Apache-2.0-WorkshopAndroidDownloader.txt`
- `NOTICE.md`

动作：迁移源项目 proto，保留 Apache 2.0 文件头/变更说明；将 `.proto` 的 `java_package` 调整为 `com.zomdroid.workshop.steam.proto`；新增完整 Apache 2.0 文本到 `THIRD_PARTY_LICENSES/Apache-2.0-WorkshopAndroidDownloader.txt`；NOTICE 增加 WorkshopAndroidDownloader/Apache 2.0 归属、迁移文件范围和源项目链接。不得修改根 `LICENSE` 的 MIT 许可主体。

验证：运行 `./gradlew.bat :app:generateDebugProto`；检查生成类可被 Kotlin 编译任务引用，NOTICE 不包含错误的许可证声明。

## 阶段 1：官方核心下载

### 4. 迁移 Steam 协议层

目录：`app/src/main/java/com/zomdroid/workshop/steam/protocol/`

文件：从源项目迁移并改包名：

- `InputStreamCompat.kt`
- `Models.kt`
- `OkHttpSteamCmSession.kt`
- `OkHttpTimeouts.kt`
- `SteamContentClient.kt`
- `SteamDirectoryClient.kt`
- `SteamMachineId.kt`
- `SteamPacketCodec.kt`
- `SteamPublishedFileClient.kt`

动作：保留 CM WebSocket、目录服务、Content 服务、manifest request code、CDN token、PublishedFile 查询和 refresh-token 会话接口；将所有 `top.apricityx.workshop.steam.*` 包声明和导入改为 `com.zomdroid.workshop.steam.*`，将 proto 导入改为 `com.zomdroid.workshop.steam.proto.*`；修正 Android API 30/Java 11 不兼容调用；禁止日志输出凭据和完整 Cookie。

测试文件：

- `app/src/test/java/com/zomdroid/workshop/steam/protocol/SteamPacketCodecTest.kt`
- `app/src/test/java/com/zomdroid/workshop/steam/protocol/SteamDirectoryClientTest.kt`

验证：先运行新增协议单元测试（预期初始为红），完成迁移后运行 `./gradlew.bat :app:testDebugUnitTest --tests 'com.zomdroid.workshop.steam.protocol.*'`。

### 5. 迁移 Workshop 核心模型、manifest/chunk 和官方下载器

目录：`app/src/main/java/com/zomdroid/workshop/core/`

文件：

- `Models.kt`
- `DepotManifest.kt`
- `WorkshopChecksum.kt`
- `WorkshopChunkProcessor.kt`
- `WorkshopDownloadEngine.kt`
- `WorkshopFileIntegrity.kt`
- `WorkshopOutputPathManager.kt`
- `DirectWorkshopDownloader.kt`
- `UgcWorkshopDownloader.kt`
- `PublishedFileResolver.kt`
- `SteamCdnTransport.kt`
- `DownloadFailureMessages.kt`

动作：将源项目 `file_url` 与 UGC manifest 两条路径统一为 `WorkshopDownloadRequest`/`DownloadEvent`；将所有 `top.apricityx.workshop.workshop.*` 包声明和导入改为 `com.zomdroid.workshop.core.*`，同时更新对 `com.zomdroid.workshop.steam.*` 的引用；将 staging、`.part`、断点、文件完整性校验、并发 chunk 下载和安全相对路径校验接入应用私有目录；把 AppID 固定策略放在上层，不写死在通用核心。

测试文件：

- `app/src/test/java/com/zomdroid/workshop/core/DepotManifestParserTest.kt`
- `app/src/test/java/com/zomdroid/workshop/core/WorkshopChunkProcessorTest.kt`
- `app/src/test/java/com/zomdroid/workshop/core/WorkshopFileIntegrityTest.kt`
- `app/src/test/java/com/zomdroid/workshop/core/WorkshopOutputPathManagerTest.kt`
- `app/src/test/java/com/zomdroid/workshop/core/DirectWorkshopDownloaderTest.kt`
- `app/src/test/java/com/zomdroid/workshop/core/WorkshopDownloadEngineTest.kt`
- `app/src/test/java/com/zomdroid/workshop/core/PublishedFileResolverTest.kt`

验证：使用 MockWebServer 覆盖 HTTP 200/206、Range 被忽略、manifest 失败、chunk 重试、重复文件、非法路径、校验失败和成功恢复；运行 `./gradlew.bat :app:testDebugUnitTest --tests 'com.zomdroid.workshop.core.*'`。

### 6. 建立 Java 兼容门面和应用网络容器

文件：

- `app/src/main/java/com/zomdroid/workshop/WorkshopRuntime.kt`
- `app/src/main/java/com/zomdroid/workshop/WorkshopJavaFacade.kt`
- `app/src/main/java/com/zomdroid/workshop/WorkshopPaths.kt`
- `app/src/main/java/com/zomdroid/workshop/WorkshopAppContract.kt`

动作：集中创建 OkHttp、Json、目录客户端、resolver、download engine；提供 Java 可调用的启动/查询/取消接口；定义 AppID `108600`、应用私有 staging、`Downloads/zomdroid/workshop` 完成目录和第三方备用通道标识。不要在 Fragment 中直接创建协议对象。

验证：添加 `WorkshopJavaFacadeTest.kt` 或最小 Kotlin/Java 编译调用样例，运行 `./gradlew.bat :app:testDebugUnitTest` 和 `./gradlew.bat :app:compileDebugJavaWithJavac`。

## 阶段 2：匿名下载、统一文件和安装器接入

### 7. 替换现有 Mod 下载入口为官方 Workshop 下载

文件：

- `app/src/main/java/com/zomdroid/steam/SteamModDownloader.java`
- `app/src/main/java/com/zomdroid/fragments/SteamDownloadFragment.java`
- `app/src/main/res/layout/fragment_steam_download.xml`
- `app/src/main/res/values/strings.xml`

动作：保留现有游戏下载标签；将 Mod 标签接到 Workshop Java 门面，支持一个或多个 Zomboid Workshop ID；匿名解析、下载、完成归档和进度回调统一走新核心；不破坏现有游戏登录下载。

测试/验证：新增 `SteamModDownloadAdapterTest.kt` 覆盖 ID 解析、空输入和任务创建；运行相关单元测试并执行 `./gradlew.bat :app:assembleDebug`。

### 8. 统一完成文件引用并接入 InstallerService

文件：

- `app/src/main/java/com/zomdroid/WorkshopFileAccess.java` 或 `app/src/main/java/com/zomdroid/workshop/WorkshopFileAccess.kt`
- `app/src/main/java/com/zomdroid/InstallerService.java`
- `app/src/main/java/com/zomdroid/fragments/InstallModFragment.java`
- `app/src/main/res/xml/file_paths.xml`

动作：为下载库文件提供安全的本地/Content URI 引用；本阶段不修改 `InstallModFragment` 或移除现有 `ggntw.com` 调用。待任务 17 的备用客户端和统一下载库入口完成后，才切换旧入口，并在切换前后验证无功能回归；将下载完成文件填入现有 Mod ZIP 导入流程；从 Workshop 页面传递可选的目标实例名和 Build 版本。

验证：用临时 ZIP 测试现有 `InstallerService` 的智能导入、非法 ZIP 路径、无实例和覆盖行为；运行 `./gradlew.bat :app:compileDebugJavaWithJavac` 和现有安装器相关测试（若无测试则记录真机验证项）。

## 阶段 3：持久化下载中心和后台服务

### 9. 建立持久化任务模型与 Store

文件：

- `app/src/main/java/com/zomdroid/workshop/download/DownloadCenterModels.kt`
- `app/src/main/java/com/zomdroid/workshop/download/DownloadCenterStore.kt`
- `app/src/main/java/com/zomdroid/workshop/download/DownloadCenterManager.kt`
- `app/src/test/java/com/zomdroid/workshop/download/DownloadCenterStoreTest.kt`
- `app/src/test/java/com/zomdroid/workshop/download/DownloadCenterStateTest.kt`

动作：迁移源项目任务状态、进度、日志、错误、文件和账号绑定模型；使用应用私有 JSON/原子替换持久化；实现排队、暂停、继续、重试、取消、删除、恢复和并发任务上限；恢复时将 Running 任务转为 Queued 并继续 staging。

验证：先写 Store round-trip、损坏 JSON、暂停/恢复、重试和重启恢复测试；运行 `./gradlew.bat :app:testDebugUnitTest --tests 'com.zomdroid.workshop.download.*'`。

### 10. 建立 Workshop 前台服务和通知

文件：

- `app/src/main/java/com/zomdroid/workshop/download/WorkshopDownloadForegroundService.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/drawable/ic_workshop_download.xml`
- `app/src/main/res/values/strings.xml`

动作：用持久化 DownloadCenterManager 驱动服务；服务启动/恢复队列、显示当前任务进度和失败状态、无活动任务时停止；适配 Android 11–15 的 foreground service/dataSync 和通知权限；保留 `DownloadKeepAliveService` 给现有游戏下载，不让两个服务共享状态。

验证：运行 `./gradlew.bat :app:assembleDebug`；在 API 30 和 API 35 模拟器/真机验证切后台、通知、取消和进程重启恢复。

### 11. 下载中心 UI 和安装动作

文件：

- `app/src/main/java/com/zomdroid/fragments/WorkshopDownloadCenterFragment.java`
- `app/src/main/res/layout/fragment_workshop_download_center.xml`
- `app/src/main/res/layout/item_workshop_download_task.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/navigation/nav_graph.xml`

动作：显示活动任务和历史任务；提供暂停/继续/重试/取消/删除、日志和第三方备用重试按钮；备用按钮先显示第三方风险确认，再调用 `ggntw.com`；完成项支持选择 Zomboid 实例并进入 InstallerService。

验证：添加 Fragment/adapter 状态测试；用 Espresso 在不联网场景验证空态、失败态、恢复态和第三方确认不自动触发；运行 `./gradlew.bat :app:testDebugUnitTest` 和对应 `connectedDebugAndroidTest`（设备可用时）。

## 阶段 4：Workshop 浏览、详情和依赖

### 12. 迁移数据模型与解析器

文件：

- `app/src/main/java/com/zomdroid/workshop/data/CatalogModels.kt`
- `app/src/main/java/com/zomdroid/workshop/data/SteamHtmlDecoder.kt`
- `app/src/main/java/com/zomdroid/workshop/data/WorkshopBrowseRepository.kt`
- `app/src/main/java/com/zomdroid/workshop/data/WorkshopDetailRepository.kt`
- `app/src/main/java/com/zomdroid/workshop/data/SteamGameRepository.kt`
- `app/src/test/java/com/zomdroid/workshop/data/SteamGameParserTest.kt`
- `app/src/test/java/com/zomdroid/workshop/data/WorkshopBrowseParserTest.kt`
- `app/src/test/java/com/zomdroid/workshop/data/WorkshopDetailParserTest.kt`

动作：迁移公开 browse/Store/API 请求和 HTML/SSR JSON fallback；固定 Zomboid AppID，同时保留模型 AppID；实现分页、排序、搜索、详情、依赖、更新日志、评论和 Steam URL；加入内存/磁盘短缓存及清晰的解析失败状态。

验证：使用固定 HTML/JSON fixture 测试旧版 workshopItem 和 SSR renderContext 两种结构、HTML 实体、非法数据、空页和分页；运行 `./gradlew.bat :app:testDebugUnitTest --tests 'com.zomdroid.workshop.data.*'`。

### 13. Workshop Fragment/详情/列表 UI

文件：

- `app/src/main/java/com/zomdroid/fragments/WorkshopFragment.java`
- `app/src/main/java/com/zomdroid/fragments/WorkshopDetailFragment.java`
- `app/src/main/res/layout/fragment_workshop.xml`
- `app/src/main/res/layout/fragment_workshop_detail.xml`
- `app/src/main/res/layout/item_workshop.xml`
- `app/src/main/res/layout/item_workshop_dependency.xml`
- `app/src/main/res/layout/item_workshop_comment.xml`
- `app/src/main/res/drawable/ic_workshop.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/navigation/nav_graph.xml`
- `app/src/main/res/menu/menu_nav.xml`

动作：实现 Zomboid Workshop 搜索、排序、分页、列表和详情；显示封面、作者、描述、标签、大小、更新时间、更新日志、评论和依赖；支持 Steam 页面跳转、下载、依赖批量入队；从 InstallModFragment 传递目标实例。

验证：用 fake repositories 做 UI 状态测试；Espresso 验证搜索、点击详情、分页、依赖入队、下载按钮和目标实例传递；运行 `./gradlew.bat :app:assembleDebug` 和可用设备上的 `connectedDebugAndroidTest`。

## 阶段 5：Steam 认证和多账号

### 14. 迁移认证协议与安全账号仓库

文件：

- `app/src/main/java/com/zomdroid/workshop/steam/protocol/SteamAuthenticationClient.kt`
- `app/src/main/java/com/zomdroid/workshop/auth/SteamAuthRepository.kt`
- `app/src/main/java/com/zomdroid/workshop/auth/SteamAccountModels.kt`
- `app/src/main/java/com/zomdroid/workshop/auth/SecurePreferences.kt`
- `app/src/test/java/com/zomdroid/workshop/auth/SteamAuthRepositoryTest.kt`
- `app/src/test/java/com/zomdroid/workshop/auth/SteamTokenProjectionTest.kt`

动作：迁移 RSA 密码加密、BeginAuth/Poll、Guard code/device confirmation、access token 生成/刷新/撤销、多账号、账号切换和网页登录 Cookie 投影；优先使用 AndroidX Security Crypto；降级存储也必须只保存加密 token；日志脱敏。

验证：Mock CM service 测试 None/EmailCode/DeviceCode/DeviceConfirmation/错误重试；测试 token refresh、失效标记、账号删除和 Cookie 不含密码；运行 `./gradlew.bat :app:testDebugUnitTest --tests 'com.zomdroid.workshop.auth.*'`。

### 15. 登录/账号 UI 与受限下载绑定

文件：

- `app/src/main/java/com/zomdroid/fragments/WorkshopAccountFragment.java`
- `app/src/main/res/layout/fragment_workshop_account.xml`
- `app/src/main/res/layout/dialog_steam_guard.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/navigation/nav_graph.xml`

动作：加入账号、切换账号、移除账号、重新认证；登录对话框处理验证码和手机确认等待；下载入队时记录当前账号，匿名任务保持匿名；受限下载失败时提供登录/切换账号入口。

验证：fake auth repository + Espresso 验证登录状态机、验证码提交、取消、切换和敏感字段不显示；运行 `./gradlew.bat :app:assembleDebug`。

## 阶段 6：Mod 库、版本和第三方备用通道

### 16. Mod 库和版本记录

文件：

- `app/src/main/java/com/zomdroid/workshop/library/ModLibraryModels.kt`
- `app/src/main/java/com/zomdroid/workshop/library/ModLibraryStore.kt`
- `app/src/main/java/com/zomdroid/workshop/library/ModLibraryRepository.kt`
- `app/src/main/java/com/zomdroid/workshop/library/ModVersioning.kt`
- `app/src/main/java/com/zomdroid/workshop/library/ModUpdateChecker.kt`
- `app/src/main/java/com/zomdroid/fragments/WorkshopModLibraryFragment.java`
- `app/src/main/res/layout/fragment_workshop_mod_library.xml`
- `app/src/test/java/com/zomdroid/workshop/library/ModVersioningTest.kt`
- `app/src/test/java/com/zomdroid/workshop/library/ModLibraryStoreTest.kt`

动作：按 AppID/Workshop ID/更新时间保存版本；缓存标题、描述、封面和已完成文件；实现手动/低频更新检查，不自动下载；提供多实例安装、旧版本清理和文件分享。

验证：覆盖同版本、更新时间倒退、缺失 metadata、旧格式迁移、重复文件和删除历史；运行 `./gradlew.bat :app:testDebugUnitTest --tests 'com.zomdroid.workshop.library.*'`。

### 17. 覆盖/备份和第三方备用重试

文件：

- `app/src/main/java/com/zomdroid/workshop/install/WorkshopInstallCoordinator.kt`
- `app/src/main/java/com/zomdroid/workshop/thirdparty/GgntwFallbackClient.kt`
- `app/src/main/java/com/zomdroid/fragments/WorkshopDownloadCenterFragment.java`
- `app/src/main/java/com/zomdroid/InstallerService.java`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/zomdroid/workshop/thirdparty/GgntwFallbackClientTest.kt`

动作：官方任务失败后只呈现用户确认按钮；调用第三方前显示风险说明；解析响应 URL 时禁止非 HTTPS/非法主机和路径穿越；结果下载到 staging 并转换为同一 Mod 库记录；安装前检测目标目录，按用户选择备份并原子替换。

验证：MockWebServer 覆盖第三方纯文本/JSON/错误响应、非 HTTPS、超时和取消；测试未确认时零网络调用；测试安装失败时原目录仍存在；运行相关单元测试。

## 阶段 7：完整验证与真机回归

### 18. 全量构建、单元测试和静态检查

动作/命令：

1. `./gradlew.bat :app:testDebugUnitTest`
2. `./gradlew.bat :app:compileDebugJavaWithJavac`
3. `./gradlew.bat :app:assembleDebug`
4. `./gradlew.bat :app:lintDebug`（若仓库现有 lint 配置允许）

验收：所有命令通过；检查现有 Steam 游戏下载、安装器和手动 ZIP 流程的测试/构建无回归。

### 19. 真机端到端验证

前置：Android 11+ arm64 设备、网络、已知公开 Zomboid Workshop ID；受限内容测试另准备可授权 Steam 账号。

场景：

1. 独立入口搜索、排序、分页、详情、评论和依赖。
2. 匿名官方链路下载一个公开 Mod，校验文件并安装到一个实例。
3. 下载中切后台、离开页面、暂停/继续、取消、重启应用后恢复。
4. 同一下载文件安装到第二个实例；测试覆盖确认和备份。
5. 官方失败后不自动访问第三方；用户确认后第三方重试进入同一下载库。
6. Steam 登录、Guard、刷新 token、多账号切换和受限下载。
7. Mod 库更新检查、重复安装、失败重试和旧版本清理。

记录：保存 logcat、任务 JSON 脱敏副本、失败 URL 主机、截图和结果；不得保存密码、token、Guard 数据或完整 Cookie。

## 计划完成条件

- 所有 19 项任务均完成或明确记录为环境阻塞。
- 阶段 1–6 的窄测试和阶段 7 的全量测试通过。
- 真实设备场景通过，或对无法验证的 Steam/设备特定场景给出明确原因和复现步骤。
- `docs/superpowers/progress.md` 更新为阶段 3/4/5 的实际完成状态。
