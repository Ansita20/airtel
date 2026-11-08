# How Udyam Digital protects your KYC documents

Your GST certificate, Udyam certificate, ID and address proof stay on your phone, locked, and
only for as long as we need them. This page lists every protection and why it is there.

## Stored safely

| Protection | Why |
|---|---|
| **Encrypted** with AES-256-GCM (Android's Jetpack Security). Photos *and* the details read from them. | Anyone who copies the phone's storage gets unreadable data. |
| **A separate key for each application.** The key lives in the phone's secure hardware (Android Keystore, and the StrongBox chip on phones that have one), so it can never be taken off the phone. | One application's key can't open another's documents. |
| **"Delete my documents" destroys the key**, then the files. | Any leftover copy, anywhere, can no longer be opened ("crypto-shredding"). |
| Files are kept **only in the app's private storage**. Scanner and camera temporary images are deleted as soon as the encrypted copy is saved. | No other app, and no gallery, can see them. |
| **Location, phone model and time are removed** from photos (EXIF data) before saving. | A document photo shouldn't reveal where you live or which phone you have. |
| The stored copy carries a visible watermark: **"For Accel Business KYC only - {date}"**. | A leaked copy can't be passed off for a loan, SIM or anything else. |

## Only you can open them

| Protection | Why |
|---|---|
| Viewing a saved document needs your **fingerprint, face or phone PIN**, and asks again after the app has been **in the background for 2 minutes**. | Someone who picks up your unlocked phone can't browse your IDs. |
| **No screenshots or screen recording** on screens with documents, IDs, your plan and prices, or the help chat. These screens are also blank in the recent-apps list. | Stops accidental sharing and screen-recording malware. |
| ID fields can't be copied. "Copy reference" copies **masked numbers only** (27\*\*\*\*\*\*34X1Z5). | Other apps can read the clipboard. |

## Nothing leaves the phone that shouldn't

| Protection | Why |
|---|---|
| **No backups**: documents are never sent to cloud backup or to a new phone. | Backups are a common way data leaks. |
| **HTTPS only.** The one online service (optional AI wording in the help chat) is **certificate-pinned**. | A fake Wi-Fi hotspot can't read or change the traffic. |
| That service only ever receives **check codes and masked numbers, never document images**. A final filter masks any full GSTIN, PAN, Udyam, Aadhaar or phone number before sending. | Your documents are read on the phone (on-device OCR) and never uploaded. |
| The release app **writes no logs** with names, numbers or document text, and its code is obfuscated. | Logs and reverse-engineering are common leak points. |
| On a **rooted or modified phone** we warn you that documents are less safe. We don't block you. | You can make an informed choice. |

## Kept only as long as needed (checked daily)

| Data | Deleted |
|---|---|
| Document photos and the details read from them | 30 days after your KYC is approved or not approved. Applications never submitted: 90 days after the last change. |
| Verification result + masked IDs | At the end of your contract (12 months if no contract). |
| Plan-screen usage events (no names or numbers) | After 90 days. |

## On record

An **encrypted access log** records who or what opened, read, saved or deleted each document,
and when (for example "you viewed your ID", "retention deleted application A1B2"). It holds
no document contents, names or numbers.

## Limits we're open about

- A phone with no screen lock can't show saved documents. Uploading still works.
- The AI help is a hackathon demo: its key is in the app. A real launch would route it through
  Accel's own server, with the same masking.
- Pins on the AI service are set to expire on 1 Jan 2028, so an old app falls back to normal
  HTTPS checks instead of breaking. They must be updated before then.
