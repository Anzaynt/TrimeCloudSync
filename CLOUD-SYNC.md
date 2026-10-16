# Cloud Sync fork 0.2.3

Latest source update: **0.2.9**. Adds default-on pull-only mode shared by manual and background sync, including a network-level write guard. Updates the four main alphabet layouts to staggered rows with independent symbol/number/language keys; keeps toolbar, colors and overall height. See [0.2.9 guide](docs/仅拉取与键位布局-0.2.9使用说明.md). Source-only delivery; original signing key and Android SDK are not available on this machine. Earlier entries below describe historical versions.

Base commit: af23c4a631274d118ed76ae77e6dc8d8f7024c56. Package top.loto2.rimecloudime; JNI namespace com.osfans.trime. Min SDK 26; compile SDK 37; target SDK 35. JDK 17 / Gradle 9.7.1 / Build Tools 36.0.0; see build-cloud.ps1. Corresponding native dependencies and pinned prebuilt libraries are included with source/provenance. Fork code changes are GPL-3.0-or-later; bundled Hanamaru repository does not declare a license; retain original attribution and provenance for this personal build.

S3 synchronization retains desktop file manifest, performs native dictionary union and coverage verification, publishes snapshots using If-Match CAS, serializes native maintenance, encrypts credentials with Android Keystore. Download object cache retains current references; config backups have a two-generation / 128 MiB cap. No credentials are embedded.

0.2.3 replaces custom Starry theme with four Hanamaru themes and their fonts/image. Removes the added export UI. Adds public GitHub repository import using a pinned HEAD commit archive; bounded extraction, allowed resources, protected configuration filtering, actual theme parser validation, layout selection, rollback on ordinary exceptions and cleanup of previous managed theme files. Theme choices are deduplicated by config ID, with user files preferred as the loader does. New version initializes the requested theme once without resetting future imported theme choices.

Details and limitations: app/src/main/assets/theme-licenses/Hanamaru与GitHub主题导入-使用说明.md. Source includes Hanamaru-provenance.json and the real upstream ZIP used by HanamaruTest. No Android runtime verification has been performed. GitHub imports do not execute repository generation scripts; APK upgrades remain manual.

## 0.2.4

Replaces Hanamaru with the Mintimate/RimeTheme Trime theme. Missing original danjing definitions are restored and namespace references retargeted. Expanded configuration and 16 background images are bundled. Public GitHub import retains bounds and protection and adds this dependency repair for Mintimate/RimeTheme. Defaults to Mint Dark Blue once on upgrade; see app/src/main/assets/theme-licenses/薄荷主题试用版-说明.md. Theme testing still uses the historical HanamaruTest class name alongside import regressions.

## 0.2.5

Classic theme personal variant based on harry10086/mytrime rime/classic.trime.yaml. Main key positions and swipe bindings preserved; toolbar simplified and increased to 22sp with 4dp padding, candidate height 34dp, keyboard height 260dp, bottom padding 12dp. Uses system font fallback and normalized Material icon names. Default selects wechat_dark once on upgrade, retaining user future theme choices. Mint bundle/commit override removed. See Classic-provenance.json and bundled Chinese guide. No physical OnePlus ACE 3 Pro validation.


## 0.2.6 桉泽版

主空格键改为“桉 泽”，删除长按中英切换和提示，保留工具栏切换、候选翻页及 A 全选。主题底部留白 0，首次升级开启现有忽略系统手势区域设置。系统导航控件需在手机系统设置处理，未声称能够由 APK 完全隐藏。


## 0.2.7 菜单修正版

方案菜单根据视口和项目数量调整行高，并独立处理系统导航栏底部 inset，保留小屏及大字体下滚动。主键盘和同步逻辑未更改。


## 0.2.8

Keyboard height 208dp (previously 260dp), narrower gaps and wider second/third letter rows. Foreground GitHub latest-release checking, bounded APK download, digest/package/version/signature validation and user-confirmed Android installation. No embedded credentials or silent installation.
