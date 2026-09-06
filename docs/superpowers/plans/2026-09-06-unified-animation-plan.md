# Zomdroid 统一动画系统实施计划

关联设计：`docs/superpowers/specs/2026-09-06-unified-animation-design.md`

## 执行边界

- 只修改启动器 UI 的动画表现，不改变导航结构、业务逻辑、主题颜色、下载状态和游戏运行页面。
- 不引入 Compose、MotionLayout 或新的第三方动画库。
- 每个任务完成后先运行该任务的验证命令，再进入下一个任务。
- 保留当前工作区中与动画无关的已有修改，不执行 reset、clean 或大范围格式化。
- 阶段 4 的 MuMu 实机检查由用户完成；代码侧负责构建、单元测试和资源引用检查。

## 阶段 0：基线与动画调用清单

### 任务 0.1：建立构建基线

范围：不修改生产代码。

动作：

1. 记录 `git status --short`，区分本任务已有脏文件和本任务新增文件。
2. 执行 `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console plain --no-daemon`。
3. 记录当前 Debug APK 路径和大小。

验证：基线构建成功；失败时先记录现有失败，不把它归因于动画任务。

### 任务 0.2：固定需要覆盖的入口

范围：只读检查以下文件：

- `app/src/main/res/navigation/nav_graph.xml`
- `app/src/main/java/com/zomdroid/LauncherActivity.java`
- `app/src/main/java/com/zomdroid/fragments/LauncherFragment.java`
- `app/src/main/java/com/zomdroid/fragments/WorkshopFragment.java`
- `app/src/main/java/com/zomdroid/fragments/WorkshopFavoritesFragment.java`
- `app/src/main/java/com/zomdroid/fragments/WorkshopDetailFragment.java`
- `app/src/main/java/com/zomdroid/fragments/WorkshopAccountFragment.java`
- `app/src/main/java/com/zomdroid/fragments/WorkshopModLibraryFragment.java`
- `app/src/main/java/com/zomdroid/fragments/WorkshopDownloadCenterFragment.java`
- `app/src/main/java/com/zomdroid/fragments/SteamDownloadFragment.java`
- `app/src/main/java/com/zomdroid/fragments/SettingsFragment.java`
- `app/src/main/java/com/zomdroid/fragments/OptimizationFragment.java`
- `app/src/main/java/com/zomdroid/ControlsEditorActivity.java`

动作：保存 `navigate`, `startAnimation`, `layoutAnimation`, `setVisibility`、`animate()` 和 `scheduleLayoutAnimation()` 的当前位置，作为后续清理依据。

验证：

```powershell
rg -n 'navigate\\(|startAnimation|layoutAnimation|setVisibility|scheduleLayoutAnimation|\\.animate\\(' app/src/main/java app/src/main/res --glob '*.java' --glob '*.xml'
```

输出中每个现有动画调用都必须能归入后续阶段 2–4 的一个具体任务。

## 阶段 1：动画基础设施

### 任务 1.1：定义统一 motion token

文件：

- 新增 `app/src/main/res/values/motion.xml`

动作：定义以下资源，数值固定且与设计文档一致：

- `motion_fast`：160ms；
- `motion_standard`：220ms；
- `motion_emphasis`：280ms；
- `motion_page_offset_percent`：8；
- `motion_content_offset_dp`：12；
- `motion_list_stagger_ms`：24；
- `motion_list_max_staggered_items`：6。

验证：执行 `./gradlew.bat :app:assembleDebug --console plain --no-daemon`；资源解析成功且没有重复资源名。

### 任务 1.2：建立统一页面和内容动画资源

文件：

- 新增 `app/src/main/res/anim/motion_forward_enter.xml`
- 新增 `app/src/main/res/anim/motion_forward_exit.xml`
- 新增 `app/src/main/res/anim/motion_back_enter.xml`
- 新增 `app/src/main/res/anim/motion_back_exit.xml`
- 新增 `app/src/main/res/anim/motion_content_enter.xml`
- 新增 `app/src/main/res/anim/motion_fade_through.xml`
- 新增 `app/src/main/res/anim/motion_dialog_enter.xml`
- 新增 `app/src/main/res/anim/motion_dialog_exit.xml`

动作：

- 页面前进/返回使用透明度和横向轻微位移，前进与返回方向互为镜像；
- 内容入场使用透明度和 12dp 纵向位移；
- fade-through 只做同页面状态替换；
- dialog 资源只用于自定义操作层，不叠加在 MaterialAlertDialog 上；
- 所有 duration 和 offset 引用 `motion.xml`，不在资源中重复写数值。

验证：执行 `git diff --check` 和 `./gradlew.bat :app:assembleDebug --console plain --no-daemon`。

### 任务 1.3：建立 MotionAnimations 工具类及纯策略测试

文件：

- 新增 `app/src/main/java/com/zomdroid/ui/MotionAnimations.java`
- 新增 `app/src/test/java/com/zomdroid/ui/MotionAnimationsPolicyTest.kt`

动作：`MotionAnimations` 只提供以下 API：

- `forwardNavOptions()`：返回统一前进/返回资源的 `NavOptions`；
- `animateContentEnter(View, long)`：对单个新增内容执行一次入场；
- `animateFirstVisibleChildren(ViewGroup)`：最多处理 6 个可见子项，并按 24ms 递增延迟；
- `crossfade(View, View)`：显示新状态并淡出旧状态；
- `setExpanded(View, boolean)`：对可折叠内容执行统一展开/收起；
- `cancel(View...)`：页面销毁时取消未完成动画。

`MotionAnimationsPolicyTest` 覆盖：160/220/280ms 三档、最大 6 项限制、延迟递增和空/不可见子项跳过规则。测试不依赖网络、Steam 或真实设备。

验证：先执行 `./gradlew.bat :app:testDebugUnitTest --tests '*MotionAnimationsPolicyTest' --console plain --no-daemon`，再执行 `git diff --check`。

## 阶段 2：全局页面转场

### 任务 2.1：统一 Navigation action

文件：

- `app/src/main/res/navigation/nav_graph.xml`

动作：为以下 action 补齐 `app:enterAnim`、`app:exitAnim`、`app:popEnterAnim`、`app:popExitAnim`，全部引用任务 1.2 的资源：

- `action_open_new_game_instance_fragment`
- `action_open_settings_fragment`
- `action_open_instance_settings`
- `action_open_gamepad_mapper`
- `action_open_wiki_fragment`
- `action_open_install_mod`
- `action_install_controls`
- `action_install_saves`
- `action_install_driver`
- `action_export_log`
- `action_open_optimization`
- `action_open_controls_editor_launch`
- `action_game_settings`
- `action_install_native_libs`
- `action_download_steam`
- `action_open_workshop_favorites`
- `action_open_workshop_download_center`
- `action_open_workshop`
- `action_open_workshop_account`
- `action_open_workshop_library`
- `action_install_mod_open_workshop`
- `action_workshop_favorite_detail`
- `action_download_center_open_workshop_account`
- `action_workshop_detail`

验证：执行 `./gradlew.bat :app:assembleDebug --console plain --no-daemon`；再用 `rg` 检查上述 action 不存在缺少动画属性的情况。

### 任务 2.2：统一直接 destination 导航

文件：

- `app/src/main/java/com/zomdroid/ui/MotionAnimations.java`
- `app/src/main/java/com/zomdroid/LauncherActivity.java`
- `app/src/main/java/com/zomdroid/fragments/LauncherFragment.java`
- `app/src/main/java/com/zomdroid/fragments/NewGameInstanceFragment.java`
- `app/src/main/java/com/zomdroid/fragments/OptimizationFragment.java`
- `app/src/main/java/com/zomdroid/fragments/SettingsFragment.java`
- `app/src/main/java/com/zomdroid/fragments/SteamDownloadFragment.java`

动作：

- 所有直接使用 destination ID 的 `navigate` 调用改为传入 `MotionAnimations.forwardNavOptions()`；
- 保留现有 arguments 和 action ID，不改变返回栈；
- `LauncherActivity.workshopForwardNavOptions()` 改为复用统一工具类，删除重复动画配置；
- 不对相同 destination 的重复点击增加额外动画。

验证：执行 `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console plain --no-daemon`；检查上述文件中不再存在未传统一 NavOptions 的直接 destination 导航。

## 阶段 3：列表和异步状态动画

### 任务 3.1：统一 RecyclerView 初次入场和 ItemAnimator

文件：

- `app/src/main/java/com/zomdroid/fragments/LauncherFragment.java`
- `app/src/main/java/com/zomdroid/fragments/WorkshopFragment.java`
- `app/src/main/java/com/zomdroid/fragments/WorkshopFavoritesFragment.java`
- `app/src/main/java/com/zomdroid/fragments/WorkshopDetailFragment.java`
- `app/src/main/res/layout/fragment_launcher.xml`
- `app/src/main/res/layout/fragment_workshop.xml`
- `app/src/main/res/layout/fragment_workshop_favorites.xml`
- `app/src/main/res/layout/fragment_workshop_detail.xml`

动作：

- 删除上述页面对 `workshop_content_layout` 的重复 layout animation 引用；
- RecyclerView 初始化后使用统一 ItemAnimator 和 `animateFirstVisibleChildren`；
- Workshop 搜索、分页、收藏刷新和详情轮播不再通过 `scheduleLayoutAnimation()` 重播整页；
- 仅首次内容绑定或明确新增项触发入场，滚动回收不触发；
- 详情轮播保留 PagerSnapHelper 行为，只对新选中的图片做轻微透明度切换。

验证：执行 `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console plain --no-daemon`；静态检查不存在上述页面的 `scheduleLayoutAnimation()`。

### 任务 3.2：统一 LinearLayout 动态内容入场

文件：

- `app/src/main/java/com/zomdroid/fragments/WorkshopAccountFragment.java`
- `app/src/main/java/com/zomdroid/fragments/WorkshopModLibraryFragment.java`
- `app/src/main/java/com/zomdroid/fragments/WorkshopDownloadCenterFragment.java`
- `app/src/main/res/layout/fragment_workshop_account.xml`
- `app/src/main/res/layout/fragment_workshop_mod_library.xml`
- `app/src/main/res/layout/fragment_workshop_download_center.xml`

动作：

- 移除 `fragment_workshop_account.xml` 的 layout animation；
- 账号行、Mod 库条目、下载任务卡片使用 `animateContentEnter`；
- 下载中心只对本次新增的 task view 动画，状态刷新只更新文本、进度和操作按钮；
- 删除任务后保留 Recycler/Linear 容器稳定，不触发剩余任务整组重播；
- Fragment 销毁时调用 `MotionAnimations.cancel`。

验证：执行 `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console plain --no-daemon`；静态检查上述页面不存在 `startAnimation` 和 `scheduleLayoutAnimation()` 的旧调用。

### 任务 3.3：统一 loading/content/error/empty 状态

文件：

- `app/src/main/java/com/zomdroid/fragments/LauncherFragment.java`
- `app/src/main/java/com/zomdroid/fragments/WorkshopFragment.java`
- `app/src/main/java/com/zomdroid/fragments/WorkshopFavoritesFragment.java`
- `app/src/main/java/com/zomdroid/fragments/WorkshopDetailFragment.java`
- `app/src/main/java/com/zomdroid/fragments/WorkshopAccountFragment.java`
- `app/src/main/java/com/zomdroid/fragments/WorkshopDownloadCenterFragment.java`
- `app/src/main/java/com/zomdroid/fragments/SteamDownloadFragment.java`
- `app/src/main/java/com/zomdroid/fragments/InstallModFragment.java`
- `app/src/main/java/com/zomdroid/fragments/InstallSavesFragment.java`
- `app/src/main/java/com/zomdroid/fragments/InstallDriverFragment.java`
- `app/src/main/java/com/zomdroid/fragments/ModFixesFragment.java`
- `app/src/main/java/com/zomdroid/fragments/OptimizationFragment.java`
- `app/src/main/java/com/zomdroid/fragments/GameSettingsFragment.java`
- `app/src/main/java/com/zomdroid/fragments/ExportLogFragment.java`

动作：

- 将 loading、content、error、empty 的互斥 `setVisibility` 切换收敛到 `MotionAnimations.crossfade`；
- 为每个页面记录当前状态，重复状态回调不重新播放；
- 进度条、进度文本和下载日志更新保持即时更新，不触发容器淡入；
- 任务进度 Dialog 的标题、消息、进度和完成按钮保持原有业务逻辑，只统一出现/消失节奏；
- 异步回调在页面销毁后不再访问旧 View。

验证：

```powershell
./gradlew.bat :app:testDebugUnitTest --console plain --no-daemon
./gradlew.bat :app:assembleDebug --console plain --no-daemon
```

并新增 `app/src/test/java/com/zomdroid/ui/MotionStateTransitionTest.kt`，覆盖初始 loading、成功、失败、空结果和重复状态不重播规则。

## 阶段 4：局部交互和收尾

### 任务 4.1：统一折叠、面板和收藏反馈

文件：

- `app/src/main/java/com/zomdroid/fragments/SettingsFragment.java`
- `app/src/main/java/com/zomdroid/fragments/OptimizationFragment.java`
- `app/src/main/java/com/zomdroid/ControlsEditorActivity.java`
- `app/src/main/java/com/zomdroid/fragments/WorkshopFragment.java`

动作：

- 设置页和优化页 `setupCollapsible` 改用 `MotionAnimations.setExpanded`；
- 控制编辑器设置卡片的 300ms 动画改用 `motion_standard`，保留左右两侧进入方向；
- Workshop 收藏按钮缩放改用 `motion_fast`；
- 不给普通按钮额外叠加 scale，保留 ripple 按下反馈。

验证：执行 `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console plain --no-daemon`；`rg` 检查不再存在与动画规范冲突的 300ms 硬编码。

### 任务 4.2：统一底部导航、抽屉和弹窗边界

文件：

- `app/src/main/java/com/zomdroid/LauncherActivity.java`
- `app/src/main/res/layout/activity_launcher.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values/styles.xml`

动作：

- 底部导航选中项只增加统一短反馈，不改变导航栏高度和布局；
- DrawerLayout 继续使用原生滑动/遮罩动画，不叠加自定义平移；
- MaterialAlertDialog、下载删除确认弹窗、Steam Guard 弹窗只保留一层出现动画；
- 检查主题切换重建 Activity 时不出现旧页面和新页面双重动画。

验证：执行 `./gradlew.bat :app:assembleDebug --console plain --no-daemon`，并静态检查没有对 Dialog 根布局重复调用页面转场资源。

### 任务 4.3：清理旧资源和未引用调用

文件：

- `app/src/main/res/anim/workshop_enter.xml`
- `app/src/main/res/anim/workshop_exit.xml`
- `app/src/main/res/anim/workshop_pop_enter.xml`
- `app/src/main/res/anim/workshop_pop_exit.xml`
- `app/src/main/res/anim/workshop_content_enter.xml`
- `app/src/main/res/anim/workshop_content_layout.xml`

动作：只有在 `rg -n 'workshop_(enter|exit|pop_enter|pop_exit|content_enter|content_layout)' app/src/main` 确认无生产引用后，才删除旧资源；保留仍有引用的资源并记录原因。

验证：执行 `git diff --check`、资源编译和 `rg` 未引用检查；不得出现资源引用缺失。

## 阶段 5：验证与交付

### 任务 5.1：测试和静态检查

动作：

1. 执行 `git diff --check`。
2. 执行 `./gradlew.bat :app:testDebugUnitTest --console plain --no-daemon`。
3. 执行 `./gradlew.bat :app:assembleDebug --console plain --no-daemon`。
4. 检查 `git status --short`，确认只包含本任务预期文件和工作区原有修改。

通过标准：资源、Java/Kotlin 编译和全部 JVM 单元测试通过。

### 任务 5.2：MuMu 实机验证清单

由用户在 MuMu 上执行：

- 启动器 -> 设置 -> 返回；
- 启动器 -> Workshop -> 详情 -> 返回；
- Workshop 搜索、分页、收藏和详情图片轮播；
- 下载中心新增任务、进度更新、删除确认和删除完成；
- Mod 库、账号页和 Steam 下载页；
- 设置/优化页展开和收起；
- 控制编辑器侧面板打开和关闭；
- 系统动画缩放为 0 时确认页面仍直接到达最终状态；
- 快速连续点击和返回键不出现重叠、闪烁或崩溃。

### 任务 5.3：更新进度文档并交付 APK

文件：

- `docs/superpowers/progress.md`

动作：记录阶段 1–5 的完成情况、测试结果、APK 路径和 MuMu 实机验证状态；不覆盖工作区中其他任务的历史记录。

最终交付：

- `app/build/outputs/apk/debug/zomdroid-debug-1.4.9.apk`
- 统一动画设计文档和本实施计划。
