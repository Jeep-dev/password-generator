package com.passwordgen

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 最近任务列表里不显示应用截图，免得密码出现在缩略图里；普通截图不受影响。
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false)

        val words = assets.open("eff_large_wordlist.txt").bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.toList()
        }
        val generator = Generator(words)

        setContent {
            AppTheme {
                GeneratorScreen(generator)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 主题
// ---------------------------------------------------------------------------

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFFD0BCFF), secondary = Color(0xFFCCC2DC), tertiary = Color(0xFFEFB8C8))
        else -> lightColorScheme(primary = Color(0xFF6750A4), secondary = Color(0xFF625B71), tertiary = Color(0xFF7D5260))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

// ---------------------------------------------------------------------------
// 主界面
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneratorScreen(generator: Generator) {
    // 设置和结果都只放在内存里：界面配置变化时 Activity 不会重建（见 AndroidManifest 的 configChanges），
    // 也不写进 savedInstanceState，避免密码被系统持久化。
    val context = LocalContext.current
    val saved = remember { SettingsStore.load(context) }
    var mode by remember { mutableStateOf(saved.mode) }
    var pwOpts by remember { mutableStateOf(saved.password) }
    var ppOpts by remember { mutableStateOf(saved.passphrase) }
    var pinOpts by remember { mutableStateOf(saved.pin) }
    var regen by remember { mutableIntStateOf(0) }
    var secret by remember { mutableStateOf<Secret?>(null) }

    LaunchedEffect(mode, pwOpts, ppOpts, pinOpts) {
        SettingsStore.save(context, Settings(mode, pwOpts, ppOpts, pinOpts))
    }

    LaunchedEffect(mode, pwOpts, ppOpts, pinOpts, regen) {
        secret = when (mode) {
            Mode.PASSWORD -> generator.password(pwOpts)
            Mode.PASSPHRASE -> generator.passphrase(ppOpts)
            Mode.PIN -> generator.pin(pinOpts)
        }
    }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("密码生成器") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val content = Modifier.widthIn(max = 600.dp).fillMaxWidth()

            SingleChoiceSegmentedButtonRow(content) {
                Mode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { mode = m },
                        shape = SegmentedButtonDefaults.itemShape(i, Mode.entries.size),
                    ) { Text(m.label) }
                }
            }

            ResultCard(
                secret = secret,
                modifier = content,
                onRegenerate = { regen++ },
                onCopy = {
                    val s = secret ?: return@ResultCard
                    SecureClipboard.copy(context, s.text)
                    if (!SecureClipboard.systemShowsCopyNotice) {
                        scope.launch {
                            snackbar.showSnackbar("已复制，${SecureClipboard.CLEAR_AFTER_SECONDS} 秒后自动清空剪贴板")
                        }
                    }
                },
            )

            OptionsCard(content) {
                when (mode) {
                    Mode.PASSWORD -> PasswordOptionsUi(pwOpts) { pwOpts = it }
                    Mode.PASSPHRASE -> PassphraseOptionsUi(ppOpts) { ppOpts = it }
                    Mode.PIN -> PinOptionsUi(pinOpts) { pinOpts = it }
                }
            }

            Text(
                "密码只在本机生成，不联网、不保存。复制后 ${SecureClipboard.CLEAR_AFTER_SECONDS} 秒自动清空剪贴板。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = content.padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun ResultCard(
    secret: Secret?,
    modifier: Modifier,
    onRegenerate: () -> Unit,
    onCopy: () -> Unit,
) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SelectionContainer {
                Text(
                    text = colorize(secret?.text.orEmpty()),
                    fontFamily = FontFamily.Monospace,
                    fontSize = if ((secret?.text?.length ?: 0) > 32) 18.sp else 22.sp,
                    lineHeight = 30.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (secret != null) StrengthMeter(secret.entropyBits)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(onClick = onRegenerate, modifier = Modifier.weight(1f)) {
                    Icon(RefreshIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("重新生成")
                }
                Button(onClick = onCopy, modifier = Modifier.weight(1f), enabled = secret != null) {
                    Icon(CopyIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("复制")
                }
            }
        }
    }
}

/** 数字和符号换个颜色，方便手动输入时分辨。 */
@Composable
private fun colorize(text: String): AnnotatedString {
    val digit = MaterialTheme.colorScheme.primary
    val symbol = MaterialTheme.colorScheme.tertiary
    return buildAnnotatedString {
        for (c in text) {
            when {
                c.isDigit() -> withStyle(SpanStyle(color = digit, fontWeight = FontWeight.SemiBold)) { append(c) }
                !c.isLetterOrDigit() && c != ' ' -> withStyle(SpanStyle(color = symbol, fontWeight = FontWeight.SemiBold)) { append(c) }
                else -> append(c)
            }
        }
    }
}

@Composable
private fun StrengthMeter(bits: Double) {
    val strength = strengthOf(bits)
    val color by animateColorAsState(
        when (strength) {
            Strength.WEAK -> MaterialTheme.colorScheme.error
            Strength.FAIR -> Color(0xFFE09A00)
            Strength.STRONG -> Color(0xFF43A047)
            Strength.VERY_STRONG -> Color(0xFF2E7D32)
        },
        label = "strengthColor",
    )
    // 128 位及以上算满格。
    val progress by animateFloatAsState((bits / 128).toFloat().coerceIn(0.04f, 1f), label = "strength")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { progress },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                "${strength.label} · ${bits.roundToInt()} 位熵",
                style = MaterialTheme.typography.labelLarge,
                color = color,
                modifier = Modifier.weight(1f),
            )
            Text(
                "离线破解${crackTime(bits)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OptionsCard(modifier: Modifier, content: @Composable () -> Unit) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

// ---------------------------------------------------------------------------
// 各模式的选项
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PasswordOptionsUi(o: PasswordOptions, onChange: (PasswordOptions) -> Unit) {
    IntSlider("长度", o.length, PasswordOptions.MIN_LENGTH..PasswordOptions.MAX_LENGTH, "位") {
        onChange(o.copy(length = it))
    }
    SectionLabel("包含字符")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (c in CharClass.entries) {
            val selected = c in o.classes
            FilterChip(
                selected = selected,
                // 至少保留一种字符类型。
                enabled = !(selected && o.classes.size == 1),
                onClick = { onChange(o.copy(classes = if (selected) o.classes - c else o.classes + c)) },
                label = { Text(c.label) },
            )
        }
    }
    if (CharClass.SYMBOL in o.classes) {
        SymbolsPicker(o.symbols) { onChange(o.copy(symbols = it)) }
    }
    SwitchRow("排除易混淆字符", "不使用 0 O 1 l I", o.excludeAmbiguous) { onChange(o.copy(excludeAmbiguous = it)) }
}

/** 有些网站只接受部分符号：可以选预设，也可以自己填允许的符号。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SymbolsPicker(symbols: String, onChange: (String) -> Unit) {
    var custom by remember { mutableStateOf(SymbolPreset.entries.none { it.symbols == symbols }) }
    var text by remember { mutableStateOf(symbols) }

    SectionLabel("可用符号")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (preset in SymbolPreset.entries) {
            FilterChip(
                selected = !custom && symbols == preset.symbols,
                onClick = { custom = false; onChange(preset.symbols) },
                label = { Text(preset.label) },
            )
        }
        FilterChip(
            selected = custom,
            onClick = { custom = true; text = symbols },
            label = { Text("自定义") },
        )
    }
    if (custom) {
        OutlinedTextField(
            value = text,
            onValueChange = { t ->
                text = sanitizeSymbols(t)
                if (text.isNotEmpty()) onChange(text)
            },
            label = { Text("允许的符号") },
            placeholder = { Text("例如 !@#$", fontFamily = FontFamily.Monospace) },
            isError = text.isEmpty(),
            supportingText = {
                Text(if (text.isEmpty()) "至少输入一个符号" else "共 ${text.length} 个。只认键盘上的英文符号，重复的会自动去掉")
            },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Text(
            symbols,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val SymbolPreset.label: String
    get() = when (this) {
        SymbolPreset.ALL -> "全部"
        SymbolPreset.COMMON -> "常用"
    }

private val CharClass.label: String
    get() = when (this) {
        CharClass.UPPER -> "大写 A-Z"
        CharClass.LOWER -> "小写 a-z"
        CharClass.DIGIT -> "数字 0-9"
        CharClass.SYMBOL -> "符号 !@#"
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PassphraseOptionsUi(o: PassphraseOptions, onChange: (PassphraseOptions) -> Unit) {
    IntSlider("单词数", o.words, PassphraseOptions.MIN_WORDS..PassphraseOptions.MAX_WORDS, "个") {
        onChange(o.copy(words = it))
    }
    SectionLabel("分隔符")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (sep in PassphraseOptions.SEPARATORS) {
            FilterChip(
                selected = o.separator == sep,
                onClick = { onChange(o.copy(separator = sep)) },
                label = { Text(if (sep == " ") "空格" else sep, fontFamily = FontFamily.Monospace) },
            )
        }
    }
    SwitchRow("首字母大写", null, o.capitalize) { onChange(o.copy(capitalize = it)) }
    SwitchRow("加一个数字", "接在随机一个单词后面", o.includeNumber) { onChange(o.copy(includeNumber = it)) }
}

@Composable
private fun PinOptionsUi(o: PinOptions, onChange: (PinOptions) -> Unit) {
    IntSlider("位数", o.length, PinOptions.MIN_LENGTH..PinOptions.MAX_LENGTH, "位") { onChange(o.copy(length = it)) }
    Text(
        "PIN 码只适合有错误次数限制的场合（手机锁屏、银行卡），不要当作网站或服务器密码。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------------------------------------------------------------------------
// 小组件
// ---------------------------------------------------------------------------

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun IntSlider(label: String, value: Int, range: IntRange, unit: String, onChange: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("$value $unit", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { v -> v.roundToInt().let { if (it != value) onChange(it) } },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// Material Symbols 的两个图标，直接内置，省得为两个图标引入整个图标库。
private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(addPathNodes(path), fill = SolidColor(Color.Black))
    .build()

private val CopyIcon = icon(
    "Copy",
    "M16,1H4C2.9,1 2,1.9 2,3v14h2V3h12V1zM19,5H8C6.9,5 6,5.9 6,7v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7C21,5.9 20.1,5 19,5zM19,21H8V7h11V21z",
)

private val RefreshIcon = icon(
    "Refresh",
    "M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z",
)
