package com.passwordgen

import java.math.BigInteger
import java.security.SecureRandom
import java.util.Random
import kotlin.math.ln
import kotlin.math.log2

/** 一次生成的结果：文本和它的熵（比特）。 */
data class Secret(val text: String, val entropyBits: Double)

enum class CharClass(val chars: String) {
    UPPER("ABCDEFGHIJKLMNOPQRSTUVWXYZ"),
    LOWER("abcdefghijklmnopqrstuvwxyz"),
    DIGIT("0123456789"),
    // 不含引号、反斜杠、反引号和竖线：这些字符粘贴进命令行、配置文件或网页表单时容易出问题。
    SYMBOL("!@#$%^&*()-_=+[]{};:,.<>?/~"),
}

/** 容易看错的字符：0/O、1/l/I。 */
const val AMBIGUOUS = "0O1lI"

data class PasswordOptions(
    val length: Int = 20,
    val classes: Set<CharClass> = CharClass.entries.toSet(),
    val excludeAmbiguous: Boolean = false,
) {
    init {
        require(length in MIN_LENGTH..MAX_LENGTH) { "length $length" }
        require(classes.isNotEmpty()) { "至少要选一种字符" }
    }

    /** 实际使用的各类字符（已按设置去掉易混淆字符）。 */
    fun charSets(): List<String> = CharClass.entries
        .filter { it in classes }
        .map { c -> if (excludeAmbiguous) c.chars.filterNot { it in AMBIGUOUS } else c.chars }

    companion object {
        const val MIN_LENGTH = 8
        const val MAX_LENGTH = 64
    }
}

data class PassphraseOptions(
    val words: Int = 6,
    val separator: String = "-",
    val capitalize: Boolean = false,
    val includeNumber: Boolean = false,
) {
    init {
        require(words in MIN_WORDS..MAX_WORDS) { "words $words" }
    }

    companion object {
        const val MIN_WORDS = 4
        const val MAX_WORDS = 10
        val SEPARATORS = listOf("-", " ", ".", "_")
    }
}

data class PinOptions(val length: Int = 6) {
    init {
        require(length in MIN_LENGTH..MAX_LENGTH) { "length $length" }
    }

    companion object {
        const val MIN_LENGTH = 4
        const val MAX_LENGTH = 12
    }
}

class Generator(
    private val wordList: List<String>,
    private val random: Random = SecureRandom(),
) {
    init {
        require(wordList.size >= 2) { "词表太小" }
    }

    /**
     * 随机密码：每个字符都从字符池里均匀抽取。如果某个选中的字符类型一次都没出现，就整串重抽，
     * 这样结果在“每类至少出现一次”的所有密码里仍然是均匀分布的，不会因为硬塞字符而带上可预测的位置规律。
     */
    fun password(o: PasswordOptions): Secret {
        val sets = o.charSets()
        val pool = sets.joinToString("")
        // 长度比字符类型数还短时不可能每类都出现；选项范围（≥8 位、≤4 类）保证了这种情况不会发生。
        check(o.length >= sets.size)
        val buf = CharArray(o.length)
        while (true) {
            for (i in buf.indices) buf[i] = pool[random.nextInt(pool.length)]
            if (sets.all { set -> buf.any { it in set } }) {
                return Secret(String(buf), passwordEntropy(sets.map { it.length }, o.length))
            }
        }
    }

    /** 单词口令：从词表中均匀抽词（可重复）。数字会接在随机一个单词的后面。 */
    fun passphrase(o: PassphraseOptions): Secret {
        val words = MutableList(o.words) {
            val w = wordList[random.nextInt(wordList.size)]
            if (o.capitalize) w.replaceFirstChar { it.uppercaseChar() } else w
        }
        var bits = o.words * log2(wordList.size.toDouble())
        if (o.includeNumber) {
            val at = random.nextInt(words.size)
            words[at] = words[at] + random.nextInt(10)
            bits += log2(10.0 * words.size)
        }
        return Secret(words.joinToString(o.separator), bits)
    }

    fun pin(o: PinOptions): Secret {
        val text = buildString { repeat(o.length) { append('0' + random.nextInt(10)) } }
        return Secret(text, o.length * log2(10.0))
    }
}

/**
 * 精确计算“每类至少出现一次”的密码有多少种，再取 log2。
 * 用容斥原理：Σ_{S⊆类型} (-1)^|S| · (池大小 − S 中字符总数)^长度。各类字符互不重叠，所以可以直接相加。
 */
fun passwordEntropy(classSizes: List<Int>, length: Int): Double {
    val total = classSizes.sum()
    var count = BigInteger.ZERO
    for (mask in 0 until (1 shl classSizes.size)) {
        var removed = 0
        for (i in classSizes.indices) if (mask and (1 shl i) != 0) removed += classSizes[i]
        val term = BigInteger.valueOf((total - removed).toLong()).pow(length)
        count = if (Integer.bitCount(mask) % 2 == 0) count + term else count - term
    }
    return log2(count)
}

fun log2(x: BigInteger): Double {
    require(x.signum() > 0)
    val shift = (x.bitLength() - 60).coerceAtLeast(0)
    return ln(x.shiftRight(shift).toDouble()) / ln(2.0) + shift
}

enum class Strength(val label: String) { WEAK("弱"), FAIR("一般"), STRONG("强"), VERY_STRONG("很强") }

fun strengthOf(bits: Double): Strength = when {
    bits < 45 -> Strength.WEAK
    bits < 60 -> Strength.FAIR
    bits < 80 -> Strength.STRONG
    else -> Strength.VERY_STRONG
}

/**
 * 估算离线暴力破解的平均耗时：假设攻击者拿到了快速哈希，每秒能试 1000 亿次，平均试一半就能猜中。
 * 这是偏悲观的估计；在线猜（比如 SSH 登录）要慢上亿倍。
 */
fun crackTime(bits: Double, guessesPerSecond: Double = 1e11): String {
    val seconds = Math.pow(2.0, bits - 1) / guessesPerSecond
    val minute = 60.0
    val hour = 3600.0
    val day = 86400.0
    val year = 365.25 * day
    return when {
        seconds < 1 -> "不到 1 秒"
        seconds < minute -> "约 ${seconds.toInt()} 秒"
        seconds < hour -> "约 ${(seconds / minute).toInt()} 分钟"
        seconds < day -> "约 ${(seconds / hour).toInt()} 小时"
        seconds < year -> "约 ${(seconds / day).toInt()} 天"
        else -> "约 " + bigYears(seconds / year)
    }
}

private fun bigYears(years: Double): String = when {
    years < 1e4 -> "${years.toLong()} 年"
    years < 1e8 -> "${(years / 1e4).toLong()} 万年"
    years < 1e12 -> "${(years / 1e8).toLong()} 亿年"
    else -> "超过 1 万亿年"
}
