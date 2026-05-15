# CSV Keyboard

Android Custom IME that types CSV row data into any app field.

## Setup Steps
1. Enable keyboard
2. Load CSV
3. Configure columns
4. Use

## Requirements
* **Permissions required:** `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`
* **Min SDK:** 26 | **Target SDK:** 35

## Architecture Overview
`CsvParser` → `Room DB` → `KeyboardViewModel` → `CsvKeyboardService` + `FloatingBallService`

## Completion Status
* Phases 1–5 complete
