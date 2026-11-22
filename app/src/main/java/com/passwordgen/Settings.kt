package com.passwordgen

import android.content.Context

/** 记住用户的生成设置（只存设置，从不存生成的密码）。 */
data class Settings(
    val mode: Mode = Mode.PASSWORD,
    val password: PasswordOptions = PasswordOptions(),
    val passphrase: PassphraseOptions = PassphraseOptions(),
    val pin: PinOptions = PinOptions(),
)

enum class Mode(val label: String) { PASSWORD("随机密码"), PASSPHRASE("单词口令"), PIN("PIN 码") }

object SettingsStore {
    private const val FILE = "settings"

    fun load(context: Context): Settings {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val d = Settings()
        // 任何一项读出来不合法（比如以后改了取值范围），就整组退回默认值。
        fun <T> orDefault(default: T, read: () -> T): T = runCatching(read).getOrDefault(default)
        return Settings(
            mode = orDefault(d.mode) { Mode.valueOf(p.getString("mode", d.mode.name)!!) },
            password = orDefault(d.password) {
                PasswordOptions(
                    length = p.getInt("pw.length", d.password.length),
                    classes = p.getStringSet("pw.classes", null)?.map(CharClass::valueOf)?.toSet() ?: d.password.classes,
                    excludeAmbiguous = p.getBoolean("pw.excludeAmbiguous", d.password.excludeAmbiguous),
                    symbols = p.getString("pw.symbols", d.password.symbols)!!,
                )
            },
            passphrase = orDefault(d.passphrase) {
                PassphraseOptions(
                    words = p.getInt("pp.words", d.passphrase.words),
                    separator = p.getString("pp.separator", d.passphrase.separator)!!
                        .also { require(it in PassphraseOptions.SEPARATORS) },
                    capitalize = p.getBoolean("pp.capitalize", d.passphrase.capitalize),
                    includeNumber = p.getBoolean("pp.includeNumber", d.passphrase.includeNumber),
                )
            },
            pin = orDefault(d.pin) { PinOptions(p.getInt("pin.length", d.pin.length)) },
        )
    }

    fun save(context: Context, s: Settings) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("mode", s.mode.name)
            .putInt("pw.length", s.password.length)
            .putStringSet("pw.classes", s.password.classes.map { it.name }.toSet())
            .putBoolean("pw.excludeAmbiguous", s.password.excludeAmbiguous)
            .putString("pw.symbols", s.password.symbols)
            .putInt("pp.words", s.passphrase.words)
            .putString("pp.separator", s.passphrase.separator)
            .putBoolean("pp.capitalize", s.passphrase.capitalize)
            .putBoolean("pp.includeNumber", s.passphrase.includeNumber)
            .putInt("pin.length", s.pin.length)
            .apply()
    }
}
