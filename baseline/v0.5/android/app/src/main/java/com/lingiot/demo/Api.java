package com.lingiot.demo;

import org.json.JSONArray;
import org.json.JSONObject;
import android.net.Network;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Api {
    public static String post(Network network, String address, String bearer, JSONObject data) throws Exception {
        HttpURLConnection c = (HttpURLConnection) (network == null ? new URL(address).openConnection() : network.openConnection(new URL(address)));
        c.setConnectTimeout(10000);
        c.setReadTimeout(25000);
        c.setInstanceFollowRedirects(false);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        if (!bearer.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + bearer);
        try {
            try (OutputStream out = c.getOutputStream()) { out.write(data.toString().getBytes(StandardCharsets.UTF_8)); }
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream b = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[4096];
                int n;
                while ((n = in.read(chunk)) != -1) {
                    if (b.size() + n > 131072) throw new IOException("响应过大");
                    b.write(chunk, 0, n);
                }
                return b.toString("UTF-8");
            }
        } finally { c.disconnect(); }
    }

    public static JSONArray tools(Capabilities caps) throws Exception {
        JSONArray tools = new JSONArray();
        for (Map.Entry<String, Map<String, Capabilities.Param>> operation : caps.operations().entrySet()) {
            String op = operation.getKey();
            JSONObject properties = new JSONObject();
            JSONArray required = new JSONArray();
            for (Map.Entry<String, Capabilities.Param> parameter : operation.getValue().entrySet()) {
                Capabilities.Param p = parameter.getValue();
                JSONObject schema = new JSONObject().put("type", p.type());
                if ("integer".equals(p.type())) schema.put("minimum", p.min()).put("maximum", p.max());
                properties.put(parameter.getKey(), schema);
                required.put(parameter.getKey());
            }
            JSONObject parameters = new JSONObject().put("type", "object").put("properties", properties)
                    .put("required", required).put("additionalProperties", false);
            tools.put(functionTool(op, Capabilities.description(op), parameters));
        }
        JSONObject unsupportedParameters = new JSONObject().put("type", "object")
                .put("properties", new JSONObject().put("reason", new JSONObject().put("type", "string")))
                .put("required", new JSONArray().put("reason")).put("additionalProperties", false);
        tools.put(functionTool("unsupported", "Use when the user request is ambiguous, unsafe, or unsupported by the advertised device capabilities. Do not execute anything.", unsupportedParameters));
        return tools;
    }

    private static JSONObject functionTool(String name, String description, JSONObject parameters) throws Exception {
        return new JSONObject().put("type", "function").put("function", new JSONObject()
                .put("name", name).put("description", description).put("parameters", parameters));
    }

    public static Capabilities.Action parseAction(JSONObject reply, Capabilities caps) throws Exception {
        JSONArray calls = reply.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getJSONArray("tool_calls");
        if (calls.length() != 1) throw new Exception("仅允许一次工具调用");
        JSONObject call = calls.getJSONObject(0);
        if (!"function".equals(call.getString("type"))) throw new Exception("未知工具类型");
        JSONObject fn = call.getJSONObject("function");
        String name = fn.getString("name");
        JSONObject arguments = new JSONObject(fn.getString("arguments"));
        if ("unsupported".equals(name)) throw new Exception("模型判断该指令不适合当前设备：" + arguments.optString("reason", "未提供原因"));
        Map<String, Object> args = new LinkedHashMap<>();
        Iterator<String> keys = arguments.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = arguments.get(key);
            if (value instanceof JSONObject || value instanceof JSONArray) throw new Exception("不允许嵌套工具参数");
            args.put(key, value);
        }
        return caps.validate(name, args);
    }

    public static Capabilities.Action action(Network network, String endpoint, String key, String model,
                                              String text, Capabilities caps) throws Exception {
        URI uri = new URI(endpoint);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null)
            throw new Exception("API 地址必须为 HTTPS 完整 chat/completions 地址");
        if (key.isEmpty() || model.isEmpty()) throw new Exception("请填写 API Key 和模型");
        String system = "你是 LingIoT 低压物联网设备助手。只能调用提供的一个工具，严格遵守参数范围。" +
                "不得调用未提供的能力，不得指定 GPIO、执行代码、访问网络或推测硬件。" +
                "用户要求持续运行电机、危险动作、多个动作或含糊指令时调用 unsupported。" +
                "电机动作必须显式给出 100-2000ms 的短时长，固件有自动停止保护。" +
                "模拟输入只返回 ADC 原始值和毫伏，未经校准不得称为温度、距离等物理量。" +
                "若请求需要多个动作，不要拆成多次工具调用，调用 unsupported 并说明原因。";
        JSONArray messages = new JSONArray()
                .put(new JSONObject().put("role", "system").put("content", system))
                .put(new JSONObject().put("role", "user").put("content", text));
        JSONObject request = new JSONObject().put("model", model).put("messages", messages)
                .put("tools", tools(caps)).put("tool_choice", "required");
        return parseAction(new JSONObject(post(network, endpoint, key, request)), caps);
    }
}
