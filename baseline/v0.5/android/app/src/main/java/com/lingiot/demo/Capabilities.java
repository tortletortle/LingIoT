package com.lingiot.demo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Local allowlist and parameter policy for device-advertised actions. */
public final class Capabilities {
    public static final class Param {
        private final String type;
        private final int min;
        private final int max;

        public Param(String type, int min, int max) {
            if (!"integer".equals(type) && !"boolean".equals(type)) throw new IllegalArgumentException("未知参数类型");
            if ("integer".equals(type) && min > max) throw new IllegalArgumentException("反向参数范围");
            this.type = type;
            this.min = min;
            this.max = max;
        }
        public static Param integer(int min, int max) { return new Param("integer", min, max); }
        public static Param bool() { return new Param("boolean", 0, 0); }
        public String type() { return type; }
        public int min() { return min; }
        public int max() { return max; }
    }

    public static final class Action {
        private final String op;
        private final Map<String, Object> args;
        private Action(String op, Map<String, Object> args) {
            this.op = op;
            this.args = Collections.unmodifiableMap(new LinkedHashMap<>(args));
        }
        public String op() { return op; }
        public Map<String, Object> args() { return args; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Action)) return false;
            Action a = (Action) other;
            return op.equals(a.op) && args.equals(a.args);
        }
        @Override public int hashCode() { return 31 * op.hashCode() + args.hashCode(); }
        @Override public String toString() { return op + args; }
    }

    private static final Map<String, Map<String, Param>> LOCAL_POLICY;
    static {
        Map<String, Map<String, Param>> p = new LinkedHashMap<>();
        p.put("set_led", params("enabled", Param.bool()));
        p.put("blink", params("hz", Param.integer(1, 5)));
        p.put("set_rgb", params("r", Param.integer(0, 255), "g", Param.integer(0, 255), "b", Param.integer(0, 255)));
        p.put("set_servo", params("angle", Param.integer(0, 180)));
        p.put("set_motor", params("speed", Param.integer(-120, 120), "duration_ms", Param.integer(100, 2000)));
        p.put("read_analog", Collections.emptyMap());
        p.put("set_analog_led_rule", params("enabled", Param.bool(), "threshold_mv", Param.integer(0, 3300),
                "above", Param.bool(), "hysteresis_mv", Param.integer(0, 500)));
        p.put("get_state", Collections.emptyMap());
        p.put("stop", Collections.emptyMap());
        LOCAL_POLICY = Collections.unmodifiableMap(p);
    }

    private final Map<String, Map<String, Param>> operations;

    public Capabilities(String profile, Map<String, Map<String, Param>> advertised) {
        if (!"demo-board-v1".equals(profile)) throw new IllegalArgumentException("不支持的设备类型");
        if (advertised == null || advertised.size() > 16) throw new IllegalArgumentException("能力数量过多");
        Map<String, Map<String, Param>> result = new TreeMap<>();
        for (Map.Entry<String, Map<String, Param>> entry : advertised.entrySet()) {
            String name = entry.getKey();
            Map<String, Param> local = LOCAL_POLICY.get(name);
            if (local == null) continue; // Unknown operations never become model tools.
            Map<String, Param> actual = entry.getValue();
            if (actual == null || !actual.keySet().equals(local.keySet())) throw new IllegalArgumentException("设备参数结构不匹配：" + name);
            Map<String, Param> narrowed = new TreeMap<>();
            for (String paramName : local.keySet()) {
                Param bound = local.get(paramName);
                Param declared = actual.get(paramName);
                if (declared == null || !bound.type().equals(declared.type())) throw new IllegalArgumentException("设备参数类型不匹配：" + name + "/" + paramName);
                if ("integer".equals(bound.type())) {
                    if (declared.min() < bound.min() || declared.max() > bound.max()) throw new IllegalArgumentException("设备声明越过本地安全范围：" + name + "/" + paramName);
                }
                narrowed.put(paramName, declared);
            }
            result.put(name, Collections.unmodifiableMap(narrowed));
        }
        if (!result.containsKey("get_state") || !result.containsKey("stop")) throw new IllegalArgumentException("设备缺少状态查询或停止能力，拒绝控制");
        if (result.containsKey("set_analog_led_rule") && !result.containsKey("read_analog"))
            throw new IllegalArgumentException("模拟量自动化依赖 read_analog 能力");
        operations = Collections.unmodifiableMap(result);
    }

    private static Map<String, Param> params(Object... pairs) {
        Map<String, Param> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) out.put((String) pairs[i], (Param) pairs[i + 1]);
        return Collections.unmodifiableMap(out);
    }

    public Map<String, Map<String, Param>> operations() { return operations; }
    public static boolean isKnownOperation(String op) { return LOCAL_POLICY.containsKey(op); }

    @SuppressWarnings("unchecked")
    public boolean validState(Map<String, Object> state) {
        if (state == null || !(state.get("led") instanceof Boolean)) return false;
        try {
            long hz = number(state, "blink_hz");
            if (hz < 0 || hz > 5) return false;
            if (operations.containsKey("set_rgb")) {
                Map<String, Object> rgb = (Map<String, Object>) state.get("rgb");
                if (rgb == null || number(rgb, "r") < 0 || number(rgb, "r") > 255 || number(rgb, "g") < 0 || number(rgb, "g") > 255 || number(rgb, "b") < 0 || number(rgb, "b") > 255) return false;
            }
            if (operations.containsKey("set_servo")) {
                Map<String, Object> servo = (Map<String, Object>) state.get("servo");
                if (servo == null || number(servo, "angle") < 0 || number(servo, "angle") > 180) return false;
            }
            if (operations.containsKey("set_motor")) {
                Map<String, Object> motor = (Map<String, Object>) state.get("motor");
                if (motor == null || number(motor, "speed") < -120 || number(motor, "speed") > 120 || !(motor.get("active") instanceof Boolean) || Boolean.TRUE.equals(motor.get("active")) != (number(motor, "speed") != 0)) return false;
            }
            if (operations.containsKey("read_analog")) {
                Map<String, Object> analog = (Map<String, Object>) state.get("analog");
                if (analog == null || number(analog, "raw") < 0 || number(analog, "raw") > 4095 || number(analog, "millivolts") < 0 || number(analog, "millivolts") > 3300) return false;
            }
            if (operations.containsKey("set_analog_led_rule")) {
                Map<String, Object> rule = (Map<String, Object>) state.get("analog_rule");
                if (rule == null || !(rule.get("enabled") instanceof Boolean) || !(rule.get("above") instanceof Boolean) ||
                        number(rule, "threshold_mv") < 0 || number(rule, "threshold_mv") > 3300 ||
                        number(rule, "hysteresis_mv") < 0 || number(rule, "hysteresis_mv") > 500) return false;
            }
            return true;
        } catch (RuntimeException ex) { return false; }
    }

    public Action validate(String op, Map<String, Object> args) {
        Map<String, Param> params = operations.get(op);
        if (params == null || args == null || !args.keySet().equals(params.keySet())) throw new IllegalArgumentException("设备不支持该操作或参数结构不正确：" + op);
        for (Map.Entry<String, Param> entry : params.entrySet()) {
            Object value = args.get(entry.getKey());
            Param param = entry.getValue();
            if ("boolean".equals(param.type())) {
                if (!(value instanceof Boolean)) throw new IllegalArgumentException("参数必须为布尔值：" + entry.getKey());
            } else {
                long n = integer(value);
                if (n < param.min() || n > param.max()) throw new IllegalArgumentException("参数越界：" + entry.getKey());
            }
        }
        return new Action(op, args);
    }

    public static long integer(Object value) {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long))
            throw new IllegalArgumentException("参数必须为整数");
        return ((Number) value).longValue();
    }

    public static String description(String op) {
        switch (op) {
            case "set_led": return "Turn the onboard LED on or off; enabled is boolean.";
            case "blink": return "Blink the onboard LED at the requested frequency in Hz.";
            case "set_rgb": return "Set an RGB LED color using three integer channels r, g and b, each 0-255.";
            case "set_servo": return "Move a standard positional servo to an angle from 0 to 180 degrees. Not for continuous-rotation servos.";
            case "set_motor": return "Run a low-voltage DC motor through a TB6612FNG driver. speed is -120..120 and duration_ms MUST be 100..2000; the firmware automatically stops it. Stop before reversing direction. Never request continuous motion.";
            case "read_analog": return "Read the analog input; returns raw ADC count and calibrated millivolts. Do not infer temperature or other physical units without a calibrated sensor.";
            case "set_analog_led_rule": return "Configure a runtime-only ESP32 local rule: when analog millivolts cross the threshold in the selected direction, turn the onboard LED on; hysteresis_mv prevents rapid toggling. enabled=false disables the rule and turns the LED off. The rule runs on the ESP32 without the phone or cloud, but is cleared after reboot. Do not describe ADC millivolts as temperature or another physical unit.";
            case "get_state": return "Read the current device state; no parameters.";
            case "stop": return "Turn off the onboard LED and RGB, stop blinking and the DC motor, and disable the analog LED automation rule; no parameters.";
            default: throw new IllegalArgumentException("Unknown operation");
        }
    }

    @SuppressWarnings("unchecked")
    public static boolean matches(Action action, Map<String, Object> state) {
        if (state == null) return false;
        try {
            switch (action.op()) {
                case "set_led":
                    return Boolean.valueOf((Boolean) action.args().get("enabled")).equals(state.get("led")) &&
                            number(state, "blink_hz") == 0 && analogRuleInactive(state);
                case "blink":
                    return number(state, "blink_hz") == integer(action.args().get("hz")) && analogRuleInactive(state);
                case "set_rgb": {
                    Map<String, Object> rgb = (Map<String, Object>) state.get("rgb");
                    return rgb != null && number(rgb, "r") == integer(action.args().get("r")) &&
                            number(rgb, "g") == integer(action.args().get("g")) && number(rgb, "b") == integer(action.args().get("b"));
                }
                case "set_servo": {
                    Map<String, Object> servo = (Map<String, Object>) state.get("servo");
                    return servo != null && number(servo, "angle") == integer(action.args().get("angle"));
                }
                case "set_motor": {
                    Map<String, Object> motor = (Map<String, Object>) state.get("motor");
                    long speed = integer(action.args().get("speed"));
                    return motor != null && number(motor, "speed") == speed && (speed == 0 || Boolean.TRUE.equals(motor.get("active")));
                }
                case "read_analog": {
                    Map<String, Object> analog = (Map<String, Object>) state.get("analog");
                    return analog != null && number(analog, "raw") >= 0 && number(analog, "raw") <= 4095 &&
                            number(analog, "millivolts") >= 0 && number(analog, "millivolts") <= 3300;
                }
                case "set_analog_led_rule": {
                    Map<String, Object> rule = (Map<String, Object>) state.get("analog_rule");
                    boolean enabled = (Boolean) action.args().get("enabled");
                    boolean matches = rule != null && Boolean.valueOf(enabled).equals(rule.get("enabled")) &&
                            Boolean.valueOf((Boolean) action.args().get("above")).equals(rule.get("above")) &&
                            number(rule, "threshold_mv") == integer(action.args().get("threshold_mv")) &&
                            number(rule, "hysteresis_mv") == integer(action.args().get("hysteresis_mv"));
                    if (!matches || number(state, "blink_hz") != 0) return false;
                    if (!enabled) return Boolean.FALSE.equals(state.get("led"));
                    Map<String, Object> analog = (Map<String, Object>) state.get("analog");
                    if (analog == null) return false;
                    long mv = number(analog, "millivolts");
                    long threshold = integer(action.args().get("threshold_mv"));
                    long hysteresis = integer(action.args().get("hysteresis_mv"));
                    boolean above = (Boolean) action.args().get("above");
                    boolean led = Boolean.TRUE.equals(state.get("led"));
                    if (above && mv >= threshold && !led) return false;
                    if (above && mv < threshold - hysteresis && led) return false;
                    if (!above && mv <= threshold && !led) return false;
                    if (!above && mv > threshold + hysteresis && led) return false;
                    return true;
                }
                case "stop": {
                    if (!Boolean.FALSE.equals(state.get("led")) || number(state, "blink_hz") != 0) return false;
                    Map<String, Object> rgb = (Map<String, Object>) state.get("rgb");
                    if (rgb != null && (number(rgb, "r") != 0 || number(rgb, "g") != 0 || number(rgb, "b") != 0)) return false;
                    Map<String, Object> motor = (Map<String, Object>) state.get("motor");
                    if (motor != null && number(motor, "speed") != 0) return false;
                    Map<String, Object> rule = (Map<String, Object>) state.get("analog_rule");
                    return rule == null || Boolean.FALSE.equals(rule.get("enabled"));
                }
                case "get_state": return state.get("led") instanceof Boolean && number(state, "blink_hz") >= 0 && number(state, "blink_hz") <= 5;
                default: return false;
            }
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static boolean analogRuleInactive(Map<String, Object> state) {
        Object value = state.get("analog_rule");
        if (value == null) return true; // V0.3 devices do not advertise this optional feature.
        if (!(value instanceof Map)) return false;
        return Boolean.FALSE.equals(((Map<?, ?>) value).get("enabled"));
    }

    private static long number(Map<String, Object> map, String key) { return integer(map.get(key)); }
}
