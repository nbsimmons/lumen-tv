# Amazon Appstore submission checklist

Checked against Amazon's Fire TV test criteria and Appstore policies, October 2026. Sources:

- [Test criteria](https://developer.amazon.com/docs/app-testing/test-criteria.html)
- [Device filtering](https://developer.amazon.com/docs/app-submission/device-filtering-and-compatibility.html)
- [Store assets](https://developer.amazon.com/docs/app-submission/appstore-details.html)
- [Privacy policy](https://developer.amazon.com/docs/policy-center/privacy-security.html)
- [Restricted content](https://developer.amazon.com/docs/policy-center/restricted-content.html)
- [Fire OS 16](https://developer.amazon.com/docs/fire-tv/fire-os-16.html)

## Done in the app

| Requirement | Status |
|---|---|
| targetSdk ≥ 34 (36 recommended, required for Fire OS 16) | targetSdk 36, minSdk 28 |
| Touchscreen not required; LEANBACK_LAUNCHER; banner | Declared in the manifest |
| No Google Play Services / Firebase | None in the dependency tree |
| 32- and 64-bit ARM; 16 KB native page alignment (Fire OS 16) | armeabi-v7a + arm64-v8a, 16 KB aligned |
| Back goes up a level, never loops; Back on Home exits | Tested on the Stick |
| Predictive back (Fire OS 16 no longer sends Back as a key press) | `enableOnBackInvokedCallback` with BackHandler, plus a key fallback for Fire OS 7/8 |
| Home key leaves within 2s, state kept, playback pauses | Tested on the Stick |
| D-pad reaches everything, focus always visible | Tested screen by screen |
| Media keys (play/pause, play, pause, rewind, fast-forward) in the player | Handled |
| Screen stays on during playback; audio focus; pause when audio becomes noisy | Handled |
| Loading indicator for any wait | Spinners on every data-loading screen |
| Errors handled gracefully (server down, title missing, playback failure) | Error states with Try Again / Go Back |
| Alternative to typing on the TV | Quick Connect sign-in |
| Data survives restarts and updates | DataStore + Keystore |
| Captions follow the system caption style | Used when captions are enabled in Fire TV settings |
| Alexa transport controls | MediaSession + `voicecommandcontrol` permission |
| Privacy policy viewable in the app | Settings › Privacy Policy |
| Not presented as an official Jellyfin/Seerr app | Own name and icon; "not affiliated" note in Settings |
| Warning before sending a password over http:// | Shown on the password screen |

## You need to do before submitting

1. **Host the privacy policy at a public URL.** The text is in `app/src/main/res/values/privacy.xml`; copy it to a web page and add a contact email. Put the URL in the console.
2. **Fill in the privacy questionnaire** (privacy labels). Lumen collects authentication data and viewing activity, sends it only to the user's own servers, and shares nothing with the developer.
3. **Answer the account deletion questions.** Lumen has no accounts of its own. Accounts live on the user's own Jellyfin/Seerr servers. In-app: Sign Out and Disconnect Seerr remove all credentials, and uninstalling removes everything. *This is the least certain area: Amazon's FAQ says password-login apps must offer account deletion, and it isn't clear how they apply that to self-hosted accounts. Explain it in the testing notes.*
4. **Set up a reviewer test server** and add it to the Testing Instructions. Amazon rejects apps it can't sign in to. You'll need:
   - a public **https://** Jellyfin URL with a test user
   - royalty-free media only (e.g. Blender open movies: *Big Buck Bunny*, *Sintel*, *Tears of Steel*)
   - optionally a Seerr instance, or a note that Discover is optional
   - to keep it running for every review, including updates
5. **Content rating questionnaire.** No content of its own; users stream from their own servers.
6. **Store assets.** All PNG/JPG with no transparency, except where noted:
   - App icon: 1280×720
   - Background: 1920×1080
   - Icons: 512×512 (transparency allowed) and 114×114
   - 3–10 screenshots at 1920×1080
   - **Use only the royalty-free demo library in screenshots**, not your real library: posters of commercial films are a copyright risk.
7. **Description.** Every feature you list must work for the reviewer (rule 3.2). Never mention torrents, Radarr/Sonarr download clients or "free movies".
8. **Device targeting.** Tick Fire OS 7/8 (and 14) Fire TV devices. Vega OS devices (Fire TV Stick 4K Select 2025, Stick HD 2026, Stick 4K 3rd gen 2026) can't run APKs and will show as unsupported.
9. **Signing.** Create a real release keystore before the first upload (the current build uses the debug key). Amazon re-signs by default, but every update must come from the same upload key.

## Risks to be aware of

- **Seerr.** Amazon's restricted-content policy bans apps that "promote" piracy or torrent downloads. Seerr itself only manages requests, but reviewers may associate it with Radarr/Sonarr. Mitigations: Discover is opt-in (it only appears after you connect a Seerr server), the listing should frame it as "request titles for your household's media server", and screenshots should avoid it or show only demo content. If the app is rejected for this, the fallback is a build where Seerr is hidden behind a Settings switch that is off by default.
- **YouTube trailers** use YouTube's official IFrame embed inside the app (allowed by YouTube's terms; no stream extraction). Reviewers may still check YouTube's branding rules, so don't promote trailers as an ad-free feature in the listing.
- **Fire OS 16** behaviour (predictive back, edge-to-edge) is coded for but untested, since no Fire OS 16 hardware exists yet.
- **Unconfirmed:** Amazon's blog mentions a cold-start time-to-first-frame target under 2 seconds. Lumen cold-starts in about 0.7s on a Fire TV Stick 4K.
