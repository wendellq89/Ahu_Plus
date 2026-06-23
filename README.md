# Ahu_Plus

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2026.02-4285F4?logo=jetpackcompose&logoColor=white)

> 安徽大学校园助手 Android 应用 — 把 CAS 统一身份认证背后的教务、一卡通、学习通、智慧安大等系统聚合到一个原生 App 里。

非官方项目,仅供学习交流。

---

## 功能一览

- **教务** — 课表(多学期)、成绩与 GPA、考试安排、培养方案、空闲教室、排考预测
- **一卡通** — 校园卡余额、消费账单、水电费查询、智慧安大支付码
- **学习通** — 课程列表、视频/文档/音频自动学习引擎、作业查看、消息中心、字体反混淆解码
- **个人中心** — 学生信息、考勤、培养方案完成度、教务通告、校长信箱
- **小工具** — 桌面 Widget(下一节课提醒)、课程提醒通知、AI 评论、深色模式

## 技术栈

| | |
|---|---|
| 语言 | Kotlin 2.2.10 |
| UI | Jetpack Compose + Material 3 (Compose BOM 2026.02.01) |
| 构建 | AGP 9.2.1 / Gradle 9.4.1 / Java 11 |
| 网络 | OkHttp + Conscrypt(强制 BoringSSL TLS) |
| 持久化 | DataStore Preferences |
| 架构 | 单 Activity + Navigation Compose + 手动 DI |
| 最低版本 | Android 7.0 (API 24),目标 API 36 |

## 构建

```bash
# 默认 arm64-v8a Debug(约 29 MB)
./gradlew assembleDebug

# 单元测试
./gradlew :app:testDebugUnitTest

# 安装到已连接设备
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

> Windows 用 `gradlew.bat`。模拟器装 `app-universal-debug.apk`(包含全 ABI)。

### Release 签名

`app/build.gradle.kts` 读取根目录 `local.properties`(已在 `.gitignore` 中):

```properties
AHU_RELEASE_STORE_FILE=/path/to/your.jks
AHU_RELEASE_STORE_PASSWORD=...
AHU_RELEASE_KEY_ALIAS=...
AHU_RELEASE_KEY_PASSWORD=...
```

未配置时回退到本机 `debug.keystore`,仅供本地自测,**不要分发**。

## 项目结构

```
app/src/main/java/com/yourname/ahu_plus/
├── MainActivity.kt              单 Activity 入口
├── AhuPlusApplication.kt        手动 DI 容器
├── data/
│   ├── local/                   DataStore 持久化
│   ├── model/                   数据模型
│   ├── network/                 OkHttp + Session 自动续期
│   └── repository/              30+ Repository
├── service/                     学习通后台前台服务
├── notification/                Widget + 课程提醒
├── ui/
│   ├── navigation/              NavHost
│   ├── screen/                  各业务页面
│   └── theme/                   Material3 Token
└── util/                        DES/AES 加密 + TTF 解析 + 悬浮窗
```

## 认证体系

不同业务系统的认证方式:

| 系统 | 域名 | 认证 |
|---|---|---|
| 教务 / 一卡通余额 / 学生一张表 | `*.ahu.edu.cn` | CAS SSO |
| 智慧安大支付码 | `adwmh.ahu.edu.cn` | CAS SSO(仅 TLS 1.2) |
| 学习通 | `passport2.chaoxing.com` | 手机号 + 密码 |
| 排考预测数据 | Gitee 公开仓库 | 无需登录 |

CAS 流程: `GET /login` → DES(username+password+lt) → `POST /cas/device` → `POST /cas/login` → CASTGC → ST → 目标系统 Session。

## 隐私

- 所有认证凭据仅存在本地 DataStore,加密由 `androidx.security.crypto` 处理
- App 不向除安徽大学官方系统、用户主动配置的第三方(腾讯云 COS 备份、AI 平台 API)以外的服务发送任何数据
- 建议 fork 后将 `applicationId` 改为自己的,并使用自己的签名

## 许可

[GPL-3.0](LICENSE)。本项目与安徽大学官方无关联,所有商标归各自所有者。
