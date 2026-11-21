#include "../firmware/LingIoT/control.h"
#include <cassert>
#include <cstdint>
#include <iostream>

int main() {
  Control c;
  assert(c.setLed(true) && c.led && c.blinkHz == 0);
  assert(c.blink(2, 100));
  c.tick(349); assert(c.led);
  c.tick(350); assert(!c.led);
  c.tick(600); assert(c.led);
  assert(!c.blink(0, 600));
  assert(!c.blink(6, 600));

  assert(c.setRgb(0, 128, 255));
  assert(c.red == 0 && c.green == 128 && c.blue == 255);
  assert(!c.setRgb(-1, 0, 0));
  assert(!c.setRgb(0, 256, 0));
  assert(c.setServo(90) && c.servoAngle == 90);
  assert(c.setServo(0) && c.setServo(180));
  assert(!c.setServo(181));

  assert(c.setMotor(100, 500, 1000));
  assert(c.motorSpeed == 100);
  c.tick(1499); assert(c.motorSpeed == 100);
  assert(!c.setMotor(-120, 2000, 1499)); // Must stop before reversing direction.
  c.tick(1500); assert(c.motorSpeed == 0);
  assert(c.setMotor(-120, 2000, 2000));
  assert(c.motorSpeed == -120);
  assert(!c.setMotor(121, 500, 2000));
  assert(!c.setMotor(20, 99, 2000));
  assert(!c.setMotor(20, 2001, 2000));
  c.stop(); assert(!c.led && c.blinkHz == 0 && c.red == 0 && c.green == 0 && c.blue == 0 && c.motorSpeed == 0);

  // millis() rollover must not extend a motor pulse.
  assert(c.setMotor(50, 200, 0xfffffff0U));
  c.tick(0x000000b7U); assert(c.motorSpeed == 50);
  c.tick(0x000000b8U); assert(c.motorSpeed == 0);

  c.sampleAnalog(2048, 1650);
  assert(c.analogRaw == 2048 && c.analogMilliVolts == 1650);

  // Above-threshold rule turns on at the threshold and turns off below threshold-hysteresis.
  assert(c.setAnalogLedRule(true, 1500, true, 100));
  assert(c.analogRuleEnabled && c.led); // Current sample is already above the threshold.
  c.sampleAnalog(1600, 1600); assert(c.led);
  c.sampleAnalog(1450, 1450); assert(c.led); // Inside hysteresis band: retain state.
  c.sampleAnalog(1399, 1399); assert(!c.led);
  c.sampleAnalog(1550, 1550); assert(c.led);
  assert(!c.setAnalogLedRule(true, 3301, true, 100));
  assert(!c.setAnalogLedRule(true, 1500, true, 501));
  c.setLed(false); assert(!c.analogRuleEnabled && !c.led);

  // Below-threshold rule has symmetric hysteresis.
  assert(c.setAnalogLedRule(true, 1200, false, 100));
  c.sampleAnalog(1100, 1100); assert(c.led);
  c.sampleAnalog(1250, 1250); assert(c.led);
  c.sampleAnalog(1301, 1301); assert(!c.led);
  c.stop(); assert(!c.analogRuleEnabled && !c.led);
  assert(c.setAnalogLedRule(false, 1200, false, 100));
  assert(!c.analogRuleEnabled && !c.led);
  std::cout << "Control state machine: analog automation and hysteresis passed\n";
}
