# LinkVault

A 100% offline, privacy-first Android link manager and password vault with AES-256 encrypted database, authenticated local backups, and system autofill support.

## Key Features

- **100% Offline & Private:** Zero network permissions (`android.permission.INTERNET` is completely absent). All data remains strictly on your device.
- **SQLCipher Encrypted Database:** Database encrypted with a 256-bit key derived via HKDF-SHA256 from user-controlled key hierarchy.
- **Field-Level Secret Encryption:** Sensitive password notes and credentials encrypted with AES-256-GCM (`LVENC1:`) and Associated Authenticated Data.
- **Android System Autofill:** Standard `AutofillService` and `CredentialProviderService` integration for seamlessly filling and saving login credentials in browsers and apps.
- **Robust Offline Backups (`LVBK3`):** V3 authenticated local and Storage Access Framework (SAF) backups with atomic writes, SHA-256 verification, and rollback protection.
- **Local Domain Avatars:** On-device deterministic domain initial avatar generation without external favicon network requests.
- **High-Performance UI:** Built with Jetpack Compose, `@Immutable` models, and WorkManager background task scheduling.
