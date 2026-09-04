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

## Steam Account Entry Unification

- Implemented at: 2026-09-04
- Scope: The Steam game download page now contains only the Project Zomboid depot downloader. Its former credential form and duplicate Workshop-ID tab were removed; Workshop tasks remain in the persistent Workshop download center.
- Shared session: The Steam game page and Workshop page open the same `WorkshopAccountFragment`, read the same encrypted multi-account store and active account, and pass the selected refresh-token session to the game downloader only in memory. Missing or expired accounts are rejected before a game download starts.
- Validation: Java/Kotlin compilation, JVM unit tests and `assembleDebug` pass. APK: `app/build/outputs/apk/debug/zomdroid-debug-1.4.8.apk`. MuMu ADB device `b488527b` is currently unauthorized, so installation and UI smoke testing are pending device authorization.

## Runtime Game Menu

- Implemented at: 2026-09-04
- Current scope: Prevent an accidental Android Back press from leaving the running game; expose a
  runtime drawer with virtual-control editing, touch-control override, vibration, Build 42 F10 quick
  save, a keep-running return to launcher action, and a confirmed real-exit action.
- Lifecycle: GameActivity now stays above LauncherActivity. The keep-running action reorders the
  existing launcher Activity to the front while retaining GameActivity underneath; selecting the same
  instance or pressing Back can resume it. The real-exit action requests Project Zomboid's
  `GameWindow.closeRequested` shutdown flag through the embedded-JVM bridge, waits for the game JVM to
  stop, and then finishes back to the launcher. The embedded JVM is destroyed only after an explicit
  requested shutdown so the next game launch can create a fresh VM.
- Validation: Java/Kotlin compilation, ARM64 external-native build, JVM unit tests, debug APK assembly,
  ADB installation and launcher startup pass. A full in-game drawer/exit/relaunch smoke test remains
  pending until a playable instance is present on the connected device.

## Stage 7 Full Validation

## Workshop Direct Access Follow-up

- Implemented at: 2026-09-04
- Scope: Added the Watt Toolkit route resolver, persisted route cache, forwarded-host DNS mapping, manual redirect handling, relay-target fallback, and original Steam-route fallback for the public Workshop catalog client. Browse, detail/API, and Workshop image requests now share this client; official Workshop downloads and the existing explicit ggntw fallback remain unchanged.
- Privacy boundary: forwarded catalog requests strip Steam Cookie headers and remain anonymous. Authenticated/restricted content continues to use the original Steam route until a separate credential-forwarding decision is made.
- Validation: Added route construction, redirect normalization, bootstrap route, relay fallback, dynamic project-group parsing, cache persistence, and MockWebServer forwarding tests. Full `:app:testDebugUnitTest` and `:app:assembleDebug` pass. APK: `app/build/outputs/apk/debug/zomdroid-debug-1.4.8.apk`.
- Device validation: Pending because MuMu ADB device `b488527b` is still `unauthorized`; install and bare-network Workshop smoke testing require accepting the RSA authorization prompt.

- Pending: Tasks 18–19 (full regression and real-device validation).
