# 开发与签名

## 仓库迁移

Windows 克隆时开启 Git longpaths。源代码依赖采用展开后的普通文件，`app/prebuilt` 中的原生库必须一并提交；不要因其扩展名为 `.so` 而漏掉。首次建仓时已将清理过的源码快照完整纳入版本控制。

## 签名

请将原私钥和以下配置保存在仓库以外，通过私密方式迁移。签名文件、密码以及 S3 密钥均不应提交。

`keystore.properties` 示例（全部值为占位符，Windows 路径使用正斜杠）：

```properties
storeFile=C:/private/prototype-signing.p12
storePassword=YOUR_STORE_PASSWORD
keyAlias=prototype
keyPassword=YOUR_KEY_PASSWORD
```

```powershell
$env:KEYSTORE_PROPERTIES='C:\private\keystore.properties'
.\build-cloud.ps1 -Sdk "$env:LOCALAPPDATA\Android\Sdk" -Jdk 'C:\path\to\jdk-17'
```

构建完成后用 Android Build Tools 的 apksigner 验证签名。原 0.2.7 发布签名证书 SHA-256：

`42013e1d2a3e28df6e04da621fcf3ec45261115d90b5cc51af462478683fa59f`

## 版本更新

修改 `app/build.gradle.kts` 中的 versionCode 和 versionName；versionCode 必须递增。同时更新 `build-cloud.ps1` 的 BUILD_VERSION_NAME，构建、测试并使用原签名发布。新的 APK 放到新的 GitHub Release，不把持续产生的构建目录提交到 Git。

验证记录注明未进行真机测试；构建成功不能证明 ColorOS 导航栏、后台保活和所有输入场景均已验证。
