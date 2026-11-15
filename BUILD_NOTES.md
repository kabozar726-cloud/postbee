# PostBee v1.0.0 — BUILD NOTES (Roman Urdu)

## v1 kya karta hai
- Aik video chunein, title + description + tags likhein, thumbnail lagayein.
- TikTok, Facebook, Instagram mein se jahan post karna ho, tick karein.
- Upload dabayein — har platform ki alag progress dikhegi, aur akhir mein har
  platform ka status (Ho gaya / Fail + wajah) aur live link (jahan mile).
- Accounts screen: teeno platforms ka login (official OAuth — password app
  mein kabhi save nahi hota, sirf token EncryptedSharedPreferences mein).
- History screen: har upload ka record (title, waqt, har platform ka status).
- Video checker: upload se pehle bata deta hai — Instagram Reel 90 second
  se lambi hai, TikTok 10 minute se lambi hai, file 300MB se bari hai, ya
  video 9:16 vertical nahi hai.

## Kya PLACEHOLDER hai (approval ke baad bharna hai)
`ApiConfig.kt` mein ye 5 cheezein khaali hain:
1. `TIKTOK_CLIENT_KEY` + `TIKTOK_CLIENT_SECRET` — TikTok for Developers
   (developers.tiktok.com) par app banane ke baad milte hain. Content Posting
   API product + `video.publish` / `video.upload` scope chahiye.
2. `META_APP_ID` + `META_APP_SECRET` — Meta for Developers
   (developers.facebook.com) par Business-type app banane ke baad milte hain.
   Permissions: pages_manage_posts, pages_read_engagement, pages_show_list,
   instagram_basic, instagram_content_publish.
3. `SERVER_BASE_URL` — Hetzner server (abhi pick pending hai). Server par
   aik chhota upload endpoint chahiye (`POST /upload` → `{"url": "..."}`),
   kyunke Instagram video ko public link se uthata hai.

Jab tak keys khaali hain, app upload dabane par saaf Roman Urdu message
degi — crash nahi hogi, aur na hi jhootha "Ho gaya" dikhayegi.

## Imaandaar hadood (ye app ke rules hain, bug nahi)
- **Facebook:** sirf Page par post hoti hai — personal profile par API se
  post band hai (2018 se). Groups par bhi nahi.
- **Instagram:** sirf Business ya Creator account par post hoti hai —
  personal account par API se post nahi hoti. Account Facebook Page se
  linked hona chahiye. Reel API se 90 second tak ki hoti hai.
- **TikTok:** jab tak TikTok app ka audit pass na ho, Direct Post sirf
  private (SELF_ONLY) hota hai aur din mein 5 users tak. Audit ke liye
  TikTok chahta hai ke app sab creators ke liye ho + website par Privacy
  Policy aur Terms hon + demo video ho.
- **Server:** Instagram ke liye video ka public link zaroori hai — is liye
  server ke baghair Instagram upload nahi chalegi. TikTok aur Facebook
  seedha phone se upload ho jate hain.

## Build kaise karein
- Repo: `kabozar726-cloud/postbee` (banana baaki hai — abhi sirf local files hain)
- Workflow `.github/workflows/android.yml` pehle se likha hai (JDK 17,
  Gradle 8.9, `gradle assembleDebug`, release tag `build-<number>`).
- Workflow file ko browser web UI se banana hai (MCP push-files mein
  `.github/workflows` 403 deta hai — installation mein Workflows permission nahi).
- Repo Settings → Actions → General → Workflow permissions = **Read and write**
  rakhna hai, warna release step 403 dega.

## Version
- Package: `com.postbee.app` — versionCode 1, versionName `1.0.0`
- minSdk 26, targetSdk 34, compileSdk 34
- Stack: AGP 8.5.2, Kotlin 2.0.21, Compose BOM 2024.06.00, compiler 1.5.14
