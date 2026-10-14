# 同文·云同步 / TrimeCloudSync

基于 [同文输入法 Trime](https://github.com/osfans/trime) 的个人定制版，集成 Rime S3 同步、GitHub 主题导入和“桉 泽”键盘。Android 包名 `top.loto2.rimecloudime`，当前版本 **0.2.7**。

## 下载

- [0.2.7 APK、对应源码与验证记录](https://github.com/Anzaynt/TrimeCloudSync/releases/tag/v0.2.7)
- [安装及本版说明](docs/菜单修正版-0.2.7使用说明.md)

0.2.7 修复菜单末行显示不全：自适应行高，避让系统导航区，空间不足时可滚动。保留“桉 泽”空格键、工具栏中英切换、A 全选和已有云同步。可覆盖安装此前同签名版本，不需要先卸载或清除应用数据。尚未进行安卓真机测试。

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

Release 中的 `TrimeCloudSync-0.2.7-source.zip` 是生成已发布 APK 时保存的源码包；本 Git 仓库在该源码基础上增加迁移说明和版本管理文件整理，没有重新编译 Release APK。上游发布工作流保存在 `doc/upstream-workflows/`，不会在这个个人仓库自动执行上游发布任务。
