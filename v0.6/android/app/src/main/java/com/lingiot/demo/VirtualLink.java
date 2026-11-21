package com.lingiot.demo;
import org.json.JSONObject;
import java.util.*;
import java.util.function.LongSupplier;
/** In-process LIP v2 transport. No Bluetooth, socket, or physical GPIO. */
public final class VirtualLink implements DeviceProtocol.Link {
    public static final String TOKEN="virtual_only";
    private final VirtualBoard board;
    private final LongSupplier clock;
    public VirtualLink(VirtualBoard board,LongSupplier clock){this.board=board;this.clock=clock;}
    public String exchange(String raw)throws Exception{
        JSONObject q=new JSONObject(raw);JSONObject out=new JSONObject().put("v",2).put("id",q.getString("id"));
        try{if(Capabilities.integer(q.get("v"))!=2||!TOKEN.equals(q.getString("token")))throw new IllegalArgumentException("invalid virtual frame");
            String op=q.getString("op");JSONObject a=q.getJSONObject("args");
            if(op.equals("describe")){if(a.length()!=0)throw new IllegalArgumentException("describe args");JSONObject ops=new JSONObject();
                for(Map.Entry<String,Map<String,Capabilities.Param>> e:VirtualBoard.capabilities().operations().entrySet()){JSONObject ps=new JSONObject();for(Map.Entry<String,Capabilities.Param> p:e.getValue().entrySet()){Capabilities.Param v=p.getValue();JSONObject j=new JSONObject().put("type",v.type());if(v.type().equals("integer"))j.put("min",v.min()).put("max",v.max());ps.put(p.getKey(),j);}ops.put(e.getKey(),ps);}out.put("capabilities",new JSONObject().put("profile","demo-board-v1").put("ops",ops));
            }else{Map<String,Object> args=new LinkedHashMap<>();Iterator<String> ks=a.keys();while(ks.hasNext()){String k=ks.next();args.put(k,a.get(k));}out.put("state",new JSONObject(board.execute(op,args,clock.getAsLong())));}
            return out.put("ok",true).toString();
        }catch(Exception e){return out.put("ok",false).put("error",e.getMessage()==null?"virtual error":e.getMessage()).toString();}
    }
}
