# 🧩 Android CSV Data Keyboard — Full Build Specification Prompt

> **Target AI Coding Assistant:** Cursor / Google Jules / Google AI Studio / Gemini  
> **Project Type:** Android IME (Input Method Editor) + Companion App  
> **Language:** Kotlin  
> **Min SDK:** 26 (Android 8.0) | **Target SDK:** 34  
> **Architecture:** MVVM + Clean Architecture  
> **Package Name:** `com.mahavtaar.csvkeyboard`

---

## 🎯 Project Overview

Build a **fully functional Android Custom Keyboard (IME)** that loads data from a **CSV file**, maps each column to a configurable behavior, and lets the user:

- **Type / Paste** the cell value of a column directly into any focused input field (system-wide).
- **Display** a column's value as a read-only info panel inside the keyboard UI without typing it.
- **Navigate rows** using Next / Previous buttons — advancing through each CSV record one at a time.
- **Configure** which columns are "Type", "Info", or "Hidden" via a companion Settings screen (outside the keyboard).

The keyboard works **system-wide** — any app, any input field — just like Gboard or SwiftKey, but instead of typing letters, it pastes structured data from a CSV row.

---

## 📁 Project Structure

```
com.mahavtaar.csvkeyboard/
├── ui/
│   ├── keyboard/
│   │   ├── CsvKeyboardService.kt         ← IME Service (main keyboard)
│   │   ├── KeyboardView.kt               ← Custom keyboard view (XML-inflated)
│   │   └── KeyboardViewModel.kt          ← ViewModel for keyboard state
│   ├── setup/
│   │   ├── MainActivity.kt               ← Launcher: IME enable + CSV import
│   │   └── SetupViewModel.kt
│   ├── config/
│   │   ├── ColumnConfigActivity.kt       ← Column mapping configuration screen
│   │   └── ColumnConfigViewModel.kt
│   └── components/
│       ├── InfoChipView.kt               ← Custom view for Info-mode columns
│       └── TypeButtonView.kt             ← Custom view for Type-mode columns
├── data/
│   ├── csv/
│   │   ├── CsvParser.kt                  ← CSV file reader/parser
│   │   └── CsvRepository.kt              ← Data access layer for CSV rows
│   ├── model/
│   │   ├── CsvRow.kt                     ← Data model: Map<String, String>
│   │   ├── ColumnConfig.kt               ← Column mode enum + metadata
│   │   └── KeyboardSession.kt            ← Current row index + loaded data
│   └── prefs/
│       ├── AppPreferences.kt             ← SharedPreferences wrapper
│       └── ColumnConfigStore.kt          ← Persist column configs as JSON
├── res/
│   ├── layout/
│   │   ├── keyboard_view.xml             ← Root keyboard layout
│   │   ├── activity_main.xml
│   │   └── activity_column_config.xml
│   ├── xml/
│   │   └── method.xml                    ← IME metadata declaration
│   └── values/
│       └── themes.xml
└── AndroidManifest.xml
```

---

## 📄 Data Model

### `ColumnMode` Enum

```kotlin
enum class ColumnMode {
    TYPE,    // Renders as a tappable button → commits value to focused field
    INFO,    // Renders as a read-only chip/label → shown until user presses Next
    HIDDEN   // Not shown in the keyboard at all
}
```

### `ColumnConfig` Data Class

```kotlin
data class ColumnConfig(
    val columnName: String,       // Header from CSV row 0
    val mode: ColumnMode,         // TYPE, INFO, or HIDDEN
    val displayLabel: String,     // Custom label shown in keyboard (editable)
    val order: Int,               // Render order in keyboard UI (drag-to-reorder)
    val textSizeMultiplier: Float = 1.0f,
    val colorHex: String = "#FFFFFF"
)
```

### `CsvRow`

```kotlin
data class CsvRow(
    val rowIndex: Int,
    val data: Map<String, String>   // columnName → cell value
)
```

### `KeyboardSession`

```kotlin
data class KeyboardSession(
    val filePath: String,
    val headers: List<String>,
    val rows: List<CsvRow>,
    val currentIndex: Int = 0,
    val totalRows: Int = rows.size
) {
    val currentRow: CsvRow get() = rows[currentIndex]
    val hasPrevious: Boolean get() = currentIndex > 0
    val hasNext: Boolean get() = currentIndex < totalRows - 1
}
```

---

## 🔧 Core Modules

---

### 1. `CsvParser.kt`

**Responsibilities:**

- Accept a `Uri` from the file picker (SAF — Storage Access Framework).
- Read CSV using `BufferedReader` from `contentResolver.openInputStream(uri)`.
- Support:
  - Comma and semicolon delimiters (auto-detect or configurable).
  - Quoted fields with embedded commas: `"Sharma, Raj"`.
  - UTF-8 encoding with BOM stripping.
  - Empty rows skipped automatically.
- Return `ParseResult(headers: List<String>, rows: List<CsvRow>)`.
- Handle errors: file not found, malformed CSV, empty file — return sealed `Result<ParseResult, CsvError>`.

**Implementation notes:**

```kotlin
class CsvParser {
    fun parse(uri: Uri, context: Context, delimiter: Char = ','): Result<ParseResult> {
        // 1. Open InputStream via ContentResolver
        // 2. Read line 0 as headers (split + trim)
        // 3. For each subsequent line, zip with headers into Map<String, String>
        // 4. Handle quoted fields with a simple state machine
        // 5. Return ParseResult or CsvError
    }
}
```

---

### 2. `CsvKeyboardService.kt` — The IME Service

This is the **heart of the app**. Extend `InputMethodService`.

#### Lifecycle Methods to Override:

| Method | Purpose |
|---|---|
| `onCreateInputView()` | Inflate `keyboard_view.xml`, initialize ViewModel, render columns |
| `onStartInput()` | Called every time keyboard opens; refresh current row display |
| `onFinishInput()` | Optional cleanup |
| `onCreateCandidatesView()` | Optional: show row progress bar above keyboard |

#### Key Logic:

```kotlin
class CsvKeyboardService : InputMethodService() {

    private lateinit var viewModel: KeyboardViewModel
    private lateinit var keyboardBinding: KeyboardViewBinding

    override fun onCreateInputView(): View {
        keyboardBinding = KeyboardViewBinding.inflate(layoutInflater)
        viewModel = ViewModelProvider.AndroidViewModelFactory
            .getInstance(application)
            .create(KeyboardViewModel::class.java)

        observeState()
        return keyboardBinding.root
    }

    private fun observeState() {
        viewModel.currentRow.observe(...) { row ->
            renderRow(row)
        }
        viewModel.columnConfigs.observe(...) { configs ->
            rebuildKeyboardLayout(configs)
        }
    }

    private fun commitText(value: String) {
        currentInputConnection?.commitText(value, 1)
    }

    private fun renderRow(row: CsvRow) {
        // For each ColumnConfig:
        //   MODE=TYPE  → render TypeButtonView with label + value, onClick → commitText(value)
        //   MODE=INFO  → render InfoChipView with label + value (no tap action)
        //   MODE=HIDDEN → skip
    }
}
```

---

### 3. `keyboard_view.xml` — Keyboard Layout

Structure:

```xml
<LinearLayout vertical>

    <!-- Row 1: Info Chips Strip (scrollable horizontal) -->
    <HorizontalScrollView>
        <LinearLayout id="infoChipsContainer" horizontal>
            <!-- InfoChipView items added programmatically -->
        </LinearLayout>
    </HorizontalScrollView>

    <!-- Divider -->

    <!-- Row 2: Type Buttons Grid (FlexboxLayout or GridLayout) -->
    <com.google.android.flexbox.FlexboxLayout id="typeButtonsContainer">
        <!-- TypeButtonView items added programmatically -->
    </com.google.android.flexbox.FlexboxLayout>

    <!-- Divider -->

    <!-- Row 3: Navigation Bar -->
    <LinearLayout horizontal>
        <ImageButton id="btnPrevious" icon="arrow_left" />
        <TextView id="tvRowCounter" text="1 / 250" />
        <ImageButton id="btnNext" icon="arrow_right" />
        <Space weight=1 />
        <ImageButton id="btnSettings" icon="settings" />
        <ImageButton id="btnReload" icon="refresh" />
    </LinearLayout>

</LinearLayout>
```

**Constraints:**
- Total keyboard height: **max 280dp** (respect screen space).
- Info strip height: **48dp**; Type button area: **160dp**; Nav bar: **48dp**.
- Use `WindowInsets` to handle gesture navigation bar overlap.
- No internal scrolling in type button area — if buttons overflow, wrap to next line (FlexboxLayout).

---

### 4. `TypeButtonView` — Tap to Paste

Custom View or a styled `MaterialButton`:

- Shows **column label** (small, 10sp, muted) on top.
- Shows **cell value** (bold, 14sp, primary color) below.
- Background: rounded rectangle, configurable color per column.
- On single tap: call `commitText(value)` via callback.
- On long press: show a `PopupMenu` with options:
  - **Copy to Clipboard** (without typing).
  - **Type with Space** (appends a space after the value).
  - **Type with Newline**.

```
┌─────────────────────┐
│  Borrower Name      │  ← label (small, gray)
│  Rajesh Kumar       │  ← value (bold, white)
└─────────────────────┘
```

---

### 5. `InfoChipView` — Display Only

Compact chip-style view:

- Shows **column label** on left + **cell value** on right.
- Separated by a subtle `|` or vertical divider.
- Tapping it does NOT type — instead it copies value to clipboard with a Toast: `"Copied: <value>"`.
- Background: semi-transparent dark chip, no border.

```
[ Loan ID: KOT2025001 ]   [ EMI: ₹4,500 ]   [ Due: 15-May ]
```

---

### 6. Navigation Logic

In `KeyboardViewModel`:

```kotlin
fun goNext() {
    if (session.hasNext) {
        _session.value = session.copy(currentIndex = session.currentIndex + 1)
        persistCurrentIndex()
    }
}

fun goPrevious() {
    if (session.hasPrevious) {
        _session.value = session.copy(currentIndex = session.currentIndex - 1)
        persistCurrentIndex()
    }
}
```

- Row counter shows: `"Row 12 / 250"`.
- **Swipe left/right** on the keyboard view also triggers next/previous.
- After pressing **Next**, the keyboard automatically refreshes all displayed values.
- The current row index is **persisted in SharedPreferences** so it survives keyboard close/reopen.

---

### 7. `ColumnConfigActivity.kt` — Column Mapping Screen

Launched from the main app. Shows a **RecyclerView** of all CSV columns with:

| UI Element | Purpose |
|---|---|
| Column name (non-editable) | Original header from CSV |
| Display Label field | Editable short name shown in keyboard |
| Mode toggle (TYPE / INFO / HIDDEN) | SegmentedButton or 3-state toggle |
| Color picker circle | Tap to pick button color (TYPE mode only) |
| Drag handle | Reorder columns in keyboard layout |
| Preview chip | Live mini-preview of how it'll look |

**Toolbar actions:**
- **Save** → persist to `ColumnConfigStore` as JSON in SharedPreferences.
- **Reset** → restore all columns to default (TYPE mode, auto-label).
- **Preview** → open a mock keyboard preview bottom sheet.

---

### 8. `MainActivity.kt` — Setup & Launcher

A simple onboarding/control screen with:

1. **Step 1: Enable Keyboard**
   - Detect if IME is enabled via `InputMethodManager`.
   - Button → open Android IME Settings (`Settings.ACTION_INPUT_METHOD_SETTINGS`).
   - Button → open "Change Keyboard" picker (`InputMethodManager.showInputMethodPicker()`).
   - Show green checkmark when enabled and selected.

2. **Step 2: Load CSV File**
   - Button → launch SAF file picker filtered to `text/csv` and `text/*`.
   - Show loaded file name, row count, column count after parsing.
   - Button → **Configure Columns** → opens `ColumnConfigActivity`.

3. **Step 3: Go Use It!**
   - Instructional text: "Open any app, tap any input field, switch to CSV Keyboard."
   - Quick stats: `250 rows loaded · 6 columns · 3 TYPE · 2 INFO · 1 HIDDEN`.

---

## 💾 Persistence Layer

### `AppPreferences.kt`

```kotlin
object AppPreferences {
    const val KEY_CSV_URI = "csv_uri"
    const val KEY_CURRENT_ROW = "current_row_index"
    const val KEY_COLUMN_CONFIGS = "column_configs_json"
    const val KEY_CSV_HEADERS = "csv_headers_json"
    const val KEY_DELIMITER = "csv_delimiter"
}
```

### `ColumnConfigStore.kt`

- Serialize `List<ColumnConfig>` to JSON using `kotlinx.serialization` or `Gson`.
- Store in SharedPreferences under `KEY_COLUMN_CONFIGS`.
- Provide `save()`, `load()`, `reset()` methods.
- On first load (no saved config), auto-generate default configs: first 3 columns = TYPE, rest = INFO.

### CSV Data Caching

- On first load, parse CSV and store rows as JSON array in SharedPreferences (if file size < 5MB).
- For large files (>5MB), keep URI and re-parse on each keyboard open with a background coroutine.
- Show a loading spinner in the keyboard for max 1 second during re-parse.

---

## 🎨 UI / UX Requirements

### Theme
- **Dark theme mandatory** (keyboard must not blind users).
- Background: `#1A1A2E` (deep navy).
- Info chips: `#16213E` background, `#E0E0E0` text.
- Type buttons: configurable, default `#0F3460`, accent `#E94560`.
- Navigation bar: `#0D0D0D`.
- Font: **Roboto Mono** for values (ensures alignment), **Roboto** for labels.

### Animations
- Row transition (Next/Prev): values cross-fade with 150ms duration.
- Button tap: ripple effect + brief scale-down (0.95x, 80ms).
- Keyboard first appearance: slide-up from bottom (200ms).

### Accessibility
- All buttons have `contentDescription`.
- Type buttons minimum touch target: 48×48dp.
- Support TalkBack: Info chips announce as `"<label>: <value>, info only"`.

---

## 🔒 Permissions & Security

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />

<!-- IME Service Declaration -->
<service
    android:name=".ui.keyboard.CsvKeyboardService"
    android:label="@string/ime_name"
    android:permission="android.permission.BIND_INPUT_METHOD"
    android:exported="true">
    <intent-filter>
        <action android:name="android.view.InputMethod" />
    </intent-filter>
    <meta-data
        android:name="android.view.im"
        android:resource="@xml/method" />
</service>
```

**`res/xml/method.xml`:**

```xml
<input-method xmlns:android="http://schemas.android.com/apk/res/android"
    android:settingsActivity="com.mahavtaar.csvkeyboard.ui.config.ColumnConfigActivity" />
```

- CSV data stays **on-device only** — no network permission needed.
- Use SAF `Uri` with `takePersistableUriPermission()` to retain file access across reboots.

---

## 📦 Dependencies (`build.gradle`)

```gradle
dependencies {
    // Core
    implementation "androidx.core:core-ktx:1.12.0"
    implementation "androidx.appcompat:appcompat:1.6.1"
    implementation "com.google.android.material:material:1.11.0"
    implementation "androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0"
    implementation "androidx.lifecycle:lifecycle-livedata-ktx:2.7.0"

    // Coroutines
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"

    // Serialization
    implementation "org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2"

    // FlexboxLayout (for wrapping type buttons)
    implementation "com.google.android.flexbox:flexbox:3.0.0"

    // Color Picker (for column color config)
    implementation "com.github.skydoves:colorpickerview:2.3.0"

    // Drag & Drop RecyclerView (for column reordering)
    // Built-in via ItemTouchHelper — no extra dependency needed

    // Testing
    testImplementation "junit:junit:4.13.2"
    androidTestImplementation "androidx.test.ext:junit:1.1.5"
}
```

---

## 🔄 State Flow Diagram

```
CSV File Loaded (Uri)
        │
        ▼
   CsvParser.parse()
        │
        ▼
  KeyboardSession created
  (headers, rows, index=0)
        │
        ▼
  ColumnConfigStore.load()
  (or generate defaults)
        │
        ▼
  KeyboardViewModel.state
        │
     ┌──┴───────────────────┐
     ▼                      ▼
INFO columns           TYPE columns
(InfoChipView)         (TypeButtonView)
  ↓ shown               ↓ tapped
  "Copy"                commitText(value)
  only                  → input field



  [← Prev]  Row 3/250  [Next →]
       ↓
  currentIndex changes
       ↓
  All views refresh with
  new row's cell values
```

---

## ✅ Feature Checklist

### Phase 1 — Core IME
- [ ] IME Service registers and appears in Android keyboard list
- [ ] CSV file loads via SAF file picker
- [ ] Headers parsed, rows stored in memory
- [ ] Default column config auto-generated (first 3 = TYPE, rest = INFO)
- [ ] TYPE columns render as buttons in keyboard view
- [ ] INFO columns render as chips in info strip
- [ ] Tapping TYPE button commits value to focused field
- [ ] Next / Previous navigation works
- [ ] Row counter updates correctly
- [ ] Current row index persisted across sessions

### Phase 2 — Configuration
- [ ] `ColumnConfigActivity` opens from main app
- [ ] Per-column mode toggle (TYPE / INFO / HIDDEN)
- [ ] Custom display label editable per column
- [ ] Column order reorderable via drag
- [ ] Color picker for TYPE buttons
- [ ] Config saves and keyboard reflects it immediately
- [ ] Reset to defaults option

### Phase 3 — UX Polish
- [ ] Long-press TYPE button shows popup (Copy / Type+Space / Type+Newline)
- [ ] Tapping INFO chip copies value to clipboard + Toast
- [ ] Swipe left/right on keyboard → Next / Previous row
- [ ] Cross-fade animation on row change
- [ ] Settings icon in keyboard → deep-link to ColumnConfigActivity
- [ ] Reload icon in keyboard → re-parse CSV file
- [ ] Dark theme applied globally
- [ ] Loading state during CSV parsing

### Phase 4 — Advanced
- [ ] Search within loaded CSV (keyboard has a search bar mode toggle)
- [ ] Filter rows by column value (e.g., show only rows where Status = "Pending")
- [ ] Multiple CSV profiles (switch between different CSV files)
- [ ] Export current row index + session state as a log
- [ ] Highlight already-used rows (mark as done)

---

## 🧪 Sample CSV Structure (Use Case: Loan Collections)

```csv
Loan_ID,Borrower_Name,Mobile,EMI_Amount,Due_Date,Status
KOT2025001,Rajesh Kumar,9876543210,4500,15-May-2025,Pending
KOT2025002,Priya Sharma,9988776655,6200,10-May-2025,Overdue
KOT2025003,Amit Verma,9123456780,3800,20-May-2025,Paid
```

**Recommended Default Column Mapping:**

| Column | Mode | Label |
|---|---|---|
| `Loan_ID` | INFO | Loan ID |
| `Borrower_Name` | TYPE | Name |
| `Mobile` | TYPE | Mobile |
| `EMI_Amount` | INFO | EMI |
| `Due_Date` | INFO | Due |
| `Status` | INFO | Status |

> **Workflow:** Agent opens any CRM / dialer app → switches to CSV Keyboard → sees Loan ID + EMI + Due Date + Status in the info strip → taps "Name" button to type borrower's name → taps "Mobile" to type phone number → presses Next to move to the next borrower.

---

## 🔮 Floating Ball Overlay — Always-On Info Bubble

A **system-wide floating overlay bubble** (like Facebook Chat Heads) that stays visible on screen at all times — even when the keyboard is closed, even in other apps. It shows the **current row's key info** at a glance and expands into a full info panel on tap.

---

### Overview & Behaviour

```
Collapsed (ball):              Expanded (info card):
                               ┌─────────────────────────────┐
  ╭────╮                       │  📋 Row 12 / 250            │
  │ 12 │  ← row number         │─────────────────────────────│
  ╰────╯                       │  Loan ID   KOT2025012       │
   drag                        │  Name      Rajesh Kumar      │
   anywhere                    │  Mobile    9876543210        │
                               │  EMI       ₹4,500           │
                               │  Due       15-May-2025      │
                               │  Status    Pending          │
                               │─────────────────────────────│
                               │  [← Prev]  [Next →]  [✕]   │
                               └─────────────────────────────┘
```

**Interaction model:**
- **Single tap** on ball → expand into full info card.
- **Single tap** outside card / on `✕` → collapse back to ball.
- **Drag** ball anywhere on screen → sticks to nearest edge (left or right) when released (edge-snapping).
- **Long press** on ball → show quick menu: `Stop Floating`, `Go to Settings`, `Reload CSV`.
- **Next / Prev** buttons inside expanded card → navigate rows (synced with keyboard).
- **Tap any info row** in the card → copy that value to clipboard + Toast.

---

### `FloatingBallService.kt` — Foreground Service

Extend `Service`. Use `WindowManager` to add views as `TYPE_APPLICATION_OVERLAY`.

```kotlin
class FloatingBallService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var ballView: View
    private lateinit var cardView: View
    private var ballParams: WindowManager.LayoutParams
    private var cardParams: WindowManager.LayoutParams
    private var isExpanded = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        inflateBall()
        inflateCard()
        startForeground(NOTIF_ID, buildNotification())
    }

    private fun inflateBall() {
        ballView = LayoutInflater.from(this).inflate(R.layout.view_floating_ball, null)
        ballParams = WindowManager.LayoutParams(
            64.dp, 64.dp,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0; y = 300
        }
        windowManager.addView(ballView, ballParams)
        setupBallTouchListener()
    }

    private fun inflateCard() {
        cardView = LayoutInflater.from(this).inflate(R.layout.view_floating_card, null)
        cardParams = WindowManager.LayoutParams(
            320.dp, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        // Card NOT added to WindowManager yet — added on expand
    }

    private fun setupBallTouchListener() {
        var initialX = 0; var initialY = 0
        var touchX = 0f; var touchY = 0f
        var isDragging = false

        ballView.setOnTouchListener { _, event ->
            when (event.action) {
                ACTION_DOWN -> { touchX = event.rawX; touchY = event.rawY; isDragging = false; true }
                ACTION_MOVE -> {
                    val dx = event.rawX - touchX; val dy = event.rawY - touchY
                    if (abs(dx) > 5 || abs(dy) > 5) isDragging = true
                    ballParams.x = (initialX + dx).toInt()
                    ballParams.y = (initialY + dy).toInt()
                    windowManager.updateViewLayout(ballView, ballParams)
                    true
                }
                ACTION_UP -> {
                    if (!isDragging) expandCard()
                    else snapToEdge()   // animate to nearest screen edge
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge() {
        val screenWidth = resources.displayMetrics.widthPixels
        val targetX = if (ballParams.x + 32.dp < screenWidth / 2) 0 else screenWidth - 64.dp
        // Animate ballParams.x → targetX using ValueAnimator (300ms, DecelerateInterpolator)
    }

    private fun expandCard() {
        isExpanded = true
        // Position card near ball, but keep within screen bounds
        cardParams.x = ballParams.x.coerceIn(0, screenWidth - 320.dp)
        cardParams.y = ballParams.y
        windowManager.addView(cardView, cardParams)
        refreshCardData()
        // Animate: scale from 0.5 + alpha 0 → 1.0 + alpha 1 (200ms)
    }

    private fun collapseCard() {
        isExpanded = false
        if (cardView.isAttachedToWindow) windowManager.removeView(cardView)
    }

    fun refreshCardData() {
        val session = CsvRepository.getSession() ?: return
        val row = session.currentRow
        val configs = ColumnConfigStore.load(this)
        // Populate cardView rows dynamically
        val container = cardView.findViewById<LinearLayout>(R.id.cardRowsContainer)
        container.removeAllViews()
        configs.filter { it.mode != ColumnMode.HIDDEN }.forEach { config ->
            val value = row.data[config.columnName] ?: "—"
            val rowView = inflateInfoRow(config.displayLabel, value, config.colorHex)
            rowView.setOnClickListener { copyToClipboard(value, config.displayLabel) }
            container.addView(rowView)
        }
        cardView.findViewById<TextView>(R.id.tvCardRowCounter).text =
            "Row ${session.currentIndex + 1} / ${session.totalRows}"
    }

    override fun onDestroy() {
        if (ballView.isAttachedToWindow) windowManager.removeView(ballView)
        if (cardView.isAttachedToWindow) windowManager.removeView(cardView)
        super.onDestroy()
    }
}
```

---

### `view_floating_ball.xml` — The Ball

```xml
<FrameLayout
    android:layout_width="64dp"
    android:layout_height="64dp">

    <!-- Outer glow ring (animated pulse) -->
    <View
        android:id="@+id/glowRing"
        android:layout_width="64dp"
        android:layout_height="64dp"
        android:background="@drawable/ball_glow_ring" />

    <!-- Main ball -->
    <LinearLayout
        android:layout_width="56dp"
        android:layout_height="56dp"
        android:layout_gravity="center"
        android:background="@drawable/ball_bg"
        android:orientation="vertical"
        android:gravity="center">

        <!-- Row number (large) -->
        <TextView
            android:id="@+id/tvBallRow"
            android:textSize="16sp"
            android:textStyle="bold"
            android:textColor="#FFFFFF"
            android:text="12" />

        <!-- Mini label -->
        <TextView
            android:textSize="8sp"
            android:textColor="#AAAAAA"
            android:text="ROW" />
    </LinearLayout>

</FrameLayout>
```

**`@drawable/ball_bg`:** Circular gradient — `#E94560` center → `#0F3460` edge, with a subtle drop shadow (`elevation="8dp"`).

**Pulse animation on the glow ring** — plays whenever the row changes (signals data update):

```xml
<!-- res/animator/ball_pulse.xml -->
<set>
    <objectAnimator property="scaleX" from="1.0" to="1.4" duration="400" repeatMode="reverse" />
    <objectAnimator property="scaleY" from="1.0" to="1.4" duration="400" repeatMode="reverse" />
    <objectAnimator property="alpha"  from="0.8" to="0.0" duration="400" repeatMode="reverse" />
</set>
```

---

### `view_floating_card.xml` — The Expanded Info Card

```xml
<MaterialCardView
    android:layout_width="320dp"
    android:layout_height="wrap_content"
    app:cardBackgroundColor="#1A1A2E"
    app:cardCornerRadius="16dp"
    app:cardElevation="16dp">

    <LinearLayout android:orientation="vertical" android:padding="16dp">

        <!-- Header -->
        <LinearLayout android:orientation="horizontal">
            <TextView android:id="@+id/tvCardRowCounter"
                android:text="Row 12 / 250"
                android:textColor="#E94560"
                android:textStyle="bold"
                android:textSize="13sp" />
            <Space android:layout_weight="1" />
            <ImageButton android:id="@+id/btnCardClose"
                android:src="@drawable/ic_close"
                android:tint="#AAAAAA" />
        </LinearLayout>

        <View android:layout_height="1dp"
            android:background="#2A2A4E"
            android:layout_marginVertical="8dp" />

        <!-- Dynamic info rows go here -->
        <LinearLayout android:id="@+id/cardRowsContainer"
            android:orientation="vertical"
            android:divider="@drawable/row_divider"
            android:showDividers="middle" />

        <View android:layout_height="1dp"
            android:background="#2A2A4E"
            android:layout_marginVertical="8dp" />

        <!-- Navigation -->
        <LinearLayout android:orientation="horizontal" android:gravity="center">
            <MaterialButton android:id="@+id/btnCardPrev" android:text="← Prev"
                style="@style/Widget.Material3.Button.OutlinedButton" />
            <Space android:layout_width="16dp" />
            <MaterialButton android:id="@+id/btnCardNext" android:text="Next →"
                style="@style/Widget.Material3.Button.OutlinedButton" />
        </LinearLayout>

    </LinearLayout>
</MaterialCardView>
```

**Each dynamic info row** (`inflateInfoRow()`):

```
┌────────────────────────────────────────┐
│  Loan ID          │  KOT2025012        │
│  (label, gray)    │  (value, white)    │
└────────────────────────────────────────┘
                           ↑ tap to copy
```

---

### `FloatingBallManager.kt` — Start / Stop Controller

```kotlin
object FloatingBallManager {

    fun start(context: Context) {
        if (!Settings.canDrawOverlays(context)) {
            // Launch permission intent
            context.startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
            return
        }
        context.startForegroundService(Intent(context, FloatingBallService::class.java))
        AppPreferences.save(context, AppPreferences.KEY_BALL_ENABLED, true)
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, FloatingBallService::class.java))
        AppPreferences.save(context, AppPreferences.KEY_BALL_ENABLED, false)
    }

    fun isRunning(context: Context): Boolean {
        // Check via ActivityManager.getRunningServices()
    }
}
```

---

### Permissions Required

```xml
<!-- AndroidManifest.xml additions -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

<service
    android:name=".ui.floating.FloatingBallService"
    android:foregroundServiceType="specialUse"
    android:exported="false" />
```

**Permission request flow in `MainActivity`:**

```
Step 0 (new): Enable Floating Ball
   └── Check Settings.canDrawOverlays()
       ├── Granted → show toggle ON/OFF
       └── Not granted → show "Allow Display Over Other Apps" button
                         → open ACTION_MANAGE_OVERLAY_PERMISSION
```

---

### Sync Between Keyboard and Floating Ball

The floating ball and the keyboard **share the same data source** (`CsvRepository` + `AppPreferences`). When either navigates, the other updates:

```
User taps [Next →] in keyboard
        │
        ▼
KeyboardViewModel.goNext()
        │
        ├── Updates SharedPreferences (currentIndex)
        │
        └── Broadcasts LocalBroadcast: ACTION_ROW_CHANGED
                │
                ▼
        FloatingBallService.receiver
                │
                ▼
        refreshCardData() → card UI updates instantly
```

Use `LocalBroadcastManager` (or a `SharedFlow` via a singleton `SessionBus`):

```kotlin
object SessionBus {
    val rowChanged = MutableSharedFlow<Int>(replay = 1, extraBufferCapacity = 1)
}

// In KeyboardViewModel:
fun goNext() {
    // ... update index ...
    SessionBus.rowChanged.tryEmit(session.currentIndex)
}

// In FloatingBallService:
lifecycleScope.launch {
    SessionBus.rowChanged.collect { index ->
        refreshCardData()
        animateBallPulse()
    }
}
```

---

### Floating Ball — Feature Checklist

- [ ] `SYSTEM_ALERT_WINDOW` permission requested and handled gracefully
- [ ] Ball appears as a 64dp circular overlay, draggable anywhere
- [ ] Ball snaps to nearest screen edge on drag release (animated)
- [ ] Ball shows current row number; updates on navigation
- [ ] Pulse animation fires on every row change
- [ ] Single tap expands info card with all INFO + TYPE columns
- [ ] Info card is positioned smartly (stays within screen bounds)
- [ ] Each row in card is tappable → copies value to clipboard
- [ ] Next / Prev in card navigates rows and syncs with keyboard
- [ ] Close button collapses card back to ball
- [ ] Long-press on ball shows popup menu (Stop / Settings / Reload)
- [ ] Foreground notification shown with `Stop Floating` action
- [ ] Ball auto-restores on app reopen if `KEY_BALL_ENABLED = true`
- [ ] Ball hides/dims when a fullscreen app or video is detected (optional)
- [ ] Toggle ball on/off from `MainActivity` and from keyboard settings icon

---

### Foreground Notification (Required for Android 8+)

```kotlin
private fun buildNotification(): Notification {
    val stopIntent = PendingIntent.getService(
        this, 0,
        Intent(this, FloatingBallService::class.java).apply {
            action = ACTION_STOP
        },
        PendingIntent.FLAG_IMMUTABLE
    )
    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("CSV Keyboard Active")
        .setContentText("Row ${session.currentIndex + 1} / ${session.totalRows} — tap to open")
        .setSmallIcon(R.drawable.ic_csv_ball)
        .addAction(R.drawable.ic_stop, "Stop Floating", stopIntent)
        .setOngoing(true)
        .setSilent(true)
        .build()
}
```

---

## 📋 Build Instructions for AI Coding Assistant

1. **Start with** `CsvParser.kt` and write unit tests for it first.
2. **Then** scaffold the `InputMethodService` skeleton with a hardcoded static keyboard view.
3. **Then** wire `KeyboardViewModel` with `LiveData` and connect to the IME view.
4. **Then** build `MainActivity` with IME enable + overlay permission + file picker.
5. **Then** build `FloatingBallService` with ball drag, snap, expand/collapse card.
6. **Then** wire `SessionBus` so keyboard and ball stay in sync on navigation.
7. **Then** build `ColumnConfigActivity` with RecyclerView + ItemTouchHelper.
8. **Last** polish animations, long-press menus, swipe gestures, and advanced features.
7. Use **ViewBinding** throughout — no `findViewById`.
8. Use **Coroutines + `viewModelScope`** for all async CSV parsing — never on the main thread.
9. All strings in `res/values/strings.xml` — no hardcoded strings in Kotlin files.
10. Follow **Material Design 3** component guidelines for all UI elements.

---

*Generated for Mahavtaar Enterprises — Internal Tool Specification*  
*Build target: Android Studio Hedgehog | Kotlin 1.9+ | AGP 8.x*
