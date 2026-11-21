package com.lingiot.demo;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.View;
import android.widget.*;
import android.content.Context;
import java.util.*;

/** Generates native controls only for locally validated device capabilities. */
final class Dashboard extends LinearLayout {
    interface Dispatch{void send(String op,Map<String,Object> args);}
    interface Input{Object value();}
    private final LinearLayout controls;
    private final TextView state,source;
    private final View swatch;
    private final Chart chart;
    private final Telemetry history=new Telemetry(60);
    private final Dispatch dispatch;
    private String signature=""; private Set<String> supported=Collections.emptySet();
    Dashboard(Context c,Dispatch d){super(c);dispatch=d;setOrientation(VERTICAL);source=text(this,"尚未读取设备能力",15);state=text(this,"暂无有效状态",16);swatch=new View(c);swatch.setBackgroundColor(0xffdddddd);addView(swatch,new LayoutParams(-1,dp(16)));chart=new Chart(c);addView(chart,new LayoutParams(-1,dp(140)));chart.setVisibility(GONE);swatch.setVisibility(GONE);controls=new LinearLayout(c);controls.setOrientation(VERTICAL);addView(controls);}
    int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private TextView text(LinearLayout parent,String s,int size){TextView v=new TextView(getContext());v.setText(s);v.setTextSize(size);v.setTextColor(0xff223631);parent.addView(v);return v;}
    void reset(){signature="";supported=Collections.emptySet();controls.removeAllViews();invalidateState();source.setText("尚未读取设备能力");chart.setVisibility(GONE);swatch.setVisibility(GONE);}
    void invalidateState(){state.setText("暂无有效状态（上次数据不可用）");history.clear();chart.invalidate();swatch.setBackgroundColor(0xffdddddd);}
    private static String title(String op){switch(op){case "set_led":return "LED 开关";case "blink":return "LED 闪烁";case "set_rgb":return "RGB 调色";case "set_servo":return "舵机角度";case "set_motor":return "电机短时运行";case "read_analog":return "读取模拟输入";case "set_analog_led_rule":return "ADC → LED 本地规则";default:return op;}}
    private static String field(String p){switch(p){case "enabled":return "启用";case "above":return "高于阈值触发";case "angle":return "角度 (°)";case "speed":return "速度 (PWM)";case "duration_ms":return "运行时长 (ms)";case "threshold_mv":return "阈值 (mV)";case "hysteresis_mv":return "回差 (mV)";case "hz":return "频率 (Hz)";case "r":return "红";case "g":return "绿";case "b":return "蓝";default:return p;}}
    private static int initial(String p){switch(p){case "angle":return 90;case "duration_ms":return 500;case "threshold_mv":return 1500;case "hysteresis_mv":return 100;case "hz":return 2;default:return 0;}}
    void showCapabilities(Capabilities caps,boolean virtual){StringBuilder sig=new StringBuilder();for(Map.Entry<String,Map<String,Capabilities.Param>> e:caps.operations().entrySet()){sig.append(e.getKey());for(Map.Entry<String,Capabilities.Param> p:e.getValue().entrySet())sig.append('/').append(p.getKey()).append(':').append(p.getValue().type()).append(':').append(p.getValue().min()).append(':').append(p.getValue().max());}
        supported=caps.operations().keySet();source.setText((virtual?"模拟 ESP32 · 非实物":"ESP32 · 固件声明")+" / "+caps.operations().size()+" 项能力");if(sig.toString().equals(signature))return;signature=sig.toString();controls.removeAllViews();history.clear();chart.invalidate();chart.setVisibility(caps.operations().containsKey("read_analog")?VISIBLE:GONE);swatch.setVisibility(caps.operations().containsKey("set_rgb")?VISIBLE:GONE);
        for(String op:new String[]{"set_led","blink","set_rgb","set_servo","read_analog","set_analog_led_rule","set_motor"}){Map<String,Capabilities.Param> params=caps.operations().get(op);if(params==null)continue;
            LinearLayout card=new LinearLayout(getContext());card.setOrientation(VERTICAL);card.setPadding(dp(12),dp(10),dp(12),dp(10));GradientDrawable bg=new GradientDrawable();bg.setColor(0xffffffff);bg.setCornerRadius(dp(12));card.setBackground(bg);LayoutParams lp=new LayoutParams(-1,-2);lp.topMargin=dp(10);controls.addView(card,lp);text(card,title(op),18);
            if(op.equals("set_motor"))text(card,"低压驱动板；限时动作，反向前需停止。",12);
            Map<String,Input> inputs=new LinkedHashMap<>();
            for(Map.Entry<String,Capabilities.Param> entry:params.entrySet()){String p=entry.getKey();Capabilities.Param v=entry.getValue();if(v.type().equals("boolean")){Switch toggle=new Switch(getContext());toggle.setText(field(p));toggle.setChecked(p.equals("above"));card.addView(toggle);inputs.put(p,toggle::isChecked);}else{TextView caption=text(card,"",14);SeekBar slider=new SeekBar(getContext());slider.setMax(v.max()-v.min());slider.setProgress(Math.min(v.max(),Math.max(v.min(),initial(p)))-v.min());caption.setText(field(p)+" · "+(slider.getProgress()+v.min()));slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){caption.setText(field(p)+" · "+(progress+v.min()));}public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}});card.addView(slider);inputs.put(p,()->slider.getProgress()+v.min());}}
            Button apply=new Button(getContext());apply.setText(params.isEmpty()?"读取":"应用");apply.setOnClickListener(view->{Map<String,Object> args=new LinkedHashMap<>();for(Map.Entry<String,Input> p:inputs.entrySet())args.put(p.getKey(),p.getValue().value());dispatch.send(op,args);});card.addView(apply);
        }
    }
    void showState(Map<String,Object> s,boolean virtual){StringBuilder out=new StringBuilder(virtual?"模拟状态（非物理反馈）":"固件状态（非物理反馈）");out.append("\nLED ").append(Boolean.TRUE.equals(s.get("led"))?"开":"关").append("  ·  闪烁 ").append(s.get("blink_hz")).append(" Hz");
        if(supported.contains("set_rgb") && s.get("rgb") instanceof Map){Map<?,?> rgb=(Map<?,?>)s.get("rgb");int r=((Number)rgb.get("r")).intValue(),g=((Number)rgb.get("g")).intValue(),b=((Number)rgb.get("b")).intValue();swatch.setBackgroundColor(Color.rgb(r,g,b));out.append("\nRGB ").append(r).append(" / ").append(g).append(" / ").append(b);}
        if(supported.contains("set_servo") && s.get("servo") instanceof Map)out.append("\n舵机 ").append(((Map<?,?>)s.get("servo")).get("angle")).append("°");if(supported.contains("set_motor") && s.get("motor") instanceof Map)out.append("  ·  电机 ").append(((Map<?,?>)s.get("motor")).get("speed"));
        if(supported.contains("read_analog") && s.get("analog") instanceof Map){int mv=((Number)((Map<?,?>)s.get("analog")).get("millivolts")).intValue();history.add(SystemClock.elapsedRealtime(),mv);chart.invalidate();out.append("\n模拟输入 ").append(mv).append(" mV");}
        if(supported.contains("set_analog_led_rule") && s.get("analog_rule") instanceof Map)out.append("  ·  规则 ").append(Boolean.TRUE.equals(((Map<?,?>)s.get("analog_rule")).get("enabled"))?"启用":"关闭");state.setText(out);}
    private final class Chart extends View{private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);Chart(Context c){super(c);setContentDescription("最近 ADC 毫伏采样趋势图");}protected void onDraw(Canvas c){super.onDraw(c);float left=dp(42),right=getWidth()-dp(8),top=dp(16),bottom=getHeight()-dp(22);if(right<=left||bottom<=top)return;p.setStrokeWidth(dp(1));p.setTextSize(dp(11));p.setColor(0xff73857e);c.drawLine(left,top,left,bottom,p);c.drawLine(left,bottom,right,bottom,p);c.drawText("3300",0,top+dp(3),p);c.drawText("0 mV",0,bottom,p);List<Telemetry.Sample> a=history.snapshot();if(a.isEmpty()){c.drawText("等待采样",left+dp(8),top+dp(20),p);return;}long first=a.get(0).ms,last=a.get(a.size()-1).ms,span=Math.max(1,last-first);p.setColor(0xff176c61);p.setStrokeWidth(dp(2));float px=0,py=0;long prev=0;for(int i=0;i<a.size();i++){Telemetry.Sample s=a.get(i);float x=left+(right-left)*(s.ms-first)/span,y=bottom-(bottom-top)*s.mv/3300f;if(i>0&&s.ms-prev<=10000)c.drawLine(px,py,x,y,p);c.drawCircle(x,y,dp(2),p);px=x;py=y;prev=s.ms;}c.drawText(a.size()+" 点 / "+(last-first)/1000+" s",left,bottom+dp(16),p);}}
}
