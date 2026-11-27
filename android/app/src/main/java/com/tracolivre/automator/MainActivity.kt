package com.tracolivre.automator

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import kotlin.math.max

private data class TargetApp(val label: String, val packageName: String)
private data class TracePreparationResult(
    val strokes: List<TraceStroke>,
    val usedAi: Boolean,
    val aiFallbackReason: String? = null
)

class MainActivity : ComponentActivity() {
    companion object {
        private const val DRAWING_APP_PACKAGE = "com.newlang.weelife.sa"
        private val BRUSH_SIZES = listOf(
            "Muito fino",
            "Fino",
            "Médio-fino",
            "Médio",
            "Médio-grosso",
            "Grosso"
        )
        private const val INK = 0xff292843.toInt()
        private const val MUTED = 0xff77768c.toInt()
        private const val PURPLE = 0xff6858e8.toInt()
        private const val BACKGROUND = 0xfff6f5fb.toInt()
        private const val BORDER = 0xffe9e7f2.toInt()
    }

    private lateinit var imagePicker: ActivityResultLauncher<Array<String>>
    private lateinit var previewFrame: FrameLayout
    private lateinit var previewImage: ImageView
    private lateinit var previewPlaceholder: TextView
    private lateinit var tracePreviewView: TracePreviewView
    private lateinit var traceSummaryLabel: TextView
    private lateinit var tracePreviewButton: Button
    private lateinit var targetSpinner: Spinner
    private lateinit var brushSizeSpinner: Spinner
    private lateinit var aiEnhancement: CheckBox
    private lateinit var detailLabel: TextView
    private lateinit var detailSlider: SeekBar
    private lateinit var statusLabel: TextView
    private lateinit var prepareButton: View
    private lateinit var chooseImageButton: View
    private lateinit var removeImageButton: View
    private lateinit var vectorDrawingButton: View
    private lateinit var targets: List<TargetApp>
    private var selectedBitmap: Bitmap? = null
    private var selectedBrushSize = 2
    private var detail = 55

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) loadImage(uri)
        }
        buildInterface()
        loadTargetApps()
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::statusLabel.isInitialized) updateStatus()
    }

    override fun onDestroy() {
        selectedBitmap?.recycle()
        super.onDestroy()
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BACKGROUND)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safeInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(safeInsets.left, safeInsets.top, safeInsets.right, safeInsets.bottom)
            insets
        }
        setContentView(root)
        root.addView(buildTopBar(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(60)
        ))
        root.addView(View(this).apply {
            setBackgroundColor(BORDER)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }
        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }
        root.addView(scrollView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        content.addView(buildPreviewCard(), spacedParams(bottom = 12))
        content.addView(buildSettingsCard())
        root.addView(View(this).apply {
            setBackgroundColor(BORDER)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        root.addView(buildBottomBar(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
    }

    private fun buildTopBar(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            setBackgroundColor(Color.WHITE)
        }
        val mark = ImageView(this).apply {
            setImageResource(com.tracolivre.automator.R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Ícone Traço Automático"
        }
        row.addView(mark, LinearLayout.LayoutParams(dp(38), dp(38)))
        row.addView(makeText("Traço Automático", 18f, INK, bold = true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
            })
        return row
    }

    private fun buildBottomBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setBackgroundColor(Color.WHITE)
        }
        statusLabel = TextView(this).apply {
            text = "Escolha uma imagem para começar."
            textSize = 13f
            setTextColor(MUTED)
            setPadding(0, dp(3), 0, dp(8))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        bar.addView(statusLabel)
        prepareButton = Button(this).apply {
            text = "Preparar desenho"
            isAllCaps = false
            textSize = 16f
            minHeight = dp(50)
            setTextColor(Color.WHITE)
            isEnabled = false
            backgroundTintList = android.content.res.ColorStateList.valueOf(PURPLE)
            setOnClickListener { prepareDrawing() }
        }
        bar.addView(prepareButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        val accessibilityButton = quietButton("Configurar Acessibilidade") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        bar.addView(accessibilityButton)
        return bar
    }

    private fun buildPreviewCard(): View {
        val card = cardColumn()
        card.addView(makeText("Imagem de referência", 18f, INK, bold = true))
        card.addView(makeText("Escolha uma imagem para preparar o desenho.", 13f, MUTED),
            spacedParams(top = 4))

        previewFrame = FrameLayout(this).apply {
            background = rounded(0xfffbfaff.toInt(), 18, BORDER)
            clipToOutline = true
        }
        previewImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
            contentDescription = "Prévia da imagem escolhida"
        }
        previewPlaceholder = TextView(this).apply {
            text = "Sua arte aparece aqui"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(MUTED)
        }
        previewFrame.addView(previewImage, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        previewFrame.addView(previewPlaceholder, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        tracePreviewView = TracePreviewView()
        tracePreviewView.visibility = View.GONE
        previewFrame.addView(tracePreviewView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        card.addView(previewFrame, spacedParams(height = 180, top = 12))

        traceSummaryLabel = makeText("", 12f, MUTED).apply {
            visibility = View.GONE
        }
        card.addView(traceSummaryLabel, spacedParams(top = 8))
        tracePreviewButton = Button(this).apply {
            text = "Ver contornos"
            isAllCaps = false
            visibility = View.GONE
            setOnClickListener {
                val showContours = tracePreviewView.visibility != View.VISIBLE
                tracePreviewView.visibility = if (showContours) View.VISIBLE else View.GONE
                text = if (showContours) "Ver imagem original" else "Ver contornos"
            }
        }
        card.addView(tracePreviewButton, spacedParams(top = 2))

        chooseImageButton = Button(this).apply {
            text = "Escolher imagem"
            isAllCaps = false
            setOnClickListener { imagePicker.launch(arrayOf("image/*")) }
        }
        card.addView(chooseImageButton, spacedParams(top = 8))
        vectorDrawingButton = Button(this).apply {
            text = "Criar desenho vetorial offline"
            isAllCaps = false
            setOnClickListener { showVectorDrawingDialog() }
        }
        card.addView(vectorDrawingButton, spacedParams(top = 4))
        removeImageButton = quietButton("Remover imagem") {
            removeSelectedImage()
        }.apply {
            visibility = View.GONE
            setTextColor(0xffc44848.toInt())
            contentDescription = "Remover imagem selecionada"
        }
        card.addView(removeImageButton, spacedParams(top = 4))
        return card
    }

    private fun loadImage(uri: Uri) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri).use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Não foi possível ler essa imagem." }

            var sampleSize = 1
            while (max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > 900) {
                sampleSize *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = contentResolver.openInputStream(uri).use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: error("Não foi possível abrir essa imagem.")

            selectedBitmap?.recycle()
            selectedBitmap = bitmap
            previewPlaceholder.visibility = View.GONE
            previewImage.setImageBitmap(bitmap)
            previewImage.visibility = View.VISIBLE
            clearTracePreview()
            prepareButton.isEnabled = true
            removeImageButton.visibility = View.VISIBLE
            detail = TraceProcessor.recommendDetail(bitmap)
            detailSlider.progress = detail
            invalidatePendingPlan("Imagem carregada. Selecione o app de desenho e prepare os traços.")
            statusLabel.text =
                "Imagem carregada. Detalhe sugerido: $detail%. Você pode ajustar antes de preparar."
        } catch (error: Exception) {
            Toast.makeText(this, error.message ?: "Não foi possível abrir a imagem.", Toast.LENGTH_LONG).show()
        }
    }

    private fun removeSelectedImage() {
        selectedBitmap?.recycle()
        selectedBitmap = null
        previewImage.setImageDrawable(null)
        previewImage.visibility = View.GONE
        previewPlaceholder.visibility = View.VISIBLE
        clearTracePreview()
        prepareButton.isEnabled = false
        removeImageButton.visibility = View.GONE
        invalidatePendingPlan("Imagem removida. Escolha outra imagem para começar.")
        statusLabel.text = "Imagem removida. Escolha outra imagem para começar."
    }

    private fun invalidatePendingPlan(message: String) {
        getSharedPreferences(DrawingAccessibilityService.PREFERENCES, MODE_PRIVATE).edit()
            .putBoolean(DrawingAccessibilityService.KEY_AWAITING_TARGET, false)
            .putBoolean(DrawingAccessibilityService.KEY_PLAN_READY, false)
            .putString(DrawingAccessibilityService.KEY_STATUS, message)
            .apply()
    }

    private fun clearTracePreview() {
        tracePreviewView.setStrokes(emptyList(), 1f)
        tracePreviewView.visibility = View.GONE
        tracePreviewButton.visibility = View.GONE
        tracePreviewButton.text = "Ver contornos"
        traceSummaryLabel.visibility = View.GONE
    }

    private fun showTracePreview(strokes: List<TraceStroke>, selectedDetail: Int) {
        val aspect = selectedBitmap?.let { it.width.toFloat() / it.height } ?: 1f
        tracePreviewView.setStrokes(strokes, aspect)
        tracePreviewView.visibility = View.GONE
        tracePreviewButton.text = "Ver contornos"
        tracePreviewButton.visibility = if (strokes.isEmpty()) View.GONE else View.VISIBLE
        traceSummaryLabel.text = if (strokes.isEmpty()) {
            "Nenhum contorno detectado."
        } else {
            "${strokes.size} traços detectados · detalhe $selectedDetail%"
        }
        traceSummaryLabel.visibility = View.VISIBLE
    }

    private fun showVectorDrawingDialog() {
        val dialogContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val templateSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                VectorDrawingFactory.templates
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }
        dialogContent.addView(makeText("Escolha um desenho", 14f, INK, bold = true))
        dialogContent.addView(templateSpinner)
        val nameInput = EditText(this).apply {
            hint = "Nome ou palavra (opcional)"
            isSingleLine = true
            filters = arrayOf(android.text.InputFilter.LengthFilter(12))
        }
        dialogContent.addView(nameInput)

        AlertDialog.Builder(this)
            .setTitle("Desenho vetorial offline")
            .setMessage("Escolha uma forma pronta e, se quiser, acrescente um nome. Não usa IA nem internet.")
            .setView(dialogContent)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Preparar desenho") { _, _ ->
                val target = targets.getOrNull(targetSpinner.selectedItemPosition)
                if (target == null) {
                    statusLabel.text = "Nenhum app de desenho compatível foi encontrado."
                    return@setPositiveButton
                }
                if (!isAccessibilityServiceEnabled()) {
                    statusLabel.text = "Ative a Acessibilidade e tente novamente."
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    return@setPositiveButton
                }

                val template = templateSpinner.selectedItem?.toString()
                    ?: VectorDrawingFactory.templates.first()
                val strokes = try {
                    VectorDrawingFactory.create(template, nameInput.text.toString())
                } catch (error: IllegalArgumentException) {
                    statusLabel.text = error.message ?: "Não foi possível criar o desenho."
                    return@setPositiveButton
                }
                previewImage.setImageDrawable(null)
                previewImage.visibility = View.GONE
                previewPlaceholder.visibility = View.GONE
                tracePreviewView.setStrokes(strokes, 1f)
                tracePreviewView.visibility = View.VISIBLE
                tracePreviewButton.visibility = View.GONE
                traceSummaryLabel.text = "$template · ${strokes.size} traços vetoriais"
                traceSummaryLabel.visibility = View.VISIBLE
                prepareButton.isEnabled = false
                queuePlan(strokes, 1f, target, usedAi = false, aiFallbackReason = null)
            }
            .show()
    }

    private fun prepareDrawing() {
        val target = targets.getOrNull(targetSpinner.selectedItemPosition)
        if (target == null) {
            statusLabel.text = "Nenhum app de desenho compatível foi encontrado."
            return
        }
        if (!isAccessibilityServiceEnabled()) {
            statusLabel.text = "Ative a Acessibilidade e toque novamente em Preparar desenho."
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        val bitmap = selectedBitmap
        if (bitmap == null) {
            statusLabel.text = "Escolha uma imagem para começar."
            return
        }

        clearTracePreview()
        getSharedPreferences(DrawingAccessibilityService.PREFERENCES, MODE_PRIVATE).edit()
            .putBoolean(DrawingAccessibilityService.KEY_AWAITING_TARGET, false)
            .putBoolean(DrawingAccessibilityService.KEY_PLAN_READY, false)
            .apply()
        prepareButton.isEnabled = false
        chooseImageButton.isEnabled = false
        removeImageButton.isEnabled = false
        vectorDrawingButton.isEnabled = false
        statusLabel.text = if (aiEnhancement.isChecked) {
            "Preparando com IA local. Na primeira vez, o modelo pode levar um pouco para carregar..."
        } else {
            "Preparando sua imagem..."
        }
        val imageForProcessing = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: run {
            prepareButton.isEnabled = true
            chooseImageButton.isEnabled = true
            removeImageButton.isEnabled = true
            vectorDrawingButton.isEnabled = true
            statusLabel.text = "Não foi possível preparar uma cópia da imagem."
            return
        }
        val selectedDetail = detail
        val useAiEnhancement = aiEnhancement.isChecked
        val imageAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        Thread {
            val result = try {
                runCatching {
                    if (!useAiEnhancement) {
                        TracePreparationResult(
                            TraceProcessor.trace(imageForProcessing, selectedDetail),
                            usedAi = false
                        )
                    } else {
                        try {
                            val aiStrokes = traceWithOnDeviceAi(imageForProcessing, selectedDetail)
                            if (aiStrokes.isNotEmpty()) {
                                TracePreparationResult(aiStrokes, usedAi = true)
                            } else {
                                TracePreparationResult(
                                    TraceProcessor.trace(imageForProcessing, selectedDetail),
                                    usedAi = false,
                                    aiFallbackReason = "a IA não encontrou um assunto nítido"
                                )
                            }
                        } catch (aiError: Exception) {
                            TracePreparationResult(
                                TraceProcessor.trace(imageForProcessing, selectedDetail),
                                usedAi = false,
                                aiFallbackReason = aiError.cause?.message
                                    ?: aiError.message
                                    ?: aiError.javaClass.simpleName
                            )
                        }
                    }
                }
            } finally {
                imageForProcessing.recycle()
            }
            runOnUiThread {
                prepareButton.isEnabled = true
                chooseImageButton.isEnabled = true
                removeImageButton.isEnabled = true
                vectorDrawingButton.isEnabled = true
                result.onSuccess { preparation ->
                    val strokes = preparation.strokes
                    showTracePreview(strokes, selectedDetail)
                    if (preparation.aiFallbackReason != null) {
                        statusLabel.text = "A IA não ficou disponível (${preparation.aiFallbackReason}); vou usar o traçado local sem IA."
                    }
                    if (strokes.isEmpty()) {
                        statusLabel.text = if (useAiEnhancement && preparation.aiFallbackReason == null) {
                            "A IA não encontrou contornos úteis. Tente outra imagem ou desative a separação por IA."
                        } else {
                            "Não encontrei contornos. Tente outra imagem ou ajuste o detalhe."
                        }
                        return@onSuccess
                    }
                    queuePlan(
                        strokes,
                        imageAspect,
                        target,
                        preparation.usedAi,
                        preparation.aiFallbackReason
                    )
                }.onFailure { error ->
                    statusLabel.text = "Não foi possível preparar o desenho: ${error.cause?.message ?: error.message}"
                }
            }
        }.start()
    }

    private fun traceWithOnDeviceAi(bitmap: Bitmap, selectedDetail: Int): List<TraceStroke> {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("deeplab_v3.tflite")
            .build()
        val segmenterOptions = ImageSegmenter.ImageSegmenterOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setOutputCategoryMask(true)
            .setOutputConfidenceMasks(false)
            .build()
        val segmenter = ImageSegmenter.createFromOptions(this, segmenterOptions)
        return try {
            val result = segmenter.segment(BitmapImageBuilder(bitmap).build())
            val mask = result.categoryMask().orElse(null)
                ?: error("A IA não retornou a máscara do assunto.")
            val maskWidth = mask.width
            val maskHeight = mask.height
            require(maskWidth > 0 && maskHeight > 0) { "A máscara de IA está vazia." }
            val maskBytes = ByteBufferExtractor.extract(mask)
            require(maskBytes.capacity() >= maskWidth * maskHeight) { "A máscara de IA está incompleta." }
            val sourcePixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(sourcePixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val foregroundPixels = IntArray(sourcePixels.size)
            var foundForeground = false
            for (y in 0 until bitmap.height) {
                val maskY = (y.toLong() * maskHeight / bitmap.height).toInt()
                for (x in 0 until bitmap.width) {
                    val maskX = (x.toLong() * maskWidth / bitmap.width).toInt()
                    val sourceIndex = y * bitmap.width + x
                    val category = maskBytes.get(maskY * maskWidth + maskX).toInt() and 0xff
                    if (category != 0) {
                        foregroundPixels[sourceIndex] = sourcePixels[sourceIndex]
                        foundForeground = true
                    }
                }
            }
            require(foundForeground) { "A IA não encontrou um objeto conhecido na imagem." }
            val foreground = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            try {
                foreground.setPixels(
                    foregroundPixels,
                    0,
                    bitmap.width,
                    0,
                    0,
                    bitmap.width,
                    bitmap.height
                )
                TraceProcessor.trace(foreground, selectedDetail)
            } finally {
                foreground.recycle()
            }
        } finally {
            segmenter.close()
        }
    }

    private fun queuePlan(
        strokes: List<TraceStroke>,
        imageAspect: Float,
        target: TargetApp,
        usedAi: Boolean,
        aiFallbackReason: String?
    ) {
        try {
            require(strokes.isNotEmpty()) { "O desenho não contém traços." }
            val mode = when {
                usedAi -> " com IA local"
                aiFallbackReason != null -> " no modo local sem IA"
                else -> ""
            }
            val warning = aiFallbackReason?.let {
                "A IA não ficou disponível ($it); usei o traçado local sem IA. "
            }.orEmpty()
            DrawingPlanFile.write(this, strokes)
            getSharedPreferences(DrawingAccessibilityService.PREFERENCES, MODE_PRIVATE)
                .edit()
                .putString(DrawingAccessibilityService.KEY_TARGET_PACKAGE, target.packageName)
                .putFloat(DrawingAccessibilityService.KEY_IMAGE_ASPECT, imageAspect)
                .putInt(DrawingAccessibilityService.KEY_BRUSH_SIZE, selectedBrushSize)
                .putBoolean(
                    DrawingAccessibilityService.KEY_DRAWING_APP_PROFILE,
                    target.packageName == DRAWING_APP_PACKAGE
                )
                .putBoolean(DrawingAccessibilityService.KEY_AWAITING_TARGET, true)
                .putBoolean(DrawingAccessibilityService.KEY_PLAN_READY, true)
                .putBoolean(DrawingAccessibilityService.KEY_IS_DRAWING, false)
                .putString(
                    DrawingAccessibilityService.KEY_STATUS,
                    "$warning Plano pronto: ${strokes.size} traços$mode."
                )
                .apply()
            statusLabel.text =
                "${warning}Tudo pronto: ${strokes.size} traços$mode. Abra “${target.label}” para desenhar."
        } catch (error: Exception) {
            statusLabel.text = "Não foi possível preparar o desenho: ${error.message}"
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val expected = ComponentName(this, DrawingAccessibilityService::class.java)
        return enabledServices.split(':').any { entry ->
            ComponentName.unflattenFromString(entry) == expected
        }
    }

    private fun loadTargetApps() {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        targets = packageManager.queryIntentActivities(launcherIntent, 0)
            .map { result ->
                TargetApp(
                    result.loadLabel(packageManager).toString(),
                    result.activityInfo.packageName
                )
            }
            .filter { it.packageName != packageName }
            .distinctBy { it.packageName }
            .sortedWith(compareBy<TargetApp> { it.packageName != DRAWING_APP_PACKAGE }.thenBy { it.label.lowercase() })

        val labels = if (targets.isEmpty()) listOf("Nenhum app compatível encontrado") else targets.map { it.label }
        targetSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        targetSpinner.isEnabled = targets.isNotEmpty()
    }

    private fun updateStatus() {
        if (!::statusLabel.isInitialized) return
        val status = getSharedPreferences(DrawingAccessibilityService.PREFERENCES, MODE_PRIVATE)
            .getString(DrawingAccessibilityService.KEY_STATUS, null)
        if (!status.isNullOrBlank()) statusLabel.text = status
    }

    private fun buildSettingsCard(): LinearLayout {
        val card = cardColumn()
        card.addView(makeText("Configurações", 18f, INK, bold = true))

        card.addView(makeText("App de desenho", 14f, INK, bold = true), spacedParams(top = 14))
        targetSpinner = Spinner(this)
        card.addView(targetSpinner, spacedParams(height = 48, top = 4, bottom = 12))

        card.addView(makeText("Espessura do lápis", 14f, INK, bold = true))
        brushSizeSpinner = Spinner(this)
        brushSizeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, BRUSH_SIZES).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        brushSizeSpinner.setSelection(selectedBrushSize)
        brushSizeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedBrushSize = position.coerceIn(0, BRUSH_SIZES.lastIndex)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        card.addView(brushSizeSpinner, spacedParams(height = 48, top = 4, bottom = 8))
        card.addView(makeText("A cor selecionada no app de desenho será usada.", 12f, MUTED),
            spacedParams(bottom = 14))

        aiEnhancement = CheckBox(this).apply {
            text = "Separar o assunto com IA local"
            textSize = 14f
            setTextColor(INK)
            buttonTintList = android.content.res.ColorStateList.valueOf(PURPLE)
            isChecked = true
        }
        card.addView(aiEnhancement)
        card.addView(makeText(
            "Processamento no aparelho, sem internet ou Google Play services. A IA reconhece categorias comuns; se não encontrar o assunto, o app usa o modo local.",
            11f,
            MUTED
        ), spacedParams(top = 2, bottom = 14))

        detailLabel = makeText("Detalhe sugerido pela imagem  ·  $detail%", 12f, INK, bold = true)
        card.addView(detailLabel)
        detailSlider = SeekBar(this).apply {
            max = 100
            progress = detail
            progressTintList = android.content.res.ColorStateList.valueOf(PURPLE)
            thumbTintList = android.content.res.ColorStateList.valueOf(PURPLE)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    detail = progress
                    detailLabel.text = "Detalhe  ·  $detail%"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        card.addView(detailSlider, spacedParams(top = 2))
        return card
    }

    private inner class TracePreviewView : View(this@MainActivity) {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xffe53955.toInt()
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        private var strokes: List<TraceStroke> = emptyList()

        private var previewAspect = 1f

        fun setStrokes(value: List<TraceStroke>, aspect: Float) {
            strokes = value
            previewAspect = aspect.coerceAtLeast(0.01f)
            invalidate()
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            if (strokes.isEmpty() || width <= 0 || height <= 0) return

            val contentWidth = minOf(width.toFloat(), height * previewAspect)
            val contentHeight = contentWidth / previewAspect
            canvas.save()
            canvas.translate((width - contentWidth) / 2f, (height - contentHeight) / 2f)
            canvas.scale(contentWidth, contentHeight)
            paint.strokeWidth = dp(1.5f) / contentWidth
            strokes.forEach { stroke ->
                if (stroke.points.size >= 2) {
                    val path = android.graphics.Path()
                    path.moveTo(stroke.points.first().x, stroke.points.first().y)
                    for (pointIndex in 1 until stroke.points.size) {
                        val point = stroke.points[pointIndex]
                        path.lineTo(point.x, point.y)
                    }
                    canvas.drawPath(path, paint)
                }
            }
            canvas.restore()
        }
    }

    private fun cardColumn() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(Color.WHITE, 14, BORDER)
    }

    private fun makeText(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false,
        maxLines: Int = Int.MAX_VALUE
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        this.maxLines = maxLines
        if (maxLines == 1) ellipsize = android.text.TextUtils.TruncateAt.END
    }

    private fun quietButton(title: String, action: () -> Unit) = TextView(this).apply {
        text = title
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(PURPLE)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        minHeight = dp(44)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun rounded(color: Int, radius: Int, stroke: Int? = null) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun spacedParams(
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        top: Int = 0,
        bottom: Int = 0,
        start: Int = 0,
        end: Int = 0
    ) = LinearLayout.LayoutParams(
        if (height == ViewGroup.LayoutParams.MATCH_PARENT) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.MATCH_PARENT,
        if (height == ViewGroup.LayoutParams.WRAP_CONTENT || height == ViewGroup.LayoutParams.MATCH_PARENT) height else dp(height)
    ).apply {
        topMargin = dp(top)
        bottomMargin = dp(bottom)
        marginStart = dp(start)
        marginEnd = dp(end)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = value * resources.displayMetrics.density
}
