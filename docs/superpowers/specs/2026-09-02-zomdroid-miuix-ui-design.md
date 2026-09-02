# Zomdroid Miuix UI 重构设计

日期：2026-09-02  
状态：待用户审批  
关联项目：`zomdroid`  
Miuix 源码：`D:\apps\appss\ws\app\miuix`

## 1. 设计结论

本次工作是一次主应用 UI 与交互重构，不是对现有 XML 页面进行换肤。主应用的页面布局、信息架构、导航、反馈方式和操作路径重新设计为 Kotlin Compose + Miuix；现有 Java 业务层、服务、数据层和文件处理逻辑保持不变。

最终目标是：主应用管理界面由一个 Compose 根页面承载，使用 `MiuixTheme`、Miuix 组件和 `miuix-nav` 管理页面栈。迁移期间可以暂时使用 Fragment 作为承载容器，但 Fragment/XML 不是最终架构。

游戏运行时的渲染承载、虚拟输入层和自定义控制编辑器属于本轮明确保留的原生范围。它们的周边普通页面可以使用 Miuix，但不修改自定义 Canvas、渲染、输入和配置存储实现。

## 2. 背景与现状

### 2.1 Zomdroid 当前 UI

当前应用是单模块 Android 应用，使用：

- Java Activity/Fragment
- XML Layout 与 ViewBinding
- AndroidX Navigation Fragment
- Material 组件和 Material 主题
- `DrawerLayout` + `MaterialToolbar` 作为启动器外壳
- 多个功能 Fragment：实例、设置、安装器、优化、Workshop、账号和下载中心
- `GameActivity` 使用 `GameInputView` 与 `InputControlsView` 叠加游戏画面
- `ControlsEditorActivity` 使用自定义 `InputControlsView` 和 XML 属性面板

当前项目已具备 Workshop 浏览、详情、下载中心、账号认证和模组库等业务能力。这些能力不是本次设计的新增功能，UI 重构只重新组织它们的入口和呈现方式。

### 2.2 Miuix 当前能力

本地 Miuix 是 Compose Multiplatform 库，包含：

- `miuix-ui`：主题、基础组件、Scaffold、TopAppBar、Button、Card、SearchBar、TabRow、ProgressIndicator、BottomSheet、Dialog 等
- `miuix-preference`：Preference、ArrowPreference、SwitchPreference、CheckboxPreference、Spinner/Dropdown Preference 等
- `miuix-icons`：基础和扩展图标
- `miuix-nav`：基于可序列化路由和连续栈的 Compose 导航运行时
- `miuix-blur`：可选模糊效果，Android 最低版本要求高于 Zomdroid 的 API 30

Miuix 当前是实验性 API，接入时必须锁定版本/源码状态，不能依赖未验证的自动升级。

## 3. 目标

### 3.1 产品目标

1. 将主应用从“历史功能抽屉”改为以用户任务为中心的应用结构。
2. 让用户打开应用后可以直接看到、启动和管理游戏实例。
3. 将 Workshop 的浏览、详情、下载和模组库组织成连续工作流。
4. 将安装、控制、性能和诊断工具集中管理，减少入口分散。
5. 让设置页按主题分组，避免一张过长且难以扫描的表单。
6. 统一加载、空状态、错误、任务、确认和恢复交互。
7. 在手机、平板和横屏中保持合理的信息密度。
8. 保留现有功能结果、数据格式和底层副作用。

### 3.2 技术目标

1. 主应用普通页面最终全部使用 Kotlin Compose + Miuix。
2. 最终使用 Miuix 应用壳和 `miuix-nav`，不保留主应用的 Fragment/XML 页面作为最终实现。
3. 通过 Kotlin UI 状态层将现有 Java 业务接入 Compose。
4. Miuix 作为新页面的唯一视觉组件体系；不在新页面混用 Material 控件。
5. 只为 Compose/Miuix 接入修改必要的构建配置。

## 4. 非目标与硬性边界

以下约束优先级高于所有视觉和架构偏好。

### 4.1 允许修改

- Compose/Miuix 页面和组件
- 页面导航、转场、返回和 UI 交互
- UI 层 ViewModel、StateFlow、UiState 和 Adapter
- 页面主题、图标、字符串和布局资源
- UI 层图片加载与缓存
- 为接入 Compose/Miuix 必需的 Kotlin、Compose 和 Miuix Gradle 配置
- 临时 Fragment/Activity 承载代码中与 UI 绑定相关的部分

### 4.2 禁止修改

- `InstallerService` 的安装、解压、任务和后台逻辑
- Workshop 网络、Steam 协议、认证、下载、解析和任务核心逻辑
- `GameInstance` 模型、持久化结构和实例目录规则
- 游戏启动逻辑、JRE/JVM 参数语义和运行环境处理
- JNI、C/C++、渲染器、原生库和游戏画面承载逻辑
- 文件安装、备份、路径校验和压缩包处理逻辑
- Workshop 下载中心的数据格式和任务持久化格式
- 已有功能的数量、结果、数据格式和副作用
- 与 UI 无关的单元测试、底层配置和核心实现

新增 UI Adapter 不得反向修改底层代码以适配 UI；如果现有 API 不足，优先在 UI 层包装现有 API。

### 4.3 范围纠正记录

- 本设计不依据工作区中可能存在的旧截图判断当前 UI；当前源码和资源才是依据。
- “依赖初始化页面”方案已撤回。首次运行继续使用原有进度 Dialog，只替换为 Miuix UI。
- “游戏内虚拟控制层和控制编辑器全部 Compose 化”方案已撤回。本次主应用管理 UI 使用 Miuix，游戏画面和自定义输入 Canvas 暂不迁移。
- “全 Compose”指最终主应用管理界面，不代表修改底层游戏渲染和输入实现。

## 5. 目标架构

### 5.1 最终应用结构

```text
LauncherActivity
└── Compose 根页面
    ├── ZomdroidMiuixTheme
    ├── Miuix Scaffold
    │   ├── TopAppBar
    │   ├── 页面内容
    │   ├── 全局任务入口
    │   └── NavigationBar / NavigationRail
    └── miuix-nav
        ├── Instances
        ├── Workshop
        ├── Tools
        ├── Settings
        └── 子页面栈
```

`GameActivity` 和 `ControlsEditorActivity` 继续是独立的 Android 宿主；本设计不要求替换它们的渲染或输入底层。

### 5.2 迁移期间结构

迁移期间可以让旧 Fragment 暂时返回 Compose 内容，作为回滚和逐页验证手段：

```text
旧 Navigation/Fragment 容器
└── ComposeView
    └── Miuix Screen
```

这只是迁移策略，不是最终产品结构。新页面不能简单复刻旧 XML 的布局层级、卡片分组和按钮排列。

### 5.3 UI 状态边界

```text
现有 Java 服务 / 数据 / 业务 API
        ↓
UI 层 Kotlin Adapter / Repository facade
        ↓
ViewModel + StateFlow + UiState
        ↓
Compose/Miuix Screen
```

Compose 页面只负责渲染状态和发出用户事件。安装、下载、认证、文件操作等副作用由现有能力执行；UI 层只负责调用、订阅和映射。

建议每个主页面显式建模以下状态：

- `Loading`
- `Content`
- `Empty`
- `Error`
- `Refreshing`
- `Submitting` 或 `Running`
- `Success` / `Completed`

不在 Composable 中直接访问 `SharedPreferences`、启动服务或执行业务线程。

## 6. 导航与应用壳

### 6.1 一级导航

四个根入口：

1. 实例
2. Workshop
3. 工具
4. 设置

手机使用底部 `NavigationBar`；中等及以上宽度使用 `NavigationRail`。一级导航只承载根任务，不放 Wiki、捐赠、GitHub 或每一个历史工具入口。

### 6.2 子页面

实例详情、新建实例、Workshop 详情、账号管理、工具详情和设置子页面进入导航栈后隐藏一级导航，TopAppBar 显示返回按钮和标题。

页面返回时恢复：

- 搜索词
- 排序和筛选
- Tab
- 列表滚动位置
- 未提交的表单状态（在安全范围内）

### 6.3 转场

- 普通页面：Miuix 默认横向页面转场
- 详情页：普通页面转场
- 排序、筛选、选择器：OverlayPopup 或 BottomSheet
- 确认和危险操作：OverlayDialog
- 返回：支持系统返回和边缘返回手势
- 不使用夸张动画，也不为动画修改底层行为

## 7. 页面设计

### 7.1 实例首页

实例页是默认首页，目标是“打开后快速启动游戏”。

页面结构：


- TopAppBar：标题、全局任务入口和必要的页面操作
- 实例内容区：手机单列，平板/横屏两列
- 实例卡片：Banner、名称、Build、安装状态、最近启动信息
- 主操作：启动
- 次要操作：实例详情或右上角菜单
- FloatingActionButton：新建实例
- 空状态：说明所需准备内容，并提供“创建实例”

卡片操作规则：

- 点击卡片进入实例详情
- 启动按钮直接执行现有启动流程
- 未安装完成、缺失游戏文件或缺失依赖时显示可操作原因
- 备份恢复、存储管理和删除放入实例详情或菜单

### 7.2 实例详情

实例详情保持轻量，集中承载实例级管理：

- Banner、实例名、Build 版本
- 启动按钮
- 安装状态和任务进度
- 备份恢复
- 存储管理
- 游戏设置导入/导出
- 删除实例

渲染器、分辨率、JVM 参数、环境变量、内存优化等仍按现有语义属于全局运行设置，不在详情页伪装成实例独立配置。

### 7.3 新建实例

新建实例不使用多步向导，采用单页紧凑表单：

- 实例名称
- 必选游戏文件
- 自动检测到的 Build 和 Banner
- 折叠的附加内容：原生库、存档、Mod
- 底部固定“开始安装”

校验和检测结果内联显示，不以 Toast 作为主要反馈。安装启动后返回实例首页，由实例卡片显示现有任务状态。

### 7.4 Workshop 工作区

Workshop 作为一个连续工作区，包含三个 Tab：

- 浏览
- 下载
- 模组库

浏览页：

- 顶部 Miuix SearchBar
- 排序和筛选放入菜单或 BottomSheet
- 紧凑列表项，左侧封面，右侧标题、作者、更新时间、大小和状态
- 整行进入详情
- 每项只保留一个下载/状态操作
- 当前源码中的分页行为由 UI 改为加载更多或等价的连续加载表现；底层查询和分页数据不改

详情页：

- 封面和标题
- 作者、更新时间、文件大小等元数据
- 主要下载操作
- 描述、标签、更新日志、评论和依赖
- Steam 页面链接
- 依赖批量下载入口
- 目标实例选择和安装入口

模组库：

- 显示已下载版本和更新状态
- 支持手动/低频更新检查
- 支持安装到一个或多个实例
- 支持旧版本清理、分享和删除

账号状态：

- 未登录可浏览公开内容
- 访问受限内容或下载需要账号时按需提示登录
- 登录、Steam Guard、设备确认和多账号切换使用独立账号流程
- 不显示或存储明文密码、Token、完整 Cookie

### 7.5 全局下载中心

顶部任务入口显示活动任务数量或状态 Badge，点击进入统一下载中心。

统一展示：

- 实例安装任务
- Workshop 下载任务
- 队列状态
- 进度
- 暂停、继续、重试、取消、删除
- 错误原因
- 安装到实例
- 第三方备用下载的明确确认入口

任务在后台运行，不阻塞当前页面。底层任务队列、前台服务、通知和持久化逻辑不改，UI 只重新订阅和展示。

### 7.6 工具中心

工具首页按任务分组：

#### 安装与导入

- Mod
- 控制方案
- 存档
- 原生库
- 驱动
- Steam 游戏文件

#### 控制

- 触控控制
- 控制器映射
- 控制编辑器入口

#### 性能与诊断

- 优化设置
- 游戏设置导入/导出
- 导出日志
- 故障排查/Wiki

每个工具使用 Miuix `ArrowPreference`，显示标题、摘要和箭头。具体工具页使用 Miuix 表单、状态卡片、Dialog 和任务入口，但调用现有安装与导入逻辑。

### 7.7 设置中心

设置首页只展示分类入口：

- 外观：浅色/深色、动态颜色、语言
- 游戏运行：渲染器、驱动、分辨率、推荐预设
- 控制：触控和震动
- 数据：备份、存储、日志
- 高级：JVM 参数、环境变量、调试模式、内存优化

设置子页使用 Miuix Preference、Switch、Slider、Spinner/Dropdown 和 TextField。配置作用域保持现有定义，尤其不把全局运行设置改成实例设置。

### 7.8 首次启动与版本更新

首次启动流程：

```text
法律声明 Miuix OverlayDialog
        ↓
现有依赖安装进度 Miuix Dialog
        ↓
实例首页
```

依赖安装不新增页面，不修改 `InstallerService`，继续沿用现有任务和检查机制。版本更新说明同样使用可关闭的 Miuix Dialog；开源许可、第三方服务和素材归属在“关于”页长期可访问。

## 8. 主题与组件策略

### 8.1 主题

- 使用 Miuix 默认蓝灰配色
- 默认跟随系统浅色/深色模式
- 不使用旧的 Zomdroid 橙色作为主题色
- 可选提供 Miuix Monet 动态颜色
- 不硬编码业务颜色；语义状态使用 Miuix 主题色
- 不以 Blur 作为基础视觉
- API 33 以上可选启用 Blur，API 30/31/32 使用 surface/半透明降级

### 8.2 组件

- `MiuixTheme`
- `Scaffold`
- `TopAppBar`
- `NavigationBar` / `NavigationRail`
- `Card` / `Surface`
- `SearchBar`
- `TabRow`
- `ArrowPreference` / `SwitchPreference` / `CheckboxPreference`
- `Button` / `IconButton` / `FloatingActionButton`
- `ProgressIndicator`
- `OverlayDialog` / `OverlayBottomSheet` / `OverlayListPopup`
- Miuix Icons

新页面不使用旧 Material 控件作为视觉组件，不继续使用旧渐变工具栏、橙色分割线和文字字符图标。

### 8.3 图片

- 本地 Banner 使用 Compose 资源加载
- Workshop 封面和描述图在 UI 层使用图片加载器
- UI 层负责内存/磁盘缓存、占位、失败重试和固定比例裁剪
- 不修改 Workshop 数据模型和网络客户端
- 图片加载失败不能阻塞详情或下载操作

## 9. 可访问性、国际化与响应式

### 9.1 可访问性

- 所有文本来自现有多语言资源体系
- 可点击区域至少 48dp
- 图标按钮提供 ContentDescription
- TalkBack 能读出标题、摘要、当前状态和操作结果
- 状态不能只依赖颜色
- 支持系统字体放大，避免固定高度裁切
- 支持 RTL

### 9.2 响应式断点

- Compact：手机，底部导航、单列列表
- Medium：`w600dp` 附近，NavigationRail、实例两列
- Expanded：更宽内容区；Workshop 可采用列表/详情双栏，实例使用多列网格
- 设置和工具页限制最大内容宽度，避免平板上过度拉伸

## 10. 失败与危险操作

所有页面统一使用以下状态：

- 加载中
- 下拉刷新
- 空列表
- 网络错误
- 离线但有缓存
- 未登录/权限不足
- 下载失败
- 安装进行中
- 安装成功/失败

危险操作必须明确确认：

- 删除实例
- 覆盖安装
- 删除已下载文件
- 清空任务
- 删除账号
- 清理旧版本

确认界面显示影响范围，默认操作为取消，破坏性操作使用 Miuix error 语义色。实际删除、覆盖和备份逻辑不变。

## 11. 迁移策略

### 11.1 原则

1. 先建立 Miuix 主题和根应用壳。
2. 页面按用户任务重构，不逐个照搬 XML。
3. 每个迁移阶段可编译、可运行、可回滚。
4. 新页面稳定后删除旧 XML 和旧 Material UI 绑定。
5. 不提供正式用户可见的旧 UI 回退开关。
6. 使用 Git 提交边界进行回滚，不通过新增功能开关扩大复杂度。

### 11.2 页面顺序

1. 构建配置、Miuix 主题和 Compose 根壳
2. 实例首页、实例详情、新建实例
3. 首次启动 Dialog、依赖进度 Dialog、任务入口
4. 设置分类首页和设置子页面
5. Workshop 浏览、详情、下载和模组库
6. 工具中心和普通工具页面
7. 账号、Wiki、关于和普通内容页
8. 清理旧主应用 XML、Fragment 承载层和 Material UI 资源

## 12. 风险与控制措施

### 12.1 Kotlin/Compose/Miuix 版本不兼容

Miuix 本地源码当前使用的 Kotlin/Compose 版本可能高于 Zomdroid 现有版本。接入必须先验证 Gradle、Kotlin、Compose Compiler、AGP、minSdk 和 Java 11 的兼容性。只允许修改接入所必需的构建配置，不能借机升级或重构业务依赖。

### 12.2 UI 状态与旧 Java 回调脱节

通过 UI Adapter、ViewModel 和明确的 UiState 统一订阅；不在 Composable 中直接绑定服务生命周期。旧任务和认证状态必须能够在页面重建后重新读取。

### 12.3 Workshop 图片影响滚动性能

使用 UI 层图片缓存、尺寸约束、占位和列表复用。图片失败不影响文本和下载按钮。

### 12.4 旧功能入口在重构中丢失

以当前 `nav_graph.xml`、`menu_nav.xml` 和全部普通 Fragment 为功能清单，建立入口映射表。每个旧入口必须映射到新页面或新页面中的等价操作。

### 12.5 重构范围意外扩展到底层

每个阶段结束后检查 Git diff。除 UI 文件和必要构建配置外，禁止出现 `workshop` 核心、`InstallerService`、JNI、C/C++、游戏启动和持久化文件改动。

## 13. 验收标准

### 13.1 产品验收

- 默认进入实例首页
- 四个一级入口工作正常
- 根页面和子页面导航层级清晰
- 新建实例为单页紧凑流程
- Workshop 浏览、详情、下载、模组库连续可用
- 下载中心可从全局入口进入
- 设置分类和全局配置作用域清晰
- 首次启动包含法律声明和依赖安装进度 Dialog
- 账号登录、Steam Guard 和按需登录入口可用
- 危险操作有明确确认

### 13.2 兼容性验收

- 现有游戏启动、实例管理、手动 ZIP 导入和安装流程行为不变
- 现有 Workshop 下载、认证、任务恢复和模组安装行为不变
- 现有持久化文件和数据格式不变
- 现有底层单元测试保持通过

### 13.3 UI 验收

- Miuix 默认蓝灰主题生效
- 新页面不依赖旧橙色主题和 Material 控件
- 手机与 `w600dp` 以上布局可用
- 浅色/深色、动态颜色、字体放大、多语言和 RTL 可用
- 加载、空、错误、离线和任务状态可见且可操作
- Compose UI 测试覆盖根导航、空状态、错误状态、表单校验和关键按钮状态

### 13.4 代码边界验收

- 非 UI 业务代码无修改
- `InstallerService`、Workshop 核心、JNI/C++、游戏启动和持久化无修改
- 仅保留 Compose/Miuix 接入所需的构建配置变化
- 旧页面删除前已完成行为回归验证

## 14. 设计审批门槛

本设计获批后，下一阶段才生成实施计划。实施计划必须为每项工作列出：

- 精确文件路径
- 原子化修改内容
- 先写的测试或验证
- 执行命令
- 预期结果
- 对应的 UI 范围说明

在设计获批前不执行生产代码修改，也不生成会推动实现的计划文件。
