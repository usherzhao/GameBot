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
```
## 2. 配置文件 (config.json)
请在项目根目录下创建 config.json，填入你的企业微信 Webhook Key 和不同分辨率下的按钮坐标。

```JSON

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
```
key: 对应企业微信 Webhook URL key= 后面的字符串。

resolutions: 键必须是 宽x高 格式。程序启动时会自动检测当前屏幕分辨率，并匹配对应的点击坐标。

### 3. 在 IDEA 中运行
打开 src/main/java/bot/GameBot.java (或你的实际包路径)。

右键点击 -> Run 'GameBot.main()'。

注意: 确保 IDEA 的 "Working directory" (工作目录) 设置为项目根目录，否则会提示找不到 config.json。

## 💻 开发者指南
### 1. 环境准备JDK 11 或更高版本。Maven 3.x。首次运行需要等待 Maven 下载 PyTorch 原生引擎库（约 200MB）。
### 2. 模型训练 (GameSceneTrainer)如果你需要更新 AI 的识别能力：将采集好的截图分类放入 dataset/game, dataset/login, dataset/select 目录。运行 GameSceneTrainer.java。训练完成后，模型会自动保存到 build/model 目录。主程序 GameBot 会直接读取 build/model 下的最新模型，无需手动复制。
### 3.  状态与逻辑说明状态
状态 (Class),描述,触发行为
,正常挂机画面,维持心跳，无操作。
login,游戏登录/掉线界面,视为异常状态。若持续 5 分钟，发送企业微信通知。
select,角色选择界面,视为准备就绪。读取 config.json 中的坐标，点击“进入游戏”按钮（含 5秒 冷却防抖）。
|状态 (Class)|描述|触发行为|
|-|-|-|
|game|正常挂机画面|维持心跳，无操作。|
|login|游戏登录/掉线界面|视为异常状态。若持续 5 分钟，发送企业微信通知。。|
|select|角色选择界面|视为准备就绪。读取 config.json 中的坐标，点击“进入游戏”按钮（含 5秒 冷却防抖）。。|

# ⚠️ 免责声明
本项目仅供 Java AI 技术研究与学习 使用。 请勿将本软件用于违反游戏服务条款（ToS）的行为。作者不对任何账号封禁或损失负责。
