# Mod 库已安装 Mod 增强实施计划

设计依据：`docs/superpowers/specs/2026-09-06-installed-mod-library-design.md`

## 执行边界

- 只新增 Mod 库页面的实例级已安装 Mod 查看能力，不改变现有共享归档的业务语义和 JSON 格式。
- 不添加启用/禁用、加载顺序、拖动排序、回收站或持续文件监听。
- Workshop 关联只读取本地共享归档和本地归档内容；未知 Mod 不自动联网查询。
- 不新增存储权限。打开目录复用 `AppStorageProvider` 和已有 `DocumentsContract` URI 方案。
- 所有后台结果必须经过页面生命周期和扫描请求序号校验后才能更新 UI。

## 原子任务

### 1. 建立已安装 Mod 的纯领域模型与测试契约

文件：

- 新增 `app/src/main/java/com/zomdroid/workshop/library/InstalledModModels.kt`
- 新增 `app/src/test/java/com/zomdroid/workshop/library/InstalledModModelsTest.kt`

内容：

- 定义已安装 Mod 条目、扫描结果、排序枚举和本地详情字段。
- 条目包含实例名、规范化根路径、相对路径、名称、Mod ID、描述、缩略图路径、大小、根目录最后修改时间、元数据完整性、重复 ID 标记和可空 Workshop ID。
- 定义名称升序、最近修改、最早修改三种排序；保证同值时按 ID 和相对路径稳定排序。
- 通过构造测试固定空值、缺失字段和默认值语义。

验证：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.zomdroid.workshop.library.InstalledModModelsTest' --console plain --no-daemon
```

预期：模型测试通过。

### 2. 先写 `mod.info` 解析测试，再实现容错解析器

文件：

- 新增 `app/src/test/java/com/zomdroid/workshop/library/ModInfoParserTest.kt`
- 新增 `app/src/main/java/com/zomdroid/workshop/library/ModInfoParser.kt`

内容：

- 先覆盖 `name`、`id`、`description`、`poster`、`icon`、空行、未知键和重复键。
- 覆盖缺少名称、缺少 ID、格式损坏、读取异常和编码回退；单个字段异常不能抛出并中断整个扫描。
- 解析路径只允许相对于当前 Mod 根目录，拒绝 `..` 穿越和绝对路径。
- `name` 为空时由扫描器回退到目录名，`id` 为空时标记信息不完整。

验证：先运行测试确认缺少实现时失败，再完成最小实现并运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.zomdroid.workshop.library.ModInfoParserTest' --console plain --no-daemon
```

预期：所有解析和容错测试通过。

### 3. 先写目录扫描测试，再实现后台扫描器

文件：

- 新增 `app/src/test/java/com/zomdroid/workshop/library/InstalledModScannerTest.kt`
- 新增 `app/src/main/java/com/zomdroid/workshop/library/InstalledModScanner.kt`

内容：

- 使用临时目录构造单层 Mod、Workshop 外层目录、包含多个 `mod.info` 的嵌套包、无 `mod.info` 的普通目录和损坏元数据。
- 递归发现每个含 `mod.info` 的根目录，每个根目录只产生一个条目；继续递归以支持一个包内多个 Mod 根目录。
- 统计 Mod 根目录大小和根目录 `lastModified`，计算相对于 `Zomboid/mods` 的路径。
- 只统计没有任何可识别 Mod 根目录的候选目录为忽略目录，避免把 `media` 等内部目录计入噪声。
- 使用规范化路径、根目录前缀检查和已访问规范路径集合，防止符号链接穿越和目录循环。
- 不能访问单个目录时记录扫描问题并继续；不存在 Mods 目录返回明确的缺失状态，不自动创建目录。
- 缩略图只返回根目录内存在的 `poster` 或 `icon` 文件，加载失败由 UI 使用默认图标。

验证：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.zomdroid.workshop.library.InstalledModScannerTest' --console plain --no-daemon
```

预期：递归识别、忽略统计、大小/时间、路径安全和局部失败容错测试通过。

### 4. 先写搜索、排序和重复 ID 测试，再实现查询层

文件：

- 新增 `app/src/test/java/com/zomdroid/workshop/library/InstalledModQueryTest.kt`
- 新增 `app/src/main/java/com/zomdroid/workshop/library/InstalledModQuery.kt`

内容：

- 本地搜索匹配名称、Mod ID、目录名和相对路径，忽略大小写并保持结果稳定。
- 默认名称升序；最近修改按时间降序，最早修改按时间升序；所有排序使用名称、ID、路径作为稳定次序。
- 同一个非空 Mod ID 出现多次时，所有条目保留并标记 `duplicateModId`；空 ID 不作为重复冲突。
- 查询只处理内存扫描结果，不重新读盘。

验证：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.zomdroid.workshop.library.InstalledModQueryTest' --console plain --no-daemon
```

预期：搜索、三种排序、空查询和重复 ID 测试通过。

### 5. 增加本地 Workshop 关联索引并测试唯一/歧义匹配

文件：

- 修改 `app/src/main/java/com/zomdroid/workshop/install/WorkshopModArchiveInspector.kt`
- 新增 `app/src/main/java/com/zomdroid/workshop/library/InstalledModWorkshopMatcher.kt`
- 新增 `app/src/test/java/com/zomdroid/workshop/library/InstalledModWorkshopMatcherTest.kt`

内容：

- 复用归档根目录识别逻辑，从本地 ZIP 的 `mod.info` 中读取每个 Mod 根的 Mod ID，不解压到实例目录。
- 当前实例只考虑 `ModLibraryEntry.installedInstances` 包含当前实例的共享归档。
- 优先按归档根目录名与实际 Mod 根目录名匹配，其次按归档内 Mod ID 匹配；多候选时不关联，只有唯一 Workshop ID 才写入条目。
- 外部复制 Mod 在本地归档存在相同且唯一 Mod ID 时可关联，否则保持未匹配。
- 归档不存在、损坏或读取失败时跳过该候选，不影响已安装 Mod 列表。

验证：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.zomdroid.workshop.library.InstalledModWorkshopMatcherTest' --console plain --no-daemon
```

预期：根目录名匹配、Mod ID 匹配、唯一匹配、多候选和损坏归档测试通过。

### 6. 先写删除边界测试，再实现安全文件操作

文件：

- 新增 `app/src/test/java/com/zomdroid/workshop/library/InstalledModFileActionsTest.kt`
- 新增 `app/src/main/java/com/zomdroid/workshop/library/InstalledModFileActions.kt`

内容：

- 只允许删除当前实例 `Zomboid/mods` 根目录下、且与扫描条目根路径一致的目录。
- 拒绝实例外路径、Mods 根目录本身、路径穿越、符号链接解析后的外部路径和扫描结果之外的目标。
- 删除目标目录及其内容；操作返回成功/失败和可展示的错误信息。
- 打开目录只负责生成已验证的目录 URI 所需路径，实际系统 Intent 由 Fragment 调用已有 Provider。

验证：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.zomdroid.workshop.library.InstalledModFileActionsTest' --console plain --no-daemon
```

预期：合法删除、同级保留、实例外拒绝、根目录拒绝和符号链接边界测试通过。

### 7. 重构 Mod 库布局，加入实例级已安装 Mod 区域

文件：

- 修改 `app/src/main/res/layout/fragment_workshop_mod_library.xml`
- 新增 `app/src/main/res/layout/item_installed_mod.xml`
- 新增必要的默认 Mod 图标 Vector Drawable

内容：

- 在现有共享归档列表上方增加实例选择器、已安装 Mod 标题、扫描摘要、搜索框、排序菜单、刷新按钮、进度状态、空状态和已安装列表容器。
- 保留共享 Mod 库说明、清理旧版本按钮和原有列表容器，不复用同一数据容器。
- 条目显示本地缩略图/默认图标、Mod 名称、Mod ID、相对路径、大小、最后修改时间、元数据不完整/ID 重复/Workshop 关联状态和更多菜单。
- 按现有项目控件样式和窄屏布局约束，避免把排序菜单和刷新操作撑满页面。

验证：

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac --console plain --no-daemon
```

预期：布局资源和 Java/Kotlin 互操作编译通过。

### 8. 接入实例选择、后台扫描、查询和返回刷新

文件：

- 修改 `app/src/main/java/com/zomdroid/fragments/WorkshopModLibraryFragment.java`
- 如需保存页面级选择，新增或修改 Mod 库专用 SharedPreferences 常量文件

内容：

- 从 `GameInstanceManager` 获取实例；恢复上次页面选择，失效时回退第一个可用实例；无实例时渲染返回启动器空状态。
- 页面首次打开、实例切换、从后台返回和手动刷新时启动单线程后台扫描。
- 使用 Fragment View 生命周期、任务序号和 executor 取消/忽略过期结果；销毁 View 时取消 UI 回调。
- 扫描完成后将结果交给查询层，搜索和排序只刷新已安装列表；共享归档列表仍由现有 `render()` 流程负责。
- 刷新按钮显示忙碌状态；错误保留上一次成功结果并提供重试；删除成功后触发同一刷新流程。

验证：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugJavaWithJavac --console plain --no-daemon
```

预期：单元测试和 Fragment 相关编译通过，现有共享库行为无回归编译错误。

### 9. 加入详情、Workshop 入口、打开目录和二次删除确认

文件：

- 修改 `app/src/main/java/com/zomdroid/fragments/WorkshopModLibraryFragment.java`
- 新增已安装 Mod 更多菜单资源或复用项目既有菜单资源
- 如采用 XML 详情布局，新增 `app/src/main/res/layout/dialog_installed_mod_detail.xml`

内容：

- 点击条目打开本地详情，展示解析字段、实际路径、大小和最后修改时间。
- 唯一 Workshop 匹配时显示进入现有详情页的入口；未匹配时不显示虚假的联网入口。
- “打开所在文件夹”优先打开 Mod 根目录，失败后打开 `Zomboid/mods`，并提示完整路径。
- 删除弹窗显示 Mod 名称、实例名称、完整路径；确认后再次校验路径并删除，成功刷新并提示名称，失败保留条目并显示原因。
- 所有 UI 回调检查 `isAdded()`/View 状态，复用 `MotionAnimations` 为新增区域和条目提供已有统一动画。

验证：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console plain --no-daemon
```

预期：Debug APK 可组装，现有共享库安装、更新检查、分享和归档删除入口保持可用。

### 10. 补齐中文、英语、俄语文案并做资源校验

文件：

- 修改 `app/src/main/res/values/strings.xml`
- 修改 `app/src/main/res/values-zh-rCN/strings.xml`
- 修改 `app/src/main/res/values-ru/strings.xml`
- 修改新增菜单/布局引用的资源文件

内容：

- 增加实例选择、扫描状态、摘要、搜索、排序、详情、关联、打开目录、删除确认、错误和空状态文案。
- 仅新增本功能所需的中文、英语、俄语资源；其他语言回退默认英语资源。
- 检查格式化占位符、复数数量和字符串转义。

验证：

```powershell
.\gradlew.bat :app:assembleDebug --console plain --no-daemon
```

预期：资源合并和 Debug 构建通过，无缺失资源。

### 11. 全量回归、差异审查和验收记录

文件：

- 更新 `docs/superpowers/progress.md`
- 必要时新增 `app/src/test/java/com/zomdroid/workshop/library/InstalledMod*Test.kt` 的边界覆盖

内容：

- 运行全部 JVM 单元测试、Java 编译和 Debug APK 构建。
- 执行 `git diff --check`，确认没有误改主题、导航、下载中心或其他无关模块。
- 审查删除边界、生命周期回调、旧共享库列表和资源回退。
- 记录主机验证结果；MuMu/实机安装、实例选择、真实目录扫描、打开目录和删除交互由用户执行。

验证：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugJavaWithJavac :app:assembleDebug --console plain --no-daemon
git diff --check
```

预期：测试、编译、APK 构建和差异检查全部通过；计划任务全部完成后再进入实机验证阶段。

## 计划完成定义

## 执行记录

- 任务 1–10 已按顺序完成；任务 11 的主机侧回归、差异审查和 Debug APK 验证已完成。
- 已验证：`.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console plain --no-daemon`。
- 已验证：`git diff --check`。
- 实机/MuMu 验证按计划保留给用户执行：实例选择、真实 `Zomboid/mods` 扫描、打开目录和二次确认删除。

- 所有原子任务按顺序完成并有对应验证记录。
- 共享 Mod 库现有功能没有行为回归。
- 已安装 Mod 的扫描、搜索、排序、详情、Workshop 唯一匹配、打开目录和二次确认删除均有直接测试或可复核验证。
- 生产代码没有引入与本功能无关的重构或依赖。
