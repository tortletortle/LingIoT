import com.lingiot.demo.*;
import java.util.*;
public class VirtualBoardTest {
    static int count;static void check(boolean v){count++;if(!v)throw new AssertionError("assertion "+count);}
    static Map<String,Object> a(Object... p){return VirtualBoard.map(p);}static Map<String,Object> act(VirtualBoard b,String op,long t,Object... p){Map<String,Object> s=b.execute(op,a(p),t);check(VirtualBoard.capabilities().validState(s));return s;}
    static Object get(Map<String,Object> s,String o,String k){return ((Map<?,?>)s.get(o)).get(k);}
    static void reject(Runnable r){boolean ok=false;try{r.run();}catch(IllegalArgumentException ex){ok=true;}check(ok);}
    public static void main(String[] args){VirtualBoard b=new VirtualBoard();check(VirtualBoard.capabilities().operations().size()==9);
        check(act(b,"get_state",0).get("led").equals(false));check(act(b,"set_led",0,"enabled",true).get("led").equals(true));
        check(act(b,"blink",100,"hz",2).get("led").equals(true));check(act(b,"get_state",350).get("led").equals(false));check(act(b,"get_state",600).get("led").equals(true));
        Map<String,Object> s=act(b,"set_rgb",610,"r",1,"g",80,"b",255);check(get(s,"rgb","b").equals(255));reject(()->b.execute("set_rgb",a("r",256,"g",0,"b",0),611));
        check(get(act(b,"set_servo",620,"angle",150),"servo","angle").equals(150));
        check(get(act(b,"set_motor",700,"speed",80,"duration_ms",500),"motor","active").equals(true));reject(()->b.execute("set_motor",a("speed",-80,"duration_ms",500),701));check(get(act(b,"get_state",1199),"motor","speed").equals(80));check(get(act(b,"get_state",1200),"motor","speed").equals(0));
        b.sample(1600);check(act(b,"set_analog_led_rule",1201,"enabled",true,"above",true,"threshold_mv",1500,"hysteresis_mv",100).get("led").equals(true));b.sample(1450);check(act(b,"get_state",1202).get("led").equals(true));b.sample(1399);check(act(b,"get_state",1203).get("led").equals(false));
        check(act(b,"set_analog_led_rule",1204,"enabled",true,"above",false,"threshold_mv",1500,"hysteresis_mv",100).get("led").equals(true));b.sample(1601);check(act(b,"get_state",1205).get("led").equals(false));
        check(get(act(b,"set_led",1206,"enabled",true),"analog_rule","enabled").equals(false));b.sample(0);check(act(b,"get_state",1207).get("led").equals(true));
        s=act(b,"stop",1208);check(s.get("led").equals(false));check(get(s,"rgb","b").equals(0));check(get(s,"servo","angle").equals(150));check(get(s,"motor","speed").equals(0));reject(()->b.sample(-1));reject(()->b.execute("gpio",a(),1209));
        Telemetry t=new Telemetry(3);t.add(10,0);t.add(20,100);t.add(30,200);t.add(40,300);check(t.snapshot().size()==3);check(t.snapshot().get(0).ms==20);t.add(40,400);check(t.snapshot().get(2).mv==400);reject(()->t.add(39,500));reject(()->t.add(50,3301));t.snapshot().clear();check(t.snapshot().size()==3);t.clear();check(t.snapshot().isEmpty());
        System.out.println("VirtualBoard + Telemetry: "+count+" assertions passed");}
}
