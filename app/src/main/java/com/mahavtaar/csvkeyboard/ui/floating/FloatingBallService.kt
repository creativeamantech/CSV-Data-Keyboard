package com.mahavtaar.csvkeyboard.ui.floating

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.mahavtaar.csvkeyboard.R
import com.mahavtaar.csvkeyboard.data.csv.CsvRepository
import com.mahavtaar.csvkeyboard.data.csv.SessionBus
import com.mahavtaar.csvkeyboard.data.model.ColumnMode
import com.mahavtaar.csvkeyboard.data.prefs.ColumnConfigStore
import kotlinx.coroutines.launch
import kotlin.math.abs

class FloatingBallService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private lateinit var ballView: View
    private lateinit var cardView: View
    private lateinit var ballParams: WindowManager.LayoutParams
    private lateinit var cardParams: WindowManager.LayoutParams
    private var isExpanded = false

    companion object {
        const val ACTION_STOP = "STOP_FLOATING"
        private const val CHANNEL_ID = "CsvKeyboardFloatingChannel"
        private const val NOTIF_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())

        inflateBall()
        inflateCard()

        lifecycleScope.launch {
            SessionBus.rowChanged.collect {
                refreshCardData()
                animateBallPulse()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            FloatingBallManager.stop(this)
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CSV Keyboard Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
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

    private fun inflateBall() {
        ballView = LayoutInflater.from(this).inflate(R.layout.view_floating_ball, null)
        ballParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0; y = 300
        }
        windowManager.addView(ballView, ballParams)
        setupBallTouchListener()
        refreshCardData()
    }

    private fun inflateCard() {
        cardView = LayoutInflater.from(this).inflate(R.layout.view_floating_card, null)
        cardParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

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

        ballView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = ballParams.x
                    initialY = ballParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (abs(dx) > 10 || abs(dy) > 10) isDragging = true

                    if (isDragging) {
                        ballParams.x = (initialX + dx).toInt()
                        ballParams.y = (initialY + dy).toInt()
                        windowManager.updateViewLayout(ballView, ballParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) expandCard()
                    else snapToEdge()
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
        windowManager.updateViewLayout(ballView, ballParams)
    }

    private fun expandCard() {
        if (isExpanded) return
        isExpanded = true
        val screenWidth = resources.displayMetrics.widthPixels
        cardParams.x = ballParams.x.coerceIn(0, screenWidth - 320)
        cardParams.y = ballParams.y
        windowManager.addView(cardView, cardParams)
        refreshCardData()
    }

    private fun collapseCard() {
        if (!isExpanded) return
        isExpanded = false
        if (cardView.isAttachedToWindow) windowManager.removeView(cardView)
    }

    private fun animateBallPulse() {
        val glowRing = ballView.findViewById<View>(R.id.glowRing)
        val animator = AnimatorInflater.loadAnimator(this, R.animator.ball_pulse) as AnimatorSet
        animator.setTarget(glowRing)
        animator.start()
    }

    private fun refreshCardData() {
        val session = CsvRepository.getSession() ?: CsvRepository.loadSessionFromPrefs(this)
        if (session == null) {
            ballView.findViewById<TextView>(R.id.tvBallRow).text = "-"
            return
        }
        CsvRepository.initSession(session)
        val row = session.currentRow
        ballView.findViewById<TextView>(R.id.tvBallRow).text = (session.currentIndex + 1).toString()

        val configs = ColumnConfigStore.load(this) ?: return
        val container = cardView.findViewById<LinearLayout>(R.id.cardRowsContainer)
        container.removeAllViews()

        configs.filter { it.mode != ColumnMode.HIDDEN }.sortedBy { it.order }.forEach { config ->
            val value = row.data[config.columnName] ?: "—"
            val rowView = LayoutInflater.from(this).inflate(R.layout.view_floating_card_row, container, false)
            rowView.findViewById<TextView>(R.id.tvRowLabel).text = config.displayLabel
            rowView.findViewById<TextView>(R.id.tvRowValue).text = value
            rowView.setOnClickListener { copyToClipboard(value, config.displayLabel) }
            container.addView(rowView)
        }
        cardView.findViewById<TextView>(R.id.tvCardRowCounter).text =
            getString(R.string.row_counter, session.currentIndex + 1, session.totalRows)
    }

    private fun copyToClipboard(text: String, label: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.copied_toast, text), Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        if (ballView.isAttachedToWindow) windowManager.removeView(ballView)
        if (cardView.isAttachedToWindow) windowManager.removeView(cardView)
        super.onDestroy()
    }
}
