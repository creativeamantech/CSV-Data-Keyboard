# 🔧 CSV Keyboard App — Fix & Polish Prompt

Apply ALL of the following fixes to the existing project without changing the overall architecture.

---

## 🚨 Critical Fixes

### 1. Floating Ball — MIUI / Xiaomi Compatibility

The app is running on a MIUI device. Standard `SYSTEM_ALERT_WINDOW` alone is not enough.

**Add to `FloatingBallManager.start()`:**

```kotlin
fun start(context: Context) {
    // Standard overlay check
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
        !Settings.canDrawOverlays(context)) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        context.startActivity(intent)
        return
    }

    // MIUI: also check background start ability — just attempt and catch
    try {
        context.startForegroundService(
            Intent(context, FloatingBallService::class.java)
        )
    } catch (e: Exception) {
        // Fallback: start as regular service
        context.startService(
            Intent(context, FloatingBallService::class.java)
        )
    }
    AppPreferences.save(context, AppPreferences.KEY_BALL_ENABLED, true)
}
```

**Add to `AndroidManifest.xml` inside `<application>`:**

```xml
<!-- MIUI background pop-up permission -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />

<!-- Required on MIUI to prevent service kill -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

**Add a `BootReceiver` to auto-restart the ball after reboot:**

```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (AppPreferences.get(context, AppPreferences.KEY_BALL_ENABLED, false)) {
                FloatingBallManager.start(context)
            }
        }
    }
}
```

```xml
<!-- In AndroidManifest.xml -->
<receiver android:name=".BootReceiver" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

---

### 2. Floating Ball — WindowManager Crash Fix

On Android 12+ and MIUI, adding/removing views to WindowManager on a non-main thread crashes silently.

**Wrap ALL `windowManager.addView()`, `removeView()`, and `updateViewLayout()` calls:**

```kotlin
private fun safeAddView(view: View, params: WindowManager.LayoutParams) {
    Handler(Looper.getMainLooper()).post {
        try {
            if (!view.isAttachedToWindow) {
                windowManager.addView(view, params)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

private fun safeRemoveView(view: View) {
    Handler(Looper.getMainLooper()).post {
        try {
            if (view.isAttachedToWindow) {
                windowManager.removeView(view)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

private fun safeUpdateLayout(view: View, params: WindowManager.LayoutParams) {
    Handler(Looper.getMainLooper()).post {
        try {
            if (view.isAttachedToWindow) {
                windowManager.updateViewLayout(view, params)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
```

Replace all raw `windowManager.*` calls with these safe wrappers throughout `FloatingBallService`.

---

### 3. CSV Loading — URI Persistence Fix

After app restart, the SAF URI permission is lost unless explicitly persisted.

**In `CsvRepository` or wherever the URI is saved, add:**

```kotlin
fun saveUri(context: Context, uri: Uri) {
    // Take persistent permission — without this, URI dies on reboot
    context.contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION
    )
    AppPreferences.save(context, AppPreferences.KEY_CSV_URI, uri.toString())
}
```

**On app start, verify the URI is still accessible before loading:**

```kotlin
fun loadSavedUri(context: Context): Uri? {
    val uriString = AppPreferences.get(context, AppPreferences.KEY_CSV_URI, null)
        ?: return null
    return try {
        val uri = Uri.parse(uriString)
        // Test if still accessible
        context.contentResolver.openInputStream(uri)?.close()
        uri
    } catch (e: Exception) {
        // URI no longer valid — clear it
        AppPreferences.save(context, AppPreferences.KEY_CSV_URI, null)
        null
    }
}
```

---

### 4. IME Keyboard — Null InputConnection Crash

`currentInputConnection` can be null at any time. Every single `commitText()` call must guard against this.

**Replace all `commitText` calls with:**

```kotlin
private fun safeCommitText(value: String) {
    val ic = currentInputConnection ?: return
    try {
        ic.beginBatchEdit()
        ic.commitText(value, 1)
        ic.endBatchEdit()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
```

---

### 5. Keyboard View — Inflation Context Fix

Using `layoutInflater` inside `InputMethodService.onCreateInputView()` sometimes produces views with the wrong theme.

**Fix:**

```kotlin
override fun onCreateInputView(): View {
    // Use createThemedContext to ensure dark theme applies
    val themedContext = ContextThemeWrapper(this, R.style.Theme_CsvKeyboard)
    val inflater = LayoutInflater.from(themedContext)
    keyboardBinding = KeyboardViewBinding.inflate(inflater)
    return keyboardBinding.root
}
```

---

### 6. Empty CSV / No File Loaded — Keyboard Crash

If the keyboard opens before a CSV is loaded, it must show a graceful empty state instead of crashing.

**In `CsvKeyboardService.onCreateInputView()`:**

```kotlin
val session = CsvRepository.getSession()
if (session == null || session.rows.isEmpty()) {
    // Show empty state layout instead of crashing
    return inflater.inflate(R.layout.keyboard_empty_state, null)
}
```

**`keyboard_empty_state.xml`:**

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="160dp"
    android:gravity="center"
    android:orientation="vertical"
    android:background="#1A1A2E">

    <TextView
        android:text="📂 No CSV loaded"
        android:textColor="#AAAAAA"
        android:textSize="16sp" />

    <TextView
        android:text="Open CSV Keyboard app to load a file"
        android:textColor="#666666"
        android:textSize="12sp"
        android:layout_marginTop="8dp" />
</LinearLayout>
```

---

## ⚙️ MainActivity — UX Fixes

### 7. Permission Status — Live Refresh

Currently, permission checkmarks don't update after user returns from Settings.

**In `MainActivity`, override `onResume()`:**

```kotlin
override fun onResume() {
    super.onResume()
    refreshAllPermissionStates()
}

private fun refreshAllPermissionStates() {
    // Step 1: IME enabled?
    val imeEnabled = isImeEnabled()
    updateStepUI(step1View, imeEnabled)

    // Step 2: IME selected as current?
    val imeSelected = isImeSelected()
    updateStepUI(step1SelectedView, imeSelected)

    // Step 3: Overlay permission?
    val overlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        Settings.canDrawOverlays(this) else true
    updateStepUI(step3View, overlayGranted)

    // Step 4: CSV loaded?
    val csvLoaded = CsvRepository.getSession() != null
    updateStepUI(step4View, csvLoaded)
}
```

---

### 8. Floating Ball Toggle — MainActivity

Add a clearly visible toggle card in `MainActivity` for the ball:

```xml
<MaterialCardView
    android:layout_margin="16dp"
    app:cardBackgroundColor="#16213E"
    app:cardCornerRadius="12dp">

    <LinearLayout android:orientation="horizontal" android:padding="16dp">

        <LinearLayout android:orientation="vertical" android:layout_weight="1">
            <TextView android:text="🔮 Floating Ball"
                android:textColor="#FFFFFF" android:textSize="16sp" android:textStyle="bold" />
            <TextView android:id="@+id/tvBallStatus"
                android:text="Tap to show info bubble over any app"
                android:textColor="#AAAAAA" android:textSize="12sp" />
        </LinearLayout>

        <com.google.android.material.switchmaterial.SwitchMaterial
            android:id="@+id/switchFloatingBall"
            android:layout_gravity="center_vertical" />

    </LinearLayout>
</MaterialCardView>
```

```kotlin
switchFloatingBall.isChecked = FloatingBallManager.isRunning(this)
switchFloatingBall.setOnCheckedChangeListener { _, isChecked ->
    if (isChecked) FloatingBallManager.start(this)
    else FloatingBallManager.stop(this)
    tvBallStatus.text = if (isChecked) "Floating ball is active ✓" else "Tap to show info bubble over any app"
}
```

---

## 🎨 UI Polish Fixes

### 9. Keyboard Height — Respect Navigation Bar

On gesture-navigation devices, the keyboard overlaps the nav bar.

**In `CsvKeyboardService`:**

```kotlin
override fun onComputeInsets(outInsets: Insets) {
    super.onComputeInsets(outInsets)
    outInsets.contentTopInsets = outInsets.visibleTopInsets
}
```

**In `keyboard_view.xml`, add bottom padding:**

```xml
android:paddingBottom="@dimen/keyboard_bottom_padding"
```

**In `res/values/dimens.xml`:**

```xml
<dimen name="keyboard_bottom_padding">8dp</dimen>
```

---

### 10. Type Buttons — Overflow Handling

Long cell values (e.g., long names) break the button layout.

**On every `TypeButtonView` value `TextView`:**

```xml
android:maxLines="1"
android:ellipsize="end"
android:maxWidth="120dp"
```

---

### 11. Info Chips — Empty Value Handling

If a CSV cell is empty, the chip shows blank which looks broken.

**In the chip rendering code:**

```kotlin
val displayValue = value.ifBlank { "—" }
```

---

### 12. Row Counter — Edge Cases

Fix counter showing `"Row 0 / 0"` when no CSV is loaded:

```kotlin
val counterText = if (session == null || session.totalRows == 0)
    "No data"
else
    "Row ${session.currentIndex + 1} / ${session.totalRows}"
tvRowCounter.text = counterText
```

---

## 🔁 SessionBus — Fix for Service Context

`FloatingBallService` extending `LifecycleService` requires the `androidx.lifecycle:lifecycle-service` dependency. Confirm it is in `build.gradle.kts`:

```kotlin
implementation("androidx.lifecycle:lifecycle-service:2.7.0")
```

And the service declaration must use `LifecycleService`:

```kotlin
class FloatingBallService : LifecycleService() {
    // lifecycleScope is now available here
}
```

---

## 📋 Summary of All Files to Touch

| File | Change |
|---|---|
| `FloatingBallService.kt` | Safe wrappers, LifecycleService, SessionBus collect |
| `FloatingBallManager.kt` | MIUI-safe start, try/catch fallback |
| `BootReceiver.kt` | New file — auto-restart ball on reboot |
| `CsvRepository.kt` | URI persistence + validity check on load |
| `CsvKeyboardService.kt` | Null InputConnection guard, empty state |
| `MainActivity.kt` | onResume refresh, floating ball toggle card |
| `AndroidManifest.xml` | Add BOOT_COMPLETED, BootReceiver, permissions |
| `keyboard_view.xml` | Bottom padding for nav bar |
| `keyboard_empty_state.xml` | New layout file |
| `build.gradle.kts` | Add lifecycle-service dependency |
| `res/values/dimens.xml` | keyboard_bottom_padding |

---

*Apply all fixes in one pass. Do not change any logic outside of these specified areas.*
