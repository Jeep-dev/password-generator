# 密码生成器

[![下载最新版 APK](https://img.shields.io/github/v/release/Jeep-dev/password-generator?label=%E4%B8%8B%E8%BD%BD%20APK&style=for-the-badge&logo=android)](https://github.com/Jeep-dev/password-generator/releases/latest)

一个干净的 Android 密码生成器：Kotlin + Jetpack Compose（Material 3，跟随壁纸取色、支持深色模式）。

## 功能

- **随机密码**：长度 8–64 位（默认 20 位）；大写、小写、数字、符号可选；可排除易混淆字符（`0 O 1 l I`）。保证每种选中的字符类型至少出现一次，且不破坏随机性（整串重抽，而不是往固定位置塞字符）。
- **单词口令**：从 [EFF 大词表](https://www.eff.org/dice)（7776 个英文单词）随机抽 4–10 个词，如 `cobweb-unsaid-gravity-pouch-uncork-mulch`。好记又够强：6 个词约 78 位熵。可选分隔符、首字母大写、加一个数字。
- **PIN 码**：4–12 位纯数字。
- **准确的强度评估**：按实际生成规则精确计算熵（比特），显示强度等级和离线暴力破解的估算耗时。
- **安全剪贴板**：复制时标记为敏感内容（Android 13+ 的复制提示和输入法剪贴板历史里不显示明文），30 秒后自动清空；如果这期间你复制了别的东西，就不会去动它。

## 安全设计

- 随机数来自 `SecureRandom`（系统的密码学安全随机数）。
- 不申请任何权限，**连网络权限都没有**；不保存任何生成过的密码，没有历史记录。
- 关闭系统备份；最近任务列表里不显示应用截图（Android 13+）。
- 生成逻辑（`Generator.kt`）是不依赖 Android 的纯 Kotlin，并有单元测试覆盖：字符范围、每类至少出现一次、熵的计算（和穷举结果对比）、数字分布均匀性、词表完整性（锁定 SHA-256）。

## 下载

到 [Releases](https://github.com/Jeep-dev/password-generator/releases/latest) 下载 `PasswordGen-v*.apk`。

> v3 换了新的签名密钥，不能直接覆盖安装 v2：请先卸载旧版再安装。

## 构建

每次推送都会由 GitHub Actions（`.github/workflows/build.yml`）跑单元测试并编译签名的 release APK；推送到 `main` 时按版本号发布到 Releases。版本号在 `app/build.gradle.kts` 的 `versionName`。

签名用的密钥放在仓库 Secrets 里：`PG_KEYSTORE_B64`（keystore 的 base64）、`PG_KEYSTORE_PASSWORD`、`PG_KEY_PASSWORD`。

本地构建需要 JDK 17、Android SDK 和 Gradle 8.11+：

```sh
gradle :app:testDebugUnitTest   # 单元测试
gradle :app:assembleDebug       # 调试版 APK
```

## 致谢

单词口令使用 Electronic Frontier Foundation 的 [EFF Large Wordlist](https://www.eff.org/deeplinks/2016/07/new-wordlists-random-passphrases)，按 [CC BY 3.0 US](https://creativecommons.org/licenses/by/3.0/us/) 许可使用（`app/src/main/assets/eff_large_wordlist.txt` 只保留了原文件的单词列）。

## 许可

代码以 [MIT](LICENSE) 许可发布。
