# Steam Account Entry Unification Plan

1. Replace the Steam game download login/mod controls with an account status
   card and keep only the game depot form. Verify generated view binding and
   navigation resources compile.
2. Expose the active shared Steam session through `SteamAuthRuntime` and update
   `SteamGameDownloader` to consume it without persisting credentials. Verify
   Java/Kotlin compilation.
3. Update `SteamDownloadFragment` to refresh account status, open the existing
   account page, reject missing/expired accounts, and start the unchanged game
   download state flow. Verify focused unit tests and compilation.
4. Update labels/help text and progress documentation without changing Workshop
   task storage or the third-party fallback. Verify `git diff --check`.
5. Run `:app:testDebugUnitTest` and `:app:assembleDebug`; install when ADB is
   authorized and record any device limitation.
