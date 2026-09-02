# Development Progress

## Miuix UI Refactor (2026-09-02)

- Stage 1 Requirement Exploration: completed; design approved by user.
- Design doc: `docs/superpowers/specs/2026-09-02-zomdroid-miuix-ui-design.md`
- Stage 2 Implementation Planning: plan approved; strict stage-by-stage review completed.
- Plan doc: `docs/superpowers/plans/2026-09-02-zomdroid-miuix-ui-plan.md`
- Scope guard: only UI layout, UI interaction, UI state adapters, UI tests, and Miuix-required build configuration may change.
- Protected code: installer, Workshop core, instance persistence/model, game rendering/input, ControlsEditor Canvas, JNI/C++, native libraries, and unrelated tests.
- Startup decision: legal notice Dialog -> existing dependency-install progress Dialog restyled with Miuix -> Instances; no separate initialization page.
- Stage 1 execution: completed tasks 1.1-1.3 (Miuix theme/tokens, UI route/state model, Compose launcher shell).
- Local Miuix integration: consumed locally built core/squircle/ui/icons/nav/shader AARs from `miuix.path`; no Miuix source changes.
- Validation: `:app:testDebugUnitTest` and `:app:assembleDebug` passed; installed and exercised on MuMu `127.0.0.1:5555` with no fatal app log.
- Protected-code audit: no changes under InstallerService, Workshop core, instance model/persistence, GameActivity/game input, ControlsEditor Canvas, JNI/C++, native libraries, or unrelated tests.

## Stage 2 Startup and Global Task UI (2026-09-02)

- Strict review completed against the full design and implementation plan. Only Stage 2 tasks 2.1–2.3 were executed; Stages 3–7 remain pending.
- Legal notice: existing preference key, text, acceptance timing, and blocking behavior are preserved in a Miuix `OverlayDialog`.
- Dependency installation: existing `InstallerService` progress/status is mapped by a UI-only adapter into a non-dismissible Miuix progress dialog with completion, retry, and exit states. No initialization page was added.
- Release notes: existing version gate and content source are preserved in a Miuix dialog.
- Global task entry: observes the existing Workshop download manager, remains hidden when there are no tasks, shows an Miuix download affordance when active/failed tasks exist, and routes to Workshop Downloads.
- UI host compatibility: the Compose root provides the navigation event owner required by the local Miuix overlay implementation; this is UI host wiring only.
- Validation: JVM unit tests and `:app:assembleDebug` pass with JDK 21; MuMu `127.0.0.1:5555` clear-data flow verified legal notice → dependency progress → dependency success → release notes, with no fatal app log.
- Protected-code audit: no changes to `InstallerService`, Workshop core, game/instance bottom layer, rendering/input, ControlsEditor Canvas, JNI/C++, native libraries, or unrelated tests.

## Miuix UI 重构设计（2026-09-02）

- Stage 1 Requirement Exploration：设计文档已生成，待用户审批。
- Design doc：`docs/superpowers/specs/2026-09-02-zomdroid-miuix-ui-design.md`
- 关键边界：主应用管理页面使用 Compose/Miuix；游戏渲染、自定义输入 Canvas 和底层业务保持不变。
- 强制限制：只允许修改 UI、UI 交互、UI 状态适配和接入 Miuix 所需的构建配置。
- 下一门槛：用户批准设计后，进入 Stage 2 生成实施计划。

## Stage 1 Requirement Exploration

- Started at: 2026-09-01
- Completed at: 2026-09-01
- Design doc: `docs/superpowers/specs/2026-09-01-steam-workshop-design.md`
- Current status: Design approved; strict design/plan review completed before implementation.

## Stage 2 Implementation Planning

- Completed at: 2026-09-01
- Plan doc: `docs/superpowers/plans/2026-09-01-steam-workshop-plan.md`
- Current status: Plan approved; strict review corrections recorded before execution.

## Stage 3 Plan Execution

- Started at: 2026-09-01
- Current scope: Execute approved delivery Stage 3, tasks 9–11 (persistent download center, foreground service, notifications and task UI).
- Current progress: Tasks 9–11 completed. Queue state, recovery, pause/resume, retry, cancellation, deletion, concurrency, task history, install action and navigation are implemented; full JVM tests and debug APK assembly pass.
- Boundary note: Workshop browsing, details, dependencies, authentication and the actual third-party fallback client remain later planned stages.
- Validation note: API 30/35 real-device background, notification, process-restart and end-to-end installation checks remain explicit validation items.

## Stage 4 Plan Execution

- Completed at: 2026-09-01
- Current scope: Execute approved delivery Stage 4, tasks 12–13 (official Project Zomboid Workshop catalog and detail UI).
- Current progress: Tasks 12–13 completed. Catalog models, Steam HTML/SSR JSON decoding, browse/detail/game repositories, short HTTP disk cache, search/sort/pagination, detail metadata/change log/comments/dependencies, Steam link, dependency batch enqueue, download-center integration, navigation, and target-instance propagation are implemented.
- Third-party boundary: The existing `ggntw.com` download action remains unchanged. The official catalog is an additional entry point and uses the Stage 3 download center.
- Validation: 9 focused catalog tests, full JVM unit tests, Java compilation, and debug APK assembly pass. Connected Espresso/API 30/35 validation remains for the later validation stage/device pass.

## Stage 5 Plan Execution

- Completed at: 2026-09-01
- Current scope: Execute approved delivery Stage 5, tasks 14–15 (Steam authentication/session state and account UI).
- Current progress: Tasks 14–15 completed. Steam CM RSA credential authentication, BeginAuth/Poll, email/device Guard handling, access-token generation/refresh/revocation, encrypted multi-account state, active-account switching, sanitized web-cookie projection, account UI, and Java async facade are implemented. Official download tasks now use the selected account's Refresh Token/Steam cookies; no-account tasks remain anonymous.
- Failure recovery: Official account-bound failures offer reauthentication/account switching; the existing third-party option remains available and is not used for Steam credential handling.
- Third-party boundary: Existing `ggntw.com` download service remains unchanged; no Stage 6 fallback-client implementation was added.
- Validation: 56 JVM tests pass, including repository account-store/Cookie tests and Mock CM authentication challenge, polling, error, and token tests; Java compilation and debug APK assembly also pass. Real-device Espresso validation remains in Stage 7.

## Stage 6 Plan Execution

- Completed at: 2026-09-01
- Current scope: Execute approved delivery Stage 6, tasks 16–17 (Mod library/version history, manual update checks, multi-instance installation, backup/atomic replacement, and confirmed third-party fallback retry).
- Current progress: Tasks 16–17 completed. Completed Workshop archives are recorded by AppID/Workshop ID/version key with title, description, cover URL, update time and file metadata; the library supports atomic persistence, duplicate-safe deletion, old-version cleanup, manual/low-frequency update checks, sharing, and installation to multiple validated Zomboid instances.
- Install safety: Workshop installation validates the target instance/build and archive root, asks whether to retain an existing-mod backup, moves the old directory aside before replacement, restores it if replacement fails, and only deletes the backup when the user chose not to retain it.
- Third-party boundary: Existing `ggntw.com` remains available and is never called automatically. The new fallback requires an explicit confirmation, sends no Steam credentials/tokens, accepts only HTTPS trusted hosts, rejects path traversal, downloads through a temporary `.part` file, and records the result in the same Mod library.
- Validation: Stage 6 focused suites passed (16 tests, 0 failures), including library round-trip/legacy migration/version/deletion cases, download-center regression, and fallback URL/MockWebServer response validation; Kotlin/Java compilation and `assembleDebug` pass. APK: `app/build/outputs/apk/debug/zomdroid-debug-1.4.8.apk` (168,633,422 bytes). API 30/35 and real-device end-to-end validation remain in Stage 7.
- Follow-up fix: Successful download-center and legacy anonymous Workshop downloads now remove their private staging tree after the final archive is published; startup also removes staging for tasks already recorded as successful. Failed/paused downloads retain staging for recovery. Archive names now use a sanitized Workshop title plus Workshop ID and timestamp, with metadata-title fallback for the legacy ID-only entry point.

## Stage 7 Full Validation

- Pending: Tasks 18–19 (full regression and real-device validation).
