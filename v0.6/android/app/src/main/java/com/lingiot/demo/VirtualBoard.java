package com.lingiot.demo;
import java.util.*;
/** Deterministic software model, never operates real hardware. Time is injected for tests. */
public final class VirtualBoard {
    private boolean led,rule,above=true;
    private int hz,r,g,b,angle=90,speed,mv=1650,threshold=1500,hysteresis=100;
    private long blinkAt,deadline;
    public static Map<String,Object> map(Object... pairs){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
    private static Map<String,Capabilities.Param> params(Object... pairs){Map<String,Capabilities.Param> m=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],(Capabilities.Param)pairs[i+1]);return m;}
    private static Capabilities.Param range(int min,int max){return Capabilities.Param.integer(min,max);}
    public static Capabilities capabilities(){Map<String,Map<String,Capabilities.Param>> m=new LinkedHashMap<>();
        m.put("set_led",params("enabled",Capabilities.Param.bool()));m.put("blink",params("hz",range(1,5)));
        m.put("set_rgb",params("r",range(0,255),"g",range(0,255),"b",range(0,255)));
        m.put("set_servo",params("angle",range(0,180)));m.put("set_motor",params("speed",range(-120,120),"duration_ms",range(100,2000)));
        m.put("read_analog",params());m.put("get_state",params());m.put("stop",params());
        m.put("set_analog_led_rule",params("enabled",Capabilities.Param.bool(),"above",Capabilities.Param.bool(),"threshold_mv",range(0,3300),"hysteresis_mv",range(0,500)));
        return new Capabilities("demo-board-v1",m);}
    private final Capabilities caps=capabilities();
    private void ruleTick(){if(!rule)return;if(above){if(!led&&mv>=threshold)led=true;else if(led&&mv<threshold-hysteresis)led=false;}else{if(!led&&mv<=threshold)led=true;else if(led&&mv>threshold+hysteresis)led=false;}}
    public synchronized void sample(int value){if(value<0||value>3300)throw new IllegalArgumentException("模拟电压超限");mv=value;ruleTick();}
    private void tick(long now){if(hz>0){long half=500/hz,n=Math.max(0,now-blinkAt)/half;if((n&1)==1)led=!led;blinkAt+=n*half;}if(speed!=0&&now>=deadline)speed=0;}
    private static int n(Map<String,Object> a,String k){return (int)Capabilities.integer(a.get(k));}
    public synchronized Map<String,Object> execute(String op,Map<String,Object> a,long now){caps.validate(op,a);tick(now);
        switch(op){case "set_led":rule=false;hz=0;led=(Boolean)a.get("enabled");break;
        case "blink":rule=false;hz=n(a,"hz");led=true;blinkAt=now;break;
        case "set_rgb":r=n(a,"r");g=n(a,"g");b=n(a,"b");break;
        case "set_servo":angle=n(a,"angle");break;
        case "set_motor":int next=n(a,"speed");if(speed!=0&&next!=0&&Integer.signum(speed)!=Integer.signum(next))throw new IllegalArgumentException("先停止电机再反向");speed=next;deadline=now+n(a,"duration_ms");break;
        case "set_analog_led_rule":rule=(Boolean)a.get("enabled");above=(Boolean)a.get("above");threshold=n(a,"threshold_mv");hysteresis=n(a,"hysteresis_mv");hz=0;led=false;ruleTick();break;
        case "stop":rule=false;hz=0;led=false;r=g=b=speed=0;break;
        default:break;}
        return map("led",led,"blink_hz",hz,"rgb",map("r",r,"g",g,"b",b),"servo",map("angle",angle),"motor",map("speed",speed,"active",speed!=0),"analog",map("raw",mv*4095/3300,"millivolts",mv),"analog_rule",map("enabled",rule,"above",above,"threshold_mv",threshold,"hysteresis_mv",hysteresis));
    }
}
