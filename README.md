# Game Visual AI Assistant (Java + DJL)

![Java](https://img.shields.io/badge/Language-Java%2011%2B-orange)
![DJL](https://img.shields.io/badge/AI-DJL%200.29.0-blue)
![Fastjson2](https://img.shields.io/badge/JSON-Fastjson2-green)
![License](https://img.shields.io/badge/License-MIT-lightgrey)

这是一个基于 Java 和 DJL (Deep Java Library) 开发的视觉识别游戏挂机助手。

它像人类一样通过**视觉（屏幕截图）**来分析游戏状态。利用卷积神经网络 (CNN) 实时识别当前画面（挂机中、登录界面、选人界面），并执行自动化操作（如掉线重连、自动进游戏）或发送异常通知。

## ✨ 核心功能

* **👁️ 视觉识别**: 每秒截取屏幕，通过 CNN 模型判断当前场景，不读取内存，安全防封。
* **🖥️ 分辨率自适应**: 支持在 `config.json` 中配置多套分辨率坐标，程序启动时自动识别屏幕尺寸并加载对应配置。
* **📱 企业微信通知**: 当检测到异常状态（非挂机）超过 5 分钟，自动通过 Webhook 推送消息到企业微信。
* **⚙️ 外部配置**: 核心参数（Webhook Key、按钮坐标）分离在 JSON 文件中，无需修改代码即可适配不同电脑。

## 🛠️ 技术栈

* **语言**: Java 11+
* **AI 框架**: [Deep Java Library (DJL)](https://djl.ai/)
* **底层引擎**: PyTorch
* **工具库**: OkHttp 3 (网络请求), Fastjson2 (配置解析)
* **日志**: SLF4J Simple

---

## 🚀 快速开始 (运行指南)

### 1. 目录结构
为了确保程序能正确读取配置和模型，请保持以下目录结构：

```text
ProjectRoot/
  ├── src/               # 源码目录
  ├── pom.xml            # Maven配置
  ├── config.json        # 配置文件 (必须放在项目根目录)
  └── build/             # 构建目录
       └── model/        # AI 模型文件夹 (必须包含 .params 文件)
            └── game-classifier-0000.params
## 2. 配置文件 (config.json)
请在项目根目录下创建 config.json，填入你的企业微信 Webhook Key 和不同分辨率下的按钮坐标。

JSON
```text
{
  "key": "你的企业微信Webhook_Key", 
  "resolutions": {
    "1920x1080": {
      "x": 955,
      "y": 989
    },
    "2560x1440": {
      "x": 1273,
      "y": 1318
    },
    "3840x2160": {
      "x": 1910,
      "y": 1977
    }
  }
}
key: 对应企业微信 Webhook URL key= 后面的字符串。

resolutions: 键必须是 宽x高 格式。程序启动时会自动检测当前屏幕分辨率，并匹配对应的点击坐标。
