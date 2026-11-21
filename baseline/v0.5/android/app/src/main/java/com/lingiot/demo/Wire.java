package com.lingiot.demo;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
public final class Wire {
    public static final int LIMIT=1024, CHUNK=20;
    private final ByteArrayOutputStream buf=new ByteArrayOutputStream();
    private boolean discard;
    public synchronized void reset(){buf.reset(); discard=false;}
    public synchronized void feed(byte[] b, Consumer<String> onFrame){
        for(byte x:b){
            if(x=='\n'){if(!discard && buf.size()>0) onFrame.accept(new String(buf.toByteArray(),StandardCharsets.UTF_8)); reset();}
            else if(!discard){if(buf.size()>=LIMIT){buf.reset(); discard=true;}else buf.write(x);}
        }
    }
    public static boolean privateIp(String ip){
        String[] a=ip.split("\\.",-1); if(a.length!=4)return false; int[] n=new int[4];
        try{for(int i=0;i<4;i++){if(!a[i].matches("0|[1-9][0-9]{0,2}"))return false; n[i]=Integer.parseInt(a[i]);if(n[i]>255)return false;}}catch(Exception e){return false;}
        return n[0]==10 || (n[0]==172 && n[1]>=16 && n[1]<=31) || (n[0]==192 && n[1]==168);
    }
}
