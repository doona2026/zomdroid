# Zomdroid Compose UI 全面重构实施计划

关联设计：[2026-09-01-zomdroid-compose-ui-redesign-design.md](../specs/2026-09-01-zomdroid-compose-ui-redesign-design.md)

## 执行约束

- 生产代码只修改 `zomdroid/`，不修改 `WorkshopAndroidDownloader/` 源项目。
- 保留当前工作区已有的 Workshop 未提交改动，不执行 reset、checkout 或大范围格式化。
- 先建立可编译的 Compose 基础，再按垂直切片迁移页面；旧 XML 页面在对应切片完成前保留。
- UI 层使用 Kotlin + Compose；Java 管理器、InstallerService、GameInstanceManager、GamepadManager、GameActivity 和下载服务继续作为边界内的既有能力。
- 每项任务完成后先执行该任务的验证命令，再进入下一项。
- 新增文案只进入 `values/strings.xml`、`values-zh-rCN/strings.xml` 和 `values-ru/strings.xml`；葡萄牙语与印地语资源暂时保留但不扩展。
- 所有三种外观共享页面和状态模型；不为每种外观复制一套业务页面。

## 阶段 0：基线和依赖

### 1. 记录当前状态和构建基线

文件：无生产文件修改。

动作：

- 在 `zomdroid/` 记录 `git status --short`，确认用户现有改动列表；
- 执行 `./gradlew.bat :app:testDebugUnitTest`；
- 执行 `./gradlew.bat :app:compileDebugJavaWithJavac`；
- 执行 `./gradlew.bat :app:assembleDebug`；
- 记录现有 APK 路径和失败项，后续区分基线失败与本次回归。

验证：三条命令的结果和工作区状态记录在 `docs/superpowers/progress.md`。

### 2. 接入 Compose 编译插件和运行依赖

文件：

- `gradle/libs.versions.toml`
- `build.gradle.kts`
- `app/build.gradle.kts`

动作：

- 增加 Kotlin Compose plugin；
- 增加 Compose BOM、UI、graphics、foundation、Material 3、extended icons、tooling；
- 增加 `activity-compose`、`lifecycle-runtime-compose`、`lifecycle-viewmodel-compose`、`navigation-compose` 和 `coil-compose`；
- 增加 `ui-test-junit4` 与 debug `ui-test-manifest`，为阶段 1 的 Compose 交互和截图测试提供运行环境；
- 增加 `io.github.kyant0:backdrop` 与 `io.github.kyant0:shapes`；
- 将 `navigation-compose` 与现有 `navigation-fragment`/`navigation-ui` 统一到同一条兼容当前 AGP 的 Navigation 版本线（当前固定为 2.8.5），避免混合迁移期的 AndroidX 版本分裂；
- 将 `compileSdk` 提升到 36 以满足 Backdrop/Shapes 的编译要求；保持 `targetSdk 35`、`minSdk 30`、Java/Kotlin target 11 和 native 配置不变。
- 继续保留现有 ViewBinding、Navigation Fragment、Material、Java 11 和 native 构建配置；
- 依赖版本优先采用 WorkshopAndroidDownloader 已验证的版本；如与 Zomdroid 的 compileSdk/AGP 冲突，只调整 UI 依赖，不升级无关的 native 或 Java 依赖。

验证：

```text
./gradlew.bat :app:dependencies
./gradlew.bat :app:assembleDebug
```

### 3. 补充移植归属和第三方许可证

文件（现有文件以更新为主）：

- `NOTICE.md`
- `THIRD_PARTY_LICENSES/Apache-2.0-WorkshopAndroidDownloader.txt`
- 必要时新增 Backdrop/Shapes 许可证记录

动作：更新现有 NOTICE 和许可证记录，加入 UI 组件移植范围、Apache 2.0 归属、变更说明以及 `backdrop`/`shapes` 依赖归属；不修改 Zomdroid 原有 `LICENSE`，不重复创建已有许可证正文。

验证：检查文件存在、许可证正文完整，并执行 `./gradlew.bat :app:assembleDebug`。

## 阶段 1：外观状态和 Compose Design System

### 4. 建立外观和主题模型

文件：

- `app/src/main/java/com/zomdroid/ui/theme/Color.kt`
- `app/src/main/java/com/zomdroid/ui/theme/Theme.kt`
- `app/src/main/java/com/zomdroid/ui/theme/Frontend.kt`
- `app/src/main/java/com/zomdroid/ui/model/AppearanceMode.kt`

动作：

- 定义 `LiquidGlass`、`LiteLiquidGlass`、`Classic` 三个模式；
- 定义独立的深浅色主题令牌，不绑定橙色；
- 定义统一的颜色、排版、形状、间距和层级令牌；
- 设置默认外观为液态玻璃；
- 提供 `isLiquidGlassEnabled()`、`shouldReduceLiquidGlassEffects()` 等渲染策略查询。

验证：新增 `AppearanceModeTest`，覆盖字符串存储值、未知值回退和三种模式的策略映射；执行该测试。

### 5. 移植程序化背景和 Backdrop 基础组件

文件：

- `app/src/main/java/com/zomdroid/ui/component/GlassComponents.kt`
- `app/src/main/java/com/zomdroid/ui/component/LiquidControls.kt`
- `app/src/main/java/com/zomdroid/ui/component/ProceduralWallpaper.kt`
- `app/src/main/java/com/zomdroid/ui/component/PopupHost.kt`

动作：

- 从 WorkshopAndroidDownloader 移植并改名为 Zomdroid 组件；
- `ProceduralWallpaper` 使用 Canvas 绘制渐变和光晕；
- `GlassSurface` 在完整液态模式使用分层 Backdrop、blur、vibrancy、lens、highlight、shadow 和 inner shadow；
- 轻量模式走低成本 Surface/边框路径；
- 经典模式走实色 Material Surface；
- 统一按钮、图标按钮、导航项、开关、滑块、选择器、卡片和弹窗容器；
- 组件 API 不引用 WorkshopAndroidDownloader 的业务模型。

验证：建立 `GlassComponentsTest`/Compose screenshot test 的最小样例；分别渲染三种模式并检查组件存在、点击回调和经典模式不触发玻璃路径。

### 6. 建立外观持久化适配器

文件：

- `app/src/main/java/com/zomdroid/ui/settings/UiSettingsRepository.kt`
- `app/src/main/java/com/zomdroid/LauncherPreferences.java`
- `app/src/main/java/com/zomdroid/C.java`（当前采用已有 LauncherPreferences JSON，不新增独立 SharedPreferences key，因此本文件无需修改）

动作：

- 在现有 SharedPreferences/Gson 数据中增加外观存储字段，保留旧 JSON 可读取性；
- 提供 Kotlin 类型安全的读取/写入 API；
- 不改变现有深浅色 `ThemeMode`、渲染器、JVM 参数和控制器设置；
- 对损坏或未知外观值回退到液态玻璃，并立即持久化合法值。

验证：新增存储 round-trip、旧 JSON 迁移、未知值回退测试；执行 `./gradlew.bat :app:testDebugUnitTest --tests "com.zomdroid.ui.settings.*"`。

## 阶段 2：根入口、响应式导航和全局事件

### 7. 建立应用级 UI 状态和动作模型

文件：

- `app/src/main/java/com/zomdroid/ui/model/AppUiState.kt`
- `app/src/main/java/com/zomdroid/ui/model/AppDestination.kt`
- `app/src/main/java/com/zomdroid/ui/model/AppAction.kt`
- `app/src/main/java/com/zomdroid/ui/viewmodel/AppViewModel.kt`

动作：定义五个一级模块、详情目的地、返回规则、全局提示、权限请求、安装任务和下载任务事件；ViewModel 只编排状态与边界调用，不直接包含 Compose UI。

验证：新增纯 JVM 状态机测试，覆盖一级导航、详情入栈/返回、外观切换和一次性事件消费。

### 8. 建立自适应根 Scaffold

文件：

- `app/src/main/java/com/zomdroid/ui/ZomdroidApp.kt`
- `app/src/main/java/com/zomdroid/ui/AppScaffold.kt`
- `app/src/main/java/com/zomdroid/ui/AdaptiveNavigation.kt`
- `app/src/main/java/com/zomdroid/ui/GlobalDialogs.kt`

动作：

- 以窗口宽度和方向选择底部导航、抽屉、侧边栏或固定导航栏；
- 液态玻璃模式建立 wallpaper/content/chrome 三层 Backdrop；
- 统一状态栏、导航栏、Snackbar、错误、确认和权限提示；
- 为所有一级模块提供导航项和可访问语义；
- 详情页面复用同一顶部返回栏，不复制根导航。

验证：Compose UI 测试覆盖五个一级导航项、三种外观切换、窄屏/宽屏导航选择和返回行为。

### 9. 将 LauncherActivity 接入 Compose 根入口

文件：

- `app/src/main/java/com/zomdroid/LauncherActivity.java`
- `app/src/main/java/com/zomdroid/ui/ZomdroidApp.kt`
- `app/src/main/AndroidManifest.xml`（仅在入口声明需要调整时）

动作：

- 保留法律声明、版本说明、通知权限、依赖安装、更新检查和外部 Intent 行为；
- 将 Activity 的导航和 UI 渲染切换为 Compose 根入口；
- 通过 Adapter 把 Java 回调转换为 ViewModel Action；
- 迁移期间保留旧 NavHost 的兼容分支，直到所有管理页面切片完成。

验证：`compileDebugJavaWithJavac`、`assembleDebug`；启动测试确认法律声明、版本说明和通知权限仍触发。

## 阶段 3：启动器垂直切片

### 10. 建立游戏实例只读状态适配器

文件：

- `app/src/main/java/com/zomdroid/ui/launcher/LauncherModels.kt`
- `app/src/main/java/com/zomdroid/ui/launcher/LauncherRepository.kt`
- `app/src/main/java/com/zomdroid/ui/viewmodel/LauncherViewModel.kt`
- `app/src/main/java/com/zomdroid/game/GameInstanceManager.java`（仅补充只读边界时）

动作：将现有实例列表、安装状态、版本、存档摘要和错误状态映射为不可变 UI State；保留 `GameInstanceManager` 的持久化格式和线程模型。

验证：新增实例列表映射和刷新测试；执行现有游戏实例相关测试及 `compileDebugJavaWithJavac`。

### 11. 重做启动器和实例操作

文件：

- `app/src/main/java/com/zomdroid/ui/launcher/LauncherScreen.kt`
- `app/src/main/java/com/zomdroid/ui/launcher/GameInstanceCard.kt`
- `app/src/main/java/com/zomdroid/ui/launcher/GameInstanceActions.kt`
- 原参考：`app/src/main/java/com/zomdroid/fragments/LauncherFragment.java`

动作：重做实例卡片、启动、编辑、备份、恢复、删除、存储和 Wiki 入口；所有长任务通过现有 Service/Manager 触发；不把线程、文件复制或 Intent 逻辑写入 Composable。

验证：Compose 测试覆盖空列表、单实例、多实例、启动点击、删除确认和任务进行中状态；执行 `assembleDebug`。

### 12. 重做新建实例和游戏设置

文件：

- `app/src/main/java/com/zomdroid/ui/launcher/NewGameInstanceScreen.kt`
- `app/src/main/java/com/zomdroid/ui/launcher/GameSettingsScreen.kt`
- `app/src/main/java/com/zomdroid/ui/viewmodel/NewGameInstanceViewModel.kt`
- `app/src/main/java/com/zomdroid/ui/viewmodel/GameSettingsViewModel.kt`
- 原参考：`NewGameInstanceFragment.java`、`GameSettingsFragment.java`

动作：迁移输入校验、Build 选择、安装路径、预设、存档和依赖操作；沿用 `InstallerService`、`SuggestedPreset` 和现有 URI 权限逻辑。

验证：复用/新增输入校验和 ViewModel 测试；在无设备环境执行编译和单元测试。

## 阶段 4：设置和工具切片

### 13. 重做通用设置和三种外观选择

文件：

- `app/src/main/java/com/zomdroid/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/zomdroid/ui/settings/SettingsViewModel.kt`
- 原参考：`SettingsFragment.java`、`fragment_settings.xml`

动作：迁移深浅色、前端外观、渲染器、分辨率缩放、JVM 参数、环境变量、纹理缩放、内存节省、快速存档和调试开关；保留预设确认、警告、同步刷新和 Wiki 入口。

验证：ViewModel 测试覆盖外观立即刷新、旧偏好读取、预设应用和取消；Compose 测试覆盖三种模式选择。

### 14. 重做控制器、触控和控制编辑器入口

文件：

- `app/src/main/java/com/zomdroid/ui/settings/GamepadMapperScreen.kt`
- `app/src/main/java/com/zomdroid/ui/settings/TouchControlsScreen.kt`
- `app/src/main/java/com/zomdroid/ui/settings/ControlsEditorLaunchScreen.kt`
- 原参考：`GamepadMapperFragment.java`、`TouchControlsFragment.java`、`ControlsEditorLaunchFragment.java`

动作：保持 `GamepadManager`、输入映射 JSON、控制器导入导出和 `ControlsEditorActivity` 的协议；只重做管理界面和导航反馈。

验证：输入映射 round-trip 测试、Compose 交互测试、Java 编译。

### 15. 重做安装、优化、日志和帮助页面

文件：

- `app/src/main/java/com/zomdroid/ui/tools/InstallControlsScreen.kt`
- `app/src/main/java/com/zomdroid/ui/tools/InstallDriverScreen.kt`
- `app/src/main/java/com/zomdroid/ui/tools/InstallNativeLibsScreen.kt`
- `app/src/main/java/com/zomdroid/ui/tools/InstallSavesScreen.kt`
- `app/src/main/java/com/zomdroid/ui/tools/InstallModScreen.kt`
- `app/src/main/java/com/zomdroid/ui/tools/ModFixesScreen.kt`
- `app/src/main/java/com/zomdroid/ui/tools/OptimizationScreen.kt`
- `app/src/main/java/com/zomdroid/ui/tools/ExportLogScreen.kt`
- `app/src/main/java/com/zomdroid/ui/tools/WikiScreen.kt`
- 原参考：对应 `*Fragment.java` 和 `fragment_*.xml`

动作：统一安装卡片、进度、错误、取消、实例选择、文件 URI、日志导出和 Wiki WebView 行为；现有 `InstallerService` 仍是唯一安装执行边界。

验证：每类 InstallerService task 至少一个状态映射测试；执行 `compileDebugJavaWithJavac`、`testDebugUnitTest` 和 `assembleDebug`。

## 阶段 5：Workshop、Steam 和下载切片

### 16. 建立 Workshop 状态适配器

文件：

- `app/src/main/java/com/zomdroid/ui/workshop/WorkshopUiState.kt`
- `app/src/main/java/com/zomdroid/ui/workshop/WorkshopViewModel.kt`
- `app/src/main/java/com/zomdroid/ui/workshop/WorkshopRepositoryAdapter.kt`
- 复用：`workshop/data`、`workshop/download`、`workshop/library`、`workshop/auth`

动作：将现有 Workshop Fragment 的查询、分页、详情、评论、依赖、下载、认证和库状态映射为 Compose StateFlow；复用已有仓库和 Java Facade，不复制网络实现。

验证：Fake repository 状态测试覆盖加载、空结果、错误、分页、详情和登录要求；执行现有 Workshop 测试。

### 17. 重做 Workshop 浏览、详情和账号

文件：

- `app/src/main/java/com/zomdroid/ui/workshop/WorkshopScreen.kt`
- `app/src/main/java/com/zomdroid/ui/workshop/WorkshopDetailScreen.kt`
- `app/src/main/java/com/zomdroid/ui/workshop/WorkshopAccountScreen.kt`
- `app/src/main/java/com/zomdroid/ui/workshop/WorkshopCards.kt`
- 原参考：`WorkshopFragment.java`、`WorkshopDetailFragment.java`、`WorkshopAccountFragment.java`

动作：重做搜索栏、筛选、列表、详情头图、元数据、评论、依赖、下载和 Steam Guard 对话框；接入三种外观的相同组件变体。

验证：Compose 测试覆盖搜索、排序、分页、打开详情、批量下载、依赖确认、登录弹窗和返回栈。

### 18. 重做 Steam 下载、下载中心和任务详情

文件：

- `app/src/main/java/com/zomdroid/ui/download/SteamDownloadScreen.kt`
- `app/src/main/java/com/zomdroid/ui/download/DownloadCenterScreen.kt`
- `app/src/main/java/com/zomdroid/ui/download/DownloadTaskDetailScreen.kt`
- `app/src/main/java/com/zomdroid/ui/download/DownloadViewModel.kt`
- 原参考：`SteamDownloadFragment.java`、`WorkshopDownloadCenterFragment.java`、相关 XML

动作：读取现有 `DownloadCenterManager` 的 StateFlow/快照，重做队列、进行中、完成、失败、暂停、恢复、重试、取消、删除、日志分享和安装入口；保留前台服务、通知和进程恢复逻辑。

验证：复用 `DownloadCenterStateTest`、`DownloadCenterStoreTest`，新增 Compose 状态渲染测试；执行 `testDebugUnitTest`、`compileDebugJavaWithJavac`、`assembleDebug`。

### 19. 重做模组库、版本和安装流程

文件：

- `app/src/main/java/com/zomdroid/ui/library/ModLibraryScreen.kt`
- `app/src/main/java/com/zomdroid/ui/library/ModDetailScreen.kt`
- `app/src/main/java/com/zomdroid/ui/library/ModLibraryViewModel.kt`
- 原参考：`WorkshopModLibraryFragment.java`、`ModLibrary*`、`WorkshopInstallCoordinator.kt`

动作：重做筛选、搜索、版本、更新检查、多实例安装、覆盖确认、备份和分享；所有安装仍通过现有安装协调器和 InstallerService。

验证：执行现有 ModLibrary/ModVersioning/WorkshopInstall 测试，新增库列表状态和安装确认 Compose 测试。

## 阶段 6：特殊页面、资源和清理

### 20. 将 ControlsEditorActivity 接入统一设计系统

文件：

- `app/src/main/java/com/zomdroid/ControlsEditorActivity.java`
- `app/src/main/java/com/zomdroid/ui/controls/ControlsEditorScreen.kt`
- 必要时 `app/src/main/res/layout/activity_controls_editor.xml`

动作：保持控制编辑器的输入命中、长按菜单、保存和覆盖层行为；将可重做的编辑器面板迁移到 Compose，无法安全迁移的输入画布保留为 Android View，并使用 Compose 外壳承载。

验证：控制编辑器现有手动回归路径、Java 编译和 Compose 外壳测试；确认 `GameActivity` 不被引入 Compose 依赖或生命周期回调改变。

### 21. 统一三种外观的弹窗、加载和错误状态

文件：

- `app/src/main/java/com/zomdroid/ui/component/Dialogs.kt`
- `app/src/main/java/com/zomdroid/ui/component/AsyncContent.kt`
- `app/src/main/java/com/zomdroid/ui/component/EmptyState.kt`
- `app/src/main/java/com/zomdroid/ui/component/ErrorState.kt`

动作：替换旧 MaterialAlertDialog 和手工 ProgressDialog 的管理界面入口；为每种外观定义同样的信息层级、按钮顺序、取消行为和可访问标签。

验证：Compose 测试覆盖确认、取消、不可取消任务、错误重试和空状态；抽查所有三种外观。

### 22. 完成中/英/俄资源和响应式布局

文件：

- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-zh-rCN/strings.xml`
- `app/src/main/res/values-ru/strings.xml`
- 各 Compose 页面和组件

动作：删除新增硬编码文案；补齐长文案、复数、参数化文本、内容描述和 RTL 无关的布局假设；在 360dp、600dp、840dp 宽度和横屏下检查溢出。

验证：资源 lint、三语言编译资源检查、Compose 语义测试和截图回归。

### 23. 删除已迁移 XML 和旧导航入口

文件：

- `app/src/main/res/navigation/nav_graph.xml`
- `app/src/main/res/menu/menu_nav.xml`
- `app/src/main/res/menu/menu_launcher.xml`
- 已完全替换的 `fragment_*.xml`、`item_*.xml` 和旧 Fragment
- `LauncherActivity.java` 中只服务旧 NavHost 的代码

动作：逐个确认无引用后删除旧页面；保留 `GameActivity`、游戏内布局和仍被特殊 Activity 使用的 View；不删除未迁移或仍被测试/外部 Intent 使用的资源。

验证：`rg` 检查旧资源无生产引用；执行全量单元测试、Java 编译、Compose 编译和 `assembleDebug`。

## 阶段 7：验证和性能

### 24. 补齐 ViewModel、外观和导航测试

文件：

- `app/src/test/java/com/zomdroid/ui/**`
- `app/src/androidTest/java/com/zomdroid/ui/**`

动作：为每个一级模块覆盖主要状态分支；为三种外观覆盖导航、卡片、弹窗和设置切换；为宽度/方向覆盖自适应布局。

验证：

```text
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:compileDebugJavaWithJavac
./gradlew.bat :app:connectedDebugAndroidTest
```

### 25. 验证 Backdrop 降级和资源占用

文件：

- `app/src/main/java/com/zomdroid/ui/theme/Frontend.kt`
- `app/src/main/java/com/zomdroid/ui/component/GlassComponents.kt`
- `app/src/main/java/com/zomdroid/ui/component/ProceduralWallpaper.kt`

动作：确认轻量模式不进入完整 blur/vibrancy 路径；确认经典模式不建立不必要的高成本效果；在 API 30 和 API 35、低性能模拟器/设备上验证切换和滚动。

验证：记录液态、轻量、经典三种模式的启动、列表滚动和详情打开结果；出现渲染异常时只增加明确的能力探测/回退，不改变用户持久化选择。

### 26. 全量构建、静态检查和真实设备验收

文件：无新增生产文件；更新 `docs/superpowers/progress.md`。

动作：

- 执行全量 JVM 测试；
- 执行 Kotlin/Java 编译；
- 执行 lint 和 debug APK 构建；
- 在 API 30+ arm64 设备上验证启动器、设置、Workshop、下载中心、模组库和控制编辑器；
- 验证游戏启动、前台下载服务、通知、进程恢复、安装和存档流程；
- 保存三种外观、三种语言、竖屏/横屏/大屏的关键截图和结果。

验证命令：

```text
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:compileDebugJavaWithJavac
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:connectedDebugAndroidTest
```

完成条件：所有管理页面可达，三种外观可切换并持久化，中/英/俄资源无缺失，现有核心功能无回归，GameActivity 游戏画面和触控层行为不变，许可证归属完整。
