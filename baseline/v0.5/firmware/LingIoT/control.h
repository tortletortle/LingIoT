#pragma once
#include <cstdint>

// Hardware-independent state machine. GPIO/PWM calls stay in the Arduino adapter.
struct Control {
  bool led = false;
  int blinkHz = 0;
  uint32_t lastBlinkMs = 0;

  int red = 0, green = 0, blue = 0;
  int servoAngle = 90;

  int motorSpeed = 0;
  uint32_t motorDeadlineMs = 0;

  uint16_t analogRaw = 0;
  uint16_t analogMilliVolts = 0;

  // Runtime-only local automation. It is intentionally not persisted across reboot.
  bool analogRuleEnabled = false;
  int analogRuleThresholdMv = 1500;
  bool analogRuleAbove = true;
  int analogRuleHysteresisMv = 100;

  bool setLed(bool enabled) {
    analogRuleEnabled = false;
    blinkHz = 0;
    led = enabled;
    return true;
  }

  bool blink(int hz, uint32_t now) {
    if (hz < 1 || hz > 5) return false;
    analogRuleEnabled = false;
    blinkHz = hz;
    led = true;
    lastBlinkMs = now;
    return true;
  }

  bool setRgb(int r, int g, int b) {
    if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) return false;
    red = r; green = g; blue = b;
    return true;
  }

  bool setServo(int angle) {
    if (angle < 0 || angle > 180) return false;
    servoAngle = angle;
    return true;
  }

  bool setMotor(int speed, int durationMs, uint32_t now) {
    if (speed < -120 || speed > 120 || durationMs < 100 || durationMs > 2000) return false;
    if (motorSpeed != 0 && speed != 0 && ((motorSpeed > 0) != (speed > 0))) return false;
    motorSpeed = speed;
    motorDeadlineMs = now + static_cast<uint32_t>(durationMs);
    return true;
  }

  bool setAnalogLedRule(bool enabled, int thresholdMv, bool above, int hysteresisMv) {
    if (thresholdMv < 0 || thresholdMv > 3300 || hysteresisMv < 0 || hysteresisMv > 500) return false;
    blinkHz = 0;
    analogRuleEnabled = enabled;
    analogRuleThresholdMv = thresholdMv;
    analogRuleAbove = above;
    analogRuleHysteresisMv = hysteresisMv;
    if (!enabled) {
      led = false;
    } else {
      led = false;
      updateAnalogRule();
    }
    return true;
  }

  void updateAnalogRule() {
    if (!analogRuleEnabled) return;
    const int mv = static_cast<int>(analogMilliVolts);
    if (analogRuleAbove) {
      if (!led && mv >= analogRuleThresholdMv) led = true;
      else if (led && mv < analogRuleThresholdMv - analogRuleHysteresisMv) led = false;
    } else {
      if (!led && mv <= analogRuleThresholdMv) led = true;
      else if (led && mv > analogRuleThresholdMv + analogRuleHysteresisMv) led = false;
    }
  }

  void sampleAnalog(uint16_t raw, uint16_t milliVolts) {
    analogRaw = raw;
    analogMilliVolts = milliVolts;
    updateAnalogRule();
  }

  void stop() {
    analogRuleEnabled = false;
    blinkHz = 0;
    led = false;
    red = green = blue = 0;
    motorSpeed = 0;
  }

  void tick(uint32_t now) {
    if (blinkHz > 0) {
      const uint32_t halfPeriod = 500U / static_cast<uint32_t>(blinkHz);
      const uint32_t elapsed = now - lastBlinkMs;
      const uint32_t transitions = elapsed / halfPeriod;
      if (transitions > 0) {
        if (transitions & 1U) led = !led;
        lastBlinkMs += transitions * halfPeriod;
      }
    }
    // Signed subtraction keeps the deadline check valid across millis() rollover.
    if (motorSpeed != 0 && static_cast<int32_t>(now - motorDeadlineMs) >= 0) {
      motorSpeed = 0;
    }
  }
};
