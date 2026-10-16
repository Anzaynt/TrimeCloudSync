# 同文·云同步 / TrimeCloudSync

基于 [同文输入法 Trime](https://github.com/osfans/trime) 的个人定制版，集成 Rime S3 同步、GitHub 主题导入和“桉 泽”键盘。Android 包名 `top.loto2.rimecloudime`，当前源码版本 **0.2.9**（尚未发布 APK）。

## 下载

- [最新版 APK、对应源码与验证记录](https://github.com/Anzaynt/TrimeCloudSync/releases/latest)
- [0.2.9 仅拉取与键位布局说明](docs/仅拉取与键位布局-0.2.9使用说明.md)

0.2.9 新增默认开启的“仅拉取不上传”，手动及后台同步只把云端词库合并到手机，避免手机推送覆盖电脑记录。QWERTY 起改为错位字母排列，底排独立提供符号、数字、逗号、空格、句号、中英切换和回车；上方工具栏、配色与总键盘高度保留。原签名及 Android SDK 尚未迁移，本次仅交付源码与补丁，尚未生成正式 APK、发布 Release 或进行安卓真机测试。

## 在另一台电脑继续开发

```powershell
git -c core.longpaths=true clone https://github.com/Anzaynt/TrimeCloudSync.git
cd TrimeCloudSync
git config core.longpaths true
```

用 Android Studio 打开仓库根目录。准备 **JDK 17**，通过 SDK Manager 安装 **Android SDK Platform 37** 和 **Build-Tools 36.0.0**。项目 Gradle Wrapper 固定为 **9.7.1**，首次构建会下载依赖。

Windows PowerShell 发布构建（替换 JDK 路径）：

```powershell
.\build-cloud.ps1 -Sdk "$env:LOCALAPPDATA\Android\Sdk" -Jdk 'C:\path\to\jdk-17'
```

构建产物位于 `app/build/outputs/apk/release/`。未配置私钥时生成未签名 APK。仅调试开发可运行 `app:assembleDebug`，调试版包名后缀为 `.debug`，与已安装发布版分开。

仓库已经包含 `app/prebuilt/` 四种 ABI 的固定 JNI 库、OpenCC 数据及对应原生源码/来源记录，克隆后无需运行 `git submodule update`。上游子模块已经展开为普通文件。修改 Kotlin/Java、界面或主题可以直接构建；修改原生 C++ 则需要重新编译和替换预编译库，不能仅编辑源码后沿用旧库。

回归测试：

```powershell
$env:JAVA_HOME='C:\path\to\jdk-17'
.\gradlew.bat app:testDebugUnitTest --tests com.osfans.trime.data.theme.HanamaruTest --tests com.osfans.trime.ime.switches.SwitchOptionLayoutTest
```

## 发布签名与同步配置

公开仓库不含 S3 密钥、同步用户数据、APK 签名私钥或密码。S3 参数在安装后的应用中填写。

要在新电脑编译可覆盖旧版的发布 APK，需要另外通过私密方式迁移原签名私钥及密码。可在仓库外保存 `keystore.properties`，通过 `KEYSTORE_PROPERTIES` 环境变量指定它的绝对路径；格式见 [开发说明](docs/DEVELOPMENT.md)。不要生成新签名后尝试覆盖已有发布版。

## 代码位置

- 云同步和 GitHub 主题导入：`app/src/main/java/com/osfans/trime/cloudsync/`
- 键盘主题：`app/src/main/assets/shared/classic.trime.yaml`
- 菜单适配：`app/src/main/java/com/osfans/trime/ime/switches/`
- 相关回归测试：`app/src/test/java/com/osfans/trime/`
- 历次定制说明：`CLOUD-SYNC.md`

## 来源与许可

基于 Trime 提交 `af23c4a631274d118ed76ae77e6dc8d8f7024c56`。保留 GPL-3.0-or-later 及各第三方原有许可和来源文件，详见 [LICENSE](LICENSE)、`third-party/`、各来源 JSON 和 [上游 README](README.upstream.md)。未更改第三方素材的许可条件。

Release 中的 `TrimeCloudSync-0.2.7-source.zip` 是生成已发布 APK 时保存的源码包；该版本的原始归档不变；main 分支继续包含 0.2.8 及之后的开发修改，每个 Release 提供各自对应源码。上游发布工作流保存在 `doc/upstream-workflows/`，不会在这个个人仓库自动执行上游发布任务。


## 在线更新发布约定

应用主页每天检查一次本仓库最新正式 Release，用户也可手动检查。发布必须使用 `vX.Y.Z` 标签，上传名称为 `TrimeCloudSync-X.Y.Z-universal.apk` 的四架构 APK，使用原签名并递增 versionCode。应用核对资产 SHA-256、包名和签名，交给安卓系统由用户确认安装；不是静默安装。后台仅使用键盘时不会持续检查。详细行为见当前使用说明。
