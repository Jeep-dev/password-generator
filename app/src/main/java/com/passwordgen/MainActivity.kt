package com.passwordgen

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.*
import java.security.SecureRandom

class MainActivity : Activity() {

    // --- UI References ---
    private lateinit var tvPassword: TextView
    private lateinit var tvLengthValue: TextView
    private lateinit var tvStrengthText: TextView
    private lateinit var tvBitsInfo: TextView
    private lateinit var strengthFill: View
    private lateinit var switchUppercase: Switch
    private lateinit var switchLowercase: Switch
    private lateinit var switchNumbers: Switch
    private lateinit var switchSymbols: Switch
    private lateinit var switchExclude: Switch
    private lateinit var seekBarLength: SeekBar
    private lateinit var btnGenerate: Button
    private lateinit var btnCopy: ImageButton
    private lateinit var historyContainer: LinearLayout

    // --- History (v2.0) ---
    private val passwordHistory = mutableListOf<String>()
    private val maxHistory = 5

    private val secureRandom = SecureRandom()

    // --- Color Constants ---
    companion object {
        private const val COLOR_PRIMARY = 0xFF6750A4.toInt()
        private const val COLOR_BG = 0xFFFFFBFE.toInt()
        private const val COLOR_CARD = 0xFFF7F2FA.toInt()
        private const val COLOR_ON_SURFACE = 0xFF1C1B1F.toInt()
        private const val COLOR_ON_SURFACE_VARIANT = 0xFF49454F.toInt()
        private const val COLOR_WEAK = 0xFFB3261E.toInt()
        private const val COLOR_MEDIUM = 0xFFE6A200.toInt()
        private const val COLOR_STRONG = 0xFF4CAF50.toInt()
        private const val COLOR_VERY_STRONG = 0xFF2E7D32.toInt()
        private const val COLOR_TRACK = 0xFFE7E0EC.toInt()
        private const val COLOR_HISTORY_ITEM = 0xFFFFFBFE.toInt()
        private const val COLOR_HISTORY_BORDER = 0xFFEADDFF.toInt()

        private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
        private const val NUMBERS = "0123456789"
        private const val SYMBOLS = "!@#\$%^&*()_+-=[]{}|;:,.<>?/~"
        private const val AMBIGUOUS = "0O1lI|"
    }

    // ======================================================================
    // Lifecycle
    // ======================================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- Immersive status bar: transparent + light icons ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = COLOR_BG  // match App background exactly
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
        // Android 11+ edge-to-edge
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { ctrl ->
                ctrl.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            }
        }

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(COLOR_BG)
            // horizontal padding only; top padding = status bar height for content not to overlap
            setPadding(dp(10), getStatusBarHeight() + dp(4), dp(10), dp(10))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // On Android 11+ we already setDecorFitsSystemWindows(false),
                // so we need clipToPadding=false so scrolling goes under status bar
                clipToPadding = false
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        root.addView(createTitleSection())
        root.addView(createPasswordCard())
        root.addView(createGenerateButton())
        root.addView(createOptionsCard())
        root.addView(createLengthCard())
        root.addView(createStrengthCard())
        root.addView(createHistoryCard())
        root.addView(createFooter())

        scrollView.addView(root)
        setContentView(scrollView)
        // Initial preview — show a password but don't save to history
        generatePreview()
    }

    /** Get the system status bar height in pixels */
    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else dp(24)
    }

    // ======================================================================
    // Helper: dp / rounded background
    // ======================================================================
    private fun dp(dip: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dip.toFloat(),
            resources.displayMetrics
        ).toInt()

    private fun roundRectBg(color: Int, radiusDp: Int): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    // ======================================================================
    // Haptic feedback helper (v2.0)
    // ======================================================================
    private fun triggerHaptic() {
        // Try view-based haptic first (works on most Android 10+)
        try {
            val anyView: View = if (this::tvPassword.isInitialized) tvPassword else btnGenerate
            anyView.performHapticFeedback(
                HapticFeedbackConstants.CONFIRM,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
            return
        } catch (_: Exception) { }

        // Fallback: Vibrator service
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(30)
                }
            }
        } catch (_: Exception) { }
    }

    // ======================================================================
    // Card container helper
    // ======================================================================
    private fun makeCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRectBg(COLOR_CARD, 20)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dp(3).toFloat()
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(10)
            layoutParams = lp
            setPadding(dp(18), dp(14), dp(18), dp(14))
        }
    }

    // ======================================================================
    // Title section (v2.0: no subtitle for compact one-screen layout)
    // ======================================================================
    private fun createTitleSection(): View {
        return TextView(this).apply {
            text = "Password Generator"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTextColor(COLOR_ON_SURFACE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
    }

    // ======================================================================
    // Password display card
    // ======================================================================
    private fun createPasswordCard(): View {
        val card = makeCard()
        val rel = RelativeLayout(this)
        rel.layoutParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(62)
        )

        tvPassword = TextView(this).apply {
            id = View.generateViewId()
            text = "P@ssw0rd!"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setTextColor(COLOR_ON_SURFACE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            // fixed space for ~2 lines; 3rd line can squeeze via reduced line spacing
            setLineSpacing(dp(-2).toFloat(), 1.0f)
            maxLines = 3
            val pp = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            pp.addRule(RelativeLayout.LEFT_OF, 10001)
            pp.addRule(RelativeLayout.CENTER_VERTICAL)
            layoutParams = pp
        }

        btnCopy = ImageButton(this).apply {
            id = 10001
            background = roundRectBg(COLOR_PRIMARY, 14)
            scaleType = ImageView.ScaleType.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            val cp = RelativeLayout.LayoutParams(dp(48), dp(48))
            cp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            cp.addRule(RelativeLayout.CENTER_VERTICAL)
            layoutParams = cp
            setOnClickListener {
                val pwd = tvPassword.text.toString()
                if (pwd.length == 0 || pwd == "P@ssw0rd!") return@setOnClickListener
                copyToClipboard(pwd)
                triggerHaptic()   // v2.0 haptic
                Toast.makeText(this@MainActivity, "Password copied!", Toast.LENGTH_SHORT).show()
            }
        }

        rel.addView(tvPassword)
        rel.addView(btnCopy)
        card.addView(rel)
        return card
    }

    // ======================================================================
    // Generate button
    // ======================================================================
    private fun createGenerateButton(): View {
        btnGenerate = Button(this).apply {
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)
            )
            lp.topMargin = dp(10)
            layoutParams = lp
            text = "Generate"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
            background = roundRectBg(COLOR_PRIMARY, 16)
            setOnClickListener {
                triggerHaptic()   // v2.0 haptic
                generatePassword()  // save to history
            }
        }
        return btnGenerate
    }

    // ======================================================================
    // Options card
    // ======================================================================
    private fun createOptionsCard(): View {
        val card = makeCard()

        TextView(this).apply {
            text = "Character Options"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(COLOR_ON_SURFACE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            card.addView(this)
        }

        switchUppercase = addOptionRow(card, "A-Z (Uppercase)", true)
        switchLowercase = addOptionRow(card, "a-z (Lowercase)", true)
        switchNumbers = addOptionRow(card, "0-9 (Numbers)", true)
        switchSymbols = addOptionRow(card, "!@#\$% (Symbols)", true)
        switchExclude = addOptionRow(card, "Exclude Ambiguous (0O1lI|)", true)

        return card
    }

    private fun addOptionRow(card: LinearLayout, label: String, checked: Boolean): Switch {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, if (card.childCount == 1) dp(14) else dp(8), 0, 0)
        }

        TextView(this).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(COLOR_ON_SURFACE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(this)
        }

        val sw = Switch(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, _ -> generatePreview() }
            row.addView(this)
        }

        card.addView(row)
        return sw
    }

    // ======================================================================
    // Length card
    // ======================================================================
    private fun createLengthCard(): View {
        val card = makeCard()

        val header = RelativeLayout(this)
        header.layoutParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        TextView(this).apply {
            text = "Password Length"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(COLOR_ON_SURFACE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            header.addView(this)
        }

        tvLengthValue = TextView(this).apply {
            text = "16"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTextColor(COLOR_PRIMARY)
            setTypeface(null, android.graphics.Typeface.BOLD)
            val vp = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            vp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            layoutParams = vp
            header.addView(this)
        }

        card.addView(header)

        seekBarLength = SeekBar(this).apply {
            max = 26
            progress = 10
            setPadding(0, dp(6), 0, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    thumb?.setTint(COLOR_PRIMARY)
                    progressDrawable?.setTint(COLOR_PRIMARY)
                } catch (_: Exception) { }
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seek: SeekBar, progress: Int, fromUser: Boolean) {
                    tvLengthValue.text = (progress + 6).toString()
                    if (fromUser) generatePreview()  // preview only, no history
                }
                override fun onStartTrackingTouch(seek: SeekBar) {}
                override fun onStopTrackingTouch(seek: SeekBar) {}
            })
        }
        card.addView(seekBarLength)

        return card
    }

    // ======================================================================
    // Strength card
    // ======================================================================
    private fun createStrengthCard(): View {
        val card = makeCard()

        val header = RelativeLayout(this)
        header.layoutParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        TextView(this).apply {
            text = "Password Strength"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(COLOR_ON_SURFACE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            header.addView(this)
        }

        tvStrengthText = TextView(this).apply {
            text = "Strong 128-bit"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(COLOR_STRONG)
            setTypeface(null, android.graphics.Typeface.BOLD)
            val sp = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            sp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            layoutParams = sp
            header.addView(this)
        }

        card.addView(header)

        val barContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8)
            )
            setPadding(0, dp(6), 0, 0)
            background = roundRectBg(COLOR_TRACK, 5)
        }

        strengthFill = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(120), ViewGroup.LayoutParams.MATCH_PARENT)
            background = roundRectBg(COLOR_STRONG, 5)
        }
        barContainer.addView(strengthFill)
        card.addView(barContainer)

        tvBitsInfo = TextView(this).apply {
            text = "Entropy: 128 bits"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_ON_SURFACE_VARIANT)
            setPadding(0, dp(2), 0, 0)
        }
        card.addView(tvBitsInfo)

        return card
    }

    // ======================================================================
    // v2.0: History Card - saves last 5 passwords, tap to copy
    // ======================================================================
    private fun createHistoryCard(): View {
        val card = makeCard()

        // Header row
        val header = RelativeLayout(this)
        header.layoutParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        TextView(this).apply {
            text = "Recent Passwords"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(COLOR_ON_SURFACE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            header.addView(this)
        }

        val clearBtn = TextView(this).apply {
            text = "Clear"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(COLOR_PRIMARY)
            setTypeface(null, android.graphics.Typeface.BOLD)
            val rp = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            rp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            rp.addRule(RelativeLayout.CENTER_VERTICAL)
            layoutParams = rp
            setOnClickListener {
                passwordHistory.clear()
                refreshHistoryDisplay()
                Toast.makeText(this@MainActivity, "History cleared", Toast.LENGTH_SHORT).show()
            }
        }
        header.addView(clearBtn)
        card.addView(header)

        historyContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(10)
            layoutParams = lp
        }
        card.addView(historyContainer)

        // Placeholder message
        refreshHistoryDisplay()

        return card
    }

    private fun refreshHistoryDisplay() {
        historyContainer.removeAllViews()
        if (passwordHistory.isEmpty()) {
            val placeholder = TextView(this).apply {
                text = "No recent passwords yet."
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(COLOR_ON_SURFACE_VARIANT)
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(4))
            }
            historyContainer.addView(placeholder)
            return
        }

        // Show most recent first (manual reverse to avoid Kotlin stdlib extension incompatibility)
        val size = passwordHistory.size
        for (i in size - 1 downTo 0) {
            val pwd = passwordHistory[i]
            val index = size - 1 - i
            val itemRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = roundRectBg(COLOR_HISTORY_ITEM, 12)
                // Add border effect with padding + background
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = dp(6)
                layoutParams = lp
                setPadding(dp(14), dp(12), dp(14), dp(12))
                // Click to copy
                setOnClickListener {
                    copyToClipboard(pwd)
                    triggerHaptic()
                    Toast.makeText(this@MainActivity, "Copied: $pwd", Toast.LENGTH_SHORT).show()
                }
            }

            // Index badge
            val badge = TextView(this).apply {
                text = "#${passwordHistory.size - index}"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(COLOR_PRIMARY)
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                val lp = LinearLayout.LayoutParams(dp(32), dp(28))
                lp.rightMargin = dp(10)
                layoutParams = lp
                background = roundRectBg(0xFFEADDFF.toInt(), 8)
                gravity = Gravity.CENTER
            }
            itemRow.addView(badge)

            // Password text (truncated if too long)
            val pwdText = TextView(this).apply {
                text = pwd
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(COLOR_ON_SURFACE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                gravity = Gravity.CENTER_VERTICAL
                maxLines = 1
            }
            itemRow.addView(pwdText)

            // Copy icon indicator
            val copyIcon = TextView(this).apply {
                text = "📋"
                textSize = 16f
                gravity = Gravity.CENTER
                val lp = LinearLayout.LayoutParams(dp(36), dp(36))
                lp.leftMargin = dp(6)
                layoutParams = lp
                gravity = Gravity.CENTER
            }
            itemRow.addView(copyIcon)

            historyContainer.addView(itemRow)
        }
    }

    private fun addToHistory(pwd: String) {
        if (pwd.length == 0) return
        // Avoid duplicates at the top
        passwordHistory.remove(pwd)
        passwordHistory.add(pwd)
        // Trim to max 5
        while (passwordHistory.size > maxHistory) {
            passwordHistory.removeAt(0)
        }
        refreshHistoryDisplay()
    }

    // ======================================================================
    // Footer
    // ======================================================================
    private fun createFooter(): View {
        return TextView(this).apply {
            text = "Passwords are generated locally and never leave your device."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_ON_SURFACE_VARIANT)
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, dp(36))
        }
    }

    // ======================================================================
    // Clipboard helper
    // ======================================================================
    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("password", text)
        clipboard.setPrimaryClip(clip)
    }

    // ======================================================================
    // Core: Password generation
    // ======================================================================
    /** Generate a password and save it to history (for Generate button). */
    private fun generatePassword() {
        val password = doGenerate()
        addToHistory(password)
    }

    /** Generate a password for preview only — does NOT save to history. */
    private fun generatePreview() {
        val password = doGenerate()
        // just update the UI, no history touch
    }

    /** Internal: compute password, update display & strength, return the password. */
    private fun doGenerate(): String {
        var pool = buildString {
            if (switchUppercase.isChecked) append(UPPERCASE)
            if (switchLowercase.isChecked) append(LOWERCASE)
            if (switchNumbers.isChecked) append(NUMBERS)
            if (switchSymbols.isChecked) append(SYMBOLS)
        }

        if (switchExclude.isChecked) {
            for (c in AMBIGUOUS) pool = pool.replace(c.toString(), "")
        }

        if (pool.length == 0) {
            pool = UPPERCASE + LOWERCASE + NUMBERS + SYMBOLS
            if (switchExclude.isChecked) {
                for (c in AMBIGUOUS) pool = pool.replace(c.toString(), "")
            }
        }

        val len = seekBarLength.progress + 6
        val pwd = StringBuilder(len)
        for (i in 0 until len) {
            pwd.append(pool[secureRandom.nextInt(pool.length)])
        }

        val password = pwd.toString()
        tvPassword.text = password
        updateStrength(password, pool.length)
        return password
    }

    // ======================================================================
    // Strength calculation
    // ======================================================================
    private fun updateStrength(pwd: String, poolSize: Int) {
        val entropy = (Math.log(poolSize.toDouble()) / Math.log(2.0)) * pwd.length
        val bits = entropy.toInt()
        val (label, color, pct) = when {
            bits < 30  -> Triple("Weak", COLOR_WEAK, 0.25f)
            bits < 50  -> Triple("Fair", COLOR_MEDIUM, 0.45f)
            bits < 70  -> Triple("Good", COLOR_MEDIUM, 0.65f)
            bits < 100 -> Triple("Strong", COLOR_STRONG, 0.82f)
            else       -> Triple("Very Strong", COLOR_VERY_STRONG, 1.0f)
        }

        tvStrengthText.text = "$label $bits-bit"
        tvStrengthText.setTextColor(color)

        val parent = strengthFill.parent as? FrameLayout
        if (parent != null) {
            val w = parent.width
            if (w > 0) {
                val lp = strengthFill.layoutParams as FrameLayout.LayoutParams
                lp.width = Math.max(dp(10), (w * pct).toInt())
                strengthFill.layoutParams = lp
            }
        }

        val bg = strengthFill.background
        if (bg is android.graphics.drawable.GradientDrawable) {
            bg.setColor(color)
        }
        tvBitsInfo.text = "Entropy: $bits bits"
    }
}
