# LinkVault Security & Performance Architecture (`SECURITY.md`)

## 1. Threat Model & Defense-in-Depth Overview
LinkVault is a **100% offline application** with zero network permissions (`android.permission.INTERNET` is completely absent from the manifest). It is hardened against physical and local storage compromise scenarios:
1. **Compromised Device Storage (Physical/Root/Backup Extraction):**
   - `android:allowBackup="false"` and strict `backup_rules.xml` / `data_extraction_rules.xml` block ADB and system auto-backup extraction of databases and preferences.
   - The Room SQLite database (`link_database`) is encrypted at rest using **SQLCipher (`net.zetetic:sqlcipher-android`)** with a 256-bit key derived via HKDF-SHA256.
   - Sensitive note/password records are additionally encrypted at the field level (`LVENC1:` prefix) using **AES-256-GCM** with per-record Associated Authenticated Data (`AAD = id|userEmail`) so rows cannot be transplanted across users or records.
   - Preferences and lockout counters are stored in `EncryptedSharedPreferences` (`SecureStorage`).
2. **Encrypted Local & Storage Access Framework Backups (`LVBK3`):**
   - Encrypted local and SAF backups use the **V3 Authenticated Envelope (`LVBK3`)**:
     - `Header`: Magic `"LVBK3"`, version `3`, 16-byte random salt, PBKDF2 iteration count (`>= 600,000`), 12-byte random GCM IV.
     - `Key Derivation`: `KEK = HKDF-SHA256(PBKDF2-HMAC-SHA256(MasterPassword, salt, iterations) || SecretKey128Bit, info="LinkVault-Backup-v3")`.
     - `Payload`: Compressed and encrypted with **AES-256-GCM** (128-bit authentication tag) with the header bytes bound as AAD.
   - Atomic write pipeline creates a `.tmp` file, performs `fd.sync()`, verifies payload integrity with SHA-256, and atomically renames to final destination.

---

## 2. Key Hierarchy
```text
[User Master Password] + [16B Random Salt]
        │
        ▼ (PBKDF2-HMAC-SHA256, >= 600,000 iterations, benchmarked 400-700ms)
[Stretched Master Key (256-bit)]  +  [128-bit Device Secret Key (Emergency Kit)]
        │
        ▼ (HKDF-SHA256)
[Key Encryption Key (KEK, 256-bit)]
        │
        ▼ Wraps via AES-256-GCM (+ Android Keystore AES-256-GCM hardware wrapping)
[256-bit Vault Data Encryption Key (DEK)]
        │
        ├──► HKDF-SHA256("LinkVault-Database-v1")    ──► SQLCipher 256-bit DB Key
        ├──► HKDF-SHA256("LinkVault-Credentials-v1") ──► Per-Record AES-256-GCM Key (LVENC1:)
        └──► HKDF-SHA256("LinkVault-Backup-v1")      ──► Local Auto-Backup AES-256-GCM Key
```

---

## 3. Authentication, Lockout & Auto-Lock
- **Passcode Hashing:** User PINs/passwords are hashed with **PBKDF2WithHmacSHA256** (600,000 iterations, 16-byte random salt) and verified using constant-time byte array comparison (`MessageDigest.isEqual`). Plaintext legacy PINs are automatically migrated and wiped on unlock.
- **Tamper-Resistant Lockout (`LockoutManager`):** Enforces escalating lockouts after every 5 failed attempts (`30s` → `5m` → `1h` → `24h`) using monotonic `SystemClock.elapsedRealtime()` combined with persisted state in `SecureStorage` to resist wall-clock rollback.
- **Biometric CryptoObject (`MainActivity`):** Uses `BiometricPrompt.CryptoObject` bound to an Android Keystore AES-256-GCM key (`setUserAuthenticationRequired(true)`).
- **Auto-Lock & Screen Security:** Locks automatically on screen-off or after 30–60s in the background, and sets `WindowManager.LayoutParams.FLAG_SECURE` when sensitive vaults or dialogs are active.

---

## 4. 100% Offline Architecture & System Autofill (Browsers & Native Apps)
- **Zero Network & Dangerous Permissions:** `INTERNET`, `ACCESS_NETWORK_STATE`, `QUERY_ALL_PACKAGES`, `SYSTEM_ALERT_WINDOW`, and Accessibility permissions are completely absent.
- **Local Domain & App Avatar Icons:** Domain initial avatars are generated deterministically on-device using a color hash of the normalized host; app labels and icons are resolved safely inside `try/catch` using minimal launcher `<queries>`, falling back to the normalized package name when package visibility blocks lookup.
- **Native App Identity & SHA-256 Certificate Pinning (`AppIdentityVerifier`):**
  - Every credential saved from an Android app stores the normalized `appPackage`, all current signing certificate **SHA-256** digests (`appSigners`), and the readable `appLabel`.
  - Debug/beta variants (e.g., `com.app.beta`) are treated as distinct apps unless explicitly linked by the user.
  - On fill, both the package name AND all SHA-256 signing certificates must match. If an app's signing certificate changes, LinkVault **never fills automatically** and instead presents a warning entry (*"This app was updated by a different publisher, tap to review"*) requiring vault authentication and explicit user confirmation.
- **Offline User-Confirmed App ↔ Website Linking (`AppWebsiteLinkManager`):**
  - Apps and websites are never linked automatically. A read-only bundled catalog (`assets/known_app_domain_pairs.json`) is used strictly to suggest links; cross-filling only occurs after explicit user confirmation (`Yes / No / Always ask`).
- **Autofill Privacy Disclosure:** LinkVault processes login fields strictly locally on the device, never transmits data, and stores all credentials and password history encrypted at rest on the device.

---

## 5. Performance & UI Architecture Guarantees
- **No Sensitive Plaintext in Long-Lived UI Models:** `LinkUiModel` (`@Immutable`) stores only `usernamePreview` and metadata; password secrets are decrypted strictly on-demand inside `VaultCardComponents` after biometric/PIN re-authentication and zeroed when hidden.
- **Zero Local I/O During Main List Scrolling:** Pre-computed domain host models, local avatars, and date headers avoid disk reads or string regex allocations during scroll flings.
