package com.passwordgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.log2

class GeneratorTest {
    // 单元测试的工作目录是 app/ 模块目录。
    private val wordFile = File("src/main/assets/eff_large_wordlist.txt")
    private val words = wordFile.readLines().filter { it.isNotBlank() }
    private val gen = Generator(words)

    @Test
    fun wordListIsTheEffLargeList() {
        assertEquals(7776, words.size)
        assertEquals(7776, words.toSet().size)
        assertTrue(words.all { it.matches(Regex("[a-z-]+")) })
        // 从 EFF 原始词表（https://www.eff.org/files/2016/07/18/eff_large_wordlist.txt，
        // sha256 addd35536511597a02fa0a9ff1e5284677b8883b83e986e43f15a3db996b903e）只取单词列，每行一个。
        // 锁定哈希，防止词表被意外改动。
        val sha = MessageDigest.getInstance("SHA-256").digest(wordFile.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals("6d557f0693958fb5e650b68b5bee585eb82cf4da32965505c789e924743bc522", sha)
    }

    @Test
    fun passwordUsesOnlyPoolAndHasEveryClass() {
        for (len in listOf(8, 12, 20, 64)) {
            repeat(500) {
                val o = PasswordOptions(length = len)
                val s = gen.password(o).text
                assertEquals(len, s.length)
                val pool = o.charSets().joinToString("")
                assertTrue(s.all { it in pool })
                for (set in o.charSets()) assertTrue("缺少 $set: $s", s.any { it in set })
            }
        }
    }

    @Test
    fun passwordRespectsSelectedClassesAndAmbiguity() {
        val o = PasswordOptions(length = 32, classes = setOf(CharClass.LOWER, CharClass.DIGIT), excludeAmbiguous = true)
        repeat(500) {
            val s = gen.password(o).text
            assertTrue(s.all { it.isLowerCase() || it.isDigit() })
            assertFalse(s.any { it in AMBIGUOUS })
        }
    }

    @Test
    fun passwordEntropyMatchesBruteForce() {
        // 只有一类时就是 长度 × log2(池大小)。
        assertEquals(16 * log2(26.0), passwordEntropy(listOf(26), 16), 1e-9)
        // 两类各 2 个字符、长度 3：4^3 − 2·2^3 = 48 种。
        assertEquals(log2(48.0), passwordEntropy(listOf(2, 2), 3), 1e-9)
        // 和穷举对比：两类（3 个、2 个字符），长度 4。
        val a = "abc"; val b = "12"; val pool = a + b
        var n = 0
        for (i in 0 until 625) {
            val s = buildString { var x = i; repeat(4) { append(pool[x % 5]); x /= 5 } }
            if (s.any { it in a } && s.any { it in b }) n++
        }
        assertEquals(log2(n.toDouble()), passwordEntropy(listOf(3, 2), 4), 1e-9)
        // 默认设置（20 位、四类）大约 128 位。
        val bits = gen.password(PasswordOptions()).entropyBits
        assertTrue("bits=$bits", bits in 125.0..131.0)
    }

    @Test
    fun passphraseShape() {
        repeat(300) {
            val s = gen.passphrase(PassphraseOptions(words = 6, separator = "-")).text
            // 有的 EFF 单词本身带连字符（如 "t-shirt"），所以用空格分隔来检查单词数。
            val spaced = gen.passphrase(PassphraseOptions(words = 6, separator = " ")).text
            assertEquals(6, spaced.split(" ").size)
            assertTrue(s.isNotEmpty())
        }
        val o = PassphraseOptions(words = 5, separator = ".", capitalize = true, includeNumber = true)
        repeat(300) {
            val parts = gen.passphrase(o).text.split(".")
            assertEquals(5, parts.size)
            assertTrue(parts.all { it.first().isUpperCase() })
            assertEquals(1, parts.sumOf { p -> p.count { it.isDigit() } })
            assertTrue(parts.single { p -> p.any { it.isDigit() } }.last().isDigit())
        }
        assertEquals(6 * log2(7776.0), gen.passphrase(PassphraseOptions(words = 6)).entropyBits, 1e-9)
        assertEquals(5 * log2(7776.0) + log2(50.0), gen.passphrase(o).entropyBits, 1e-9)
    }

    @Test
    fun pinIsUniformDigits() {
        val counts = IntArray(10)
        repeat(20_000) {
            val s = gen.pin(PinOptions(6)).text
            assertEquals(6, s.length)
            s.forEach { counts[it - '0']++ }
        }
        val expected = 20_000 * 6 / 10.0
        for (c in counts) assertTrue("counts=${counts.toList()}", abs(c - expected) / expected < 0.05)
        assertEquals(6 * log2(10.0), gen.pin(PinOptions(6)).entropyBits, 1e-9)
    }

    @Test
    fun strengthAndCrackTime() {
        assertEquals(Strength.WEAK, strengthOf(20.0))
        assertEquals(Strength.FAIR, strengthOf(50.0))
        assertEquals(Strength.STRONG, strengthOf(70.0))
        assertEquals(Strength.VERY_STRONG, strengthOf(100.0))
        assertEquals("不到 1 秒", crackTime(20.0))
        assertEquals("约 66 天", crackTime(60.0))
        assertEquals("约 19 万年", crackTime(80.0))
        assertEquals("超过 1 万亿年", crackTime(128.0))
    }

    @Test
    fun customSymbols() {
        assertEquals("!@#", sanitizeSymbols("!a@1# 中!@"))
        assertEquals("", sanitizeSymbols("abc 123 中文"))
        for (sym in listOf("!", "!@#", SymbolPreset.COMMON.symbols)) {
            val o = PasswordOptions(length = 16, symbols = sym)
            repeat(300) {
                val s = gen.password(o).text
                val used = s.filterNot { it.isLetterOrDigit() }
                assertTrue("$s 用了 $sym 以外的符号", used.all { it in sym })
                assertTrue(used.isNotEmpty())
            }
            val sizes = listOf(26, 26, 10, sym.length)
            assertEquals(passwordEntropy(sizes, 16), gen.password(o).entropyBits, 1e-9)
        }
        // 不选符号时，符号集是什么都无所谓。
        PasswordOptions(classes = setOf(CharClass.LOWER), symbols = "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptySymbolsWhenSymbolsSelected() {
        PasswordOptions(symbols = "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyClassSet() {
        PasswordOptions(classes = emptySet())
    }
}
