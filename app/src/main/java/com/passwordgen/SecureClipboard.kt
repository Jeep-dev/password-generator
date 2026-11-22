package com.passwordgen

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

/**
 * 复制到剪贴板：
 * - 标记为敏感内容（Android 13 起系统不会在复制提示里显示明文，输入法也不会把它记进剪贴板历史）；
 * - 一段时间后自动清空。清空前确认剪贴板里还是我们放的那条，用户中途复制了别的东西就不动它。
 */
object SecureClipboard {
    const val CLEAR_AFTER_SECONDS = 30
    private const val LABEL = "PasswordGen"
    // ClipDescription.EXTRA_IS_SENSITIVE 是 API 33 才有的常量，老系统上用同名字符串，部分输入法也认。
    private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

    private val handler = Handler(Looper.getMainLooper())
    private var pendingClear: Runnable? = null

    fun copy(context: Context, text: String) {
        val cm = context.applicationContext.getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText(LABEL, text)
        clip.description.extras = PersistableBundle().apply {
            putBoolean(
                if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else EXTRA_IS_SENSITIVE,
                true,
            )
        }
        cm.setPrimaryClip(clip)
        // 用复制时刻做标记：只清自己这一次放进去的内容。
        val stamp = cm.primaryClipDescription?.timestamp

        pendingClear?.let(handler::removeCallbacks)
        val clear = Runnable {
            pendingClear = null
            val desc = cm.primaryClipDescription
            // 应用在后台时，Android 10+ 不让读剪贴板，desc 会是 null；这时无法确认，按“还是我们的”处理。
            val stillOurs = desc == null ||
                (desc.label == LABEL && desc.timestamp == stamp)
            if (stillOurs) {
                cm.clearPrimaryClip()
            }
        }
        pendingClear = clear
        handler.postDelayed(clear, CLEAR_AFTER_SECONDS * 1000L)
    }

    /** Android 13 起系统会自己弹“已复制”的提示，应用就不必再提示一遍。 */
    val systemShowsCopyNotice: Boolean get() = Build.VERSION.SDK_INT >= 33
}
