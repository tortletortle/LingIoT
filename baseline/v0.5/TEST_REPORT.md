# LingIoT V0.5 测试报告

## 当前环境真实执行

`bash tests/run.sh`：

- C++ 固件控制状态机：通过。
- Java Wire 分帧和 IPv4：通过。
- Java Capabilities：44 项断言通过。
- Java VirtualBoard + Telemetry：51 项断言通过（能力声明、LED、闪烁、电机超时与反向拒绝、RGB、舵机、规则回差、手动覆盖、停止语义、非法参数、固定大小采样缓冲）。
- JDK JavacTask.parse：全部 Android 主源码及单测源码语法解析通过；不代表 Android 类型检查或构建通过。
- 与 V0.4 归档比较：两个固件源文件逐字节一致。

## 未执行 / 待验收

没有 Android SDK、Gradle、Arduino CLI，Maven 域名无法解析。未运行 Android Gradle/JUnit 测试（含 VirtualLinkTest），未构建 APK、未编译 ESP32 固件、未验证 Android 界面渲染、语音、真实 LLM、BLE / Wi-Fi 或实体硬件。虚拟核心测试不等于模拟 UI 集成测试。CI 工作流已保留并更新版本名，但未触发。实际手机和 ESP32 仍需验收。

## 验收清单

- [ ] 模拟模式无需设备口令即可加载 9 项能力；LED、RGB、舵机与规则状态反馈正确。
- [ ] ADC 变化、回差与曲线一致，采样失败不显示假数据。
- [ ] 切换 BLE/Wi-Fi/模拟模式清空上次读数；应用隐藏后不发起新的周期请求。
- [ ] 最小固件只呈现实际支持功能，设备未启用电机则不出现电机卡片。
- [ ] 真机执行控制后核对物理响应；超时不得自动重发；停止不属于硬件急停。
