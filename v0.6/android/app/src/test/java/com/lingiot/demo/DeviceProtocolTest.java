package com.lingiot.demo;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class DeviceProtocolTest {
  static class Fake implements DeviceProtocol.Link {
    final List<String> calls=new ArrayList<>();
    boolean led=false,badId=false,badRead=false,timeout=false,ruleEnabled=false,ruleAbove=true;
    int hz=0,r=0,g=0,b=0,angle=90,motor=0,raw=2048,mv=1650,ruleThreshold=1500,ruleHysteresis=100;
    public String exchange(String rawRequest)throws Exception{
      JSONObject q=new JSONObject(rawRequest);String op=q.getString("op");calls.add(op);
      assertEquals(2,q.getInt("v"));assertEquals("demo_key_123",q.getString("token"));JSONObject a=q.getJSONObject("args");
      JSONObject out=new JSONObject().put("v",2).put("id",badId?"wrong":q.getString("id")).put("ok",true);
      if(op.equals("describe"))return out.put("capabilities",new JSONObject(CAPS)).toString();
      switch(op){
        case "set_led":led=a.getBoolean("enabled");hz=0;ruleEnabled=false;break;
        case "blink":hz=a.getInt("hz");led=true;ruleEnabled=false;break;
        case "set_rgb":r=a.getInt("r");g=a.getInt("g");b=a.getInt("b");break;
        case "set_servo":angle=a.getInt("angle");break;
        case "set_motor":motor=a.getInt("speed");break;
        case "read_analog":raw=2048;mv=1650;break;
        case "set_analog_led_rule":
          ruleEnabled=a.getBoolean("enabled");ruleThreshold=a.getInt("threshold_mv");ruleAbove=a.getBoolean("above");ruleHysteresis=a.getInt("hysteresis_mv");hz=0;
          led=ruleEnabled&&(ruleAbove?mv>=ruleThreshold:mv<=ruleThreshold);break;
        case "stop":led=false;hz=0;r=g=b=motor=0;ruleEnabled=false;break;
        case "get_state":break;
        default:throw new AssertionError(op);
      }
      if(timeout&&op.equals("set_rgb"))throw new java.io.IOException("timeout after applying");
      JSONObject state=state();
      if(badRead&&op.equals("get_state"))state.getJSONObject("rgb").put("b",0);
      return out.put("state",state).toString();
    }
    JSONObject state()throws Exception{return new JSONObject().put("led",led).put("blink_hz",hz)
      .put("rgb",new JSONObject().put("r",r).put("g",g).put("b",b))
      .put("servo",new JSONObject().put("angle",angle))
      .put("motor",new JSONObject().put("speed",motor).put("active",motor!=0))
      .put("analog",new JSONObject().put("raw",raw).put("millivolts",mv))
      .put("analog_rule",new JSONObject().put("enabled",ruleEnabled).put("threshold_mv",ruleThreshold).put("above",ruleAbove).put("hysteresis_mv",ruleHysteresis));}
  }
  static final String CAPS="{\"profile\":\"demo-board-v1\",\"ops\":{\"set_led\":{\"enabled\":{\"type\":\"boolean\"}},\"blink\":{\"hz\":{\"type\":\"integer\",\"min\":1,\"max\":3}},\"set_rgb\":{\"r\":{\"type\":\"integer\",\"min\":0,\"max\":255},\"g\":{\"type\":\"integer\",\"min\":0,\"max\":255},\"b\":{\"type\":\"integer\",\"min\":0,\"max\":255}},\"set_servo\":{\"angle\":{\"type\":\"integer\",\"min\":0,\"max\":180}},\"set_motor\":{\"speed\":{\"type\":\"integer\",\"min\":-100,\"max\":100},\"duration_ms\":{\"type\":\"integer\",\"min\":100,\"max\":1000}},\"read_analog\":{},\"set_analog_led_rule\":{\"enabled\":{\"type\":\"boolean\"},\"threshold_mv\":{\"type\":\"integer\",\"min\":0,\"max\":3300},\"above\":{\"type\":\"boolean\"},\"hysteresis_mv\":{\"type\":\"integer\",\"min\":0,\"max\":500}},\"get_state\":{},\"stop\":{}}}";

  @Test public void structuredRoundTrip()throws Exception{
    Fake f=new Fake();DeviceProtocol d=new DeviceProtocol(f,"demo_key_123");Capabilities c=d.describe();
    assertEquals(9,c.operations().size());
    Map<String,Object> state=d.execute(c,c.validate("set_rgb",Map.of("r",0,"g",80,"b",255)));
    assertEquals(255,((Number)((Map<?,?>)state.get("rgb")).get("b")).intValue());
    assertEquals(Arrays.asList("describe","set_rgb","get_state"),f.calls);
  }
  @Test public void analogRuleIsAppliedAndIndependentlyReadBack()throws Exception{
    Fake f=new Fake();DeviceProtocol d=new DeviceProtocol(f,"demo_key_123");Capabilities c=d.describe();
    Map<String,Object> state=d.execute(c,c.validate("set_analog_led_rule",Map.of("enabled",true,"threshold_mv",1500,"above",true,"hysteresis_mv",100)));
    assertTrue(f.ruleEnabled);assertTrue(f.led);
    assertTrue(Capabilities.matches(c.validate("set_analog_led_rule",Map.of("enabled",true,"threshold_mv",1500,"above",true,"hysteresis_mv",100)),state));
    assertEquals(Arrays.asList("describe","set_analog_led_rule","get_state"),f.calls);
  }
  @Test public void motorIsTimeBoundAndDoesNotUseDelayedReadback()throws Exception{
    Fake f=new Fake();DeviceProtocol d=new DeviceProtocol(f,"demo_key_123");Capabilities c=d.describe();
    d.execute(c,c.validate("set_motor",Map.of("speed",80,"duration_ms",500)));
    assertEquals(Arrays.asList("describe","set_motor"),f.calls);
  }
  @Test public void analogReadReturnsInstantSample()throws Exception{
    Fake f=new Fake();DeviceProtocol d=new DeviceProtocol(f,"demo_key_123");Capabilities c=d.describe();
    Map<String,Object> state=d.execute(c,c.validate("read_analog",Map.of()));
    assertEquals(2048,((Number)((Map<?,?>)state.get("analog")).get("raw")).intValue());
    assertEquals(Arrays.asList("describe","read_analog"),f.calls);
  }
  @Test public void mismatch()throws Exception{
    Fake f=new Fake();DeviceProtocol d=new DeviceProtocol(f,"demo_key_123");Capabilities c=d.describe();f.badRead=true;
    assertThrows(Exception.class,()->d.execute(c,c.validate("set_rgb",Map.of("r",0,"g",80,"b",255))));
    assertEquals(Arrays.asList("describe","set_rgb","get_state"),f.calls);
  }
  @Test public void noRetryOnTimeout()throws Exception{
    Fake f=new Fake();DeviceProtocol d=new DeviceProtocol(f,"demo_key_123");Capabilities c=d.describe();f.timeout=true;
    assertThrows(Exception.class,()->d.execute(c,c.validate("set_rgb",Map.of("r",0,"g",80,"b",255))));
    assertEquals(80,f.g);assertEquals(Arrays.asList("describe","set_rgb"),f.calls);
  }
  @Test public void badId()throws Exception{
    Fake f=new Fake();f.badId=true;DeviceProtocol d=new DeviceProtocol(f,"demo_key_123");
    assertThrows(Exception.class,d::describe);assertEquals(1,f.calls.size());
  }
  @Test public void dynamicToolsAndModelResponse()throws Exception{
    Fake f=new Fake();Capabilities c=new DeviceProtocol(f,"demo_key_123").describe();JSONArray tools=Api.tools(c);Set<String> names=new HashSet<>();
    for(int i=0;i<tools.length();i++)names.add(tools.getJSONObject(i).getJSONObject("function").getString("name"));
    assertEquals(new HashSet<>(Arrays.asList("set_led","blink","set_rgb","set_servo","set_motor","read_analog","set_analog_led_rule","get_state","stop","unsupported")),names);
    Capabilities.Action blink=Api.parseAction(response("blink","{\"hz\":2}"),c);assertEquals("blink",blink.op());assertEquals(Map.of("hz",2),blink.args());
    Capabilities.Action rgb=Api.parseAction(response("set_rgb","{\"r\":0,\"g\":0,\"b\":255}"),c);assertEquals("set_rgb",rgb.op());assertEquals(Map.of("r",0,"g",0,"b",255),rgb.args());
    Capabilities.Action rule=Api.parseAction(response("set_analog_led_rule","{\"enabled\":true,\"threshold_mv\":1500,\"above\":true,\"hysteresis_mv\":100}"),c);assertEquals("set_analog_led_rule",rule.op());
    for(String arg:Arrays.asList("{\"hz\":4}","{\"hz\":\"2\"}","{\"hz\":2,\"gpio\":4}","{\"hz\":2.0}"))
      assertThrows(Exception.class,()->Api.parseAction(response("blink",arg),c));
    assertThrows(Exception.class,()->Api.parseAction(response("unsupported","{\"reason\":\"unsafe\"}"),c));
    assertThrows(Exception.class,()->Api.parseAction(response("set_motor","{\"speed\":100,\"duration_ms\":3000}"),c));
    assertThrows(Exception.class,()->Api.parseAction(response("set_analog_led_rule","{\"enabled\":true,\"threshold_mv\":5000,\"above\":true,\"hysteresis_mv\":100}"),c));
    assertEquals(Collections.singletonList("describe"),f.calls);
  }
  static JSONObject response(String op,String args)throws Exception{
    JSONObject fn=new JSONObject().put("name",op).put("arguments",args);
    JSONObject call=new JSONObject().put("type","function").put("function",fn);
    return new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("message",new JSONObject().put("tool_calls",new JSONArray().put(call)))));
  }
}
