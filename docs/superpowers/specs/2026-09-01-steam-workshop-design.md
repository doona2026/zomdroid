# Zomdroid Steam Workshop 集成设计

日期：2026-09-01

## 目标

将 `WorkshopAndroidDownloader` 的 Steam Workshop 浏览、详情、认证、下载和下载库能力集成到 Zomdroid，首版服务 Project Zomboid（Steam AppID `108600`），并让下载结果可以安装到 Zomdroid 的游戏实例。

## 已确认范围

- 新增独立 Workshop 页面，使用 Zomdroid 现有 XML、Fragment、ViewBinding 和 Navigation。
- Workshop 页面支持 Zomboid Workshop 搜索、排序、分页、列表、详情、封面、描述、标签、作者、文件大小、更新时间、更新日志、评论、依赖和 Steam 页面跳转。
- 下载核心支持 Steam `file_url` 直链和 UGC manifest/chunk 两种内容形式。
- Steam 官方 CM/CDN 为首选下载通道；保留 `ggntw.com` 作为用户明确确认后的备用通道。
- 第三方备用通道不接触 Steam 用户名、密码、Guard 数据或 token，下载结果进入同一下载库。
- 支持匿名下载公开 Workshop 内容；受限内容可通过 Steam 登录访问。
- 支持用户名/密码、Steam Guard 邮件或设备验证码、设备确认；支持多个 Steam 账号和账号切换。
- 仅加密保存 refresh token、Guard 数据和账号元信息，不保存明文密码；下载任务绑定账号。
- 下载中心使用持久化任务队列和前台服务，支持排队、暂停、继续、重试、取消、删除、通知和应用重启恢复。
- 完成文件进入 `Downloads/zomdroid/workshop`；队列状态、认证状态、临时分片和 staging 文件存放在应用私有目录。
- 下载库按 `appId + publishedFileId + 版本/更新时间` 保存历史；支持选择实例安装、下载并安装快捷操作、覆盖提示和可选备份。
- 安装复用现有 `InstallerService` 的智能 Mod 根目录识别、路径修复和 Build 41/42 处理逻辑。
- 依赖显示在详情页，可一键加入下载队列，但需要用户确认安装；首版不递归展开 Collection。
- Mod 库支持手动及低频进入时更新检查，只提示新版本，不自动下载或安装。
- 保持 Zomdroid 的 `minSdk 30`、`compileSdk 35`、Java/Kotlin target 11。
- 保留现有手动 ZIP 导入、优化 Mod 和游戏下载功能。

## 不在范围内

- 不直接搬运源项目 Compose 应用壳、独立 MainActivity、主题和导航。
- 不移植百度翻译、实验性无关功能或 Steam Workshop 写操作（点赞、收藏、发表评论）。
- 首版不做其他 Steam 游戏的 UI 和安装适配；核心数据模型保留 `appId` 以便未来扩展。
- 首版不支持 Collection 的递归下载。
- 不自动把 Workshop ID 发送给第三方备用服务。

## 分层设计

### Gradle 与依赖

在 Zomdroid `app` 中加入 Kotlin 编译支持、Coroutines、Kotlin Serialization、OkHttp 及源项目 Steam 协议所需的 protobuf/压缩依赖；版本必须兼容 Java 11、Android API 30 和现有 JavaSteam 依赖。源项目中的下载核心和协议代码迁移到 `com.zomdroid.workshop` 命名空间。

### Steam 协议与认证

- `steam-protocol` 的 CM WebSocket、目录服务、PublishedFile 查询、manifest request code、CDN token、depot key 和 manifest/chunk 解密迁移为 Zomdroid 内部 Kotlin 包。
- 认证仓库管理匿名会话、refresh-token 会话、Guard 挑战、账号切换和加密持久化。
- 网络客户端统一设置 Steam 兼容的超时、User-Agent、HTTP/HTTPS 节点策略和日志脱敏。

### Workshop 数据层

- 使用 Steam Community browse 页面和 Steam Store/API 数据完成搜索、列表和游戏固定数据。
- 使用 `GetPublishedFileDetails` 和 Community 页面完成详情、依赖、更新日志及评论解析。
- 所有解析器独立于 UI，并为 HTML/JSON 结构变化保留单元测试和缓存。

### 下载中心

- 队列状态保存到应用私有目录，采用临时文件写入后原子替换。
- 每个任务记录 AppID、Workshop ID、标题、绑定账号、状态、阶段、进度、错误、文件和版本。
- 官方下载失败后由 UI 提供“使用第三方服务重试”操作；第三方结果转换为统一的下载库条目。
- 前台服务仅负责运行/恢复任务和更新通知，Fragment 通过可观察状态重新绑定。

### Zomdroid 集成

- 新增 Workshop Fragment、列表/详情/下载中心所需 XML 和资源字符串。
- 从“导入 Mod”页面打开 Workshop 时传递当前实例；从独立入口进入时在安装动作中再选择实例。
- Workshop 文件完成后通过统一的本地文件引用交给现有安装器，而不是复制另一套 ZIP 解包代码。
- 下载库记录的文件可重复安装到多个实例；替换前根据 Workshop 元数据和目标目录显示确认/备份选项。

## 关键安全与可靠性规则

- 日志不得输出密码、refresh token、access token、Guard 数据或完整 Cookie。
- 所有 Workshop/ZIP 路径必须进行文件名清理和路径穿越校验。
- 未完成文件使用 `.part`/staging 目录，成功后原子完成；任务恢复时校验并继续，而不是盲目视为成功。
- 第三方备用服务必须在 UI 中显示第三方、广告和隐私风险说明，并且只在用户确认后调用。
- 保留源项目 Apache 2.0 许可证及 NOTICE/归属信息；既有 MIT/其他依赖归属继续保留。

## 验收标准

1. Zomdroid 在现有 Android 11+ 构建环境下可编译，原有游戏下载和手动 Mod 导入测试不回归。
2. 输入已知 Zomboid Workshop ID 可通过官方链路解析详情并下载公开 Mod，断点/重试后文件可校验。
3. 官方下载失败时不会自动调用第三方；用户确认后可通过备用服务获得同一下载库条目。
4. Workshop 列表、详情、依赖、更新日志、评论和分页在无登录场景可用；受限内容能提示登录。
5. Steam 登录、Guard、refresh-token 恢复、多账号切换和任务账号绑定可验证，敏感信息不出现在日志和明文存储中。
6. 下载任务在离开页面、应用切后台和进程重启后状态可恢复；通知能反映任务状态。
7. 下载文件可从库中选择 Zomboid 实例安装，并复用现有智能导入、覆盖和路径修复逻辑。

## 交付阶段

1. Kotlin/依赖/许可证与 Steam Workshop 核心下载接入。
2. 匿名官方下载、文件落盘和现有安装器接入。
3. 持久化下载中心、前台服务、通知和恢复。
4. Zomboid Workshop 搜索、列表、详情、依赖和独立导航入口。
5. Steam 登录、Guard、refresh token 和多账号。
6. Mod 库、版本记录、更新检查、覆盖/备份和第三方手动备用重试。
7. 单元/集成测试、真机下载验证和 UI 回归。
