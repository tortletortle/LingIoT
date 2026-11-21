# LingIoT 灵动智联 · V0.5

**目标：** 用一部安卓手机完成中文语音输入、LLM 工具调用、设备能力发现，并通过 BLE 或局域网驱动 ESP32 上的低压演示硬件。

本仓库当前是可继续开发的源码工程，不代表已在真实手机和 ESP32 上完成联调。V0.4 沿用 V0.3 的 LIP v2，增加可选能力 `set_analog_led_rule`；建议安卓 App 与固件一起升级，V0.3 App 会忽略该新增能力。主控目标为经典 ESP32 DevKit（ESP32-WROOM-32 / ESP32 Dev Module）。不同 ESP32 型号的引脚和外设能力可能不同，使用前必须核对开发板原理图。

## V0.5 本轮更新

- 原生 Android 控制台：按已校验的能力动态生成开关、整数滑块及应用按钮；未声明的硬件不出现，拖动控制滑块不会自动下发命令。
- 支持 RGB 状态色块、舵机角度、电机状态、ADC 采样数值及最近 60 个采样点趋势图；显示的是固件回执而非外部物理测量。
- 新增前台可选轮询，约 3 秒尝试读取一次，通信忙时跳过；界面隐藏时停止调度，失败时暂停并清除旧状态。正在进行的请求可能完成。
- 新增纯软件模拟模式，使用同一 LIP v2 协议和能力校验路径；不连接真实硬件，可调整模拟 ADC 输入，手动演示不需要 API Key。语音服务和 LLM 功能仍取决于系统及 API。
- 单设备模式，未实现跨设备发现与多条件通用规则。固件与 V0.4 完全相同，无需因界面更新重新烧录 V0.4 设备。
- 停止按钮不能中断正在进行的通信请求；不要将它视为硬件急停。电机应有独立断电措施，真实电机默认关闭。

## V0.4 历史更新

- 增加 ESP32 本地模拟量阈值规则：当 ADC 毫伏值达到用户设定条件时，自动点亮板载 LED。
- 支持高于阈值 / 低于阈值两种触发方向，并配置 0–500 mV 回差，避免输入在阈值附近抖动导致 LED 频繁闪烁。
- 规则每 100 ms 在 ESP32 本地采样与执行，不依赖手机保持连接或 LLM 持续在线。
- 安卓端新增阈值、回差、方向、启用和关闭控制；AI 工具由设备能力声明动态生成。
- 手动开关 LED、闪烁或全局停止会取消自动化规则，避免手动控制与规则互相争抢。
- 规则仅保存在 ESP32 RAM 中，断电或重启后自动清除；这是刻意的安全边界，避免设备重启后意外恢复动作。
- 局域网控制改为 fail-closed：无法确定到 ESP32 的本地 Wi-Fi 路由时直接阻止请求，不再回退到系统默认网络。

## V0.3 本轮更新

- 将通信载荷升级为 LIP v2：从单一整数 `value` 改为结构化 `args`，支持布尔值、多个整数参数和严格参数校验。
- 新增 RGB 三通道控制、标准位置舵机角度控制、模拟输入采样；增加有时限的直流电机驱动接口。
- ESP32 在 `describe` 时报告当前固件实际启用的能力；安卓端只从本地审核过的操作白名单中生成 LLM 工具，并验证参数结构、类型和范围。
- 直流电机能力默认关闭；启用后限制 PWM 指令幅度为 ±120/255，单次动作 100–2000 ms，到期自动停止；反向前必须先停机。
- 持久状态动作执行后进行独立 `get_state` 回读；ADC 采样和短时电机动作使用即时回执，不把瞬时量误当成持久状态。
- 安卓界面新增 RGB、舵机、模拟输入、电机的快捷演示按钮。

## 1. 系统架构

```text
手机麦克风 / 文字
        │
        ▼
Android SpeechRecognizer
        │ 识别文本，用户确认后执行
        ▼
LLM Chat Completions + Function Calling
        │
        ▼
本地能力白名单 + 参数范围校验
        │
        ├── BLE GATT ──────┐
        └── Wi-Fi HTTP ────┤
                          ▼
                     ESP32 固件
                          │
             ┌────────────┼────────────┐
             ▼            ▼            ▼
          LED/RGB       Servo      Sensor / Motor
             │            │            │
             └────────────┴────────────┘
                          │
                          ▼
                  执行回执 / 状态回读
```

手机负责语音识别和大模型推理；ESP32 负责本地硬件 I/O。ESP32 不需要运行 LLM，也不需要接入互联网。蓝牙控制时，手机可继续使用互联网访问模型；局域网模式下，手机连接 ESP32 热点或与 ESP32 处于同一局域网。

## 2. 当前支持的设备能力

| 操作 | 参数 | 作用 | 默认状态 |
|---|---|---|---|
| `set_led` | `enabled: boolean` | GPIO2 指示灯开关 | 启用 |
| `blink` | `hz: 1..5` | 指示灯闪烁 | 启用 |
| `set_rgb` | `r/g/b: 0..255` | RGB LED 颜色与亮度 | 启用 |
| `set_servo` | `angle: 0..180` | 标准位置舵机角度 | 启用 |
| `read_analog` | 无 | 读取 ADC 原始值和毫伏 | 启用 |
| `set_analog_led_rule` | `enabled`、`threshold_mv`、`above`、`hysteresis_mv` | 在 ESP32 本地按模拟量阈值控制板载 LED | 启用（ADC 开启时） |
| `set_motor` | `speed: -120..120`, `duration_ms: 100..2000` | 通过 TB6612FNG 控制低压直流电机 | **默认关闭** |
| `get_state` | 无 | 查询设备状态 | 启用 |
| `stop` | 无 | 关闭单色 LED 与 RGB、停止闪烁和电机，并关闭本地自动化规则 | 启用 |

`describe` 只发布当前编译配置启用的可选能力。安卓端对未知操作不生成工具；对已知操作，设备声明的参数范围只能收窄，不能扩大应用内的安全上限。

注意：`read_analog` 仅返回 ADC 原始值和校准毫伏值。接入热敏电阻、光敏电阻等器件后，必须添加对应的换算与校准逻辑，不能把 ADC 数值直接描述为温度、距离或照度。

## 3. 推荐硬件与接线

### 基础演示 BOM

| 器件 | 数量 | 说明 |
|---|---:|---|
| 经典 ESP32 DevKit | 1 | ESP32-WROOM-32，带 Wi-Fi + BLE |
| RGB LED（共阴极优先） | 1 | 三个颜色通道分别串联限流电阻 |
| 220–330 Ω 电阻 | 3–4 | RGB 三通道及板载/外接 LED 限流 |
| 9g 标准位置舵机 | 1 | 例如 SG90；舵机单独供电 |
| 10 kΩ 电位器 | 1 | 作为最简单的模拟量输入演示 |
| TB6612FNG 驱动板 + 小直流电机 | 可选 | 仅在启用电机能力时使用 |
| 稳压 5 V 电源 | 1 | 给舵机供电，电流能力按舵机负载选取 |

### 默认引脚映射（经典 ESP32 DevKit）

| 功能 | ESP32 GPIO | 接线注意 |
|---|---:|---|
| 单色 LED | 2 | GPIO2 → 220–330 Ω → LED 正极，LED 负极接 GND；板载 LED 极性因板型而异 |
| RGB 红 / 绿 / 蓝 | 25 / 26 / 27 | 每个颜色通道各串 220–330 Ω；共阴极接 GND |
| 舵机 PWM 信号 | 18 | 只接信号线；舵机 V+ 使用独立稳压 5 V，GND 与 ESP32 共地 |
| 电位器滑动端 | 34 | 电位器两端接 3V3 与 GND，滑动端接 GPIO34；禁止接 5 V |
| TB6612 AIN1 / AIN2 | 16 / 17 | 方向控制 |
| TB6612 PWMA | 19 | PWM 调速 |
| TB6612 STBY | 23 | 高电平使能驱动，低电平待机 |

**重要电气安全：**

- 不要把舵机、电机直接接到 ESP32 GPIO；GPIO 只输出逻辑信号。
- 舵机和电机应使用符合器件规格的独立电源，且与 ESP32 共地。不要从 ESP32 的 3V3 引脚给舵机或电机供电。
- TB6612FNG 的逻辑电源 VCC 接 3.3 V，VM 接符合电机额定值的电机电源，GND 共地；电机接驱动板 A 输出端。不要把电机接到 ESP32 引脚。
- GPIO34 是 ADC 输入脚。输入电压不得超过开发板允许范围；本演示只使用 0–3.3 V 电位器输入，且建议控制在 ADC 可测范围内。
- 初次测试先不装电机，只接 LED / RGB / 电位器；确认控制链路和供电稳定后再安装舵机与驱动板。

## 4. 固件配置与烧录

1. 安装 Arduino IDE，安装 Espressif Arduino-ESP32 **3.0.7** 核心和 ArduinoJson **7.4.2**。
2. 打开 `firmware/LingIoT/LingIoT.ino`，选择 `ESP32 Dev Module`。
3. 修改 `TOKEN` 和 `AP_PASSWORD`。`TOKEN` 使用 8–64 位字母、数字、下划线或连字符；热点密码至少 8 位。不要把真实凭据提交到公共仓库。
4. 根据接线修改功能宏：

```cpp
#define LINGIOT_ENABLE_RGB 1
#define LINGIOT_ENABLE_SERVO 1
#define LINGIOT_ENABLE_MOTOR 0  // 完成 TB6612FNG 接线后才改为 1
#define LINGIOT_ENABLE_ANALOG 1
#define LINGIOT_RGB_COMMON_ANODE 0
```

5. 烧录后打开 115200 波特率串口监视器。默认热点名为 `LingIoT-Demo`，串口会输出 AP 地址（通常为 `192.168.4.1`）。
6. 若要连接手机热点或路由器，填写 `STA_SSID` 和 `STA_PASSWORD`；ESP32 经典版通常使用 2.4 GHz Wi-Fi。

RGB 共阳极模块需要将 `LINGIOT_RGB_COMMON_ANODE` 改为 `1`。舵机脉冲范围当前按 500–2500 μs 映射到 0–180°；若舵机机械行程较小，应收窄角度或脉宽，避免顶死。

## 5. 安卓 App 构建

- Android Studio 打开 `android/`。
- JDK 17、Android SDK 35、Android Gradle Plugin 8.7.3、Gradle 8.9。
- 项目当前未附 Gradle Wrapper。可在安装 Gradle 8.9 后执行：

```bash
gradle -p android testDebugUnitTest assembleDebug
```

- 生成的调试 APK 预期路径：`android/app/build/outputs/apk/debug/app-debug.apk`。
- Android 12 及以上需要附近设备权限；Android 8–11 BLE 扫描需要位置权限。语音识别依赖手机系统提供的 SpeechRecognizer 服务。

App 操作流程：

1. 打开蓝牙，选择 BLE 模式，扫描并连接 `LingIoT-Demo`。
2. 输入与固件一致的设备访问口令，点击“读取设备能力”。
3. 先读取设备能力，再用动态控制卡片验证 LED / RGB / 舵机 / ADC；未启用的功能不会显示，也会被能力策略拒绝。
4. 如需本地自动化，在“ESP32 本地自动化”区域设置毫伏阈值、回差和触发方向，点击启用；也可以直接对 AI 说“模拟输入高于 1500 毫伏时打开 LED，低于 1400 毫伏时关闭”。
5. 填写支持 Function Calling 的模型 API 地址、模型 ID 和 API Key。
6. 点击语音按钮说出指令，核对识别文字，再点击“交给 AI 执行”。语音识别结果不会自动触发硬件动作。
7. 检查执行日志中的设备回执和状态回读。

## 6. LIP v2 通信协议

请求统一采用 JSON。BLE 以 LF（`0x0A`）结束帧，单帧上限 1024 字节；Wi-Fi 使用 HTTP POST `/command`。两种链路使用同一协议和同一固件处理器。

设备能力查询：

```json
{"v":2,"id":"req-001","token":"YOUR_TOKEN","op":"describe","args":{}}
```

能力声明示例（可选设备按固件编译宏出现或省略）：

```json
{
  "profile":"demo-board-v1",
  "ops":{
    "set_led":{"enabled":{"type":"boolean"}},
    "blink":{"hz":{"type":"integer","min":1,"max":5}},
    "set_rgb":{"r":{"type":"integer","min":0,"max":255},"g":{"type":"integer","min":0,"max":255},"b":{"type":"integer","min":0,"max":255}},
    "set_servo":{"angle":{"type":"integer","min":0,"max":180}},
    "read_analog":{},
    "set_analog_led_rule":{"enabled":{"type":"boolean"},"threshold_mv":{"type":"integer","min":0,"max":3300},"above":{"type":"boolean"},"hysteresis_mv":{"type":"integer","min":0,"max":500}},
    "get_state":{},
    "stop":{}
  }
}
```

配置本地阈值规则（ADC 高于 1500 mV 时点亮 LED，低于 1400 mV 后关闭）：

```json
{"v":2,"id":"req-rule-001","token":"YOUR_TOKEN","op":"set_analog_led_rule","args":{"enabled":true,"threshold_mv":1500,"above":true,"hysteresis_mv":100}}
```

规则由 ESP32 每 100 ms 采样执行；手机断开后仍继续工作。`enabled=false` 会关闭规则并关闭 LED；手动 `set_led`、`blink` 或 `stop` 也会取消规则。规则不会写入 NVS，ESP32 重启后回到关闭状态。

控制 RGB 灯：

```json
{"v":2,"id":"req-002","token":"YOUR_TOKEN","op":"set_rgb","args":{"r":0,"g":80,"b":255}}
```

控制舵机：

```json
{"v":2,"id":"req-003","token":"YOUR_TOKEN","op":"set_servo","args":{"angle":90}}
```

执行结果会回传相同 `id`、`ok` 和 `state`。安卓端会校验协议版本、请求 ID、操作能力、参数类型及范围。持久状态动作还会再发起一次独立状态查询；超时后不会自动重发，避免不确定状态下重复执行。

## 7. 安全边界与已知限制

- 当前是受控实验演示，不是生产级 IoT 安全系统。BLE 尚未强制配对加密，HTTP 局域网通信为明文，设备 Token 只是应用层访问检查，不具备完整的认证、机密性和重放保护。
- 不要将设备热点或 HTTP 服务暴露到公网；不要控制市电、门锁、加热器、无人设备或其他可能造成人身/财产风险的负载。
- LLM 输出始终是不可信输入；所有操作必须经过安卓本地白名单与参数校验，ESP32 固件也有独立校验。
- 电机功能默认关闭。启用前必须接 TB6612FNG 和低压小电机，确认急停/断电方式；每次动作最多 2 秒，固件自动停机。该软件超时不能替代物理急停；建议 TB6612FNG 的 STBY 加外部下拉电阻，使 ESP32 复位时驱动默认处于待机状态。
- 舵机状态回读表示固件设定的目标角度，不是外部角度传感器测量值；LED/RGB 状态同样是输出寄存器状态，不是光学测量。
- 当前仅实现一条固定动作的本地阈值规则（模拟输入 → 板载 LED），还没有通用规则编排器、设备配对向导、设备列表持久化、后台常驻服务、多设备调度或传感器标定工具。
- API Key 仅由用户在运行时输入，不写入 ESP32 指令帧；生产部署不应把供应商 API Key 固化在 APK，应使用有鉴权、限流和审计的受控服务端代理。

## 8. 测试与交付状态

已在当前开发环境运行：

```bash
bash tests/run.sh
```

当前结果：
- C++ 硬件无关控制状态机：通过，覆盖阈值上下沿、回差保持、规则关闭及手动覆盖。
- Java BLE 分帧与私有 IPv4 校验：通过。
- Java 能力白名单与参数策略：44 项断言通过。

尚未在当前环境完成：Android Gradle 单元测试 / APK 编译、Arduino ESP32 固件编译、真实 ESP32 烧录、手机 BLE / Wi-Fi 实机联调和真实模型 API 联调。仓库包含 GitHub Actions 构建工作流，推送到 GitHub 后可执行完整构建；在工作流实际成功前，不应把 APK 或固件视为已验证交付物。

## 9. 开源组件复用

本项目优先复用成熟开源组件，而非自行实现底层协议栈：

- Arduino-ESP32：ESP32 GPIO、LEDC PWM、ADC、Wi-Fi、BLE 适配层。
- ArduinoJson：嵌入式 JSON 解析与序列化。
- Android SpeechRecognizer：系统语音识别接口。
- Android BluetoothGatt：原生 BLE GATT 客户端。
- OpenAI 兼容 Chat Completions Function Calling：模型工具调用接口；具体 API 兼容性取决于服务商。

参考：
- https://github.com/espressif/arduino-esp32
- https://docs.espressif.com/projects/arduino-esp32/en/latest/api/ledc.html
- https://docs.espressif.com/projects/arduino-esp32/en/latest/api/adc.html
- https://github.com/bblanchon/ArduinoJson
- https://developer.android.com/reference/android/speech/SpeechRecognizer
- https://developer.android.com/develop/connectivity/bluetooth/ble/ble-overview
- https://developers.openai.com/api/docs/guides/function-calling
