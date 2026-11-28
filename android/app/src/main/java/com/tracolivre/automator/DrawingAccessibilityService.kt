package com.tracolivre.automator

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.TargetApi
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class DrawingAccessibilityService : AccessibilityService() {
    companion object {
        const val PREFERENCES = "drawing_flow"
        const val KEY_TARGET_PACKAGE = "target_package"
        const val KEY_IMAGE_ASPECT = "image_aspect"
        const val KEY_BRUSH_SIZE = "brush_size"
        const val KEY_AWAITING_TARGET = "awaiting_target"
        const val KEY_PLAN_READY = "plan_ready"
        const val KEY_IS_DRAWING = "is_drawing"
        const val KEY_STATUS = "status"

        private const val DRAWING_APP_PACKAGE = "com.newlang.weelife.sa"
        internal val BRUSH_SIZE_LABELS = listOf(
            "Muito fino",
            "Fino",
            "Médio-fino",
            "Médio",
            "Médio-grosso",
            "Grosso"
        )
        private const val DRAWING_SPEED_PIXELS_PER_SECOND = 1000f
        private const val MAX_STROKE_DURATION_MS = 2200L
        private const val TAG = "DrawingAccessibility"

        @Volatile
        var isConnected = false
            private set
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val screenshotExecutor = Executors.newSingleThreadExecutor()
    private lateinit var windowManager: WindowManager
    private var promptView: View? = null
    private var progressView: View? = null
    private var progressLabel: TextView? = null
    private var quickAccessBubble: View? = null
    private var quickAccessPanel: View? = null
    private var selectionView: AreaSelectionView? = null
    private var previewView: BoardPreviewView? = null
    private var targetPackage: String? = null
    private var strokes: List<TraceStroke> = emptyList()
    private var drawingBounds: RectF? = null
    private var colorPickerControls: ColorPickerControls? = null
    private var selectedColor: Int? = null
    private var isSelectingColor = false
    private var nextStroke = 0
    private var isDrawing = false
    private var isPaused = false
    private var targetIsForeground = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        publishStatus("Acessibilidade ativa.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            handleAccessibilityEvent(event)
        } catch (error: RuntimeException) {
            Log.e(TAG, "Falha ao processar evento de acessibilidade.", error)
            stopDrawing(
                "O serviço encontrou um erro ao acompanhar o app. Desative e ative a Acessibilidade novamente. " +
                    (error.message ?: error.javaClass.simpleName)
            )
        }
    }

    private fun handleAccessibilityEvent(event: AccessibilityEvent?) {
        val eventType = event?.eventType ?: return
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) return
        val packageName = event.packageName?.toString() ?: return
        val preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        if (packageName == this.packageName) {
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && MainActivity.isInForeground) {
                targetIsForeground = false
                removeQuickAccessPanel()
                removeQuickAccessBubble()
                if (isDrawing) {
                    pauseDrawing("Desenho pausado para abrir o WeeLife Line Art.", false)
                }
            }
            return
        }
        val wantedPackage = preferences.getString(KEY_TARGET_PACKAGE, null)
        val isTargetApp = packageName == wantedPackage
        val wasTargetForeground = targetIsForeground
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            targetIsForeground = isTargetApp
        } else if (isTargetApp) {
            targetIsForeground = true
        } else {
            return
        }
        if (isTargetApp) {
            targetPackage = wantedPackage
            showQuickAccessBubble()
        } else if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            removeQuickAccessPanel()
            removeQuickAccessBubble()
        }

        val returnedToTarget = isTargetApp && !wasTargetForeground && targetIsForeground
        if (returnedToTarget &&
            (preferences.getBoolean(KEY_AWAITING_TARGET, false) ||
                (preferences.getBoolean(KEY_PLAN_READY, false) && !isDrawing && !isPaused))
        ) {
            targetPackage = wantedPackage
            preferences.edit()
                .putBoolean(KEY_AWAITING_TARGET, false)
                .putString(KEY_STATUS, "App de desenho aberto. Escolha o lápis e marque a área.")
                .apply()
            mainHandler.post { showDrawingPrompt() }
        } else if (isDrawing && packageName != targetPackage) {
            pauseDrawing("O app de desenho perdeu o foco.", false)
        } else if (isPaused && packageName == targetPackage) {
            mainHandler.post { showResumePrompt() }
        }
    }

    override fun onInterrupt() {
        stopDrawing("Acessibilidade foi interrompida.")
    }

    override fun onDestroy() {
        isConnected = false
        isDrawing = false
        isPaused = false
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
            .putBoolean(KEY_IS_DRAWING, false)
            .apply()
        removePrompt()
        removeProgressOverlay()
        removeQuickAccessPanel()
        removeQuickAccessBubble()
        removeSelectionView()
        removePreviewView()
        screenshotExecutor.shutdown()
        super.onDestroy()
    }

    private fun showDrawingPrompt() {
        val isTargetApp = targetPackage == DRAWING_APP_PACKAGE
        val actions = if (isTargetApp) {
            listOf(
                "Reconhecer lousa" to {
                    removePrompt()
                    recognizeCanvas()
                },
                "Marcar área manualmente" to {
                    removePrompt()
                    showAreaSelection()
                },
                "Cancelar" to { cancelFlow("Operação cancelada.") }
            )
        } else {
            listOf(
                "Marcar área manualmente" to {
                    removePrompt()
                    showAreaSelection()
                },
                "Cancelar" to { cancelFlow("Operação cancelada.") }
            )
        }
        showActionPanel(
            "WeeLife Line Art",
            if (isTargetApp) {
                "No WeeLife, escolha manualmente o lápis e a espessura desejada; depois reconheça a lousa."
            } else {
                "Selecione o lápis e marque a área do desenho."
            },
            actions
        )
    }

    private fun showActionPanel(
        title: String,
        message: String,
        actions: List<Pair<String, () -> Unit>>
    ) {
        removePrompt()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(10), dp(13), dp(8))
            setBackgroundColor(Color.rgb(36, 36, 58))
        }

        panel.addView(TextView(this).apply {
            text = title
            textSize = 14f
            setTextColor(Color.WHITE)
        })
        panel.addView(TextView(this).apply {
            text = message
            textSize = 12f
            setTextColor(Color.rgb(225, 225, 240))
            setPadding(0, dp(4), 0, dp(5))
        })
        actions.forEach { (label, action) ->
            panel.addView(Button(this).apply {
                text = label
                setOnClickListener { action() }
            })
        }

        promptView = panel
        try {
            windowManager.addView(panel, WindowManager.LayoutParams(
                minOf(dp(360), resources.displayMetrics.widthPixels - dp(32)),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            })
        } catch (error: RuntimeException) {
            promptView = null
            val message = "Não consegui mostrar os controles sobre o app: ${error.message}"
            publishStatus(message)
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showQuickAccessBubble() {
        if (quickAccessBubble != null || !isConnected || !targetIsForeground) return
        val bubble = Button(this).apply {
            text = "WL"
            textSize = 12f
            isAllCaps = false
            minWidth = dp(48)
            minHeight = dp(48)
            setPadding(dp(4), 0, dp(4), 0)
            contentDescription = "Abrir controles do WeeLife Line Art"
            setOnClickListener {
                if (quickAccessPanel == null) showQuickAccessPanel() else removeQuickAccessPanel()
            }
        }
        quickAccessBubble = bubble
        try {
            windowManager.addView(
                bubble,
                WindowManager.LayoutParams(
                    dp(56),
                    dp(48),
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    x = dp(8)
                    y = dp(72)
                }
            )
        } catch (error: RuntimeException) {
            quickAccessBubble = null
            publishStatus("Não consegui mostrar o atalho flutuante: ${error.message}")
        }
    }

    private fun showQuickAccessPanel() {
        removeQuickAccessPanel()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(8))
            setBackgroundColor(Color.rgb(36, 36, 58))
        }
        panel.addView(TextView(this).apply {
            text = "WeeLife Line Art"
            textSize = 14f
            setTextColor(Color.WHITE)
        })
        val status = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
            .getString(KEY_STATUS, null)
            .orEmpty()
        panel.addView(TextView(this).apply {
            text = status
            textSize = 11f
            setTextColor(Color.rgb(225, 225, 240))
            setPadding(0, dp(4), 0, dp(5))
            maxLines = 3
        })

        when {
            isDrawing -> addQuickAction(panel, "Pausar desenho") {
                pauseDrawing("Desenho pausado pelo usuário.", false)
                showQuickAccessPanel()
            }
            isPaused -> addQuickAction(panel, "Continuar desenho") {
                removeQuickAccessPanel()
                continueDrawing()
            }
        }
        if (isDrawing || isPaused) {
            addQuickAction(panel, "Parar desenho") {
                stopDrawing("Desenho interrompido pelo usuário.")
                showQuickAccessPanel()
            }
        }
        if (getSharedPreferences(PREFERENCES, MODE_PRIVATE).getBoolean(KEY_PLAN_READY, false)) {
            addQuickAction(panel, "Preparação e área de desenho") {
                removeQuickAccessPanel()
                showDrawingPrompt()
            }
        }
        addQuickAction(panel, "Trocar imagem / opções") {
            openMainActivityForConfiguration()
        }
        addQuickAction(panel, "Fechar painel") {
            removeQuickAccessPanel()
        }

        quickAccessPanel = panel
        try {
            windowManager.addView(
                panel,
                WindowManager.LayoutParams(
                    minOf(dp(280), resources.displayMetrics.widthPixels - dp(24)),
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    x = dp(8)
                    y = dp(124)
                }
            )
        } catch (error: RuntimeException) {
            quickAccessPanel = null
            publishStatus("Não consegui abrir os controles flutuantes: ${error.message}")
        }
    }

    private fun addQuickAction(panel: LinearLayout, label: String, action: () -> Unit) {
        panel.addView(Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        })
    }

    private fun openMainActivityForConfiguration() {
        removeQuickAccessPanel()
        removeQuickAccessBubble()
        removePrompt()
        removeSelectionView()
        removePreviewView()
        if (isDrawing || isPaused) {
            stopDrawing("Desenho interrompido para alterar a imagem ou as opções.")
        }
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } catch (error: RuntimeException) {
            publishStatus("Não consegui abrir o WeeLife Line Art: ${error.message}")
        }
    }

    private fun removeQuickAccessBubble() {
        quickAccessBubble?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        quickAccessBubble = null
    }

    private fun removeQuickAccessPanel() {
        quickAccessPanel?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        quickAccessPanel = null
    }

    @TargetApi(Build.VERSION_CODES.R)
    private fun recognizeCanvas() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            publishStatus("O reconhecimento automático requer Android 11. Use “Marcar área manualmente”.")
            showDrawingPrompt()
            return
        }
        publishStatus("Analisando a tela para encontrar a lousa...")
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, screenshotExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    var failure: Exception? = null
                    val bounds = try {
                        val hardwareBuffer = screenshot.hardwareBuffer
                        try {
                            val captured = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                                ?: error("A captura da tela ficou vazia.")
                            try {
                                CanvasDetector.find(captured)
                            } finally {
                                captured.recycle()
                            }
                        } finally {
                            hardwareBuffer.close()
                        }
                    } catch (error: Exception) {
                        Log.e(TAG, "Falha ao reconhecer a lousa.", error)
                        failure = error
                        null
                    }
                    mainHandler.post {
                        if (!isConnected) return@post
                        if (failure != null) {
                            val reason = failure?.let { it.message ?: it.javaClass.simpleName }
                            publishStatus(
                                "Falha no reconhecimento da lousa ($reason). Tente marcar a área manualmente."
                            )
                            showDrawingPrompt()
                        } else if (bounds == null) {
                            publishStatus("Não encontrei uma lousa nítida. Ajuste a tela e tente marcar a área manualmente.")
                            showDrawingPrompt()
                        } else {
                            showRecognizedBounds(bounds, automatic = true)
                        }
                    }
                }

                override fun onFailure(errorCode: Int) {
                    mainHandler.post {
                        if (!isConnected) return@post
                        publishStatus("Não consegui analisar a tela (código $errorCode). Tente marcar a área manualmente.")
                        showDrawingPrompt()
                    }
                }
            })
        } catch (error: RuntimeException) {
            Log.e(TAG, "Não foi possível iniciar a captura da tela.", error)
            publishStatus(
                "Não foi possível iniciar o reconhecimento (" +
                    (error.message ?: error.javaClass.simpleName) +
                    "). Tente marcar a área manualmente."
            )
            showDrawingPrompt()
        }
    }

    private fun showRecognizedBounds(bounds: RectF, automatic: Boolean) {
        removePreviewView()
        previewView = BoardPreviewView(bounds)
        try {
            windowManager.addView(previewView, WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ))
        } catch (error: RuntimeException) {
            previewView = null
            publishStatus("Não consegui mostrar a área reconhecida: ${error.message}")
            showDrawingPrompt()
            return
        }

        val manualToolInstructions = if (targetPackage == DRAWING_APP_PACKAGE) {
            val brushSize = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                .getInt(KEY_BRUSH_SIZE, 2)
                .coerceIn(0, BRUSH_SIZE_LABELS.lastIndex)
            "No WeeLife, selecione manualmente o lápis e a espessura \"${BRUSH_SIZE_LABELS[brushSize]}\". " +
                "O app não mudará essas ferramentas."
        } else {
            ""
        }
        publishStatus(
            if (automatic) "Lousa reconhecida. Confira o retângulo destacado antes de desenhar."
            else "Área marcada. Confira o retângulo antes de desenhar."
        )
        showActionPanel(
            if (automatic) "Lousa reconhecida" else "Área marcada",
            "Confira o retângulo destacado. $manualToolInstructions O desenho ficará dentro desta área.",
            listOf(
                "Usar esta área" to {
                    removePrompt()
                    removePreviewView()
                    showColorPickerPrompt(bounds)
                },
                (if (automatic) "Marcar manualmente" else "Marcar novamente") to {
                    removePrompt()
                    removePreviewView()
                    showAreaSelection()
                },
                "Cancelar" to { cancelFlow("Operação cancelada.") }
            )
        )
    }

    private fun showColorPickerPrompt(bounds: RectF) {
        showActionPanel(
            "Preparar cores",
            "No WeeLife, selecione o lápis e a espessura. Abra o seletor de cores e deixe-o aberto; o APK vai ajustar as cores detectadas na imagem.",
            listOf(
                "Iniciar desenho colorido" to {
                    removePrompt()
                    detectColorPicker(bounds)
                },
                "Voltar à área" to {
                    removePrompt()
                    showDrawingPrompt()
                },
                "Cancelar" to { cancelFlow("Operação cancelada.") }
            )
        )
    }

    @TargetApi(Build.VERSION_CODES.R)
    private fun detectColorPicker(bounds: RectF) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            publishStatus("A seleção automática de cores requer Android 11 ou superior.")
            showColorPickerPrompt(bounds)
            return
        }
        publishStatus("Verificando o seletor de cores do WeeLife...")
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, screenshotExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    var controls: ColorPickerControls? = null
                    var failure: Exception? = null
                    try {
                        val hardwareBuffer = screenshot.hardwareBuffer
                        try {
                            val captured = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                                ?: error("A captura da tela ficou vazia.")
                            try {
                                controls = ColorPickerDetector.find(captured)
                            } finally {
                                captured.recycle()
                            }
                        } finally {
                            hardwareBuffer.close()
                        }
                    } catch (error: Exception) {
                        Log.e(TAG, "Falha ao detectar o seletor de cores.", error)
                        failure = error
                    }
                    mainHandler.post {
                        if (!isConnected) return@post
                        val detectedControls = controls
                        if (failure != null || detectedControls == null) {
                            val reason = failure?.let { " (${it.message ?: it.javaClass.simpleName})" }.orEmpty()
                            publishStatus(
                                "Não encontrei o seletor de cores$reason. Abra o círculo colorido do WeeLife e tente novamente."
                            )
                            showColorPickerPrompt(bounds)
                        } else {
                            startDrawing(bounds, detectedControls)
                        }
                    }
                }

                override fun onFailure(errorCode: Int) {
                    mainHandler.post {
                        if (!isConnected) return@post
                        publishStatus("Não consegui verificar a paleta (código $errorCode). Tente novamente.")
                        showColorPickerPrompt(bounds)
                    }
                }
            })
        } catch (error: RuntimeException) {
            Log.e(TAG, "Não foi possível verificar o seletor de cores.", error)
            publishStatus("Não foi possível verificar a paleta: ${error.message}")
            showColorPickerPrompt(bounds)
        }
    }

    private inner class BoardPreviewView(private val bounds: RectF) : View(this@DrawingAccessibilityService) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val clearXfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
            canvas.drawColor(Color.argb(110, 17, 18, 30))
            val location = IntArray(2)
            getLocationOnScreen(location)
            val localBounds = RectF(
                bounds.left - location[0],
                bounds.top - location[1],
                bounds.right - location[0],
                bounds.bottom - location[1]
            )
            paint.xfermode = clearXfermode
            canvas.drawRect(localBounds, paint)
            paint.xfermode = null
            paint.color = Color.rgb(107, 92, 231)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(4).toFloat()
            canvas.drawRect(localBounds, paint)
            paint.style = Paint.Style.FILL
            canvas.restore()
        }
    }

    private fun showAreaSelection() {
        val view = AreaSelectionView()
        selectionView = view
        try {
            windowManager.addView(view, WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ))
            publishStatus("Toque nos dois cantos da área de desenho.")
        } catch (error: RuntimeException) {
            selectionView = null
            publishStatus("Não consegui iniciar a seleção da área: ${error.message}")
        }
    }

    private inner class AreaSelectionView : View(this@DrawingAccessibilityService) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var firstCorner: PointF? = null
        private var firstMarker: PointF? = null

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.argb(145, 17, 18, 30))
            paint.color = Color.WHITE
            paint.textSize = dp(18).toFloat()
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(
                if (firstCorner == null) "Toque no canto superior esquerdo do papel" else "Agora toque no canto inferior direito",
                width / 2f,
                dp(80).toFloat(),
                paint
            )
            firstMarker?.let { point ->
                paint.color = Color.rgb(107, 92, 231)
                canvas.drawCircle(point.x, point.y, dp(10).toFloat(), paint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action != MotionEvent.ACTION_UP) return true
            val point = PointF(event.rawX, event.rawY)
            val first = firstCorner
            if (first == null) {
                firstCorner = point
                val location = IntArray(2)
                getLocationOnScreen(location)
                firstMarker = PointF(point.x - location[0], point.y - location[1])
                invalidate()
            } else {
                finishAreaSelection(first, point)
            }
            return true
        }
    }

    private fun finishAreaSelection(first: PointF, second: PointF) {
        val bounds = RectF(
            minOf(first.x, second.x),
            minOf(first.y, second.y),
            maxOf(first.x, second.x),
            maxOf(first.y, second.y)
        )
        if (bounds.width() < dp(50) || bounds.height() < dp(50)) {
            removeSelectionView()
            publishStatus("A área ficou pequena. Prepare o desenho novamente e marque dois cantos mais distantes.")
            return
        }
        removeSelectionView()
        showRecognizedBounds(bounds, automatic = false)
    }

    private fun startDrawing(bounds: RectF, controls: ColorPickerControls) {
        try {
            val preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
            targetPackage = preferences.getString(KEY_TARGET_PACKAGE, targetPackage)
            strokes = DrawingPlanFile.read(this)
            val imageAspect = preferences
                .getFloat(KEY_IMAGE_ASPECT, bounds.width() / bounds.height())
                .coerceAtLeast(0.01f)
            val areaAspect = bounds.width() / bounds.height()
            drawingBounds = if (imageAspect > areaAspect) {
                val fittedHeight = bounds.width() / imageAspect
                RectF(bounds.left, bounds.centerY() - fittedHeight / 2f, bounds.right, bounds.centerY() + fittedHeight / 2f)
            } else {
                val fittedWidth = bounds.height() * imageAspect
                RectF(bounds.centerX() - fittedWidth / 2f, bounds.top, bounds.centerX() + fittedWidth / 2f, bounds.bottom)
            }
            colorPickerControls = controls
        } catch (error: Exception) {
            publishStatus("Não foi possível carregar os traços: ${error.message}")
            return
        }
        nextStroke = 0
        selectedColor = null
        isSelectingColor = false
        isDrawing = true
        isPaused = false
        targetIsForeground = true
        publishStatus("Desenhando 0 de ${strokes.size} traços...")
        showProgressOverlay()
        dispatchNextBatch()
    }

    private fun dispatchNextBatch() {
        if (!isDrawing) return
        if (nextStroke >= strokes.size) {
            isDrawing = false
            isPaused = false
            removeProgressOverlay()
            getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
                .putBoolean(KEY_PLAN_READY, false)
                .apply()
            publishStatus("Desenho concluído.")
            return
        }

        val bounds = drawingBounds ?: run {
            stopDrawing("Área de desenho indisponível.")
            return
        }
        val desiredColor = strokes[nextStroke].color
        if (selectedColor != desiredColor) {
            if (isSelectingColor) return
            isSelectingColor = true
            publishStatus("Selecionando a cor ${String.format("#%06X", 0xFFFFFF and desiredColor)}...")
            selectDrawingColor(desiredColor, openPicker = selectedColor != null) { success ->
                isSelectingColor = false
                if (!success) {
                    stopDrawing("Não consegui ajustar a cor no WeeLife. Verifique a paleta e prepare o desenho novamente.")
                    return@selectDrawingColor
                }
                selectedColor = desiredColor
                if (!isDrawing) return@selectDrawingColor
                dispatchNextBatch()
            }
            return
        }
        val strokeIndex = nextStroke
        val points = strokes[strokeIndex].points
        val gesture = GestureDescription.Builder()
        val path = Path()
        var previousX = bounds.left + points.first().x * bounds.width()
        var previousY = bounds.top + points.first().y * bounds.height()
        path.moveTo(previousX, previousY)
        var pathLength = 0f
        points.drop(1).forEach { point ->
            val x = bounds.left + point.x * bounds.width()
            val y = bounds.top + point.y * bounds.height()
            pathLength += kotlin.math.hypot(
                (x - previousX).toDouble(),
                (y - previousY).toDouble()
            ).toFloat()
            path.lineTo(x, y)
            previousX = x
            previousY = y
        }
        val duration = (pathLength / DRAWING_SPEED_PIXELS_PER_SECOND * 1000L)
            .toLong()
            .coerceIn(120L, MAX_STROKE_DURATION_MS)
        gesture.addStroke(GestureDescription.StrokeDescription(path, 0L, duration))

        val accepted = dispatchGesture(gesture.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                if (!isDrawing && !isPaused) return
                nextStroke = strokeIndex + 1
                publishStatus("Desenhando $nextStroke de ${strokes.size} traços...")
                if (isDrawing) {
                    updateProgressOverlay()
                    dispatchNextBatch()
                } else {
                    pauseDrawing("Desenho pausado.", true)
                }
            }

            override fun onCancelled(gestureDescription: GestureDescription) {
                if (isDrawing || isPaused) {
                    pauseDrawing("O Android cancelou um traço.", true)
                }
            }
        }, mainHandler)

        if (!accepted) pauseDrawing("O Android não aceitou o próximo traço.", true)
    }

    private fun selectDrawingColor(color: Int, openPicker: Boolean, onFinished: (Boolean) -> Unit) {
        val controls = colorPickerControls
        if (controls == null) {
            onFinished(false)
            return
        }

        fun tap(point: PointF, onCompleted: () -> Unit) {
            val path = Path().apply {
                moveTo(point.x, point.y)
                lineTo(point.x + 0.1f, point.y + 0.1f)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, 100L))
                .build()
            val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    onCompleted()
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    onFinished(false)
                }
            }, mainHandler)
            if (!accepted) onFinished(false)
        }

        fun setColorAndClose() {
            tap(controls.huePoint(color)) {
                tap(controls.shadePoint(color)) {
                    tap(controls.button) { onFinished(true) }
                }
            }
        }

        if (openPicker) tap(controls.button, ::setColorAndClose) else setColorAndClose()
    }

    private fun showProgressOverlay() {
        removeProgressOverlay()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(5), dp(5), dp(5))
            setBackgroundColor(Color.rgb(36, 36, 58))
        }
        progressLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 10f
            text = "Desenhando $nextStroke de ${strokes.size}..."
        }
        panel.addView(progressLabel)
        panel.addView(Button(this).apply {
            text = "Pausar"
            textSize = 10f
            minHeight = dp(38)
            setPadding(dp(2), 0, dp(2), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                if (isDrawing) pauseDrawing("Solicitando pausa...", false)
            }
        })
        progressView = panel
        try {
            windowManager.addView(panel, WindowManager.LayoutParams(
                dp(96),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                val screen = resources.displayMetrics
                val bounds = drawingBounds
                val leftSpace = bounds?.left ?: 0f
                val rightSpace = bounds?.let { screen.widthPixels - it.right } ?: 0f
                val topSpace = bounds?.top ?: 0f
                val bottomSpace = bounds?.let { screen.heightPixels - it.bottom } ?: 0f
                val sideMargin = dp(4)
                when {
                    maxOf(leftSpace, rightSpace) >= dp(104).toFloat() -> {
                        gravity = Gravity.TOP or
                            (if (rightSpace >= leftSpace) Gravity.END else Gravity.START)
                        x = sideMargin
                        y = maxOf(dp(8), (screen.heightPixels - dp(180)) / 2)
                    }
                    bottomSpace >= dp(180).toFloat() -> {
                        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                        x = 0
                        y = sideMargin
                    }
                    topSpace >= dp(180).toFloat() -> {
                        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                        x = 0
                        y = sideMargin
                    }
                    else -> {
                        gravity = Gravity.TOP or Gravity.END
                        x = sideMargin
                        y = dp(8)
                    }
                }
            })
        } catch (error: RuntimeException) {
            progressView = null
            progressLabel = null
            stopDrawing("Não consegui manter a tela ativa durante o desenho: ${error.message}")
        }
    }

    private fun updateProgressOverlay() {
        progressLabel?.text = "Desenhando $nextStroke de ${strokes.size}..."
    }

    private fun pauseDrawing(message: String, showPrompt: Boolean) {
        isDrawing = false
        isPaused = true
        removeProgressOverlay()
        publishStatus("$message Progresso: $nextStroke de ${strokes.size} traços.")
        if (showPrompt) showResumePrompt() else removePrompt()
    }

    private fun showResumePrompt() {
        if (!isPaused || !targetIsForeground || targetPackage == null) return
        showActionPanel(
            "Desenho pausado",
            "Progresso: $nextStroke de ${strokes.size} traços.",
            listOf(
                "Continuar" to {
                    removePrompt()
                    continueDrawing()
                },
                "Parar" to { stopDrawing("Desenho cancelado pelo usuário.") }
            )
        )
    }

    private fun continueDrawing() {
        if (!isPaused) return
        isPaused = false
        isDrawing = true
        publishStatus("Continuando em $nextStroke de ${strokes.size} traços...")
        showProgressOverlay()
        if (isDrawing) dispatchNextBatch()
    }

    private fun cancelFlow(message: String) {
        removePrompt()
        removeSelectionView()
        stopDrawing(message)
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
            .putBoolean(KEY_AWAITING_TARGET, false)
            .apply()
    }

    private fun stopDrawing(message: String) {
        isDrawing = false
        isPaused = false
        removeProgressOverlay()
        removePrompt()
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
            .putBoolean(KEY_PLAN_READY, false)
            .apply()
        publishStatus(message)
    }

    private fun publishStatus(message: String) {
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
            .putBoolean(KEY_IS_DRAWING, isDrawing)
            .putString(KEY_STATUS, message)
            .apply()
    }

    private fun removePrompt() {
        promptView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        promptView = null
    }

    private fun removeProgressOverlay() {
        progressView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        progressView = null
        progressLabel = null
    }

    private fun removeSelectionView() {
        selectionView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        selectionView = null
    }

    private fun removePreviewView() {
        previewView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        previewView = null
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
