# Steam Account Entry Unification Design

## Scope

Unify Steam account sign-in for the Steam game downloader and the Steam Workshop
features. Both screens must open the same account-management destination and use
the same encrypted multi-account state and selected account.

Steam game downloads and Workshop downloads remain separate products:

- Steam game downloads keep their existing `SteamDownloadState`, depot/manifest
  flow, output files, and progress UI.
- Workshop downloads keep `DownloadCenterManager`, Workshop task persistence,
  and the Workshop download center.

## UI decisions

- The Steam game download screen no longer exposes a second username/password
  form or a Workshop-mod tab.
- Its login card becomes an account status card with a single
  `Manage Steam accounts` action.
- That action and the Workshop toolbar account action navigate to the same
  `WorkshopAccountFragment`.
- The existing Steam game download menu label becomes `Steam game download`.
- Manual Workshop ID/URL downloads belong to the Workshop flow/download center,
  not the Steam game downloader screen.

## Runtime decisions

- `SteamAuthRuntime` is the Java-facing source for the selected account session.
- `SteamGameDownloader` accepts the shared refresh-token session in memory and
  uses its existing JavaSteam depot pipeline. No password is stored or passed
  by the Steam game download UI.
- If no account is selected or reauthentication is required, the game download
  does not start and the user is directed to account management.
- Existing anonymous Workshop tasks and the explicit third-party fallback are
  unchanged.

## Acceptance criteria

1. A login performed from the account page reached through either feature is
   visible as the same selected account from both features.
2. Steam game download starts only with the selected shared session; no second
   credential form is present.
3. Workshop download task progress is not shown in the Steam game downloader,
   and Steam game download progress is not shown in the Workshop center.
4. Existing tests, Java/Kotlin compilation, and debug APK assembly pass.
