import com.lingiot.demo.Capabilities;
import java.util.*;
public class CapabilitiesTest {
  static int cases=0;
  static Capabilities.Param i(int a,int b){return Capabilities.Param.integer(a,b);}
  static Map<String,Capabilities.Param> p(Object... x){Map<String,Capabilities.Param> m=new LinkedHashMap<>();for(int n=0;n<x.length;n+=2)m.put((String)x[n],(Capabilities.Param)x[n+1]);return m;}
  static void check(boolean b){cases++;if(!b)throw new AssertionError("case "+cases);}
  static void rejects(Runnable f){cases++;try{f.run();}catch(IllegalArgumentException e){return;}throw new AssertionError("accepted case "+cases);}
  static Capabilities caps(){return new Capabilities("demo-board-v1",Map.of(
    "set_led",p("enabled",Capabilities.Param.bool()),
    "blink",p("hz",i(1,3)),
    "set_rgb",p("r",i(0,255),"g",i(0,255),"b",i(0,255)),
    "set_servo",p("angle",i(10,170)),
    "set_motor",p("speed",i(-100,100),"duration_ms",i(100,1000)),
    "read_analog",p(),"set_analog_led_rule",p("enabled",Capabilities.Param.bool(),"threshold_mv",i(0,3300),"above",Capabilities.Param.bool(),"hysteresis_mv",i(0,500)),
    "get_state",p(),"stop",p(),"gpio",p("pin",i(0,39))));}
  public static void main(String[] args){
    Capabilities c=caps();
    check(c.operations().keySet().equals(new TreeSet<>(Arrays.asList("set_led","blink","set_rgb","set_servo","set_motor","read_analog","set_analog_led_rule","get_state","stop"))));
    check(c.validate("set_led",Map.of("enabled",true)).op().equals("set_led"));
    check(c.validate("blink",Map.of("hz",3)).args().get("hz").equals(3));
    check(c.validate("set_rgb",Map.of("r",0,"g",128,"b",255)).args().size()==3);
    check(c.validate("set_servo",Map.of("angle",10)).op().equals("set_servo"));
    check(c.validate("set_motor",Map.of("speed",-100,"duration_ms",1000)).op().equals("set_motor"));
    check(c.validate("read_analog",Map.of()).args().isEmpty());
    check(c.validate("set_analog_led_rule",Map.of("enabled",true,"threshold_mv",1500,"above",true,"hysteresis_mv",100)).op().equals("set_analog_led_rule"));
    rejects(()->c.validate("blink",Map.of("hz",4)));
    rejects(()->c.validate("set_led",Map.of("enabled",1)));
    rejects(()->c.validate("set_rgb",Map.of("r",0,"g",256,"b",0)));
    rejects(()->c.validate("set_rgb",Map.of("r",0,"g",0)));
    rejects(()->c.validate("set_rgb",Map.of("r",0,"g",0,"b",0,"gpio",2)));
    rejects(()->c.validate("set_servo",Map.of("angle",181)));
    rejects(()->c.validate("set_motor",Map.of("speed",101,"duration_ms",500)));
    rejects(()->c.validate("set_motor",Map.of("speed",20,"duration_ms",2001)));
    rejects(()->c.validate("set_motor",Map.of("speed",20.0,"duration_ms",500)));
    rejects(()->c.validate("set_analog_led_rule",Map.of("enabled",true,"threshold_mv",3301,"above",true,"hysteresis_mv",100)));
    rejects(()->c.validate("set_analog_led_rule",Map.of("enabled",true,"threshold_mv",1500,"above",true,"hysteresis_mv",501)));
    rejects(()->c.validate("set_analog_led_rule",Map.of("enabled",true,"threshold_mv",1500,"above",true)));
    rejects(()->c.validate("gpio",Map.of("pin",2)));
    rejects(()->c.validate("unsupported",Map.of()));
    rejects(()->new Capabilities("evil",Map.of("get_state",p(),"stop",p())));
    rejects(()->new Capabilities("demo-board-v1",Map.of("get_state",p())));
    rejects(()->new Capabilities("demo-board-v1",Map.of("get_state",p(),"stop",p(),"set_analog_led_rule",p("enabled",Capabilities.Param.bool(),"threshold_mv",i(0,3300),"above",Capabilities.Param.bool(),"hysteresis_mv",i(0,500)))));
    rejects(()->new Capabilities("demo-board-v1",Map.of("get_state",p(),"stop",p(),"set_motor",p("speed",i(-121,100),"duration_ms",i(100,1000)))));
    rejects(()->new Capabilities("demo-board-v1",Map.of("get_state",p(),"stop",p(),"set_led",p("enabled",i(0,1)))));
    Map<String,Object> ledState=Map.of("led",true,"blink_hz",0);
    check(Capabilities.matches(c.validate("set_led",Map.of("enabled",true)),ledState));
    check(!Capabilities.matches(c.validate("set_led",Map.of("enabled",true)),Map.of("led",false,"blink_hz",0)));
    check(!Capabilities.matches(c.validate("set_led",Map.of("enabled",true)),Map.of("led",true,"blink_hz",0,"analog_rule",Map.of("enabled",true))));
    check(Capabilities.matches(c.validate("blink",Map.of("hz",2)),Map.of("led",true,"blink_hz",2,"analog_rule",Map.of("enabled",false))));
    check(Capabilities.matches(c.validate("set_rgb",Map.of("r",0,"g",128,"b",255)),Map.of("rgb",Map.of("r",0,"g",128,"b",255))));
    check(Capabilities.matches(c.validate("set_servo",Map.of("angle",90)),Map.of("servo",Map.of("angle",90))));
    check(Capabilities.matches(c.validate("set_motor",Map.of("speed",80,"duration_ms",500)),Map.of("motor",Map.of("speed",80,"active",true))));
    check(Capabilities.matches(c.validate("read_analog",Map.of()),Map.of("analog",Map.of("raw",2048,"millivolts",1650))));
    Map<String,Object> ruleState=Map.of("enabled",true,"threshold_mv",1500,"above",true,"hysteresis_mv",100);
    check(Capabilities.matches(c.validate("set_analog_led_rule",Map.of("enabled",true,"threshold_mv",1500,"above",true,"hysteresis_mv",100)),Map.of("analog_rule",ruleState,"analog",Map.of("raw",2048,"millivolts",1600),"led",true,"blink_hz",0)));
    check(!Capabilities.matches(c.validate("set_analog_led_rule",Map.of("enabled",true,"threshold_mv",1500,"above",true,"hysteresis_mv",100)),Map.of("analog_rule",ruleState,"analog",Map.of("raw",2048,"millivolts",1600),"led",false,"blink_hz",0)));
    check(!Capabilities.matches(c.validate("set_analog_led_rule",Map.of("enabled",true,"threshold_mv",1500,"above",true,"hysteresis_mv",100)),Map.of("analog_rule",Map.of("enabled",true,"threshold_mv",1400,"above",true,"hysteresis_mv",100),"led",true,"blink_hz",0)));
    check(!Capabilities.matches(c.validate("read_analog",Map.of()),Map.of("analog",Map.of("raw",5000,"millivolts",1650))));
    check(Capabilities.matches(c.validate("stop",Map.of()),Map.of("led",false,"blink_hz",0,"rgb",Map.of("r",0,"g",0,"b",0),"motor",Map.of("speed",0),"analog_rule",Map.of("enabled",false,"threshold_mv",1500,"above",true,"hysteresis_mv",100))));
    check(Capabilities.matches(c.validate("get_state",Map.of()),Map.of("led",false,"blink_hz",0)));
    Map<String,Object> fullState=Map.of("led",false,"blink_hz",0,"rgb",Map.of("r",0,"g",0,"b",0),"servo",Map.of("angle",90),"motor",Map.of("speed",0,"active",false),"analog",Map.of("raw",2048,"millivolts",1650),"analog_rule",Map.of("enabled",false,"threshold_mv",1500,"above",true,"hysteresis_mv",100));
    check(c.validState(fullState));
    Map<String,Object> badState=new HashMap<>(fullState);badState.put("analog",Map.of("raw",5000,"millivolts",1650));check(!c.validState(badState));
    Map<String,Object> badRuleState=new HashMap<>(fullState);badRuleState.put("analog_rule",Map.of("enabled",true,"threshold_mv",1500,"above",true,"hysteresis_mv",900));check(!c.validState(badRuleState));
    System.out.println("Capabilities: "+cases+" assertions passed");
  }
}
