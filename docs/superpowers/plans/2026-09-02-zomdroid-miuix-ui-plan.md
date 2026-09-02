# Zomdroid Miuix UI 重构实施计划

> 状态：已批准；阶段 1 已完成
>
> 对应设计文档：[2026-09-02-zomdroid-miuix-ui-design.md](../specs/2026-09-02-zomdroid-miuix-ui-design.md)
>
> 计划范围：仅主应用页面布局、UI 交互、UI 状态适配，以及接入本地 Miuix 所必需的构建配置。

## 1. 执行规则与硬边界

### 1.1 必须遵守的限制

本计划不允许修改以下内容：

- `InstallerService`、依赖安装流程、文件安装/备份/路径处理逻辑。
- Workshop 的网络、认证、下载、解析和任务持久化逻辑。
- `GameInstance` 模型、持久化格式、启动逻辑和 JNI/C++ 代码。
- `GameActivity` 的游戏渲染宿主、`GameInputView`、`InputControlsView`、虚拟控制的触摸实现。
- `ControlsEditor` 的自定义 Canvas/输入编辑实现。
- native libraries、renderer、底层线程、数据库和无关测试。
- 与本次页面迁移无关的依赖升级、代码格式化和架构重构。

允许的代码变更只有：

- Compose/Miuix 页面、导航、主题、布局和交互。
- 将既有公开业务 API 转换成 UI 可消费状态的 Kotlin ViewModel/Adapter。
- 为 UI 编写的单元测试和 Android UI 测试。
- `settings.gradle.kts`、`gradle/libs.versions.toml`、`app/build.gradle.kts` 等接入 Miuix 所必需的构建配置。

任何任务如果需要突破上述范围，必须暂停并重新取得用户批准。

### 1.2 迁移策略

- 最终主应用管理页面使用 Compose + Miuix；Fragment/XML 只允许作为迁移过渡，不作为最终页面实现。
- 页面按垂直切片迁移，每个切片完成后都能编译、测试并人工检查。
- 不新增“依赖初始化页”。首次启动仍是：法律声明 Miuix Dialog → 现有依赖安装进度 Dialog 的 Miuix 重构 → 实例列表。
- 不保留旧的暖橙色视觉体系，不新增用户可见的旧 UI 回退开关。
- 游戏中的渲染、虚拟控制和 ControlsEditor 保持原实现；本计划不把它们改成 Compose。

## 2. 阶段 0：基线与兼容性门禁

### 任务 0.1：记录基线与工作区边界

文件：

- `D:\apps\appss\ws\zomdoid\zomdroid\docs\superpowers\progress.md`
- 仓库当前 Git 状态

动作：

1. 记录当前分支、工作区已有修改和当前提交，不覆盖用户已有改动。
2. 确认本次只允许新增/修改 `docs/superpowers/`，直到计划获得批准。
3. 保存基线构建结果；若基线本身失败，记录失败任务和日志，不把基线问题归因于 Miuix。

验证：

```powershell
git status --short
git diff --check
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

完成标准：基线结果已记录；现有失败与本次改动隔离；无生产代码变更。

### 任务 0.2：验证本地 Miuix 能否作为源码依赖接入

文件：

- `D:\apps\appss\ws\app\miuix\settings.gradle.kts`
- `D:\apps\appss\ws\app\miuix\gradle\libs.versions.toml`
- `D:\apps\appss\ws\zomdoid\zomdroid\settings.gradle.kts`
- `D:\apps\appss\ws\zomdoid\zomdroid\gradle\libs.versions.toml`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\build.gradle.kts`

动作：

1. 对照本地 Miuix 的模块和 Android artifact 坐标，确认至少需要 `miuix-ui`、`miuix-icons`、`miuix-nav`；只有实际使用偏好设置组件时才加入 `miuix-preference`。
2. 验证当前 Zomdroid 的 Kotlin、Compose Compiler、Compose Multiplatform/AndroidX Compose 版本与 Miuix 要求的兼容性。
3. 优先使用 Gradle composite build 或现有源码发布机制接入 `D:\apps\appss\ws\app\miuix`。
4. 本机绝对路径只放在未提交的 `local.properties`/Gradle 属性中；仓库脚本不得硬编码开发者机器路径。
5. 如果源码依赖无法解析，停止后续实现，不修改 Miuix 源码，也不自行引入替代 UI 库。

验证：

```powershell
.\gradlew.bat :app:dependencies --configuration debugCompileClasspath
.\gradlew.bat :app:dependencyInsight --dependency miuix --configuration debugRuntimeClasspath
.\gradlew.bat :app:compileDebugKotlin
```

完成标准：Miuix artifact 可解析，Compose 编译器可用，且不需要修改任何业务/底层源码。

### 任务 0.3：添加最小构建配置

文件：

- `D:\apps\appss\ws\zomdoid\zomdroid\settings.gradle.kts`
- `D:\apps\appss\ws\zomdoid\zomdroid\gradle\libs.versions.toml`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\build.gradle.kts`

动作：

1. 启用 Compose、Kotlin 编译所需的 Gradle 配置。
2. 只添加当前页面所需的 AndroidX Compose、Lifecycle/ViewModel Compose、Miuix UI、Miuix Icons 和 Miuix Nav 依赖。
3. 图片加载只在 Workshop 缩略图确实需要时加入现有兼容方案；不得为了 UI 重构升级无关依赖。
4. 添加 Compose UI 测试依赖，但不改现有 Java 测试运行方式。
5. 保持 `minSdk 30`；Miuix Blur 仅作为 API 33+ 可选实现，默认不让它成为启动依赖。

验证：

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:compileDebugJavaWithJavac
.\gradlew.bat :app:assembleDebug
```

完成标准：空的 Compose 入口可以编译，Java 代码仍可编译，依赖变化只服务于 Miuix UI。

## 3. 阶段 1：Compose/Miuix 应用壳

### 任务 1.1：建立 UI 包结构和设计令牌入口

新增文件：

- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\ZomdroidTheme.kt`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\common\UiTokens.kt`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\common\UiText.kt`

动作：

1. 以 MiuixTheme 为唯一新页面主题入口，使用 Miuix 默认蓝灰色体系。
2. 接入系统浅色/深色；如果 Miuix API 可用，再按设计文档接入 Monet 动态色。
3. 定义页面间距、最小触控尺寸、卡片层级、标题/正文/辅助文本层级和错误色令牌。
4. 新页面图标统一使用 Miuix Icons；不得引入旧 Material 图标作为新页面默认图标。
5. 将文本、内容描述和可访问性语义集中到 UI 层，复用现有资源字符串。

验证：添加主题预览或最小 Compose UI 测试，确认浅色、深色、字体放大和 RTL 不出现布局崩溃。

完成标准：所有后续页面只依赖该主题入口，不直接散落颜色、间距和 Material 组件。

### 任务 1.2：建立应用状态和路由模型

新增文件：

- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\navigation\AppRoute.kt`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\navigation\AppNavigationState.kt`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\state\UiResult.kt`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\state\TaskUiState.kt`

动作：

1. 定义 Instances、Workshop、Tools、Settings 四个一级目的地。
2. 定义 Workshop 的 Browse、Downloads、Library 子目的地及详情页、账户页路由。
3. 定义返回栈、选中实例、全局任务入口、错误提示和 Snackbar 状态。
4. 路由状态只负责 UI 导航，不在 Composable 中直接调用服务、数据库或安装逻辑。
5. 使用 Miuix Nav；如果当前版本 API 不足以覆盖返回栈要求，封装一个仅负责 UI 导航的适配层，不修改底层业务。

验证：为路由状态添加纯 Kotlin 测试：一级目的地切换、详情返回、重复点击当前目的地、进程重建后的默认目的地。

完成标准：页面可通过稳定的路由和 UI 状态模型组合，不依赖旧 `nav_graph.xml` 的业务逻辑。

### 任务 1.3：接入新的 Launcher Compose 根

文件：

- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\ZomdroidApp.kt`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\LauncherActivity.java`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\res\layout\activity_launcher.xml`

动作：

1. 保留 `LauncherActivity` 的生命周期和非 UI 初始化职责，只替换页面内容承载方式。
2. 将主内容切换为 `ComposeView`/`setContent`，挂载 `ZomdroidApp`。
3. 手机使用 Miuix 底部导航，平板/宽屏使用 Miuix NavigationRail；不再渲染旧 DrawerLayout、NavigationView 和暖橙色旧 Toolbar。
4. 使用状态保存机制保存当前一级目的地、Workshop 子页和返回栈中的 UI 状态。
5. 本任务不删除旧 Fragment/XML；先保留可回滚的迁移容器，等所有切片完成后清理。

验证：启动后能进入 Compose 根；在手机和平板尺寸下一级导航可见且可操作；返回键行为符合路由模型。

完成标准：新应用壳可独立运行，未迁移页面暂由明确的迁移占位状态表示，不恢复旧主 Drawer。

## 4. 阶段 2：启动流程与全局任务入口

### 任务 2.1：实现首次启动法律声明 Dialog

新增/修改文件：

- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\startup\StartupDialogs.kt`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\fragments\LauncherFragment.java`（仅移除或转接旧 UI 显示代码）

动作：

1. 使用 Miuix Dialog 展示现有法律声明文本和确认操作。
2. 复用既有首次启动偏好设置键，不改变其含义和写入时机。
3. 未确认时阻止进入主页面；确认后关闭 Dialog 并进入依赖检查。
4. 保持无障碍焦点顺序、TalkBack 描述、字体放大和 RTL 布局。

验证：清除应用数据后启动，确认声明必现；拒绝/关闭不绕过；确认后只出现依赖安装 Dialog，不出现额外初始化页。

完成标准：法律流程行为不变，只有 Dialog 的视觉和交互由 Miuix 接管。

### 任务 2.2：重构依赖安装进度 Dialog

新增/修改文件：

- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\startup\DependencyInstallDialog.kt`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\fragments\LauncherFragment.java`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\res\layout\task_progress_dialog.xml`（迁移完成后再判断是否可删除）

动作：

1. 订阅现有安装任务的进度回调/状态，不修改 `InstallerService` 和任务实现。
2. 将任务状态映射为 UI 状态：等待、进行中、完成、失败、可重试。
3. 用 Miuix Dialog、Progress、Button 和错误提示替代旧 Material 进度窗口。
4. 依赖未安装时阻止进入实例列表；已安装时直接跳过；版本更新触发的既有重装行为保持不变。
5. 失败状态提供原有重试/退出语义，不增加新的依赖初始化页面。

验证：使用现有安装任务产生等待、进度、完成和失败状态；确认 Dialog 不丢失进度、不允许非法关闭，并且安装服务调用次数与迁移前一致。

完成标准：依赖初始化仍是一个进度 Dialog，底层安装流程零修改。

### 任务 2.3：迁移发布说明 Dialog 和全局任务入口

新增/修改文件：

- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\startup\ReleaseNotesDialog.kt`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\common\GlobalTaskEntry.kt`
- 与当前发布说明、任务状态相关的现有 UI 文件

动作：

1. 将发布说明保留为 Miuix Dialog，不改版本判断和内容来源。
2. 在顶栏/页面合适位置提供全局任务入口；没有任务时不占用突出空间。
3. 任务入口只消费 `TaskUiState`，点击后导航到 Workshop Downloads 或任务详情。
4. 错误、重试、取消的 UI 事件通过 UI Adapter 转发到既有公开 API。

验证：存在任务、无任务、任务失败三种状态下入口显示正确；发布说明只在既有条件满足时显示。

## 5. 阶段 3：Instances 实例工作台

### 任务 3.1：建立实例 UI Adapter/ViewModel

新增文件：

- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\instances\InstanceUiModel.kt`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\instances\InstancesViewModel.kt`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\instances\InstancesUiAdapter.kt`

动作：

1. 读取现有 `GameInstanceManager`/公开实例 API，将实例、状态、版本、可启动性转换为不可变 UI 模型。
2. 将创建、编辑、删除、启动、刷新事件映射到现有公开调用点；不移动或修改其业务实现。
3. 对异常和空数据输出稳定的 UI 状态，不让异常穿透 Composable。
4. 明确 loading、empty、error、content 四种状态。

验证：为模型映射、空实例、多个实例、启动中、异常状态添加单元测试。

完成标准：Instances 页面不再直接依赖 Java Fragment 的 ViewBinding 或 XML。

### 任务 3.2：实现 Instances 首页

新增文件：

- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\instances\InstancesScreen.kt`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\instances\InstanceCard.kt`

动作：

1. 设计为直接启动中心：顶部上下文信息、实例卡片列表、突出主操作、新建入口和必要的次级操作。
2. 卡片显示名称、版本/状态、最后使用信息和主操作；避免把低频管理动作与启动按钮混在一起。
3. 空状态提供新建实例和导入实例入口；错误状态提供重试。
4. 所有点击、长按/更多菜单、删除确认和启动反馈都使用 Miuix 交互组件。
5. 使用可访问语义、48dp 最小触控区域和稳定 key，支持字体放大与旋转。

验证：Compose UI 测试覆盖 content、empty、loading、error、启动点击和删除确认；人工检查浅色/深色及窄屏布局。

### 任务 3.3：实现实例详情和新建实例页面

新增文件：

- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\instances\InstanceDetailScreen.kt`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\instances\NewInstanceScreen.kt`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\instances\InstanceFormState.kt`

动作：

1. 新实例使用紧凑单页表单，不做多步向导。
2. 详情页集中展示实例信息、启动、编辑、删除和进入相关设置的操作。
3. 表单校验只负责输入展示和错误提示；提交时调用现有实例 API，不改模型和持久化。
4. 迁移当前 `fragment_new_instance.xml`、`fragment_instance_detail.xml` 里的页面交互时，逐项对照既有字段和行为，不能遗漏原有能力。

验证：表单校验、旋转恢复、提交成功/失败、详情返回和删除确认均有测试或可复现检查。

## 6. 阶段 4：Tools 与 Settings

### 任务 4.1：实现 Tools 工作台及 UI Adapter

新增文件：

- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\tools\ToolsScreen.kt`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\tools\ToolsUiAdapter.kt`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\tools\ToolsViewModel.kt`

动作：

1. 按 Install/Import、Controls、Performance/Diagnostics 分组展示工具。
2. 将安装 Mod、安装 Controls、安装 Saves、Driver、Export Log、Optimization 等现有入口映射到 UI 路由或现有 Activity/公开 API。
3. 工具页只负责选择、确认、进度和结果反馈；底层安装、导出和优化实现不改。
4. 对不可用工具显示原因和可执行的替代操作，不显示失效按钮。

验证：每个现有工具入口至少有一个导航/点击测试；失败时确认错误不会导致应用崩溃或改变底层任务状态。

### 任务 4.2：实现 Settings 分类页和设置适配层

新增文件：

- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\settings\SettingsScreen.kt`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\settings\SettingsViewModel.kt`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\settings\SettingsUiAdapter.kt`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\settings\SettingsCategory.kt`

动作：

1. 分类为 Appearance、Game runtime、Controls、Data、Advanced。
2. Appearance 使用 Miuix 主题控件，保留系统浅色/深色及可选动态色设置。
3. 设置适配层只读写现有偏好设置/公开配置入口，保持键名、默认值和作用域不变。
4. 将原 Settings Fragment/XML 的可见设置逐项迁移；不得借机重命名配置键或改变运行时语义。
5. 对危险操作使用 Miuix Dialog 二次确认，对异步操作显示全局任务状态。

验证：读取、修改、重启后恢复、取消修改和危险操作确认均覆盖；设置变化不引入底层行为差异。

### 任务 4.3：迁移普通辅助页面

范围文件按实际导航引用确定，初始候选包括：

- `app/src/main/java/com/zomdoid/fragments/WikiFragment.java`
- `app/src/main/java/com/zomdoid/fragments/ExportLogFragment.java`
- `app/src/main/java/com/zomdoid/fragments/DriverFragment.java`
- `app/src/main/java/com/zomdoid/fragments/OptimizationFragment.java`
- 对应 `app/src/main/res/layout/fragment_*.xml`

动作：

1. 先逐个区分“纯页面 UI”和“包含业务编排”的代码。
2. 纯页面 UI 迁移到 Miuix；混合页面只抽出 UI 状态适配，不重写底层业务。
3. 对不属于主应用管理页面的游戏渲染、输入和 ControlsEditor 页面保持原实现。
4. 迁移完成后更新路由映射，再清理已无引用的旧 XML；清理前必须通过编译和引用搜索。

验证：导航入口、加载/空/错误状态和返回行为均可用；`rg` 确认未删除仍被引用的资源或类。

## 7. 阶段 5：Workshop 全面 UI 迁移

### 任务 5.1：建立 Workshop 状态适配层

新增文件：

- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\workshop\WorkshopUiModel.kt`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\workshop\WorkshopViewModel.kt`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\workshop\WorkshopUiAdapter.kt`
- 必要时新增与当前 Workshop 公共 API 对应的只读 UI mapper

动作：

1. 将 Browse、Downloads、Library、账户、分页、排序、搜索、加载和错误状态转换为 StateFlow UI 状态。
2. 搜索输入使用 debounce；分页和排序只改变现有查询参数，不改变网络/解析逻辑。
3. 下载任务状态映射到全局任务入口和 Downloads 子页。
4. UI 只调用当前 Workshop facade/公开 API；禁止把网络、认证、下载和解析代码复制到 ViewModel。

验证：为搜索、排序、分页、空结果、网络错误、下载状态和账户未登录状态添加 mapper/ViewModel 测试。

### 任务 5.2：实现 Browse、Downloads、Library 三个子页

新增文件：

- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\workshop\WorkshopScreen.kt`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\workshop\WorkshopBrowseScreen.kt`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\workshop\WorkshopDownloadsScreen.kt`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\workshop\WorkshopLibraryScreen.kt`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\workshop\WorkshopCards.kt`

动作：

1. 顶部使用 Miuix SearchBar；Browse 内提供排序和分页控件。
2. 使用紧凑列表/卡片呈现条目，保留标题、作者、更新时间、状态和主操作。
3. Downloads 显示进行中、已完成、失败和可重试任务；Library 显示已收藏/已安装内容及空状态。
4. 处理图片加载占位、失败占位和内容描述；图片加载失败不影响文本和操作。
5. 保持大屏双栏/侧栏可扩展，小屏单栏；不得复制 Workshop 业务逻辑。

验证：Compose UI 测试覆盖三个子页的 content/empty/loading/error 状态、搜索、排序、分页和任务点击；人工检查浅色/深色、窄屏和字体放大。

### 任务 5.3：实现 Workshop 详情和账户页

新增文件：

- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\workshop\WorkshopDetailScreen.kt`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\ui\workshop\WorkshopAccountScreen.kt`

动作：

1. 详情页展示封面、标题、作者、描述、版本/更新时间和主要下载/收藏操作。
2. 账户页显示登录状态、账户操作和认证错误；认证实现保持现有代码。
3. 对下载、取消、重试、收藏和登录跳转使用统一 UI 反馈。
4. 详情页返回 Browse/Library 时保持原查询和滚动位置，避免用户丢失上下文。

验证：详情进入/返回、图片失败、长文本、未登录、下载失败和重试路径均可复现并通过测试。

### 任务 5.4：删除旧 Workshop 页面实现

文件范围：

- `app/src/main/java/com/zomdoid/fragments/Workshop*.java`
- `app/src/main/res/layout/fragment_workshop*.xml`
- `app/src/main/res/navigation/nav_graph.xml`
- `app/src/main/res/menu/menu_nav.xml`
- 旧 Workshop 专用 Material/RecyclerView 资源

动作：

1. 先确认新页面已经覆盖旧导航图中的 Browse、详情、下载中心、Library、账户入口。
2. 只删除确认无引用且只承担旧 UI 的 Fragment/XML/菜单资源。
3. 若旧 Fragment 含有不可迁移的底层调用，保留其底层调用所在代码，改由 UI Adapter 复用；不做大范围清理。
4. 删除后运行全仓库引用搜索和编译。

完成标准：Workshop 不再渲染旧 XML 页面，不存在重复的旧/新入口；底层 Workshop 实现未被修改。

## 8. 阶段 6：主壳收口与旧 UI 清理

### 任务 6.1：切断旧 Drawer/Navigation 入口

文件：

- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\res\layout\activity_launcher.xml`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\res\navigation\nav_graph.xml`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\res\menu\menu_nav.xml`
- `D:\apps\appss\ws\zomdoid\zomdroid\app\src\main\java\com\zomdoid\LauncherActivity.java`

动作：

1. 确认所有主应用入口都已指向 Compose/Miuix 路由。
2. 删除旧 DrawerLayout、旧 NavigationView、旧 NavHost 及其菜单资源，只在无引用后进行。
3. 对仍由游戏或特殊编辑器使用的 XML/View 资源保留，不把“旧 UI 清理”扩大到游戏渲染/输入页面。
4. 继续保留旧 Fragment 类，直到引用搜索证明它们只是无用 UI 容器；混合业务类不因清理需要而重写。

验证：`rg` 搜索旧主壳引用；构建通过；启动后四个一级目的地均可到达；系统返回、旋转和进程重建不回到旧 Drawer。

### 任务 6.2：清理新主页面的 Material 视觉依赖

文件：

- `D:\apps\appss\ws\zomdoid\zomdroid\app\build.gradle.kts`
- 新 UI Kotlin 文件及其资源

动作：

1. 搜索新页面对 Material 组件、旧暖橙色颜色资源和旧图标的引用。
2. 将新页面残留引用替换为 Miuix 对应组件/图标。
3. 只有在全仓库没有其他仍需 Material 的页面或特殊 View 时，才移除 Material 依赖；否则保留依赖但禁止新页面使用旧 Material 视觉。

验证：

```powershell
rg -n "com\.google\.android\.material|Material|warm|orange|colorAccent" app/src/main/java app/src/main/res app/build.gradle.kts
.\gradlew.bat :app:lintDebug
```

完成标准：新主应用页面完全使用 Miuix 视觉体系，未误伤游戏渲染、输入和编辑器页面。

## 9. 阶段 7：验证与交付门禁

### 任务 7.1：UI 状态和交互自动化测试

新增/修改文件：

- `app/src/test/java/com/zomdoid/ui/**`
- `app/src/androidTest/java/com/zomdoid/ui/**`
- 必要的 `app/src/androidTest/AndroidManifest.xml` 或测试配置

测试范围：

- 路由切换、返回栈、状态保存。
- 首次启动法律声明、依赖进度 Dialog、失败重试和发布说明 Dialog。
- Instances 的空/加载/错误/内容、启动和删除确认。
- Workshop 的搜索、排序、分页、详情、下载、Library、账户错误。
- Tools/Settings 分类、配置读写 UI、危险操作确认。
- 浅色/深色、字体放大、TalkBack 语义、RTL、窄屏和大屏布局。

验证命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

完成标准：新增 UI 测试通过；所有失败都能区分为 UI 回归、基线问题或设备环境问题。

### 任务 7.2：静态检查、构建和范围审计

验证命令：

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:compileDebugJavaWithJavac
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
git diff --check
git status --short
```

范围审计：

1. `git diff --name-only` 只允许出现 UI 文件、UI 测试、资源和 Miuix 构建配置。
2. 对 `InstallerService`、Workshop 核心、`GameInstance`、`GameActivity`、`GameInputView`、`InputControlsView`、ControlsEditor、JNI/C++ 和 native 目录执行差异检查，结果必须为空。
3. 检查新增代码没有直接从 Composable 调用底层服务；所有调用经 UI Adapter/ViewModel。
4. 检查没有新增初始化页、旧暖橙色主视觉或用户可见的旧 UI 回退开关。

### 任务 7.3：设备人工验收

验收矩阵：

| 场景 | 必查结果 |
|---|---|
| 首次启动 | 法律声明 Dialog → 依赖安装进度 Dialog → Instances；无初始化页 |
| 已初始化启动 | 直接进入 Instances |
| 手机竖屏 | 底部导航、单栏内容、返回栈正确 |
| 平板/宽屏 | NavigationRail、双栏/详情布局正确 |
| 浅色/深色 | 仅 Miuix 蓝灰体系，无暖橙色旧主视觉 |
| Workshop 任务 | 全局任务入口、Downloads、失败重试一致 |
| 游戏启动 | 游戏渲染、输入和虚拟控制行为与迁移前一致 |
| 设置变更 | 配置键、默认值、作用域和实际运行语义不变 |
| 无障碍 | 48dp 触控、TalkBack、字体放大、RTL 可用 |

对 Android 原生项目不执行浏览器验证；以真机/模拟器的 Android UI 测试和人工检查替代。若没有可用设备，必须明确记录未完成的设备验收，不得宣称 UI 已完全验收。

## 10. 推荐提交顺序

每个提交只包含一个可回滚垂直切片，顺序如下：

1. Miuix/Compose 兼容性和最小构建配置。
2. Theme、路由、应用壳。
3. 首次启动 Dialog 和全局任务入口。
4. Instances 工作台。
5. Tools 工作台。
6. Settings 分类和设置适配层。
7. Workshop Browse/Downloads/Library。
8. Workshop 详情和账户。
9. 旧主壳/旧 Workshop UI 清理。
10. 自动化测试、静态检查和设备验收记录。

每个提交前运行与该切片对应的最小测试；阶段末再运行完整验证命令。除非用户另行批准，不在这些提交中混入底层修复、依赖大版本升级或游戏界面改造。

## 11. 计划批准门

本计划生成后暂停执行，等待用户明确批准。批准前不修改生产代码、不接入 Miuix、不删除旧 UI。

批准后执行顺序固定为：阶段 0 → 阶段 1 → 阶段 2 → 阶段 3 → 阶段 4 → 阶段 5 → 阶段 6 → 阶段 7。任一阶段发现必须修改底层代码的阻塞项，立即停止该分支并报告具体文件、原因和最小替代方案。
