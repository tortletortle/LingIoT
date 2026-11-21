package com.lingiot.demo;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;
public class VirtualLinkTest {
 @Test public void roundTrip()throws Exception{AtomicLong clock=new AtomicLong(10);VirtualBoard board=new VirtualBoard();VirtualLink link=new VirtualLink(board,clock::get);DeviceProtocol d=new DeviceProtocol(link,VirtualLink.TOKEN);Capabilities c=d.describe();assertEquals(9,c.operations().size());
  assertTrue((Boolean)d.execute(c,c.validate("set_led",Map.of("enabled",true))).get("led"));
  d.execute(c,c.validate("set_motor",Map.of("speed",80,"duration_ms",500)));clock.set(510);assertEquals(0,((Number)((Map<?,?>)d.execute(c,c.validate("get_state",Map.of())).get("motor")).get("speed")).intValue());
  board.sample(1550);assertTrue((Boolean)d.execute(c,c.validate("set_analog_led_rule",Map.of("enabled",true,"above",true,"threshold_mv",1500,"hysteresis_mv",100))).get("led"));
  board.sample(1399);assertFalse((Boolean)d.execute(c,c.validate("get_state",Map.of())).get("led"));
  assertThrows(Exception.class,()->new DeviceProtocol(link,"wrong_token").describe());
  JSONObject response=new JSONObject(link.exchange(new JSONObject().put("v",2).put("id","test").put("token",VirtualLink.TOKEN).put("op","bogus").put("args",new JSONObject()).toString()));assertFalse(response.getBoolean("ok"));
 }
}
