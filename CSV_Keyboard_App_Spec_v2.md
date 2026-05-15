# 🧩 Android CSV Data Keyboard — Full Build Specification Prompt (v2 — Corrected)

> **Target AI Coding Assistant:** Cursor / Google Jules / Google AI Studio / Gemini
> **Project Type:** Android IME (Input Method Editor) + Companion App
> **Language:** Kotlin
> **Min SDK:** 26 (Android 8.0) | **Target SDK:** 35
> **Architecture:** MVVM + Clean Architecture + Room DB
> **Package Name:** `com.mahavtaar.csvkeyboard`

> **⚠️ v2 Change Summary:** Fixed IME ViewModel lifecycle crash, replaced SharedPreferences CSV cache with Room DB, corrected foregroundServiceType for Android 14+, added missing ColumnMode/ColumnConfig definitions, fixed kotlinx.serialization plugin declaration, added JitPack repo, fixed SessionBus sync strategy, added SAF URI persistence, added CSV parser input validation, added error/empty states in keyboard, added screen rotation handling in FloatingBallService, removed unnecessary READ_EXTERNAL_STORAGE permission, corrected build instruction order, and added RTL safety and font resource bundling.

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
│   │   └── KeyboardViewModel.kt          ← ViewModel for keyboard state (manual lifecycle)
│   ├── setup/
│   │   ├── MainActivity.kt               ← Launcher: IME enable + CSV import
│   │   └── SetupViewModel.kt
│   ├── config/
│   │   ├── ColumnConfigActivity.kt       ← Column mapping configuration screen
│   │   └── ColumnConfigViewModel.kt
│   ├── floating/
│   │   ├── FloatingBallService.kt        ← Foreground overlay service
│   │   └── FloatingBallManager.kt        ← Start/stop controller
│   └── components/
│       ├── InfoChipView.kt               ← Custom view for Info-mode columns
│       └── TypeButtonView.kt             ← Custom view for Type-mode columns
├── data/
│   ├── csv/
│   │   ├── CsvParser.kt                  ← CSV file reader/parser
│   │   └── CsvRepository.kt              ← Data access layer for CSV rows
│   ├── db/
│   │   ├── AppDatabase.kt                ← Room database (replaces SharedPreferences caching)
│   │   ├── CsvRowEntity.kt               ← Room entity for cached CSV rows
│   │   ├── CsvRowDao.kt                  ← DAO for row queries
│   │   ├── ColumnConfigEntity.kt         ← Room entity for column configs
│   │   └── ColumnConfigDao.kt            ← DAO for config queries
│   ├── model/
│   │   ├── ColumnMode.kt                 ← Enum: TYPE, INFO, HIDDEN
│   │   ├── ColumnConfig.kt               ← Column metadata data class
│   │   ├── CsvRow.kt                     ← Data model: Map<String, String>
│   │   ├── ParseResult.kt                ← Sealed result from CsvParser
│   │   └── KeyboardSession.kt            ← Current row index + loaded data
│   └── prefs/
│       ├── AppPreferences.kt             ← SharedPreferences wrapper (non-bulk data only)
│       └── ColumnConfigStore.kt          ← JSON serialization helper for configs
├── bus/
│   └── SessionBus.kt                     ← SharedPreferences-backed event bus
├── res/
│   ├── font/
│   │   └── roboto_mono_regular.ttf       ← Bundled font (don't rely on system font)
│   ├── layout/
│   │   ├── keyboard_view.xml
│   │   ├── keyboard_view_error.xml       ← ← NEW: error/empty state layout
│   │   ├── activity_main.xml
│   │   ├── activity_column_config.xml
│   │   ├── view_floating_ball.xml
│   │   └── view_floating_card.xml
│   ├── animator/
│   │   └── ball_pulse.xml
│   ├── xml/
│   │   └── method.xml
│   └── values/
│       ├── strings.xml                   ← ALL user-visible strings go here (no hardcoding)
│       ├── themes.xml
│       └── font_certs.xml                ← (only if using downloadable fonts)
└── AndroidManifest.xml
```

---

## 📄 Data Models

### `ColumnMode.kt`

> **v2 fix:** This was referenced throughout the original spec but never defined. Define it explicitly.

```kotlin
enum class ColumnMode {
    TYPE,    // Renders as a tappable button — commits value to input field
    INFO,    // Renders as a read-only chip — tap copies to clipboard
    HIDDEN   // Not shown in keyboard UI
}
```

### `ColumnConfig.kt`

```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class ColumnConfig(
    val columnName: String,           // Original CSV header — immutable
    val displayLabel: String,         // Short label shown in keyboard UI
    val mode: ColumnMode,
    val colorHex: String = "#0F3460", // Background color for TYPE buttons
    val order: Int = 0                // Display order (user-reorderable)
)
```

### `CsvRow.kt`

```kotlin
data class CsvRow(
    val rowIndex: Int,
    val data: Map<String, String>   // columnName → cell value; missing columns default to ""
)
```

### `ParseResult.kt`

```kotlin
sealed class ParseResult {
    data class Success(
        val headers: List<String>,
        val rows: List<CsvRow>
    ) : ParseResult()

    sealed class Error : ParseResult() {
        object FileNotFound : Error()
        object EmptyFile : Error()
        object MalformedCsv : Error()
        data class Unknown(val message: String) : Error()
    }
}
```

### `KeyboardSession.kt`

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

## 🗄️ Room Database

> **v2 fix:** The original spec stored CSV rows as a JSON blob in SharedPreferences — this hits the ~8MB limit with real data and causes crashes. Use Room instead.

### `AppDatabase.kt`

```kotlin
@Database(
    entities = [CsvRowEntity::class, ColumnConfigEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun csvRowDao(): CsvRowDao
    abstract fun columnConfigDao(): ColumnConfigDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "csv_keyboard.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
```

### `CsvRowEntity.kt`

```kotlin
@Entity(tableName = "csv_rows")
data class CsvRowEntity(
    @PrimaryKey val rowIndex: Int,
    val dataJson: String   // Serialized Map<String, String>
)
```

### `CsvRowDao.kt`

```kotlin
@Dao
interface CsvRowDao {
    @Query("SELECT * FROM csv_rows ORDER BY rowIndex ASC")
    suspend fun getAllRows(): List<CsvRowEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<CsvRowEntity>)

    @Query("DELETE FROM csv_rows")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM csv_rows")
    suspend fun count(): Int
}
```

### `ColumnConfigEntity.kt`

```kotlin
@Entity(tableName = "column_configs")
data class ColumnConfigEntity(
    @PrimaryKey val columnName: String,
    val displayLabel: String,
    val mode: String,       // ColumnMode.name()
    val colorHex: String,
    val order: Int
)
```

---

## 🔧 Core Modules

---

### 1. `CsvParser.kt`

**Responsibilities:**

- Accept a `Uri` from the file picker (SAF — Storage Access Framework).
- Read CSV using `BufferedReader` from `contentResolver.openInputStream(uri)`.
- Support:
  - Comma and semicolon delimiters (auto-detect: check first line for which appears more).
  - Quoted fields with embedded commas: `"Sharma, Raj"`.
  - UTF-8 encoding with BOM stripping (`\uFEFF`).
  - Empty rows skipped automatically.
- **v2 fix — Input validation:** If a data row has fewer columns than the header, pad missing values with `""`. If a row has more columns than the header, discard the excess silently.
- Return `ParseResult.Success` or a `ParseResult.Error` subtype.

```kotlin
class CsvParser {

    fun parse(uri: Uri, context: Context): ParseResult {
        return try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return ParseResult.Error.FileNotFound

            val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
            val firstLine = reader.readLine()?.trimStart('\uFEFF') // Strip BOM
                ?: return ParseResult.Error.EmptyFile

            val delimiter = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','
            val headers = parseLine(firstLine, delimiter).map { it.trim() }

            if (headers.isEmpty()) return ParseResult.Error.MalformedCsv

            val rows = mutableListOf<CsvRow>()
            var lineIndex = 0

            reader.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val values = parseLine(line, delimiter)
                // Pad or trim to match header count
                val paddedValues = List(headers.size) { i -> values.getOrElse(i) { "" }.trim() }
                val dataMap = headers.zip(paddedValues).toMap()
                rows.add(CsvRow(rowIndex = lineIndex++, data = dataMap))
            }

            if (rows.isEmpty()) return ParseResult.Error.EmptyFile
            ParseResult.Success(headers, rows)

        } catch (e: Exception) {
            ParseResult.Error.Unknown(e.message ?: "Unknown error")
        }
    }

    // State-machine parser that handles quoted fields with embedded delimiters
    private fun parseLine(line: String, delimiter: Char): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == delimiter && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        fields.add(current.toString())
        return fields
    }
}
```

---

### 2. `SessionBus.kt` — Cross-Component Sync

> **v2 fix:** The original spec used a `MutableSharedFlow` singleton. This fails when `FloatingBallService` starts cold (a new process/class load gives it an empty flow with no prior emitters). Use a **SharedPreferences change listener** as the event bus backbone — it works reliably across all components in the same process.

```kotlin
object SessionBus {

    private const val PREF_NAME = "session_bus"
    private const val KEY_CURRENT_INDEX = "current_index"
    private const val KEY_CHANGE_STAMP = "change_stamp"

    fun emitRowChange(context: Context, newIndex: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
            putInt(KEY_CURRENT_INDEX, newIndex)
            putLong(KEY_CHANGE_STAMP, System.currentTimeMillis())
        }
    }

    fun getCurrentIndex(context: Context): Int =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_CURRENT_INDEX, 0)

    fun registerListener(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(listener)
    }
}
```

Usage in `FloatingBallService`:

```kotlin
private val busListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
    refreshCardData()
    animateBallPulse()
}

override fun onCreate() {
    super.onCreate()
    SessionBus.registerListener(this, busListener)
}

override fun onDestroy() {
    SessionBus.unregisterListener(this, busListener)
    super.onDestroy()
}
```

---

### 3. `KeyboardViewModel.kt` — Manual Lifecycle in IME

> **v2 fix:** `InputMethodService` is NOT a `ViewModelStoreOwner`. You cannot use `ViewModelProvider` or `LiveData.observe(lifecycleOwner)` inside it. Use `observeForever()` + a manually managed `CoroutineScope`.

```kotlin
class KeyboardViewModel(private val app: Application) {

    // Manual coroutine scope — cancel in onDestroyInputView
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val db = AppDatabase.getInstance(app)
    private val prefs = AppPreferences(app)

    private val _session = MutableStateFlow<KeyboardSession?>(null)
    val session: StateFlow<KeyboardSession?> = _session.asStateFlow()

    private val _columnConfigs = MutableStateFlow<List<ColumnConfig>>(emptyList())
    val columnConfigs: StateFlow<List<ColumnConfig>> = _columnConfigs.asStateFlow()

    init {
        scope.launch { loadSession() }
    }

    private suspend fun loadSession() {
        val rows = db.csvRowDao().getAllRows().map { entity ->
            val map = Json.decodeFromString<Map<String, String>>(entity.dataJson)
            CsvRow(entity.rowIndex, map)
        }
        if (rows.isEmpty()) return

        val headers = prefs.getCsvHeaders()
        val currentIndex = SessionBus.getCurrentIndex(app)

        _session.value = KeyboardSession(
            filePath = prefs.getCsvUri() ?: "",
            headers = headers,
            rows = rows,
            currentIndex = currentIndex.coerceIn(0, rows.lastIndex)
        )

        val configs = db.columnConfigDao().getAll().map { it.toColumnConfig() }
        _columnConfigs.value = configs.ifEmpty {
            generateDefaultConfigs(headers)
        }
    }

    fun goNext() {
        val s = _session.value ?: return
        if (s.hasNext) {
            val newIndex = s.currentIndex + 1
            _session.value = s.copy(currentIndex = newIndex)
            SessionBus.emitRowChange(app, newIndex)
        }
    }

    fun goPrevious() {
        val s = _session.value ?: return
        if (s.hasPrevious) {
            val newIndex = s.currentIndex - 1
            _session.value = s.copy(currentIndex = newIndex)
            SessionBus.emitRowChange(app, newIndex)
        }
    }

    fun destroy() {
        scope.cancel()
    }

    private fun generateDefaultConfigs(headers: List<String>): List<ColumnConfig> =
        headers.mapIndexed { i, name ->
            ColumnConfig(
                columnName = name,
                displayLabel = name.replace("_", " ").take(12),
                mode = if (i < 3) ColumnMode.TYPE else ColumnMode.INFO,
                order = i
            )
        }
}
```

---

### 4. `CsvKeyboardService.kt` — The IME Service

```kotlin
class CsvKeyboardService : InputMethodService() {

    private lateinit var viewModel: KeyboardViewModel
    private lateinit var keyboardBinding: KeyboardViewBinding
    private var errorBinding: KeyboardViewErrorBinding? = null

    override fun onCreateInputView(): View {
        viewModel = KeyboardViewModel(application)

        // Observe StateFlow manually — NO lifecycle owner in IME
        viewModel.scope.launch {
            viewModel.session.collect { session ->
                if (session == null) {
                    showErrorState()
                } else {
                    showNormalState(session)
                }
            }
        }
        viewModel.scope.launch {
            viewModel.columnConfigs.collect { configs ->
                rebuildKeyboardLayout(configs)
            }
        }

        // Setup swipe gesture on root view
        setupSwipeGesture()

        return keyboardBinding.root
    }

    override fun onDestroyInputView() {
        viewModel.destroy()   // Cancel coroutine scope to avoid leaks
        super.onDestroyInputView()
    }

    private fun showErrorState() {
        // Inflate error layout with "No CSV loaded — tap to set up" message
        // Error layout has a button that deep-links to MainActivity
    }

    private fun showNormalState(session: KeyboardSession) {
        val row = session.currentRow
        keyboardBinding.tvRowCounter.text =
            getString(R.string.row_counter, session.currentIndex + 1, session.totalRows)
        renderRow(row, viewModel.columnConfigs.value)
    }

    private fun renderRow(row: CsvRow, configs: List<ColumnConfig>) {
        keyboardBinding.infoChipsContainer.removeAllViews()
        keyboardBinding.typeButtonsContainer.removeAllViews()

        configs.sortedBy { it.order }.forEach { config ->
            val value = row.data[config.columnName] ?: ""
            when (config.mode) {
                ColumnMode.TYPE -> {
                    val btn = TypeButtonView(this).apply {
                        setLabel(config.displayLabel)
                        setValue(value)
                        setButtonColor(Color.parseColor(config.colorHex))
                        setOnClickListener { commitText(value) }
                        setOnLongClickListener {
                            showTypeButtonMenu(value)
                            true
                        }
                    }
                    keyboardBinding.typeButtonsContainer.addView(btn)
                }
                ColumnMode.INFO -> {
                    val chip = InfoChipView(this).apply {
                        setLabel(config.displayLabel)
                        setValue(value)
                        setOnClickListener { copyToClipboard(value, config.displayLabel) }
                    }
                    keyboardBinding.infoChipsContainer.addView(chip)
                }
                ColumnMode.HIDDEN -> { /* skip */ }
            }
        }
    }

    private fun commitText(value: String) {
        currentInputConnection?.commitText(value, 1)
    }

    private fun copyToClipboard(value: String, label: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(this, getString(R.string.copied, value), Toast.LENGTH_SHORT).show()
    }

    private fun showTypeButtonMenu(value: String) {
        // PopupMenu with: Copy to Clipboard / Type with Space / Type with Newline
    }

    private fun setupSwipeGesture() {
        val gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {
                val diffX = (e2.x - (e1?.x ?: 0f))
                return if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(vX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) viewModel.goPrevious() else viewModel.goNext()
                    true
                } else false
            }
        })
        keyboardBinding.root.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false  // Return false so child views still receive touches
        }
    }
}
```

---

### 5. `keyboard_view.xml` — Keyboard Layout

```xml
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:background="#1A1A2E"
    android:layoutDirection="locale">  <!-- RTL-safe -->

    <!-- Row 1: Info Chips Strip (scrollable horizontal) -->
    <!-- minHeight ensures layout doesn't collapse if no INFO columns -->
    <HorizontalScrollView
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:minHeight="48dp"
        android:scrollbars="none">
        <LinearLayout
            android:id="@+id/infoChipsContainer"
            android:layout_width="wrap_content"
            android:layout_height="match_parent"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:paddingHorizontal="8dp" />
    </HorizontalScrollView>

    <!-- Divider -->
    <View android:layout_width="match_parent" android:layout_height="1dp"
        android:background="#2A2A4E" />

    <!-- Row 2: Type Buttons Grid (FlexboxLayout — wraps to next line) -->
    <!-- maxHeight + scrollView prevents overflow if many columns -->
    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="160dp">
        <com.google.android.flexbox.FlexboxLayout
            android:id="@+id/typeButtonsContainer"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            app:flexWrap="wrap"
            app:alignItems="stretch"
            app:justifyContent="flex_start"
            android:padding="8dp" />
    </ScrollView>

    <!-- Divider -->
    <View android:layout_width="match_parent" android:layout_height="1dp"
        android:background="#2A2A4E" />

    <!-- Row 3: Navigation Bar -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:paddingHorizontal="8dp">

        <ImageButton android:id="@+id/btnPrevious"
            android:src="@drawable/ic_arrow_left"
            android:contentDescription="@string/prev_row"
            android:minWidth="48dp" android:minHeight="48dp" />

        <TextView android:id="@+id/tvRowCounter"
            android:layout_weight="1"
            android:gravity="center"
            android:textColor="#FFFFFF"
            android:fontFamily="@font/roboto_mono_regular"
            android:text="@string/row_counter_default" />

        <ImageButton android:id="@+id/btnNext"
            android:src="@drawable/ic_arrow_right"
            android:contentDescription="@string/next_row"
            android:minWidth="48dp" android:minHeight="48dp" />

        <Space android:layout_width="0dp" android:layout_height="0dp" android:layout_weight="1"/>

        <ImageButton android:id="@+id/btnSettings"
            android:src="@drawable/ic_settings"
            android:contentDescription="@string/open_settings"
            android:minWidth="48dp" android:minHeight="48dp" />

        <ImageButton android:id="@+id/btnReload"
            android:src="@drawable/ic_refresh"
            android:contentDescription="@string/reload_csv"
            android:minWidth="48dp" android:minHeight="48dp" />

    </LinearLayout>

</LinearLayout>
```

### `keyboard_view_error.xml` — Error / Empty State

> **v2 addition:** Shown when no CSV is loaded or the URI has expired.

```xml
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="200dp"
    android:orientation="vertical"
    android:gravity="center"
    android:background="#1A1A2E">

    <ImageView android:src="@drawable/ic_csv_empty"
        android:layout_width="48dp" android:layout_height="48dp"
        android:alpha="0.5" />

    <TextView
        android:text="@string/error_no_csv"
        android:textColor="#AAAAAA"
        android:textSize="14sp"
        android:layout_marginTop="12dp" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btnSetup"
        android:text="@string/tap_to_setup"
        android:layout_marginTop="16dp"
        style="@style/Widget.Material3.Button.OutlinedButton" />

</LinearLayout>
```

---

### 6. `TypeButtonView` — Tap to Paste

Custom View or styled `MaterialButton`. Each button must have `minWidth="80dp"` and `minHeight="48dp"` to maintain accessibility touch target.

```
┌──────────────────────┐
│  Borrower Name       │  ← label (10sp, #AAAAAA, Roboto)
│  Rajesh Kumar        │  ← value (14sp, bold, #FFFFFF, Roboto Mono)
└──────────────────────┘
```

- Background: rounded rectangle (8dp corners), color from `ColumnConfig.colorHex`.
- On single tap: call `commitText(value)`.
- On long press: `PopupMenu` with:
  - **Copy to Clipboard** (no typing)
  - **Type with Space** (appends `" "`)
  - **Type with Newline** (appends `"\n"`)
- `contentDescription` = `"${config.displayLabel}: $value"` for TalkBack.

---

### 7. `InfoChipView` — Display Only

Compact horizontal chip. Tap copies to clipboard + shows Toast.

```
[ Loan ID: KOT2025001 ]   [ EMI: ₹4,500 ]   [ Due: 15-May ]
```

- Background: `#16213E`, corner radius `20dp`.
- Label text: `10sp`, `#AAAAAA`.
- Value text: `12sp`, `#E0E0E0`, `Roboto Mono`.
- `contentDescription` = `"${config.displayLabel}: $value, info only"` for TalkBack.

---

### 8. Navigation Logic (`KeyboardViewModel`)

```kotlin
fun goNext() {
    val s = _session.value ?: return
    if (s.hasNext) {
        val newIndex = s.currentIndex + 1
        _session.value = s.copy(currentIndex = newIndex)
        SessionBus.emitRowChange(app, newIndex)  // Notifies FloatingBallService
    }
}

fun goPrevious() {
    val s = _session.value ?: return
    if (s.hasPrevious) {
        val newIndex = s.currentIndex - 1
        _session.value = s.copy(currentIndex = newIndex)
        SessionBus.emitRowChange(app, newIndex)
    }
}
```

- Row counter: `"Row 12 / 250"`.
- Swipe left/right on keyboard root triggers next/previous (via `GestureDetectorCompat` — see CsvKeyboardService).
- Current index is persisted via `SessionBus.emitRowChange()` which writes to SharedPreferences.

---

### 9. `ColumnConfigActivity.kt` — Column Mapping Screen

RecyclerView with `ItemTouchHelper` for drag-reorder. Each row shows:

| UI Element | Purpose |
|---|---|
| Column name (non-editable) | Original header from CSV |
| Display Label `EditText` | Short label shown in keyboard |
| Mode toggle (TYPE / INFO / HIDDEN) | `MaterialButtonToggleGroup` (3-state) |
| Color picker circle | Tap → `ColorPickerDialog` (TYPE mode only, hidden otherwise) |
| Drag handle `ImageView` | Reorder via `ItemTouchHelper` |

**Toolbar actions:**

- **Save** → persist all configs to Room DB via `ColumnConfigDao`, then call `SessionBus.emitRowChange()` with current index to trigger keyboard refresh.
- **Reset** → delete all rows from `column_configs` table; keyboard regenerates defaults on next load.
- **Preview** → open a mock keyboard `BottomSheetDialogFragment`.

---

### 10. `MainActivity.kt` — Setup & Launcher

Onboarding screen — three steps:

**Step 0: Floating Ball Permission**

```kotlin
// New in v2 — Floating ball setup before anything else
val canOverlay = Settings.canDrawOverlays(this)
// Show toggle: ON/OFF if granted, "Allow" button if not
// "Allow" opens ACTION_MANAGE_OVERLAY_PERMISSION
```

**Step 1: Enable Keyboard**

```kotlin
val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
// Check if our IME is in the enabled list
val isEnabled = imm.enabledInputMethodList.any {
    it.packageName == packageName
}
// Button → Settings.ACTION_INPUT_METHOD_SETTINGS
// Button → imm.showInputMethodPicker()
// Show green checkmark when enabled and selected
```

**Step 2: Load CSV File**

```kotlin
// SAF file picker
val launcher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    uri ?: return@registerForActivityResult
    // v2 fix: MUST take persistable permission or URI dies on reboot
    contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION
    )
    prefs.saveCsvUri(uri.toString())
    viewModel.parseCsv(uri)
}
launcher.launch(arrayOf("text/csv", "text/*", "application/csv"))
```

**Step 3: Go Use It**

- Stats: `"250 rows · 6 columns · 3 TYPE · 2 INFO · 1 HIDDEN"`.
- Instruction text.
- Toggle for Floating Ball (calls `FloatingBallManager.start()` or `.stop()`).

---

## 💾 Persistence Layer

### `AppPreferences.kt`

> **v2:** Only stores lightweight scalars and URIs. Bulk CSV data now lives in Room.

```kotlin
class AppPreferences(private val context: Context) {

    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_CSV_URI = "csv_uri"
        const val KEY_CSV_HEADERS = "csv_headers_json"
        const val KEY_DELIMITER = "csv_delimiter"
        const val KEY_BALL_ENABLED = "ball_enabled"
    }

    fun saveCsvUri(uri: String) = prefs.edit { putString(KEY_CSV_URI, uri) }
    fun getCsvUri(): String? = prefs.getString(KEY_CSV_URI, null)

    fun saveCsvHeaders(headers: List<String>) =
        prefs.edit { putString(KEY_CSV_HEADERS, Json.encodeToString(headers)) }
    fun getCsvHeaders(): List<String> =
        Json.decodeFromString(prefs.getString(KEY_CSV_HEADERS, "[]")!!)

    fun isBallEnabled(): Boolean = prefs.getBoolean(KEY_BALL_ENABLED, false)
    fun setBallEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_BALL_ENABLED, enabled) }
}
```

---

## 🎈 Floating Ball Overlay

### Overview

```
Collapsed (ball):              Expanded (info card):
                               ┌─────────────────────────────┐
  ╭────╮                       │  📋 Row 12 / 250            │
  │ 12 │  ← row number         │─────────────────────────────│
  ╰────╯                       │  Loan ID    KOT2025012      │
   drag                        │  Name       Rajesh Kumar    │
   anywhere                    │  Mobile     9876543210      │
                               │  EMI        ₹4,500         │
                               │  Due        15-May-2025    │
                               │  Status     Pending        │
                               │─────────────────────────────│
                               │  [← Prev]  [Next →]  [✕]  │
                               └─────────────────────────────┘
```

### `FloatingBallService.kt`

> **v2 fixes:** Correct `foregroundServiceType`, added `onConfigurationChanged` for screen rotation, wired `SessionBus` via SharedPreferences listener instead of SharedFlow.

```kotlin
class FloatingBallService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var ballView: View
    private lateinit var cardView: View
    private lateinit var ballParams: WindowManager.LayoutParams
    private lateinit var cardParams: WindowManager.LayoutParams
    private var isExpanded = false
    private var screenWidth = 0

    private val busListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        refreshCardData()
        animateBallPulse()
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        screenWidth = resources.displayMetrics.widthPixels
        SessionBus.registerListener(this, busListener)
        inflateBall()
        inflateCard()
        startForeground(NOTIF_ID, buildNotification())
    }

    // v2 fix: Handle screen rotation — recalculate ball position
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        // Keep ball within new screen bounds
        ballParams.x = ballParams.x.coerceIn(0, screenWidth - 64.dp)
        ballParams.y = ballParams.y.coerceIn(0, screenHeight - 64.dp)
        windowManager.updateViewLayout(ballView, ballParams)
        if (isExpanded) {
            cardParams.x = ballParams.x.coerceIn(0, screenWidth - 320.dp)
            cardParams.y = ballParams.y
            windowManager.updateViewLayout(cardView, cardParams)
        }
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
        // Card NOT added to WindowManager yet — added on expand only
    }

    private fun setupBallTouchListener() {
        var initialX = 0; var initialY = 0
        var touchX = 0f;  var touchY = 0f
        var isDragging = false

        ballView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = ballParams.x; initialY = ballParams.y
                    touchX = event.rawX;    touchY = event.rawY
                    isDragging = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX; val dy = event.rawY - touchY
                    if (abs(dx) > 5 || abs(dy) > 5) isDragging = true
                    ballParams.x = (initialX + dx).toInt()
                    ballParams.y = (initialY + dy).toInt()
                    windowManager.updateViewLayout(ballView, ballParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) expandCard() else snapToEdge()
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge() {
        val targetX = if (ballParams.x + 32.dp < screenWidth / 2) 0 else screenWidth - 64.dp
        ValueAnimator.ofInt(ballParams.x, targetX).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                ballParams.x = it.animatedValue as Int
                windowManager.updateViewLayout(ballView, ballParams)
            }
        }.start()
    }

    private fun expandCard() {
        isExpanded = true
        cardParams.x = ballParams.x.coerceIn(0, screenWidth - 320.dp)
        cardParams.y = ballParams.y
        windowManager.addView(cardView, cardParams)
        refreshCardData()
        // Scale + alpha entrance animation
        cardView.scaleX = 0.5f; cardView.scaleY = 0.5f; cardView.alpha = 0f
        cardView.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(200).start()
    }

    private fun collapseCard() {
        isExpanded = false
        if (cardView.isAttachedToWindow) windowManager.removeView(cardView)
    }

    fun refreshCardData() {
        val currentIndex = SessionBus.getCurrentIndex(this)
        // Read row from Room DB (use runBlocking sparingly — this is a tiny read)
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val db = AppDatabase.getInstance(this@FloatingBallService)
            val entities = db.csvRowDao().getAllRows()
            val row = entities.getOrNull(currentIndex) ?: return@launch
            val data = Json.decodeFromString<Map<String, String>>(row.dataJson)
            val configs = db.columnConfigDao().getAll().map { it.toColumnConfig() }
            val totalRows = entities.size

            withContext(Dispatchers.Main) {
                // Update ball row number
                ballView.findViewById<TextView>(R.id.tvBallRow).text = (currentIndex + 1).toString()

                if (!isExpanded) return@withContext
                // Populate cardView
                cardView.findViewById<TextView>(R.id.tvCardRowCounter).text =
                    getString(R.string.row_counter, currentIndex + 1, totalRows)

                val container = cardView.findViewById<LinearLayout>(R.id.cardRowsContainer)
                container.removeAllViews()
                configs.filter { it.mode != ColumnMode.HIDDEN }.forEach { config ->
                    val value = data[config.columnName] ?: "—"
                    val rowView = inflateInfoRow(config.displayLabel, value)
                    rowView.setOnClickListener { copyToClipboard(value, config.displayLabel) }
                    container.addView(rowView)
                }
            }
        }
    }

    private fun animateBallPulse() {
        val glowRing = ballView.findViewById<View>(R.id.glowRing)
        AnimatorInflater.loadAnimator(this, R.animator.ball_pulse).apply {
            setTarget(glowRing)
            start()
        }
    }

    override fun onDestroy() {
        SessionBus.unregisterListener(this, busListener)
        if (ballView.isAttachedToWindow) windowManager.removeView(ballView)
        if (cardView.isAttachedToWindow) windowManager.removeView(cardView)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIF_ID = 1001
        const val CHANNEL_ID = "csv_keyboard_channel"
        const val ACTION_STOP = "action_stop"
    }
}
```

---

### `view_floating_ball.xml`

```xml
<FrameLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="64dp"
    android:layout_height="64dp">

    <View android:id="@+id/glowRing"
        android:layout_width="64dp"
        android:layout_height="64dp"
        android:background="@drawable/ball_glow_ring"
        android:alpha="0" />

    <LinearLayout
        android:layout_width="56dp"
        android:layout_height="56dp"
        android:layout_gravity="center"
        android:background="@drawable/ball_bg"
        android:orientation="vertical"
        android:gravity="center">

        <TextView android:id="@+id/tvBallRow"
            android:textSize="16sp"
            android:textStyle="bold"
            android:textColor="#FFFFFF"
            android:fontFamily="@font/roboto_mono_regular"
            android:text="1" />

        <TextView
            android:textSize="8sp"
            android:textColor="#AAAAAA"
            android:text="@string/ball_row_label" />

    </LinearLayout>
</FrameLayout>
```

`@drawable/ball_bg`: Circular gradient `#E94560` → `#0F3460`, `elevation="8dp"`.

`res/animator/ball_pulse.xml`:

```xml
<set xmlns:android="http://schemas.android.com/apk/res/android">
    <objectAnimator android:propertyName="scaleX"
        android:valueFrom="1.0" android:valueTo="1.4"
        android:duration="400" android:repeatMode="reverse" android:repeatCount="1"/>
    <objectAnimator android:propertyName="scaleY"
        android:valueFrom="1.0" android:valueTo="1.4"
        android:duration="400" android:repeatMode="reverse" android:repeatCount="1"/>
    <objectAnimator android:propertyName="alpha"
        android:valueFrom="0.8" android:valueTo="0.0"
        android:duration="400" android:repeatMode="reverse" android:repeatCount="1"/>
</set>
```

---

### `view_floating_card.xml`

```xml
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="320dp"
    android:layout_height="wrap_content"
    app:cardBackgroundColor="#1A1A2E"
    app:cardCornerRadius="16dp"
    app:cardElevation="16dp">

    <LinearLayout android:orientation="vertical" android:padding="16dp"
        android:layout_width="match_parent" android:layout_height="wrap_content">

        <LinearLayout android:orientation="horizontal"
            android:layout_width="match_parent" android:layout_height="wrap_content">
            <TextView android:id="@+id/tvCardRowCounter"
                android:textColor="#E94560"
                android:textStyle="bold"
                android:textSize="13sp"
                android:fontFamily="@font/roboto_mono_regular"
                android:layout_weight="1"
                android:layout_width="0dp"
                android:layout_height="wrap_content"/>
            <ImageButton android:id="@+id/btnCardClose"
                android:src="@drawable/ic_close"
                android:imageTintList="@color/chip_text"
                android:contentDescription="@string/close_card"
                android:layout_width="wrap_content" android:layout_height="wrap_content"/>
        </LinearLayout>

        <View android:layout_width="match_parent" android:layout_height="1dp"
            android:background="#2A2A4E" android:layout_marginVertical="8dp"/>

        <LinearLayout android:id="@+id/cardRowsContainer"
            android:orientation="vertical"
            android:layout_width="match_parent" android:layout_height="wrap_content"/>

        <View android:layout_width="match_parent" android:layout_height="1dp"
            android:background="#2A2A4E" android:layout_marginVertical="8dp"/>

        <LinearLayout android:orientation="horizontal" android:gravity="center"
            android:layout_width="match_parent" android:layout_height="wrap_content">
            <com.google.android.material.button.MaterialButton
                android:id="@+id/btnCardPrev"
                android:text="@string/prev_label"
                style="@style/Widget.Material3.Button.OutlinedButton"/>
            <Space android:layout_width="16dp" android:layout_height="wrap_content"/>
            <com.google.android.material.button.MaterialButton
                android:id="@+id/btnCardNext"
                android:text="@string/next_label"
                style="@style/Widget.Material3.Button.OutlinedButton"/>
        </LinearLayout>

    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

---

### `FloatingBallManager.kt`

```kotlin
object FloatingBallManager {

    fun start(context: Context) {
        if (!Settings.canDrawOverlays(context)) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            )
            return
        }
        context.startForegroundService(Intent(context, FloatingBallService::class.java))
        AppPreferences(context).setBallEnabled(true)
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, FloatingBallService::class.java))
        AppPreferences(context).setBallEnabled(false)
    }
}
```

---

### Foreground Notification

```kotlin
private fun buildNotification(): Notification {
    val channel = NotificationChannel(CHANNEL_ID, "CSV Keyboard", NotificationManager.IMPORTANCE_LOW)
    getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

    val stopIntent = PendingIntent.getService(
        this, 0,
        Intent(this, FloatingBallService::class.java).apply { action = ACTION_STOP },
        PendingIntent.FLAG_IMMUTABLE
    )
    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.notif_title))
        .setContentText(getString(R.string.notif_text))
        .setSmallIcon(R.drawable.ic_csv_ball)
        .addAction(R.drawable.ic_stop, getString(R.string.stop_floating), stopIntent)
        .setOngoing(true)
        .setSilent(true)
        .build()
}
```

---

## 📦 Dependencies (`app/build.gradle.kts`)

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.23"  // v2 fix: required for @Serializable
    id("com.google.devtools.ksp")                                       // For Room annotation processing
}

android {
    compileSdk = 35
    defaultConfig {
        applicationId = "com.mahavtaar.csvkeyboard"
        minSdk = 26
        targetSdk = 35
    }
    // Required so FloatingBallService handles rotation
    configChanges = "orientation|screenSize|screenLayout"
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.6")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    // Room (replaces SharedPreferences CSV cache) — v2 addition
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // FlexboxLayout (for wrapping type buttons)
    implementation("com.google.android.flexbox:flexbox:3.0.0")

    // Color Picker — v2 fix: JitPack is required in settings.gradle.kts
    implementation("com.github.skydoves:colorpickerview:2.3.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
}
```

`settings.gradle.kts` — **v2 fix: JitPack must be added or `colorpickerview` won't resolve:**

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }  // Required for colorpickerview
    }
}
```

---

## 🔒 Permissions & Manifest

```xml
<!-- AndroidManifest.xml -->

<!-- v2 fix: READ_EXTERNAL_STORAGE removed — SAF does not need it -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<!-- v2 fix: specialUse replaced with dataSync — avoids Android 14 Play Store justification requirement -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<!-- IME Service -->
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

<!-- Floating Ball Service -->
<!-- v2 fix: configChanges declared so rotation is handled internally -->
<service
    android:name=".ui.floating.FloatingBallService"
    android:foregroundServiceType="dataSync"
    android:exported="false"
    android:configChanges="orientation|screenSize|screenLayout" />
```

`res/xml/method.xml`:

```xml
<input-method xmlns:android="http://schemas.android.com/apk/res/android"
    android:settingsActivity="com.mahavtaar.csvkeyboard.ui.config.ColumnConfigActivity" />
```

---

## 🎨 UI / UX Requirements

### Theme

- **Dark theme mandatory.**
- Background: `#1A1A2E` (deep navy).
- Info chips: `#16213E` background, `#E0E0E0` text.
- Type buttons: configurable, default `#0F3460`, accent `#E94560`.
- Navigation bar: `#0D0D0D`.
- Values font: **Roboto Mono** — bundle as `res/font/roboto_mono_regular.ttf` (do not rely on system availability).
- Labels font: **Roboto** (system default on all Android versions).

### RTL Safety

- Use `android:layoutDirection="locale"` on root `LinearLayout` in `keyboard_view.xml`.
- Use `Gravity.START` / `Gravity.END` everywhere — never `LEFT` / `RIGHT`.
- Navigation buttons (Prev / Next) swap automatically in RTL due to `layoutDirection="locale"`.

### Animations

- Row transition: values cross-fade at 150ms.
- Button tap: ripple + brief scale-down (0.95x, 80ms).
- Keyboard first appearance: slide-up from bottom (200ms).
- Ball pulse: plays on every row change (see `ball_pulse.xml`).

### Accessibility

- All buttons have `contentDescription` from `strings.xml`.
- Touch targets minimum 48×48dp everywhere.
- TalkBack: Info chips announce as `"<label>: <value>, info only"`.

---

## 🔄 State Flow Diagram

```
CSV File Loaded (Uri)
        │
        ▼
contentResolver.takePersistableUriPermission()  ← v2: must call this!
        │
        ▼
   CsvParser.parse()
        │
        ▼
  Room DB: CsvRowDao.insertAll()     AppPreferences.saveCsvHeaders()
        │
        ▼
  ColumnConfigDao.getAll()
  (or auto-generate defaults)
        │
        ▼
  KeyboardViewModel.session (StateFlow)
        │
     ┌──┴───────────────────┐
     ▼                      ▼
INFO columns           TYPE columns
(InfoChipView)         (TypeButtonView)
  ↓ tap                  ↓ tap
  copyToClipboard()      commitText(value)
  + Toast                → focused input field


  [← Prev]  Row 3/250  [Next →]   [Swipe ←→]
       ↓
  SessionBus.emitRowChange(newIndex)
       ↓
  SharedPreferences KEY_CHANGE_STAMP updated
       ↓
  FloatingBallService.busListener fires
       ↓
  refreshCardData() → card updates instantly
```

---

## ✅ Feature Checklist

### Phase 1 — Core IME

- [ ] IME Service registers and appears in Android keyboard list
- [ ] CSV file loads via SAF file picker
- [ ] `takePersistableUriPermission()` called after file pick
- [ ] Headers parsed, rows stored in Room DB
- [ ] Default column config auto-generated (first 3 = TYPE, rest = INFO)
- [ ] TYPE columns render as buttons with `minHeight="48dp"`
- [ ] INFO columns render as chips in scrollable horizontal strip
- [ ] Info chip strip has `minHeight="48dp"` even when empty
- [ ] Tapping TYPE button commits value to focused field
- [ ] Long-press TYPE button shows popup (Copy / Type+Space / Type+Newline)
- [ ] Tapping INFO chip copies to clipboard + Toast
- [ ] Next / Previous navigation works
- [ ] Swipe left/right on keyboard → Previous / Next row
- [ ] Row counter updates correctly
- [ ] Current row index persisted via `SessionBus` across sessions
- [ ] Error state shown when no CSV loaded (keyboard_view_error.xml)

### Phase 2 — Configuration

- [ ] `ColumnConfigActivity` opens from main app
- [ ] `ColumnMode` and `ColumnConfig` data classes defined as specified
- [ ] Per-column mode toggle (TYPE / INFO / HIDDEN) via `MaterialButtonToggleGroup`
- [ ] Custom display label editable per column
- [ ] Column order reorderable via `ItemTouchHelper` drag
- [ ] Color picker for TYPE buttons (JitPack in settings.gradle.kts)
- [ ] Config saves to Room DB and keyboard reflects it immediately
- [ ] Reset to defaults deletes Room config rows

### Phase 3 — Floating Ball

- [ ] `SYSTEM_ALERT_WINDOW` permission requested and handled gracefully
- [ ] Ball appears as 64dp circular overlay, draggable anywhere
- [ ] Ball snaps to nearest screen edge on release (animated, 300ms)
- [ ] Ball shows current row number; updates on navigation
- [ ] Pulse animation fires on every row change
- [ ] Single tap expands info card with all non-HIDDEN columns
- [ ] Info card positioned within screen bounds
- [ ] Each row in card tappable → copies value to clipboard
- [ ] Next / Prev in card syncs with keyboard via `SessionBus`
- [ ] Close button collapses card back to ball
- [ ] Long-press on ball shows popup (Stop / Settings / Reload)
- [ ] Foreground notification with Stop action
- [ ] Ball auto-restores on app reopen if `KEY_BALL_ENABLED = true`
- [ ] Screen rotation handled: ball stays within new screen bounds

### Phase 4 — UX Polish

- [ ] Cross-fade animation on row change (150ms)
- [ ] Settings icon in keyboard → deep-links to ColumnConfigActivity
- [ ] Reload icon in keyboard → re-parses CSV and refreshes Room DB
- [ ] Dark theme applied globally
- [ ] All strings in `res/values/strings.xml` (no hardcoded strings)
- [ ] Roboto Mono bundled as `res/font/roboto_mono_regular.ttf`
- [ ] RTL safe: `layoutDirection="locale"` on all root layouts

### Phase 5 — Advanced

- [ ] Search within loaded CSV (keyboard search bar mode toggle)
- [ ] Filter rows by column value (e.g., Status = "Pending")
- [ ] Multiple CSV profiles (Room `sessions` table + profile switcher in MainActivity)
- [ ] Mark rows as done (highlight/dim used rows)
- [ ] Export current session log (row index + timestamp)

---

## 🧪 Sample CSV Structure (Use Case: Loan Collections)

```
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

> **Workflow:** Agent opens any CRM / dialer app → switches to CSV Keyboard → sees Loan ID, EMI, Due Date, Status in the info strip → taps "Name" to type borrower name → taps "Mobile" to type phone number → presses Next to move to the next borrower.

---

## 📋 Build Instructions for AI Coding Assistant

Follow this order exactly — later modules depend on earlier ones:

1. Define all data models: `ColumnMode`, `ColumnConfig`, `CsvRow`, `ParseResult`, `KeyboardSession`.
2. Build and unit-test `CsvParser.kt` — especially the quoted-field state machine and padding logic.
3. Set up `AppDatabase`, `CsvRowDao`, `ColumnConfigDao` with Room.
4. Build `AppPreferences` (lightweight prefs only) and `SessionBus`.
5. Scaffold `InputMethodService` with a static hardcoded keyboard view to confirm IME registration.
6. Build `KeyboardViewModel` with manual `CoroutineScope` (no `ViewModelProvider`).
7. Wire `KeyboardViewModel` into `CsvKeyboardService` using `StateFlow.collect` with `observeForever`-style collection (no lifecycle owner).
8. Build `MainActivity` with: overlay permission → IME enable → SAF file picker → `takePersistableUriPermission` → parse → Room insert.
9. Build `ColumnConfigActivity` with RecyclerView + `ItemTouchHelper`.
10. Build `FloatingBallService` with ball drag, snap, expand/collapse card, and `SessionBus` listener.
11. Wire Phase 4 polish: animations, long-press menus, swipe gestures, error states, and advanced features.

**Code quality rules:**

- Use **ViewBinding** throughout — no `findViewById`.
- Use **Coroutines + `viewModelScope` or explicit scope** for all async work — never block the main thread.
- All user-visible strings in `res/values/strings.xml`.
- Follow **Material Design 3** guidelines.
- Use `Gravity.START` / `Gravity.END` — never `LEFT` / `RIGHT`.
- Every `CoroutineScope` created manually must be cancelled in the corresponding `onDestroy` / `onDestroyInputView`.

---

*Generated for Mahavtaar Enterprises — Internal Tool Specification v2*
*Build target: Android Studio Ladybug | Kotlin 1.9.23 | AGP 8.x | Min SDK 26 | Target SDK 35*
