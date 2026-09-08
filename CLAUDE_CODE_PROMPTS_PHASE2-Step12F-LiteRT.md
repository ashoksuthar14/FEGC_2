# Phase 2 · Step 12F — Path B: a real on-device model without AICore

Situation: `checkStatus()` returns `UNAVAILABLE` on the Pixel 10a. Gemini Nano is out.
This step gives you a genuine LLM running on the device anyway.

**Why this works when Nano does not.** ML Kit asks the operating system for a model that
the OS may not have. LiteRT-LM is the opposite: *you* ship the runtime and *you* ship the
model file. No AICore, no Play services dependency, no device allowlist. If the phone has a
CPU and ~1 GB of free RAM, it runs. The Pixel 10a qualifies comfortably.

Nothing you built in 12A–12D is wasted. `Narrator`, `MomentFacts`, `NarratorGuard`,
`LadderNarrator` and the Narrator Lab stay exactly as they are — this adds one more
implementation behind the same interface.

---

## 12F.0 The alternatives, and why LiteRT-LM

| Option | Verdict |
|---|---|
| **LiteRT-LM + Gemma 3** | **Use this.** Google's current on-device stack. Kotlin API marked stable, coroutine `Flow` native, ready-made `.litertlm` model files, Apache-2.0 runtime |
| MediaPipe `tasks-genai` | No. Google's docs put it in maintenance-only mode and point Android projects at LiteRT-LM |
| llama.cpp via JNI | Works, but you are writing and debugging NDK glue at hour 15 of 24 |
| MLC-LLM | Works, heavier toolchain, model compilation step you do not have time for |
| ONNX Runtime GenAI / ExecuTorch | Both viable in principle, both a bigger lift than LiteRT-LM for identical output |

Model choice: **Gemma 3 270M, q8, generic build — `gemma3-270m-it-q8.litertlm`, 304 MB.**
It is built for exactly this job (structure a few facts into a sentence), Google measured it
at 0.75% battery for 25 conversations on a Pixel 9 Pro, and it is the smallest thing that
will not embarrass you. If its English reads badly, the upgrade is
`gemma3-1b-it-int4.litertlm` (584 MB) from `litert-community/Gemma3-1B-IT`.

Ignore the `qualcomm.sm*` and `mediatek.mt*` variants — those are NPU builds for other
silicon. The Pixel uses Google Tensor, so the generic file is the right one.

---

## 12F.1 Get the model onto the phone (do this yourself, ~10 minutes)

1. Open `https://huggingface.co/litert-community/gemma-3-270m-it` and sign in. Gemma models
   are gated — you must click through and **accept the Gemma license once** or the download
   returns a 401.
2. Download **`gemma3-270m-it-q8.litertlm`** (304 MB) into your project's `models/` folder
   (add `models/` to `.gitignore` — never commit it).
3. Push it to the app's own external files directory, which needs no root and no extra
   permission:

```powershell
adb shell mkdir -p /sdcard/Android/data/eu.feg.ambient/files/models
adb push gemma3-270m-it-q8.litertlm /sdcard/Android/data/eu.feg.ambient/files/models/
adb shell ls -lh /sdcard/Android/data/eu.feg.ambient/files/models/
```

The push takes 1–2 minutes. The last command must show the file at ~304 MB — if it shows
0 or is missing, the app has not been installed yet (that directory is created on install).

> For the pitch: in production FEG serves this file from their own CDN and the app downloads
> it once on first run. Still no third party, still nothing leaving the device. `adb push` is
> just the demo shortcut so you are not building a download screen at 2 a.m.

---

## 12F.2 The Claude Code prompt

```
Gemini Nano is UNAVAILABLE on this device (Pixel 10a — no AICore support, confirmed via the
AI diagnostics screen). We are switching to Path B: LiteRT-LM running a Gemma 3 model we
ship ourselves. This does not use AICore at all.

Keep everything from step 12 intact. Narrator, MomentFacts, NarratorGuard, TemplateNarrator,
LadderNarrator and Narrator Lab do not change. This adds one more Narrator implementation.

A model file has already been pushed to the device at:
  <app external files dir>/models/gemma3-270m-it-q8.litertlm   (~304 MB)
Resolve that at runtime as File(context.getExternalFilesDir(null), "models/gemma3-270m-it-q8.litertlm").
Never bundle it in the APK and never commit it.

1. Dependency

Add to app/build.gradle.kts:
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.16.1")
0.16.1 is the latest stable release (0.17.0-alpha1 exists — do not use an alpha). Verify
against https://mvnrepository.com/artifact/com.google.ai.edge.litertlm/litertlm-android
and use the newest non-alpha version if there is one.

Add to AndroidManifest.xml inside <application>, so a GPU backend stays possible later:
    <uses-native-library android:name="libvndksupport.so" android:required="false"/>
    <uses-native-library android:name="libOpenCL.so" android:required="false"/>

2. ambient/narrator/LiteRtEngineHolder.kt

A process-wide singleton owning exactly one Engine. Creating engines is expensive and
initialize() can take up to 10 seconds.

- EngineConfig(modelPath = <resolved path>, backend = Backend.CPU(),
               cacheDir = context.cacheDir.absolutePath)
  Use CPU. GPU acceleration for Gemma 3 270M on Android is still work-in-progress upstream,
  and cacheDir materially speeds up the second load.
- Expose StateFlow<EngineState> with: NotPresent(path), Initializing, Ready(initMs), Failed(reason)
- initialize() runs on Dispatchers.IO, called once from Application.onCreate inside a
  coroutine. Never block onCreate — that is an ANR.
- If the model file is missing, go straight to NotPresent and do not throw. The app must
  still launch and work on templates.
- Provide close() for completeness; call it from onTerminate.

3. ambient/narrator/LiteRtNarrator.kt implementing Narrator

- If EngineState is not Ready, immediately delegate to TemplateNarrator.
- Create a FRESH Conversation per narration and close it (engine.createConversation().use{}).
  Each moment is independent — we must not carry context between a goal and a settlement.
- Send the same prompt text that step 12C specified for Nano (the RULES / two-line
  HEADLINE:/DETAIL: / FACTS block). Reuse that prompt builder — do not write a second one.
  Gemma is an instruction-tuned model and responds to it well.
- Use sendMessageAsync(prompt).catch{}.collect{} and accumulate the chunks; that is the
  recommended coroutine path.
- withTimeout(4000) — more generous than the 2500 ms used for Nano, because we are on CPU.
  On timeout, exception, unparseable output, or a NarratorGuard failure, fall back to
  TemplateNarrator with the same inputs.
- Report engine = NANO only if you later add Nano back; for this path add a third value to
  the NarratorEngine enum: LOCAL_GEMMA. The Narrator Lab badge must say which one truly ran.

4. Wire it in AppContainer

LadderNarrator order becomes:
  [NanoNarrator (only when NanoState is Available), LiteRtNarrator, TemplateNarrator]
On this device that resolves to [LiteRtNarrator, TemplateNarrator]. Nothing else in the app
knows or cares.

5. Update the AI diagnostics screen

Add a second card, "Local model (LiteRT-LM)", showing:
  - the resolved model path and whether the file exists, with its size in MB
  - the EngineState, including init time in ms once Ready
  - the runtime version and the backend in use
  - a "Re-initialize" button
Keep the existing Gemini Nano card above it showing UNAVAILABLE with its raw reason. Both
cards visible together is the story we tell the judges.

6. Update Narrator Lab

Three result columns now: Template | Local Gemma | Nano (shown as unavailable).
Keep the latency and engine badge on each. "Run all 64" runs through the live ladder.

Build, install, and report: engine init time, median generation latency from "Run all 64",
and paste me three sample outputs (a GOAL_ON_SLIP in PLAIN, a WITTY one, and an AWAY_DIGEST)
so I can judge the quality.
```

---

## 12F.3 Reading the result

| What you see | Meaning | Action |
|---|---|---|
| Init 3–8 s, generation 0.6–2 s, sensible English | Working as intended | Done. Keep 270M |
| Generation over 3 s consistently | CPU-bound on longer outputs | Cap the prompt: drop `habitHints` and `digestItems` from FACTS for short moment types |
| Output rambles, adds fake details, ignores the two-line format | 270M is too small for your prompt | Shorten the prompt, then try `gemma3-1b-it-int4.litertlm` (584 MB, same repo family, same code — only the filename changes) |
| Croatian output is poor | Expected at 270M | Demo Nano-path language in English and let TemplateNarrator carry Croatian. Say this openly; it is a real engineering trade-off, not a flaw |
| `EngineState.Failed` mentioning a missing symbol or ABI | Wrong runtime version | Try the next stable version down (0.16.0, then 0.15.0) |
| App ANRs at launch | `initialize()` on the main thread | Move it into a coroutine on Dispatchers.IO |

---

## 12F.5A "Does this make the app 304 MB?" — no, and here is the slide

The single most likely judge question. The answer has three parts.

**1. The file is never in the APK.** Not in the demo (adb push), not in production. The APK
stays at whatever the shell weighs — a few tens of MB.

**2. Production uses Play for On-device AI.** Google ships a mechanism built for precisely
this: models are packaged as *AI packs* in the app bundle, and Play hosts, targets, patches
and delivers them at no cost to the developer. Delivery modes:

| Mode | Counts toward the size shown on the Play listing? | Notes |
|---|---|---|
| install-time | Yes | Wrong for us |
| fast-follow | **No** | Downloads in the background just after install |
| **on-demand** | **No** | **Our choice** — arrives only when the user enables richer updates |

Per-pack limit is 1.5 GB compressed, far above our 304 MB. Play's delta patching means an
app update never re-downloads an unchanged model. Setup is a Gradle module with
`id 'com.android.ai-pack'`, `deliveryType = "on-demand"`, and
`implementation "com.google.android.play:ai-delivery:<version>"` to fetch and monitor it.

**3. Device targeting turns the ladder into a delivery strategy.** AI packs can be targeted
by RAM, SoC, device model and system features — so FEG delivers a *different pack, or none*:

- Device has Gemini Nano → **0 MB**, the OS already holds the model
- Mid-range device, user opts into richer updates → 304 MB pack, on-demand, on Wi-Fi
- Low storage or declined → **0 MB**, template narrator runs

Most of the base downloads nothing. The precedent is familiar to every user: offline maps,
offline translation packs, downloaded music. One consented download, then it works with no
network at all.

Say it on the slide as: *"The model is an on-demand Play AI pack. It does not appear in the
app's download size, most users never receive it, and the ones who do get a feature that
works in airplane mode."*

Do not build AI packs during the hackathon — `adb push` is the demo path and AI packs are
the production answer in one sentence. Only wire it up if you finish everything else.

---

## 12F.4 What this does to the pitch — it improves it

Do not present this as a downgrade. Three arguments, all true:

1. **Wider device reach.** Gemini Nano covers a handful of 2025–26 flagships. FEG's users
   in Croatia, Czechia, Slovakia, Poland and Romania are overwhelmingly on mid-range phones.
   A model FEG ships themselves runs on all of them. Shipping Nano-only would have been the
   naive answer.
2. **FEG controls the model.** Version, language quality, and the ability to fine-tune later
   on their own moment data — none of which is possible when the OS owns the model.
3. **The ladder is the product.** Nano when the OS has it, our own Gemma when it does not,
   templates when neither is possible. The AI diagnostics screen showing a real
   `606-FEATURE_NOT_FOUND` next to a working local model is a stronger technical-feasibility
   moment than a demo that only works on one phone.

Add one line to the deck: *"Tested on a Pixel 10a with no Gemini Nano support — the app
degrades to its own on-device model, and the user sees no difference."*

---

## 12F.5 Cut line

If LiteRT-LM is not producing usable text within **90 minutes**, stop and ship the template
narrator. It already passes all 64 guard tests, it is instant, and it is genuinely on-device.
The Moment Engine (step 13) and the Live Update (step 14) are worth far more to the score
than upgrading the wording of a sentence.
