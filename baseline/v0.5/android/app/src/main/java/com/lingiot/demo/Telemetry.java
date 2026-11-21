package com.lingiot.demo;
import java.util.*;
/** Bounded, monotonic-time sample buffer. Failed reads are not invented samples. */
public final class Telemetry {
    public static final class Sample {public final long ms;public final int mv;Sample(long ms,int mv){this.ms=ms;this.mv=mv;}}
    private final ArrayDeque<Sample> samples=new ArrayDeque<>();private final int limit;
    public Telemetry(int limit){if(limit<2||limit>600)throw new IllegalArgumentException();this.limit=limit;}
    public void add(long ms,int mv){if(mv<0||mv>3300||ms<0||(!samples.isEmpty()&&ms<samples.getLast().ms))throw new IllegalArgumentException("invalid sample");if(!samples.isEmpty()&&ms==samples.getLast().ms)samples.removeLast();samples.addLast(new Sample(ms,mv));while(samples.size()>limit)samples.removeFirst();}
    public List<Sample> snapshot(){return new ArrayList<>(samples);}
    public void clear(){samples.clear();}
}
