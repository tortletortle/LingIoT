package com.lingiot.demo;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** LIP v2: structured arguments, local allowlist, request IDs and independent state readback. */
public final class DeviceProtocol {
    public interface Link { String exchange(String request) throws Exception; }
    private final Link link;
    private final String secret;

    public DeviceProtocol(Link link, String secret) {
        if (secret == null || !secret.matches("[A-Za-z0-9_-]{8,64}")) throw new IllegalArgumentException("设备口令格式错误");
        this.link = link;
        this.secret = secret;
    }

    private JSONObject request(String op, Map<String, Object> args) throws Exception {
        String id = UUID.randomUUID().toString();
        JSONObject frame = new JSONObject().put("v", 2).put("id", id).put("token", secret).put("op", op).put("args", new JSONObject(args));
        String payload = frame.toString();
        if (payload.getBytes(StandardCharsets.UTF_8).length > Wire.LIMIT) throw new Exception("请求帧超长");
        String raw = link.exchange(payload);
        if (raw.getBytes(StandardCharsets.UTF_8).length > Wire.LIMIT) throw new Exception("回执超长");
        JSONObject response = new JSONObject(raw);
        Object responseId = response.get("id");
        if (Capabilities.integer(response.get("v")) != 2 || !(responseId instanceof String) || !id.equals(responseId)) throw new Exception("回执版本或 ID 不匹配");
        Object ok = response.get("ok");
        if (!(ok instanceof Boolean)) throw new Exception("回执状态类型错误");
        if (!(Boolean) ok) throw new Exception("设备拒绝：" + response.optString("error"));
        return response;
    }

    public Capabilities describe() throws Exception {
        JSONObject caps = request("describe", new HashMap<>()).getJSONObject("capabilities");
        if (!"demo-board-v1".equals(caps.getString("profile"))) throw new Exception("不支持的设备类型");
        JSONObject ops = caps.getJSONObject("ops");
        if (ops.length() > 16) throw new Exception("能力数量过多");
        Map<String, Map<String, Capabilities.Param>> advertised = new HashMap<>();
        Iterator<String> operationNames = ops.keys();
        while (operationNames.hasNext()) {
            String name = operationNames.next();
            if (!name.matches("[a-z][a-z0-9_]{0,31}")) throw new Exception("非法能力名称");
            if (!Capabilities.isKnownOperation(name)) continue;
            JSONObject params = ops.getJSONObject(name);
            if (params.length() > 8) throw new Exception("参数数量过多");
            Map<String, Capabilities.Param> parsed = new LinkedHashMap<>();
            Iterator<String> paramNames = params.keys();
            while (paramNames.hasNext()) {
                String paramName = paramNames.next();
                if (!paramName.matches("[a-z][a-z0-9_]{0,31}")) throw new Exception("非法参数名称");
                JSONObject descriptor = params.getJSONObject(paramName);
                String type = descriptor.getString("type");
                if ("boolean".equals(type)) {
                    if (descriptor.length() != 1) throw new Exception("布尔参数描述不合法");
                    parsed.put(paramName, Capabilities.Param.bool());
                } else if ("integer".equals(type)) {
                    if (descriptor.length() != 3) throw new Exception("整数参数描述不合法");
                    long min = Capabilities.integer(descriptor.get("min"));
                    long max = Capabilities.integer(descriptor.get("max"));
                    if (min < Integer.MIN_VALUE || max > Integer.MAX_VALUE || min > max) throw new Exception("整数参数范围不合法");
                    parsed.put(paramName, Capabilities.Param.integer((int) min, (int) max));
                } else {
                    throw new Exception("不支持的参数类型");
                }
            }
            advertised.put(name, parsed);
        }
        return new Capabilities(caps.getString("profile"), advertised);
    }

    private static Map<String, Object> toMap(JSONObject object) throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = object.get(key);
            if (value instanceof JSONObject) value = toMap((JSONObject) value);
            else if (value instanceof JSONArray) throw new Exception("设备状态包含不支持的数组");
            map.put(key, value);
        }
        return map;
    }

    public Map<String, Object> execute(Capabilities caps, Capabilities.Action input) throws Exception {
        Capabilities.Action action = caps.validate(input.op(), input.args());
        JSONObject acknowledgement = request(action.op(), action.args());
        Map<String, Object> acknowledgedState = toMap(acknowledgement.getJSONObject("state"));
        if (!caps.validState(acknowledgedState)) throw new Exception("设备状态结构或数值越界");
        if (!Capabilities.matches(action, acknowledgedState)) throw new Exception("设备执行回执与预期不符");

        // ADC samples and time-bounded motor pulses are snapshots, not persistent state.
        if (action.op().equals("get_state") || action.op().equals("read_analog") || action.op().equals("set_motor")) return acknowledgedState;

        Map<String, Object> readback = toMap(request("get_state", new HashMap<>()).getJSONObject("state"));
        if (!caps.validState(readback)) throw new Exception("独立状态回读结构不合法");
        if (!Capabilities.matches(action, readback)) throw new Exception("独立状态回读不匹配");
        return readback;
    }
}
