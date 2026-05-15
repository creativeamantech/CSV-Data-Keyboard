package com.mahavtaar.csvkeyboard.ui.floating

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.mahavtaar.csvkeyboard.R
import com.mahavtaar.csvkeyboard.data.csv.CsvRepository
import com.mahavtaar.csvkeyboard.data.csv.SessionBus
import com.mahavtaar.csvkeyboard.data.model.ColumnMode
import com.mahavtaar.csvkeyboard.data.prefs.ColumnConfigStore
import com.mahavtaar.csvkeyboard.ui.setup.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class FloatingBallService : Service(), SessionBus.RowChangeListener {

    private lateinit var windowManager: WindowManager
    private lateinit var ballView: View
    private lateinit var cardView: View
    private lateinit var ballParams: WindowManager.LayoutParams
    private lateinit var cardParams: WindowManager.LayoutParams
    private var isExpanded = false

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "STOP_FLOATING_BALL"
        const val CHANNEL_ID = "floating_ball_channel"
        const val NOTIF_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        inflateBall()
        inflateCard()

        SessionBus.init(this)
        SessionBus.addListener(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Ensure ball snaps to edge within new screen bounds
        if (::ballView.isInitialized && ::ballParams.isInitialized) {
            val screenWidth = resources.displayMetrics.widthPixels
            val screenHeight = resources.displayMetrics.heightPixels

            // Keep ball inside vertically
            if (ballParams.y > screenHeight - ballView.height) {
                ballParams.y = screenHeight - ballView.height
            }

            // Snap to edge horizontally
            snapToEdge()

            if (isExpanded) {
                cardParams.x = ballParams.x.coerceIn(0, screenWidth - 320)
                cardParams.y = ballParams.y
                safeUpdateLayout(cardView, cardParams)
            }
        }
    }

    override fun onRowChanged(newIndex: Int) {
        refreshCardData()
        animateBallPulse()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            FloatingBallManager.stop(this)
        }
        return START_STICKY
    }

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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CSV Floating Ball",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows floating CSV data ball"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, FloatingBallService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CSV Keyboard Active")
            .setContentText("Overlay is running")
            .setSmallIcon(R.drawable.ic_csv_ball)
            .addAction(R.drawable.ic_stop, "Stop Floating", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createOverlayParams(width: Int, height: Int): WindowManager.LayoutParams {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val initialY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = windowManager.currentWindowMetrics.windowInsets
            val topInset = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()).top
            topInset + 100 // Safely offset below notch/status bar
        } else {
            200
        }

        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = initialY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blurBehindRadius = 0
            }
        }
    }

    private fun inflateBall() {
        ballView = LayoutInflater.from(ContextThemeWrapper(this, R.style.Theme_CsvKeyboard)).inflate(R.layout.view_floating_ball, null)
        ballParams = createOverlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        safeAddView(ballView, ballParams)
        setupBallTouchListener()
        refreshCardData()

        ballView.setOnLongClickListener {
            val popup = PopupMenu(ContextThemeWrapper(this, R.style.Theme_CsvKeyboard), ballView)
            popup.menu.add(0, 1, 0, getString(R.string.stop_floating))
            popup.menu.add(0, 2, 1, getString(R.string.settings))
            popup.menu.add(0, 3, 2, getString(R.string.reload))

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        FloatingBallManager.stop(this)
                        true
                    }
                    2 -> {
                        val intent = Intent(this, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        true
                    }
                    3 -> {
                        serviceScope.launch {
                            val session = CsvRepository.loadSession(this@FloatingBallService)
                            if (session != null) {
                                CsvRepository.initSession(session)
                                withContext(Dispatchers.Main) {
                                    refreshCardData()
                                    Toast.makeText(this@FloatingBallService, "Reloaded", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
            popup.show()
            true
        }
    }

    private fun inflateCard() {
        cardView = LayoutInflater.from(ContextThemeWrapper(this, R.style.Theme_CsvKeyboard)).inflate(R.layout.view_floating_card, null)
        cardParams = createOverlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        cardView.findViewById<ImageButton>(R.id.btnCardClose).setOnClickListener { collapseCard() }
        cardView.findViewById<View>(R.id.btnCardNext).setOnClickListener {
            val session = CsvRepository.getSession() ?: return@setOnClickListener
            if (session.hasNext) CsvRepository.updateIndex(this, session.currentIndex + 1)
        }
        cardView.findViewById<View>(R.id.btnCardPrev).setOnClickListener {
            val session = CsvRepository.getSession() ?: return@setOnClickListener
            if (session.hasPrevious) CsvRepository.updateIndex(this, session.currentIndex - 1)
        }
    }

    private fun setupBallTouchListener() {
        var initialX = 0; var initialY = 0
        var touchX = 0f; var touchY = 0f
        var isDragging = false
        var isLongPress = false
        val longPressRunnable = Runnable {
            isLongPress = true
            ballView.performLongClick()
        }
        val handler = Handler(Looper.getMainLooper())

        ballView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = ballParams.x
                    initialY = ballParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    isDragging = false
                    isLongPress = false
                    handler.postDelayed(longPressRunnable, 500)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isDragging = true
                        handler.removeCallbacks(longPressRunnable)
                    }

                    if (isDragging) {
                        ballParams.x = (initialX + dx).toInt()
                        ballParams.y = (initialY + dy).toInt()
                        safeUpdateLayout(ballView, ballParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (!isDragging && !isLongPress) expandCard()
                    else if (isDragging) snapToEdge()
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge() {
        val screenWidth = resources.displayMetrics.widthPixels
        val targetX = if (ballParams.x + ballView.width / 2 < screenWidth / 2) 0 else screenWidth - ballView.width
        ballParams.x = targetX
        safeUpdateLayout(ballView, ballParams)
    }

    private fun expandCard() {
        if (isExpanded) return
        if (cardView.isAttachedToWindow) return
        isExpanded = true
        val screenWidth = resources.displayMetrics.widthPixels
        cardParams.x = ballParams.x.coerceIn(0, screenWidth - 320)
        cardParams.y = ballParams.y
        safeAddView(cardView, cardParams)
        refreshCardData()
    }

    private fun collapseCard() {
        if (!isExpanded) return
        isExpanded = false
        safeRemoveView(cardView)
    }

    private fun animateBallPulse() {
        Handler(Looper.getMainLooper()).post {
            val glowRing = ballView.findViewById<View>(R.id.glowRing)
            val animator = AnimatorInflater.loadAnimator(this@FloatingBallService, R.animator.ball_pulse) as AnimatorSet
            animator.setTarget(glowRing)
            animator.start()
        }
    }

    private fun refreshCardData() {
        serviceScope.launch(Dispatchers.IO) {
            val session = CsvRepository.getSession() ?: CsvRepository.loadSession(this@FloatingBallService)

            withContext(Dispatchers.Main) {
                if (session == null) {
                    ballView.findViewById<TextView>(R.id.tvBallRow).text = "-"
                    return@withContext
                }
                CsvRepository.initSession(session)
                val row = session.currentRow
                ballView.findViewById<TextView>(R.id.tvBallRow).text = (session.currentIndex + 1).toString()

                val configs = ColumnConfigStore.load(this@FloatingBallService)
                val container = cardView.findViewById<LinearLayout>(R.id.cardRowsContainer)
                container.removeAllViews()

                configs.filter { it.mode != ColumnMode.HIDDEN }.sortedBy { it.order }.forEach { config ->
                    val rawValue = row.data[config.columnName] ?: ""
                    val value = rawValue.ifBlank { "—" }
                    val rowView = LayoutInflater.from(this@FloatingBallService).inflate(R.layout.view_floating_card_row, container, false)
                    rowView.findViewById<TextView>(R.id.tvRowLabel).text = config.displayLabel
                    rowView.findViewById<TextView>(R.id.tvRowValue).text = value
                    rowView.setOnClickListener { copyToClipboard(value, config.displayLabel) }
                    container.addView(rowView)
                }

                val counterText = if (session.totalRows == 0) "No data" else "Row ${session.currentIndex + 1} / ${session.totalRows}"
                cardView.findViewById<TextView>(R.id.tvCardRowCounter).text = counterText
            }
        }
    }

    private fun copyToClipboard(text: String, label: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.copied_toast, text), Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        SessionBus.removeListener(this)
        serviceScope.cancel()
        safeRemoveView(ballView)
        safeRemoveView(cardView)
        super.onDestroy()
    }
}
