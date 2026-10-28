# Google Play Data Safety & Privacy Notes (`PLAY_DATA_SAFETY.md`)

## 1. Overview & Network Isolation
- **Does the app collect or share any user data?** **No.**
- **Network Permissions:** `android.permission.INTERNET` and `android.permission.ACCESS_NETWORK_STATE` are completely absent from `AndroidManifest.xml`.
- **Zero Off-Device Transmission:** All saved links, notes, attachments, and autofill credentials remain 100% on the user's device.

## 2. System Autofill & Credential Provider Disclosure
- **Local Login Field Processing:** When the user enables LinkVault as their Android Autofill / Credential Provider service, LinkVault inspects view and Compose autofill structures (`AssistStructure`) **strictly locally in memory on the device** to identify login fields (username, email, phone, password, new password, and confirm password) and match them against saved credentials.
- **No Data Transmission:** Login field contents, package names, and web domains are never transmitted off the device, logged, or shared with third parties.
- **Encrypted On-Device Storage:** Saved credentials, app package identities (`appPackage` + SHA-256 signing certificate digests), user-confirmed linked apps/websites, and password history are stored encrypted at rest on the device using **SQLCipher (256-bit AES)** and **AES-256-GCM** (`RecordEncryption` and `EncryptedSharedPreferences`) backed by the Android Keystore.
- **User Control & Opt-Out:** Every credential save or update requires explicit user confirmation via the system autofill save prompt, and every fill requires vault unlock/authentication and package + SHA-256 signing certificate verification. Users can exclude any website domain or Android app package via the **Never-Save List**.

## 3. Permissions Summary
- `android.permission.USE_BIOMETRIC`: Used strictly for local hardware-backed biometric unlock and re-authentication before revealing, copying, or autofilling passwords.
- `<queries>` (`Intent.ACTION_MAIN` + `Intent.CATEGORY_LAUNCHER`): Used strictly locally to resolve human-readable app labels and icons for user-installed launcher apps without requesting `QUERY_ALL_PACKAGES`.
- **Prohibited Permissions (Confirmed Absent):**
  - No `android.permission.INTERNET`
  - No `android.permission.QUERY_ALL_PACKAGES`
  - No `android.permission.SYSTEM_ALERT_WINDOW`
  - No `android.permission.BIND_ACCESSIBILITY_SERVICE`
