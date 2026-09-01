# Development Progress

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

## Zomdroid Compose UI 全面重构

### Stage 1 Requirement Exploration

- Started at: 2026-09-01
- Design doc: `docs/superpowers/specs/2026-09-01-zomdroid-compose-ui-redesign-design.md`
- Completed at: 2026-09-01
- Current status: Design approved; strict review completed before implementation.
- Confirmed scope: full management UI rewrite to Compose; three frontends (liquid glass, lite liquid, classic); five top-level modules; Chinese/English/Russian first; adaptive phone/tablet layouts; preserve existing behavior and data.
- Boundary: GameActivity OpenGL rendering and in-game touch overlay remain unchanged.

### Stage 2 Implementation Planning

- Started at: 2026-09-01
- Plan doc: `docs/superpowers/plans/2026-09-01-zomdroid-compose-ui-redesign-plan.md`
- Completed at: 2026-09-01
- Current status: Plan approved; strict review corrections recorded before execution.

### Stage 0 Baseline and dependency foundation

- Completed at: 2026-09-01
- Baseline: `testDebugUnitTest`, `compileDebugJavaWithJavac`, and `assembleDebug` passed before Compose changes.
- Dependencies: Compose BOM, Compose UI/Material 3, lifecycle/activity/navigation Compose, Coil, Kyant Backdrop and Shapes added; Navigation unified at 2.8.5 and compileSdk raised to 36 for Backdrop/Shapes compatibility.
- Attribution: Existing Apache-2.0 WorkshopAndroidDownloader notice/license files updated for the UI/design-system migration; Kyant Backdrop and Shapes added to `NOTICE.md`.
- Current status: Stage 0 complete.

### Stage 1 Appearance and Compose design system

- Completed at: 2026-09-01
- Theme: Added `LiquidGlass`, `LiteLiquidGlass`, and `Classic` modes with neutral/blue/mint/peach light/dark schemes; LiquidGlass is the default.
- Rendering: Added procedural Canvas wallpaper, layered Backdrop scaffold, glass surface/card, liquid button, icon/navigation/choice controls, toggle/slider adapters, and app-level popup host. Lite and Classic use explicit low-cost/Material paths.
- Persistence: Added the `frontendMode` field to the existing LauncherPreferences Gson payload, with Kotlin type-safe decoding, unknown-value normalization, and round-trip tests. The existing ThemeMode remains unchanged.
- Validation: `testDebugUnitTest`, `compileDebugKotlin`, `compileDebugJavaWithJavac`, `compileDebugAndroidTestKotlin`, and `assembleDebug` pass. The Compose instrumentation sample is compiled but not run because no connected Android device/emulator is available in this workspace.
- Boundary: Stage 2 root state, adaptive navigation, LauncherActivity Compose entry, and all feature-page migrations were intentionally not started.

### Stage 2 Root entry, adaptive navigation and global events

- Completed at: 2026-09-01
- Review: Re-read the complete design and plan, including stages 0–7, before execution; confirmed that stage 2 must keep the existing Fragment NavHost as a temporary compatibility branch and must not migrate feature pages early.
- State: Added `AppModule`, destination/back-stack, action, one-shot event, confirmation-dialog and reducer models; added `AppViewModel` with persisted appearance integration.
- Shell: Added Compose `ZomdroidApp`, adaptive narrow NavigationBar/wide NavigationRail, shared top bar, Snackbar host, global dialog host, and staged compatibility content.
- Activity: `LauncherActivity` now mounts Compose over the existing XML/NavHost. Selecting an unmigrated module switches to the corresponding legacy Fragment destination; returning to the launcher restores the Compose root. Existing legal notice, release notes, notification permission, update checks and external actions remain in the legacy lifecycle path.
- Validation: State-machine JVM tests, `testDebugUnitTest`, `compileDebugKotlin`, `compileDebugJavaWithJavac`, `compileDebugAndroidTestKotlin`, `assembleDebug`, and `git diff --check` pass. Device launch/connected tests were not run because `adb` is unavailable in the environment.
- Boundary: Stages 3–7, feature-page migration, XML removal and GameActivity changes were not started.

- Pending: Tasks 18–19 (full regression and real-device validation).
### Stage 3 Launcher vertical slice

- Completed at: 2026-09-01
- Review: Re-read the complete Compose redesign design and plan before execution; confirmed this stage is limited to Launcher, game instances, new-instance creation and game-settings management. Workshop, downloads, mod library, controls editor, XML removal and GameActivity remain later stages.
- State adapter: Added immutable launcher records/UI state and a read-only `GameInstanceManager` adapter. Installation state, build/preset, game-file checks and backup metadata are mapped without changing SharedPreferences persistence or manager ownership.
- Launcher UI: Added Compose instance cards, empty/multiple-instance states, launch validation, crash-backup recovery confirmation, delete confirmation, storage/Wiki/settings actions and task-in-progress presentation. Long-running deletion/restoration/install work stays behind `InstallerService`/`BackupManager`; Activity remains the boundary for GameActivity, DocumentsContract and Wiki navigation.
- New/settings UI: Added Compose new-instance and game-settings screens with name validation, preset selection, ZIP/INI URI pickers, optional saves/mods/native libraries and existing InstallerService import/export/create commands. The legacy Fragment/NavHost compatibility branch remains for non-migrated modules.
- Validation: Launcher mapping JVM tests, full `testDebugUnitTest`, `compileDebugKotlin`, `compileDebugJavaWithJavac`, `compileDebugAndroidTestKotlin`, `assembleDebug` and `git diff --check` pass. Instrumentation tests are compiled but not run because `adb`/a connected device is unavailable.
- Boundary: Stages 4-7 and the remaining legacy feature pages were not started.

### Stage 4 Settings and tools Compose slice

- Completed at: 2026-09-01
- Review: Re-read the complete design and plan, including stages 0–7, before execution; confirmed this slice is limited to tasks 13–15. Workshop browsing, downloads, mod library, XML cleanup, ControlsEditor canvas migration and GameActivity remain later boundaries.
- Settings: Added Compose settings state/repository/ViewModel for theme, frontend appearance, renderer/Vulkan driver, render scale, JVM/environment arguments, texture shrinking, memory saver, quick-save backup, debug and touch controls. Preset preview/confirmation/cancel/apply and Wiki/tool links are preserved. Theme and frontend appearance changes refresh the Compose shell immediately and persist through existing preferences.
- Controls: Added Compose management screens for gamepad mapping, touch controls and ControlsEditor launch, including per-instance editor background image save/clear and forwarding of the existing background-path extra. Existing `GamepadManager` CSV/JSON compatibility and `ControlsEditorActivity` protocol remain behind explicit Activity callbacks; mapping codec round-trip and malformed-input tests cover the persisted format.
- Tools: Added unified Compose screens for controls, custom driver, native libraries, saves, mods, mod fixes, optimization, log export and Wiki. Tool operations use `ToolTaskViewModel`/`ToolTaskRepository` and remain exclusively delegated to `InstallerService`; task progress/error/finished state is mapped from the existing bound service LiveData, with cancellation and instance/file URI selection.
- Validation: `testDebugUnitTest`, `compileDebugKotlin`, `compileDebugJavaWithJavac`, `compileDebugAndroidTestKotlin`, `assembleDebug`, and `git diff --check` pass. The new appearance interaction test is compiled but not run because `adb`/a connected device is unavailable.
- Boundary: Stage 5 Workshop/download/library migration and Stage 6 XML/navigation cleanup were not started.

### Stage 5 Workshop, Steam and download Compose slice

- Completed at: 2026-09-01
- Review: Re-read the complete Compose redesign specification and plan before execution; confirmed this stage is tasks 16–19 only. Stage 6 special pages/XML cleanup and GameActivity were not included.
- Workshop: Added a UI-facing repository adapter and StateFlow ViewModel for game discovery, search, sort, pagination, detail metadata, description/change notes, comments, dependencies, download enqueue and Steam account Guard/confirmation flows. Added Compose browse cards, detail page and multi-account page with the existing Workshop repositories and account store.
- Downloads: Added Compose Steam game/mod download screen backed by the process-persistent `SteamDownloadState`, including manifest/branch selection, progress, logs, cancellation and Guard-code dialog. Added persistent download-center and task-detail pages backed by `DownloadCenterManager`; foreground service, notification, recovery, pause/resume/retry/cancel/delete and log sharing remain unchanged.
- Library: Added Compose Mod library/detail pages with filtering, manual update checks, cleanup, delete, share, multi-instance install and explicit “replace” versus “replace and keep backup” confirmation. Installation still goes through `WorkshopLibraryInstaller`/`InstallerService` and the existing validated archive boundary.
- Navigation: Workshop, Downloads and Mod library now use Compose top-level destinations; Workshop account, Steam download, task detail and mod detail are Compose destinations. Legacy fragments remain available as compatibility code but are no longer selected by the Compose top-level module navigation.
- Localization: Reused the existing Chinese/English/Russian resource sets and added the download-log label in all three locales. No new color-system assumption was introduced; pages inherit the selected LiquidGlass/LiteLiquidGlass/Classic frontend.
- Validation: `testDebugUnitTest`, `compileDebugKotlin`, `compileDebugJavaWithJavac`, `compileDebugAndroidTestKotlin`, `assembleDebug`, and `git diff --check` are required final checks for this stage. Instrumentation/device execution remains unavailable because `adb` is not installed in this workspace.
- Boundary: Stage 6 ControlsEditor canvas migration, common dialog/error cleanup and XML deletion remain intentionally unstarted.

### Workshop information architecture refinement

- Completed at: 2026-09-01
- Finding: The first Stage 5 Workshop screen was a functional integration layout, not the approved information architecture. It exposed the whole Steam featured-game list and therefore rendered two unrelated search contexts on one page.
- Change: Workshop now presents Project Zomboid as the fixed primary context, one Workshop search field, horizontally scrollable sort/filter chips, loading/error/empty/page state, and a focused result-card list. The existing detail/account/download routes and repository state are unchanged.
- Validation: `compileDebugKotlin` and `git diff --check` pass after the refinement. Full Stage 5 regression commands are rerun below/after this change.

### Workshop detail regression fix

- Completed at: 2026-09-01
- Finding: The Compose detail route showed only the preview image and plain description text, so parsed Workshop gallery images and description-embedded images were not rendered. The route also displayed the missing-item state while its detail request was still in flight, and a missing Steam comment-thread context was promoted to a global Workshop error.
- Change: Restored the gallery with page position, restored description text/image blocks, retained the selected browse item for destination-driven loading, separated loading from missing/error states, and made absent or failed optional comment loading render as a non-paginated unavailable-comments state. Comment-thread parsing now accepts Steam's common single-quoted JavaScript payload and URL variants, including numeric or quoted fields.
- Regression coverage: Added `WorkshopCommentFallbackTest`; existing `WorkshopDetailParserTest` continues to cover gallery URLs, description image blocks, comment count and thread context parsing.
- Validation: `testDebugUnitTest`, `compileDebugKotlin`, `compileDebugJavaWithJavac`, `compileDebugAndroidTestKotlin`, `assembleDebug`, and `git diff --check` pass. Device execution was not run because `adb` is unavailable in this workspace. APK: `app/build/outputs/apk/debug/zomdroid-debug-1.4.8.apk` (224,174,353 bytes).

### Stage 6 Special pages, shared states, resources and cleanup

- Completed at: 2026-09-01
- Review: Re-read the complete Compose redesign specification, implementation plan and this progress log before starting. Kept `GameActivity` and in-game rendering/touch behavior outside the change boundary; retained the hardware-input mapper as a special View-based Activity.
- Controls editor: Replaced the XML settings panel and Activity shell with a Compose editor surface around the existing `InputControlsView`. Selection, drag/hit testing, long-press add, layout replacement, custom icon import, delete and save-to-disk remain on the existing Java/input model boundary.
- Shared UI states: Added appearance-agnostic Compose `Dialogs`, `AsyncContent`, `EmptyState` and `ErrorState` components. Global confirmations, Workshop loading/error/empty states and tool/library empty/error states now use the shared components; retry, cancel and non-cancelable dialog properties are explicit.
- Localization/responsive behavior: Added the new editor binding/retry labels to English, Simplified Chinese and Russian resources. The editor panel uses a scrollable, capped width surface so it remains usable on 360dp phone widths and does not consume the full canvas on tablet/landscape widths.
- Cleanup: Converted `LauncherActivity` to a Compose-only management host, added a small special host for the hardware mapper, removed old Navigation XML/menu resources and removed unreferenced management Fragments, item layouts and progress-dialog layouts. `GameActivity`, `fragment_gamepad_mapper.xml` and the input canvas compatibility resource remain intentionally preserved.
- Validation: `testDebugUnitTest`, `compileDebugKotlin`, `compileDebugJavaWithJavac`, `compileDebugAndroidTestKotlin`, `assembleDebug` and `git diff --check` pass. APK: `app/build/outputs/apk/debug/zomdroid-debug-1.4.8.apk` (222,562,402 bytes). `lintDebug` was run but remains red on pre-existing project-wide issues (duplicate `android:background` in `keyboard_styles.xml`, `local.properties` escaping, existing missing translations in other locale folders and a pre-existing Compose `LocalContext` lint finding); no new compile/resource-reference error remains. Device/instrumentation execution remains unavailable because `adb` is not installed.
### Stage 7 Validation and performance

- Completed at: 2026-09-01
- Review: Re-read the full Compose redesign design, the complete implementation plan and the prior progress entries. Confirmed Stage 7 is limited to tasks 24-26: test-matrix completion, Backdrop downgrade/resource checks, full static/build validation and device acceptance. No Workshop business flow or `GameActivity` scope was expanded.
- Test coverage: Added the Stage 7 UI matrix test for all five primary modules across 360dp, 600dp and 840dp widths in all three appearance modes, plus detail back navigation. Expanded rendering-strategy tests to prove Lite and Classic never enter the expensive Backdrop path and full Liquid Glass falls back to Lite when Backdrop is unavailable.
- Automated validation: `testDebugUnitTest`, `compileDebugKotlin`, `compileDebugJavaWithJavac`, `compileDebugAndroidTestKotlin`, `assembleDebug` and `git diff --check` pass. Production reference scan found only the intentionally retained hardware mapper XML. `GameActivity` has no diff.
- Lint: `lintDebug` was executed and remains red on the existing project-wide baseline (197 errors, 542 warnings; first error is the pre-existing duplicate `android:background` in `keyboard_styles.xml`). This was not hidden with a new baseline and did not prevent resource processing or APK assembly.
- Device boundary: `adb` is unavailable in this workspace, so `connectedDebugAndroidTest`, API 30/API 35, low-performance rendering, background service/notification, rotation, process restart and real-device end-to-end checks were not run. The executable checklist is `docs/superpowers/stage7-device-validation.md` for the user-owned device pass.
- Deliverable: `app/build/outputs/apk/debug/zomdroid-debug-1.4.8.apk`, 222,564,054 bytes, SHA-256 `879562D0269856898F7CC3224EDA7D16E610A92123CF6B5885964D9E62F79CDC`.
### Navigation duplication fix

- Completed at: 2026-09-01
- Finding: `AppScaffold` created a `ModalNavigationDrawer` for every width and also rendered `AdaptiveNavigation` as a `NavigationRail` on wide containers, so the same primary navigation could appear as both an overlay drawer and a persistent rail.
- Change: Wide layouts now render only the persistent rail; narrow layouts now render only the drawer. The narrow bottom navigation bar was removed from this shell so the primary navigation has one presentation at a time. Detail destinations retain only the back action.
- Regression coverage: Added navigation surface test tags and assertions for the narrow drawer/wide rail split, all five module labels, all three appearance modes, 360/600/840dp UI matrix and detail back navigation.
- Validation: `testDebugUnitTest`, `compileDebugKotlin`, `compileDebugJavaWithJavac`, `compileDebugAndroidTestKotlin` and `assembleDebug` pass. Instrumentation execution remains user/device-side because `adb` is unavailable.
### Navigation orientation and liquid shell fix

- Completed at: 2026-09-01
- Finding: Width-only `600dp` detection classified some portrait devices as wide; `Scaffold` also kept opaque Material chrome, so only explicit cards exposed the Backdrop effect.
- Change: Primary navigation now uses the drawer in portrait and only uses the persistent glass rail in landscape at 600dp or wider. The shell `Scaffold`, top app bar, drawer sheet and navigation surfaces now use transparent/Backdrop-aware containers; Lite and Classic continue through their low-cost surface paths.
- Regression coverage: Navigation tests now inject portrait/landscape configurations and assert the mutually exclusive drawer/rail behavior; the Stage 7 matrix follows the same orientation rule.
- Validation: `testDebugUnitTest`, `compileDebugKotlin`, `compileDebugJavaWithJavac`, `compileDebugAndroidTestKotlin` and `assembleDebug` pass. Device rendering validation remains user-side because `adb` is unavailable.

### Startup crash regression fix

- Completed at: 2026-09-01
- Reproduction: Connected to MuMu at `127.0.0.1:5555`, installed the debug APK and launched `com.zomdroid/.LauncherActivity`. The process crashed during composition with `UnsupportedOperationException: Only RoundedRectangularShape or CornerBasedShape is supported in lens effects`, originating from `GlassComponents.kt` while rendering a rectangular shell surface.
- Root cause: The new full-width top bar, drawer sheet and navigation rail/bar intentionally use `RectangleShape`, but `ZomdroidGlassSurface` unconditionally applied Backdrop's `lens` effect. Backdrop rejects that shape before the first frame.
- Change: Added the explicit `enableLens` capability flag to `ZomdroidGlassSurface`; rectangular shell surfaces disable only `lens` while retaining blur, vibrancy, borders and surface rendering. Rounded cards keep the full liquid-glass effect.
- Validation: Build/test/assemble pass. Reinstalled the APK on MuMu and launched it successfully three times; `pidof com.zomdroid` remained present and no `FATAL EXCEPTION`/`AndroidRuntime` output was produced. `git diff --check` passes. APK: `app/build/outputs/apk/debug/zomdroid-debug-1.4.8.apk`.

### Portrait bottom navigation and shared glass controls

- Completed at: 2026-09-01
- Finding: The portrait shell still opened a modal drawer, contrary to the requested mobile layout. In addition, `AdaptiveNavigation` reclassified the bottom-bar host by width alone; MuMu's 1080px/280dpi portrait window is about 617dp wide and was incorrectly rendered as a rail.
- Change: Removed the portrait drawer and menu action. The shell now renders a bottom navigation bar in portrait and a persistent rail only when the outer shell explicitly selects wide layout. The bottom bar is a rounded glass capsule.
- Shared controls: Settings appearance choices, preset actions, enum selectors, sliders, switches and text fields now use the shared liquid controls instead of opaque Material button/switch/text-field defaults. Full-liquid mode retains Backdrop; Lite and Classic use the corresponding low-cost surface path.
- Validation: Build, unit tests, Android-test compilation and APK assembly pass. MuMu ADB `127.0.0.1:5555` installed and launched the APK without crashes; UIAutomator confirmed the five navigation destinations occupy bounds at the bottom (`y=1766..1906`) and no drawer is present. A settings screenshot confirmed the glass capsule controls. No temporary `[DEBUG-*]` instrumentation remains.

### Global popup glass and selector sizing fix

- Completed at: 2026-09-01
- Review: Compared the local `WorkshopAndroidDownloader` implementation, especially its shared glass components and `WorkshopPopupMenu` host. Confirmed that its global effect comes from a page Backdrop plus an in-window popup host that measures anchor bounds, rather than from Material `DropdownMenu` styling alone.
- Change: Replaced Zomdroid's Material popup wrapper with an in-content `ZomdroidPopupHost`/`ZomdroidPopupMenu` pair. Popups now use the same Backdrop, rounded glass surface, bounded width (220–320dp), right-edge clamping and above/below placement. Migrated Settings, launcher, tools and controls-editor selectors/actions to the shared popup items.
- Change: Removed the unconstrained inner `fillMaxSize()` from liquid buttons so selectors without `fillMaxWidth()` measure to their content; full-width callers still explicitly retain full width.
- Validation: Build, unit tests, Android-test compilation and APK assembly pass. Installed on MuMu `127.0.0.1:5555`, launched successfully, opened Settings and verified a selector popup is right-anchored with the shared translucent Backdrop. No app fatal exception was found in the post-launch log.

### Liquid control parity correction

- Completed at: 2026-09-01
- Finding: The previous migration stopped at glass containers around Material controls. Toggle, Slider and portrait navigation therefore retained Material interaction geometry and did not implement WAD's component-level backdrop composition, chromatic lens edges or press/drag deformation.
- Change: Liquid mode now uses custom Backdrop-rendered Toggle, Slider and BottomNavigation components. Toggle and Slider compose a local track layer with the page backdrop and render a refractive thumb; BottomNavigation uses a moving refractive indicator and full-width tab hit targets. Liquid buttons now use WAD-style Backdrop lens, highlight, inner-shadow and press scaling. Wide liquid navigation uses a custom glass rail; Material navigation remains only in Classic mode.
- Validation: MuMu `127.0.0.1:5555` installed and launched the new APK with `pidof com.zomdroid` present and no app fatal exception in the sampled log. Settings screenshot confirms custom glass slider/thumb, liquid appearance controls and custom bottom navigation. Build, Android-test compilation and unit tests pass.

### Full Material control leakage audit

- Completed at: 2026-09-01
- Finding: A source audit still found page-level Material buttons, fields, dialogs, icon buttons, chips and progress indicators outside Settings. They made the global liquid mode inconsistent even though the main settings controls had already been custom-rendered.
- Change: Routed all page-level instances through the shared liquid component layer, including custom text/outlined buttons, text fields with placeholder/trailing/error content, dialogs, icon buttons, chips, checkboxes, progress indicators and the launcher action button. Replaced the Material top app bar with a glass-shell row. Material navigation remains only in the explicitly selected Classic appearance branch.
- Validation: No direct Material control imports or fully-qualified calls remain in `app/src/main/java/com/zomdroid/ui` for Button, Switch, Slider, TextField, AlertDialog, IconButton or progress indicators. Full build, unit tests, Android-test compilation, APK assembly and diff check pass. MuMu install/launch succeeds with `pidof com.zomdroid`; no sampled fatal exception. APK SHA-256: `A4259CB412CE8FB835B49C02D49D4E634F643B702A68E479F7111F73CEA5A4E7`.
