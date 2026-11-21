#include <Arduino.h>
#include <WiFi.h>
#include <WebServer.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <ArduinoJson.h>
#include "control.h"

// Demo credentials only. Change both before flashing; never publish real credentials.
static const char* TOKEN = "CHANGE_ME_123";
static const char* AP_PASSWORD = "CHANGE_AP_123";
static const char* STA_SSID = "";      // Optional phone hotspot / router, 2.4 GHz.
static const char* STA_PASSWORD = "";

// ESP32 DevKit V1 pin map. Check your exact board before wiring.
static constexpr int LED_PIN = 2;
static constexpr int RGB_R_PIN = 25, RGB_G_PIN = 26, RGB_B_PIN = 27;
static constexpr int SERVO_PIN = 18;
static constexpr int MOTOR_PWM_PIN = 19, MOTOR_IN1_PIN = 16, MOTOR_IN2_PIN = 17, MOTOR_STBY_PIN = 23;
static constexpr int ANALOG_PIN = 34;  // ADC1 input-only pin; never apply more than 3.3 V.

// Enable optional features only after wiring the corresponding hardware.
#ifndef LINGIOT_ENABLE_RGB
#define LINGIOT_ENABLE_RGB 1
#endif
#ifndef LINGIOT_ENABLE_SERVO
#define LINGIOT_ENABLE_SERVO 1
#endif
#ifndef LINGIOT_ENABLE_MOTOR
#define LINGIOT_ENABLE_MOTOR 0
#endif
#ifndef LINGIOT_ENABLE_ANALOG
#define LINGIOT_ENABLE_ANALOG 1
#endif
#ifndef LINGIOT_RGB_COMMON_ANODE
#define LINGIOT_RGB_COMMON_ANODE 0
#endif

static const char* SERVICE_UUID = "c48c0001-8444-4348-a923-342bc1eaa001";
static const char* RX_UUID = "c48c0002-8444-4348-a923-342bc1eaa001";
static const char* TX_UUID = "c48c0003-8444-4348-a923-342bc1eaa001";

struct Frame { char data[1025]; };
QueueHandle_t queueHandle = nullptr;
BLECharacteristic* txCharacteristic = nullptr;
WebServer server(80);
Control ctl;

static bool intArg(JsonObjectConst args, const char* name, int& value) {
  JsonVariantConst v = args[name];
  if (!v.is<int>()) return false;
  value = v.as<int>();
  return true;
}

static void addIntegerParam(JsonObject operation, const char* name, int minValue, int maxValue) {
  JsonObject p = operation[name].to<JsonObject>();
  p["type"] = "integer";
  p["min"] = minValue;
  p["max"] = maxValue;
}

static void addBooleanParam(JsonObject operation, const char* name) {
  JsonObject p = operation[name].to<JsonObject>();
  p["type"] = "boolean";
}

static void addCapabilities(JsonObject& out) {
  JsonObject caps = out["capabilities"].to<JsonObject>();
  caps["profile"] = "demo-board-v1";
  JsonObject ops = caps["ops"].to<JsonObject>();

  JsonObject led = ops["set_led"].to<JsonObject>();
  addBooleanParam(led, "enabled");
  JsonObject blink = ops["blink"].to<JsonObject>();
  addIntegerParam(blink, "hz", 1, 5);

#if LINGIOT_ENABLE_RGB
  JsonObject rgb = ops["set_rgb"].to<JsonObject>();
  addIntegerParam(rgb, "r", 0, 255);
  addIntegerParam(rgb, "g", 0, 255);
  addIntegerParam(rgb, "b", 0, 255);
#endif

#if LINGIOT_ENABLE_SERVO
  JsonObject servo = ops["set_servo"].to<JsonObject>();
  addIntegerParam(servo, "angle", 0, 180);
#endif

#if LINGIOT_ENABLE_MOTOR
  JsonObject motor = ops["set_motor"].to<JsonObject>();
  addIntegerParam(motor, "speed", -120, 120);
  addIntegerParam(motor, "duration_ms", 100, 2000);
#endif

#if LINGIOT_ENABLE_ANALOG
  ops["read_analog"].to<JsonObject>();
  JsonObject rule = ops["set_analog_led_rule"].to<JsonObject>();
  addBooleanParam(rule, "enabled");
  addIntegerParam(rule, "threshold_mv", 0, 3300);
  addBooleanParam(rule, "above");
  addIntegerParam(rule, "hysteresis_mv", 0, 500);
#endif
  ops["get_state"].to<JsonObject>();
  ops["stop"].to<JsonObject>();
}

static void sampleAnalog() {
#if LINGIOT_ENABLE_ANALOG
  const uint16_t raw = static_cast<uint16_t>(analogRead(ANALOG_PIN));
  const uint16_t mv = static_cast<uint16_t>(analogReadMilliVolts(ANALOG_PIN));
  ctl.sampleAnalog(raw, mv);
#endif
}

static void appendState(JsonObject& out) {
  sampleAnalog();
  JsonObject state = out["state"].to<JsonObject>();
  state["led"] = ctl.led;
  state["blink_hz"] = ctl.blinkHz;
#if LINGIOT_ENABLE_RGB
  JsonObject rgb = state["rgb"].to<JsonObject>();
  rgb["r"] = ctl.red; rgb["g"] = ctl.green; rgb["b"] = ctl.blue;
#endif
#if LINGIOT_ENABLE_SERVO
  JsonObject servo = state["servo"].to<JsonObject>();
  servo["angle"] = ctl.servoAngle;
#endif
#if LINGIOT_ENABLE_MOTOR
  JsonObject motor = state["motor"].to<JsonObject>();
  motor["speed"] = ctl.motorSpeed;
  motor["active"] = ctl.motorSpeed != 0;
#endif
#if LINGIOT_ENABLE_ANALOG
  JsonObject analog = state["analog"].to<JsonObject>();
  analog["raw"] = ctl.analogRaw;
  analog["millivolts"] = ctl.analogMilliVolts;
  JsonObject rule = state["analog_rule"].to<JsonObject>();
  rule["enabled"] = ctl.analogRuleEnabled;
  rule["threshold_mv"] = ctl.analogRuleThresholdMv;
  rule["above"] = ctl.analogRuleAbove;
  rule["hysteresis_mv"] = ctl.analogRuleHysteresisMv;
#endif
}

static void applyHardware() {
  digitalWrite(LED_PIN, ctl.led ? HIGH : LOW);

#if LINGIOT_ENABLE_RGB
  int r = ctl.red, g = ctl.green, b = ctl.blue;
#if LINGIOT_RGB_COMMON_ANODE
  r = 255 - r; g = 255 - g; b = 255 - b;
#endif
  ledcWrite(RGB_R_PIN, static_cast<uint32_t>(r));
  ledcWrite(RGB_G_PIN, static_cast<uint32_t>(g));
  ledcWrite(RGB_B_PIN, static_cast<uint32_t>(b));
#endif

#if LINGIOT_ENABLE_SERVO
  const uint32_t pulseUs = 500U + (static_cast<uint32_t>(ctl.servoAngle) * 2000U) / 180U;
  const uint32_t duty = (pulseUs * 65535UL) / 20000UL;
  ledcWrite(SERVO_PIN, duty);
#endif

#if LINGIOT_ENABLE_MOTOR
  const int speed = ctl.motorSpeed;
  if (speed == 0) {
    ledcWrite(MOTOR_PWM_PIN, 0);
    digitalWrite(MOTOR_IN1_PIN, LOW);
    digitalWrite(MOTOR_IN2_PIN, LOW);
    digitalWrite(MOTOR_STBY_PIN, LOW);
  } else {
    digitalWrite(MOTOR_STBY_PIN, HIGH);
    digitalWrite(MOTOR_IN1_PIN, speed > 0 ? HIGH : LOW);
    digitalWrite(MOTOR_IN2_PIN, speed > 0 ? LOW : HIGH);
    ledcWrite(MOTOR_PWM_PIN, static_cast<uint32_t>(abs(speed)));
  }
#endif
}

static bool dispatch(const char* op, JsonObjectConst args) {
  if (strcmp(op, "set_led") == 0) {
    if (args.size() != 1 || !args["enabled"].is<bool>()) return false;
    return ctl.setLed(args["enabled"].as<bool>());
  }
  if (strcmp(op, "blink") == 0) {
    int hz;
    return args.size() == 1 && intArg(args, "hz", hz) && ctl.blink(hz, millis());
  }
#if LINGIOT_ENABLE_RGB
  if (strcmp(op, "set_rgb") == 0) {
    int r, g, b;
    return args.size() == 3 && intArg(args, "r", r) && intArg(args, "g", g) && intArg(args, "b", b) && ctl.setRgb(r, g, b);
  }
#endif
#if LINGIOT_ENABLE_SERVO
  if (strcmp(op, "set_servo") == 0) {
    int angle;
    return args.size() == 1 && intArg(args, "angle", angle) && ctl.setServo(angle);
  }
#endif
#if LINGIOT_ENABLE_MOTOR
  if (strcmp(op, "set_motor") == 0) {
    int speed, durationMs;
    return args.size() == 2 && intArg(args, "speed", speed) && intArg(args, "duration_ms", durationMs) && ctl.setMotor(speed, durationMs, millis());
  }
#endif
#if LINGIOT_ENABLE_ANALOG
  if (strcmp(op, "read_analog") == 0 && args.size() == 0) {
    sampleAnalog();
    return true;
  }
  if (strcmp(op, "set_analog_led_rule") == 0) {
    bool enabled, above;
    int thresholdMv, hysteresisMv;
    const bool valid = args.size() == 4 && args["enabled"].is<bool>() && args["above"].is<bool>() &&
      intArg(args, "threshold_mv", thresholdMv) && intArg(args, "hysteresis_mv", hysteresisMv);
    if (!valid) return false;
    enabled = args["enabled"].as<bool>();
    above = args["above"].as<bool>();
    if (!ctl.setAnalogLedRule(enabled, thresholdMv, above, hysteresisMv)) return false;
    sampleAnalog();
    return true;
  }
#endif
  if (strcmp(op, "get_state") == 0 && args.size() == 0) return true;
  if (strcmp(op, "stop") == 0 && args.size() == 0) {
    ctl.stop();
    return true;
  }
  return false;
}

static String handleCommand(const String& input) {
  JsonDocument doc;
  JsonDocument out;
  out["v"] = 2;
  out["id"] = "";
  out["ok"] = false;

  if (input.length() > 1024 || deserializeJson(doc, input, DeserializationOption::NestingLimit(5))) {
    out["error"] = "invalid_json";
  } else {
    const char* id = doc["id"] | "";
    const char* op = doc["op"] | "";
    const char* token = doc["token"] | "";
    if (strlen(id) <= 64) out["id"] = id;
    JsonObjectConst args = doc["args"].as<JsonObjectConst>();

    if (!doc.is<JsonObject>() || doc.size() != 5 || !doc["v"].is<int>() || doc["v"].as<int>() != 2 ||
        strlen(id) == 0 || strlen(id) > 64 || strlen(op) == 0 || strlen(op) > 32 || args.isNull()) {
      out["error"] = "invalid_request";
    } else if (strcmp(token, TOKEN) != 0) {
      out["error"] = "unauthorized";
    } else if (strcmp(op, "describe") == 0) {
      if (args.size() != 0) out["error"] = "invalid_args";
      else { out["ok"] = true; JsonObject response = out.as<JsonObject>(); addCapabilities(response); }
    } else if (!dispatch(op, args)) {
      out["error"] = "unsupported_or_range";
    } else {
      out["ok"] = true;
      JsonObject response = out.as<JsonObject>();
      appendState(response);
      applyHardware();
    }
  }
  String result;
  serializeJson(out, result);
  return result;
}

class RxCallbacks : public BLECharacteristicCallbacks {
  Frame frame{};
  size_t used = 0;
  bool overflow = false;
 public:
  void reset() { used = 0; overflow = false; }
  void onWrite(BLECharacteristic* characteristic) override {
    auto value = characteristic->getValue();
    for (size_t i = 0; i < value.length(); ++i) {
      const char ch = value[i];
      if (ch == '\n') {
        if (!overflow && used > 0) { frame.data[used] = '\0'; xQueueSend(queueHandle, &frame, 0); }
        reset();
      } else if (!overflow) {
        if (used >= 1024 || ch == '\0') { overflow = true; used = 0; }
        else frame.data[used++] = ch;
      }
    }
  }
};

RxCallbacks receiver;
class ServerCallbacks : public BLEServerCallbacks {
  void onDisconnect(BLEServer*) override {
    receiver.reset();
    BLEDevice::startAdvertising();
  }
};

void setup() {
  Serial.begin(115200);
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

#if LINGIOT_ENABLE_RGB
  if (!ledcAttach(RGB_R_PIN, 5000, 8) || !ledcAttach(RGB_G_PIN, 5000, 8) || !ledcAttach(RGB_B_PIN, 5000, 8))
    Serial.println("RGB PWM attach failed; check pin map");
#endif
#if LINGIOT_ENABLE_SERVO
  if (!ledcAttach(SERVO_PIN, 50, 16)) Serial.println("Servo PWM attach failed");
#endif
#if LINGIOT_ENABLE_MOTOR
  pinMode(MOTOR_IN1_PIN, OUTPUT); pinMode(MOTOR_IN2_PIN, OUTPUT); pinMode(MOTOR_STBY_PIN, OUTPUT);
  if (!ledcAttach(MOTOR_PWM_PIN, 20000, 8)) Serial.println("Motor PWM attach failed");
  digitalWrite(MOTOR_STBY_PIN, LOW);
#endif
#if LINGIOT_ENABLE_ANALOG
  pinMode(ANALOG_PIN, INPUT);
  analogReadResolution(12);
  analogSetPinAttenuation(ANALOG_PIN, ADC_11db);
#endif
  applyHardware();

  queueHandle = xQueueCreate(4, sizeof(Frame));
  if (!queueHandle) { Serial.println("BLE queue init failed"); return; }

  WiFi.mode(WIFI_AP_STA);
  WiFi.softAP("LingIoT-Demo", AP_PASSWORD);
  Serial.print("AP IP: "); Serial.println(WiFi.softAPIP());
  if (strlen(STA_SSID) > 0) WiFi.begin(STA_SSID, STA_PASSWORD);

  server.on("/command", HTTP_POST, []() {
    const String response = handleCommand(server.arg("plain"));
    server.send(200, "application/json", response);
  });
  server.begin();

  BLEDevice::init("LingIoT-Demo");
  BLEServer* bleServer = BLEDevice::createServer();
  bleServer->setCallbacks(new ServerCallbacks());
  BLEService* service = bleServer->createService(SERVICE_UUID);
  BLECharacteristic* rx = service->createCharacteristic(RX_UUID, BLECharacteristic::PROPERTY_WRITE);
  rx->setCallbacks(&receiver);
  txCharacteristic = service->createCharacteristic(TX_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  txCharacteristic->addDescriptor(new BLE2902());
  service->start();
  BLEAdvertising* advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);
  advertising->setScanResponse(true);
  BLEDevice::startAdvertising();
}

void loop() {
  if (!queueHandle) { delay(1000); return; }
  static bool printedSta = false;
  if (!printedSta && WiFi.status() == WL_CONNECTED) {
    Serial.print("STA IP: "); Serial.println(WiFi.localIP());
    printedSta = true;
  }

  const uint32_t now = millis();
  ctl.tick(now);
#if LINGIOT_ENABLE_ANALOG
  static uint32_t lastAnalogSampleMs = 0;
  if (now - lastAnalogSampleMs >= 100U) {
    sampleAnalog();
    lastAnalogSampleMs = now;
  }
#endif
  applyHardware();
  server.handleClient();

  Frame frame;
  if (xQueueReceive(queueHandle, &frame, 0) == pdTRUE) {
    frame.data[1024] = '\0';
    const String response = handleCommand(String(frame.data)) + "\n";
    for (size_t offset = 0; offset < response.length(); offset += 20) {
      const size_t count = min(static_cast<size_t>(20), response.length() - offset);
      txCharacteristic->setValue(reinterpret_cast<uint8_t*>(const_cast<char*>(response.c_str() + offset)), count);
      txCharacteristic->notify();
      ctl.tick(millis());
      applyHardware();
      delay(15);
    }
  }
  ctl.tick(millis());
  applyHardware();
  delay(2);
}
