# Zomdroid 统一动画系统设计

日期：2026-09-06

## 1. 目标

统一 Zomdroid 各页面的动效节奏、方向、插值和触发时机，解决以下问题：

- 页面之间有的使用横向滑入，有的没有转场；
- Workshop、下载中心和其他列表使用了不同的内容入场方式；
- 设置页折叠、异步加载、错误/空状态切换存在突然出现或消失；
- 弹窗、抽屉、底部导航和卡片交互缺乏统一的动效边界；
- 动态刷新时列表反复播放动画，或滚动后出现不必要的重复动画。

本次只统一动画表现，不改变现有信息架构、底部/抽屉导航结构、主题颜色和业务流程。

## 2. 现状检查

当前动画实现主要分散在以下位置：

- `app/src/main/res/navigation/nav_graph.xml`：只有部分 Workshop action 配置了 enter/exit/pop 动画；多数页面 action 使用 Navigation 默认行为。
- `app/src/main/res/anim/workshop_*.xml`：Workshop 页面使用 180–220ms 的透明度和横向位移动画。
- `app/src/main/res/anim/workshop_content_enter.xml` 与 `workshop_content_layout.xml`：仅部分列表或详情区域使用纵向入场和 layout animation。
- `WorkshopDownloadCenterFragment.java`：下载任务新增时手动调用 `startAnimation`，与其他列表不共享策略。
- `ControlsEditorActivity.java`：编辑器设置卡片使用独立的 `ViewPropertyAnimator`，没有引用统一的时间和插值常量。
- `WorkshopFragment.java`：收藏按钮有独立缩放反馈。
- `DrawerLayout`、Material Dialog、RecyclerView 默认 item animator：依赖控件默认动画，导致不同页面表现不一致。

因此问题不是单个动画资源缺失，而是缺少一套覆盖“页面转场、内容入场、状态切换、交互反馈”的动画契约。

## 3. 动画原则

### 3.1 动画只表达状态变化

动画用于说明页面进入、返回、内容出现、状态变化和用户操作反馈；不为每个控件强行添加动画。静态表单和长文本页面保持稳定，避免影响阅读和输入。

### 3.2 统一方向

- 进入下一级页面：新页面从右侧轻微滑入，旧页面向左轻微退出。
- 返回上一级页面：方向完全镜像。
- 弹出操作层：从中心轻微缩放并淡入；关闭时淡出并缩回。
- 内容加载完成：淡入并从下方短距离出现。
- 同一页面内状态替换：交叉淡化，不使用横向页面转场。

位移保持克制，页面转场使用约 4%–8% 的屏幕宽度，内容入场使用约 8dp–16dp，避免“飞入”感。

### 3.3 统一节奏

动画时间分为三个档位：

| Token | 用途 | 时长 |
| --- | --- | ---: |
| `motion_fast` | 按钮、收藏、导航选中反馈 | 140–160ms |
| `motion_standard` | 内容入场、状态切换、面板展开 | 200–240ms |
| `motion_emphasis` | 页面转场、弹窗出现 | 260–300ms |

进入使用减速插值，退出使用加速插值，状态交叉淡化使用标准 Material 插值。所有自定义动画必须引用这些 token，不再在 Java 中散落硬编码时长。

## 4. 技术方案

采用“资源 token + 小型动画工具类 + Navigation 统一配置”的方案。

### 4.1 动画资源和 token

新增统一资源：

- `app/src/main/res/values/motion.xml`：集中声明时长、位移比例/距离和必要的动画参数；
- `app/src/main/res/anim/motion_forward_enter.xml`；
- `app/src/main/res/anim/motion_forward_exit.xml`；
- `app/src/main/res/anim/motion_back_enter.xml`；
- `app/src/main/res/anim/motion_back_exit.xml`；
- `app/src/main/res/anim/motion_content_enter.xml`；
- `app/src/main/res/anim/motion_fade_through.xml`；
- `app/src/main/res/anim/motion_dialog_enter.xml` 和对应退出资源（仅自定义弹出层使用）。

现有 `workshop_*` 资源不立即删除，先由统一资源替代其调用；确认没有引用后再清理，避免破坏已有 Workshop 路由。

### 4.2 页面转场

为 `nav_graph.xml` 中所有普通页面 action 补齐同一组 enter/exit/pop 动画，覆盖启动器、实例创建/设置、应用设置、安装/优化/控制、Steam 下载以及 Workshop 相关页面。

Workshop 相关的 programmatic `NavOptions` 也改为引用统一动画资源。相同目的地、重复点击和返回栈恢复不额外叠加动画。

游戏运行中的 `GameActivity` 和游戏内输入控件不纳入普通页面转场，避免影响渲染和触控响应。

### 4.3 内容入场和列表

新增一个轻量工具类，例如 `com.zomdroid.ui.MotionAnimations`，只提供三类能力：

1. 对页面首次绑定时的可见子项执行一次交错淡入；
2. 对 loading/content/error/empty 容器执行交叉淡化；
3. 对展开/收起区域执行统一的高度和透明度变化。

列表规则：

- 首屏最多交错播放 6 个可见项，单项间隔 20–30ms；
- RecyclerView 滚动回收和复用时不重复播放整页入场动画；
- 列表数据刷新优先使用 `DefaultItemAnimator` 的增删改动画，禁止重新触发布局动画；
- Workshop 列表、收藏、Mod 库、下载任务和 Workshop 详情的依赖/评论区域使用同一内容入场资源；
- 大量下载任务或长列表只动画可见范围，避免一次创建大量动画对象。

### 4.4 异步状态

网络、下载、安装和文件分析页面统一采用 `loading -> content/error/empty` 的交叉淡化。重复状态回调不重新播放；进度数值更新不做整块页面动画，只更新进度条和文本。

重点覆盖 Workshop 搜索/详情、Workshop 账号、下载中心、Steam 下载、存档/驱动/模组安装和优化页面。

### 4.5 交互反馈

- 保留 Android/Material ripple 作为触摸按下反馈，不给所有按钮统一叠加缩放，避免与 ripple 冲突；
- 收藏按钮继续使用缩放反馈，但改用统一 `motion_fast`；
- 底部导航选中项使用短时的透明度/缩放或指示器移动，不改变导航布局高度；
- 现有 DrawerLayout 使用其原生滑动和遮罩动画，不另加一层平移动画；
- 设置页高级区域、控制编辑器侧面设置卡片、可展开说明区域统一使用 `motion_standard`；
- 删除下载任务等确认弹窗使用现有 Material Dialog 的单一进入动画，不在弹窗内容内部再次播放页面转场。

### 4.6 无障碍和性能

- 不使用持续循环动画、模糊动画或大面积阴影变换；
- 自定义动画只使用 alpha、translation、scale 等硬件友好属性；
- 遵循 Android 系统动画缩放设置，系统要求减少动画时仍可直接到达最终状态；
- 页面离开时取消未完成的 View 动画，避免 Fragment 销毁后回调旧 View；
- 适配 60Hz 模拟器和低性能设备，长列表不出现明显卡顿。

## 5. 实施阶段

### 阶段 1：动画基础设施

- 定义 motion token 和统一资源；
- 建立 `MotionAnimations` 工具类；
- 先替换 Workshop 现有资源，保证行为不回退。

验证：资源编译、Java/Kotlin 编译、现有 Workshop 单元测试通过。

### 阶段 2：全局页面转场

- 补齐 Navigation action；
- 统一 programmatic navigation；
- 检查前进、返回、重复点击和深链路由。

验证：导航图资源校验、Debug 构建；实机检查所有普通页面前进/返回方向一致。

### 阶段 3：列表和异步状态

- 接入启动器、Workshop、收藏、Mod 库、下载中心和详情列表；
- 接入 loading/content/error/empty 交叉淡化；
- 防止刷新和滚动重复播放。

验证：单元测试覆盖状态切换；实机检查列表滚动、刷新、分页和下载进度更新。

### 阶段 4：局部交互和收尾

- 统一设置折叠、控制编辑器面板、收藏和底部导航反馈；
- 检查弹窗、抽屉与主题切换下的动画一致性；
- 清理已无引用的旧动画资源。

验证：完整 Debug 构建、单元测试、动画缩放为 0/1 时的实机检查，以及 MuMu 实机回归。

## 6. 验收标准

1. 所有普通 Fragment 页面前进、返回动画方向和节奏一致；Workshop 不再拥有独立的转场规则。
2. 首次进入列表时有克制的内容入场；滚动复用、分页刷新和下载进度更新不会整页重复播放。
3. loading、成功、错误和空状态切换有统一交叉淡化，且重复状态回调不闪烁。
4. 设置折叠、控制编辑器面板、收藏、底部导航和弹窗不再各自使用不同的时长/插值。
5. 不改变现有业务行为、导航返回栈、下载任务状态和游戏运行性能。
6. `:app:assembleDebug` 与 `:app:testDebugUnitTest` 通过；最终由 MuMu 实机检查动画流畅性和低动画设置兼容性。

## 7. 当前假设与边界

- “统一动画”默认指 Android 启动器内的 UI 页面，不包含游戏画面和游戏内虚拟按键的渲染动画。
- 保留当前导航架构；本任务不重新设计底部导航或抽屉。
- 不引入 Compose、MotionLayout 或新的大型动画库。
- 动画默认开启，并遵循系统动画缩放；本任务暂不新增应用内“关闭动画”开关。

