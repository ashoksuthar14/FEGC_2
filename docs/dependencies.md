# Dependency & licence disclosure

Every material third-party component in this prototype, with its licence and how it is used.

Versions are the ones actually pinned in `gradle/libs.versions.toml` and `app/build.gradle.kts`.

---

## 1. The one that needs reading first

| Component | Version | Licence | Status |
|---|---|---|---|
| **Gemma 3 270M IT (q8)** — `gemma3-270m-it-q8.litertlm` | — | **[Gemma Terms of Use](https://ai.google.dev/gemma/terms)** + Google [Prohibited Use Policy](https://ai.google.dev/gemma/prohibited_use_policy) | **Not in this repository. Not in the APK.** |

This is the only dependency whose licence is **not** a standard open-source licence, and it is the one a reviewer should look at deliberately.

- The weights are **not committed and not redistributed**. `.gitignore` excludes `*.litertlm` and `/models/`, and the file is never packaged into the APK. It is fetched by the developer from Google's own distribution and pushed to the device (see the README).
- The Gemma Terms permit use and redistribution subject to the Prohibited Use Policy travelling with the model. Because we do not redistribute the weights, **no Gemma obligation is passed to FEG or T‑Hub by reviewing or cloning this repository.**
- The prototype runs the model entirely on-device. No prompt, no fixture and no user data is sent to Google or anywhere else.
- If the file is absent the app degrades to its template narrator and remains fully functional. Nothing in the demo depends on the model being present.

---

## 2. Runtime libraries

All Apache‑2.0 unless stated.

| Library | Version | Licence | Used for |
|---|---|---|---|
| Kotlin stdlib / Gradle plugins | 2.2.0 | Apache‑2.0 | Language and build |
| Android Gradle Plugin | 8.11.1 | Apache‑2.0 | Build |
| androidx.core:core-ktx | 1.17.0 | Apache‑2.0 | Platform extensions |
| androidx.lifecycle (runtime‑ktx, runtime‑compose, viewmodel‑compose) | 2.9.1 | Apache‑2.0 | ViewModel, lifecycle-aware state |
| androidx.activity:activity-compose | 1.10.1 | Apache‑2.0 | Single-Activity host |
| androidx.compose BOM | 2025.06.01 | Apache‑2.0 | UI toolkit version alignment |
| androidx.compose.ui / ui-graphics / ui-tooling-preview | via BOM | Apache‑2.0 | UI |
| androidx.compose.material3 | via BOM | Apache‑2.0 | Design system primitives |
| androidx.compose.material:material-icons-extended | via BOM | Apache‑2.0 | Icons |
| androidx.navigation:navigation-compose | 2.9.0 | Apache‑2.0 | Navigation graph |
| **androidx.glance:glance-appwidget / glance-material3** | 1.1.1 | Apache‑2.0 | The six home-screen widgets |
| org.jetbrains.kotlinx:kotlinx-serialization-json | 1.8.1 | Apache‑2.0 | Mock fixtures, ledger and loyalty persistence |
| org.jetbrains.kotlinx:kotlinx-datetime | 0.6.2 | Apache‑2.0 | Instants and time zones |
| io.coil-kt.coil3:coil-compose | 3.2.0 | Apache‑2.0 | Image loading |
| **com.google.ai.edge.litertlm:litertlm-android** | 0.16.1 | Apache‑2.0 | On-device LLM runtime (the *runtime*, not the weights) |
| androidx.camera (core, camera2, lifecycle, view) | 1.4.1 | Apache‑2.0 | Retail slip scanning |

## 3. Proprietary but freely usable SDKs

| Library | Version | Terms | Used for |
|---|---|---|---|
| com.google.mlkit:genai-prompt | 1.0.0-beta2 | [ML Kit Terms of Service](https://developers.google.com/ml-kit/terms) | Gemini Nano probe — the middle rung of the narrator ladder |
| com.google.mlkit:barcode-scanning | 17.3.0 | [ML Kit Terms of Service](https://developers.google.com/ml-kit/terms) | Reading the barcode on a paper slip |

Not open source, but free to use and redistribute inside an application under Google's ML Kit terms. Both run on-device; the barcode scanner processes camera frames locally and no image leaves the device.

## 4. Test-only

| Library | Version | Licence |
|---|---|---|
| junit:junit | 4.13.2 | Eclipse Public Licence 1.0 |
| org.robolectric:robolectric | 4.14.1 | MIT |
| org.jetbrains.kotlinx:kotlinx-coroutines-test | 1.10.2 | Apache‑2.0 |
| androidx.test.ext:junit | 1.2.1 | Apache‑2.0 |
| androidx.test.espresso:espresso-core | 3.6.1 | Apache‑2.0 |

Test dependencies are not shipped in the APK.

---

## 5. Data, assets and trademarks

| Item | Source | Note |
|---|---|---|
| `app/src/main/assets/mock/*.json` | **Written for this prototype** | Fixtures, odds, games, tickets, promos. Entirely synthetic. No real customer, player or account data of any kind. |
| `docs/screens/*.jpg` | Screenshots of the public psk.hr product | Used as a **visual reference** for building the replica. Not redistributed as a product asset. |
| Club crests | **Generated at runtime** — `ambient/identity/CrestBitmap.kt` | **No club logo is used, scraped or bundled.** The app draws a shield from two colours, a pattern and two initials. Club crests are trademarks; this deliberately avoids them. |
| Club colours | Approximations, in `ambient/identity/ClubTheme.kt` | Chosen to be mutually distinguishable and to pass contrast checks. **Not licensed brand values.** A production build would take them from a rights-cleared source. This is stated in the source file itself. |
| App icon / vector drawables | Written for this prototype | — |
| Fonts | Android system fonts only | No font is bundled. |

**PSK and FEG names and branding** appear because this is a replica built for an FEG challenge. No claim of ownership is made and no PSK/FEG asset is redistributed beyond the reference screenshots above.

---

## 6. AI-assisted development disclosure

This prototype was built with substantial use of **Claude Code (Anthropic)** as a coding assistant, across architecture, implementation, tests and documentation. Commit messages are attributed accordingly and the co-authorship trailer is present in the git history.

The team retains responsibility for originality, security, licensing and accuracy of all incorporated content. Specifically:

- No confidential challenge material, credential, or real customer data was provided to any AI system.
- All third-party components above were selected and reviewed by the team, and each is disclosed here.
- Generated code was reviewed, compiled and tested; the test suite (103 tests) is part of that review.

The **on-device LLM inside the product** (Gemma 3 270M) is a separate matter from the development assistant, and is disclosed in §1.

---

## 7. Network and data egress

**None.** The prototype makes no network calls, holds no API keys, and has no backend. Two permissions are declared — `POST_NOTIFICATIONS` and `CAMERA` — and neither results in data leaving the device. This is verifiable: there is no HTTP client dependency in the list above.
