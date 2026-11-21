package com.lingiot.demo;
import android.Manifest;
import android.app.*;
import android.os.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Network;
import android.speech.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
public class MainActivity extends Activity {
    final ExecutorService worker=Executors.newSingleThreadExecutor(); final Handler main=new Handler(Looper.getMainLooper());
    EditText endpoint,key,model,ip,token,command; TextView status,log,capView; RadioButton bleMode,simMode; RadioGroup modes; Dashboard dashboard; CheckBox live; SeekBar simAdc; LinearLayout simulationPanel; boolean foreground; final VirtualBoard virtualBoard=new VirtualBoard(); final Runnable poll=new Runnable(){public void run(){if(!foreground)return;if(live.isChecked()&&!busy&&scanning==null)execute("get_state",Collections.emptyMap(),true);main.postDelayed(this,3000);}}; Button scan,send; SpeechRecognizer speech; volatile BleLink ble; volatile boolean busy; BluetoothLeScanner scanner; ScanCallback scanning;
    LinearLayout root; private static final int BLUE=0xff176c61;
    int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    TextView label(String x,int size){TextView t=new TextView(this);t.setText(x);t.setTextSize(size);t.setTextColor(0xff223631);root.addView(t);return t;}
    EditText field(String title,String value,boolean secret){label(title,13);EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(15);e.setInputType(secret?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);e.setText(value);e.setSaveEnabled(false);root.addView(e);return e;}
    Button button(String name,Runnable r){Button b=new Button(this);b.setText(name);b.setAllCaps(false);b.setOnClickListener(v->r.run());root.addView(b);return b;}
    @Override public void onCreate(Bundle b){super.onCreate(b);ScrollView s=new ScrollView(this);s.setFillViewport(true);s.setBackgroundColor(0xfff2f7f4);root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(20),dp(44),dp(20),dp(32));s.addView(root);setContentView(s);
        TextView title=label("LingIoT  /  灵动智联",27);title.setTextColor(BLUE);title.setTypeface(null,1);label("语音 · AI · 本地硬件控制",15);status=label("设备：未连接",16);
        label("01  设备连接",20);RadioGroup rg=new RadioGroup(this);modes=rg;rg.setOrientation(1);bleMode=new RadioButton(this);bleMode.setText("蓝牙 BLE");bleMode.setId(View.generateViewId());RadioButton wifi=new RadioButton(this);wifi.setText("局域网 / 热点");wifi.setId(View.generateViewId());rg.addView(bleMode);rg.addView(wifi);simMode=new RadioButton(this);simMode.setText("模拟");simMode.setId(View.generateViewId());rg.addView(simMode);root.addView(rg);rg.check(bleMode.getId());scan=button("扫描并连接 ESP32",this::scanBle);ip=field("设备 IPv4（热点 / 同一局域网）","192.168.4.1",false);token=field("设备访问口令（与固件一致）","",true);capView=label("设备能力：尚未读取",14);button("读取设备能力",()->manual("describe",Collections.emptyMap()));
        label("02  语音与控制",20);command=field("输入或语音识别指令","",false);command.setHint("例如：把 RGB 灯调成蓝色；舵机转到90度；读取模拟输入");button("🎙  按一下，说出指令",this::listen);send=button("交给 AI 执行",()->execute(null,null));
        label("03  设备控制台",20);button("刷新状态",()->manual("get_state",Collections.emptyMap()));button("停止 LED / RGB / 电机与规则（舵机保持）",()->manual("stop",Collections.emptyMap()));
        live=new CheckBox(this);live.setText("前台自动读取（约每 3 秒，忙时跳过）");root.addView(live);
        simulationPanel=new LinearLayout(this);simulationPanel.setOrientation(1);root.addView(simulationPanel);TextView simText=new TextView(this);simText.setText("模拟输入：1650 mV（不连接真实硬件）");simulationPanel.addView(simText);simAdc=new SeekBar(this);simAdc.setMax(3300);simAdc.setProgress(1650);simulationPanel.addView(simAdc);simAdc.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int v,boolean user){simText.setText("模拟输入："+v+" mV");if(user&&simMode.isChecked())virtualBoard.sample(v);}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){if(simMode.isChecked()&&!busy)manual("get_state",Collections.emptyMap());}});simulationPanel.setVisibility(View.GONE);
        dashboard=new Dashboard(this,this::manual);root.addView(dashboard);modes.setOnCheckedChangeListener((group,id)->{if(scanning!=null&&scanner!=null){try{scanner.stopScan(scanning);}catch(Exception ignored){}scanning=null;}dashboard.reset();live.setChecked(false);capView.setText("设备能力：尚未读取");status.setText(simMode.isChecked()?"模拟模式 · 不操作实物":"请读取当前设备能力");simulationPanel.setVisibility(simMode.isChecked()?View.VISIBLE:View.GONE);if(simMode.isChecked())manual("describe",Collections.emptyMap());});
        android.text.TextWatcher resetOnEdit=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(!simMode.isChecked()){dashboard.reset();live.setChecked(false);capView.setText("设备能力：尚未读取");status.setText("连接配置已更改，请重新读取");}}public void afterTextChanged(android.text.Editable e){}};ip.addTextChangedListener(resetOnEdit);token.addTextChangedListener(resetOnEdit);
        label("04  大模型 API",20);endpoint=field("HTTPS Chat Completions 完整地址","https://api.openai.com/v1/chat/completions",false);model=field("模型 ID","",false);key=field("API Key（仅本次运行，不保存）","",true);
        label("05  执行记录",20);log=label("尚无执行记录。",14);log.setTextIsSelectable(true);
    }
    void note(String x){main.post(()->{if(isDestroyed())return;String old=log.getText().toString();log.setText(x+"\n\n"+old.substring(0,Math.min(old.length(),5000)));});}
    boolean granted(String p){return checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED;}
    void listen(){if(!granted(Manifest.permission.RECORD_AUDIO)){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},10);return;}if(!SpeechRecognizer.isRecognitionAvailable(this)){note("系统未提供语音识别，请使用输入框。");return;}if(speech!=null)speech.destroy();speech=SpeechRecognizer.createSpeechRecognizer(this);speech.setRecognitionListener(new RecognitionListener(){public void onReadyForSpeech(Bundle b){note("正在聆听…");}public void onBeginningOfSpeech(){}public void onRmsChanged(float x){}public void onBufferReceived(byte[] b){}public void onEndOfSpeech(){}public void onError(int e){note("语音识别失败："+e+"，可改用文字输入。");}public void onResults(Bundle b){ArrayList<String> list=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(list!=null&&!list.isEmpty())command.setText(list.get(0));}public void onPartialResults(Bundle b){}public void onEvent(int a,Bundle b){}});Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"zh-CN");speech.startListening(i);}
    void scanBle(){if(busy||scanning!=null)return;if(!bleMode.isChecked()){note("请先选择 BLE 模式");return;}String[] ps=Build.VERSION.SDK_INT>=31?new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT}:new String[]{Manifest.permission.ACCESS_FINE_LOCATION};for(String p:ps)if(!granted(p)){requestPermissions(ps,11);return;}
        BluetoothManager mgr=getSystemService(BluetoothManager.class);BluetoothAdapter a=mgr==null?null:mgr.getAdapter();if(a==null||!a.isEnabled()){note("请先开启手机蓝牙");return;}scanner=a.getBluetoothLeScanner();if(scanner==null){note("蓝牙扫描不可用");return;}
        Map<String,BluetoothDevice> found=new LinkedHashMap<>();status.setText("正在扫描 LingIoT 设备…");
        scanning=new ScanCallback(){@Override public void onScanResult(int t,ScanResult r){main.post(()->found.put(r.getDevice().getAddress(),r.getDevice()));} @Override public void onScanFailed(int e){note("扫描失败："+e);}};
        ScanCallback active=scanning;try{scanner.startScan(Collections.singletonList(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(BleLink.SERVICE)).build()),new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),active);}catch(Exception e){scanning=null;note("扫描启动失败");return;}
        main.postDelayed(()->{if(scanning!=active)return;try{scanner.stopScan(active);}catch(Exception ignored){}scanning=null;if(isFinishing()||isDestroyed()||!bleMode.isChecked()||!foreground)return;status.setText("请选择设备");if(found.isEmpty()){note("没有发现设备，请检查固件和蓝牙权限");return;}BluetoothDevice[] ds=found.values().toArray(new BluetoothDevice[0]);String[] names=new String[ds.length];for(int i=0;i<ds.length;i++)names[i]=(ds[i].getName()==null?"LingIoT":ds[i].getName())+"  "+ds[i].getAddress();new AlertDialog.Builder(this).setTitle("选择 ESP32").setItems(names,(d,n)->connect(ds[n])).show();},5000);
    }
    void setBusy(boolean b){busy=b;send.setEnabled(!b);scan.setEnabled(!b);for(int i=0;i<modes.getChildCount();i++)modes.getChildAt(i).setEnabled(!b);ip.setEnabled(!b);token.setEnabled(!b);simAdc.setEnabled(!b);}
    void connect(BluetoothDevice d){if(busy||!bleMode.isChecked())return;dashboard.reset();live.setChecked(false);setBusy(true);capView.setText("设备能力：尚未读取");status.setText("连接中…");worker.execute(()->{BleLink next=new BleLink();try{if(ble!=null)ble.close();ble=null;next.connect(this,d);ble=next;main.post(()->status.setText("BLE 已连接："+d.getAddress()));}catch(Exception e){next.close();note("连接失败");main.post(()->status.setText("设备：未连接"));}finally{main.post(()->setBusy(false));}});}
    Map<String,Object> args(Object... pairs){Map<String,Object> out=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)out.put((String)pairs[i],pairs[i+1]);return out;}
    void manual(String op,Map<String,Object> values){execute(op,values);}
    void execute(String manualOp,Map<String,Object> manualArgs){execute(manualOp,manualArgs,false);}
    void execute(String manualOp,Map<String,Object> manualArgs,boolean automatic){if(busy||scanning!=null){if(!automatic)note("当前正在通信，请在回执后重试（不会排队执行）");return;}final String address=endpoint.getText().toString().trim(),k=key.getText().toString().trim(),m=model.getText().toString().trim(),text=command.getText().toString().trim(),host=ip.getText().toString().trim(),secret=token.getText().toString().trim();final boolean useBle=bleMode.isChecked(),virtual=simMode.isChecked();if(!virtual&&(secret.length()<8||secret.length()>64||!secret.matches("[A-Za-z0-9_-]+"))){note("请输入 8–64 位设备口令（字母、数字、_、-）");if(automatic)live.setChecked(false);return;}if(useBle&&ble==null){note("请先连接 ESP32");if(automatic)live.setChecked(false);return;}if(!virtual&&!useBle&&!Wire.privateIp(host)){note("仅允许私有局域网 IPv4");if(automatic)live.setChecked(false);return;}if(manualOp==null&&text.isEmpty()){note("请输入指令");return;}
        final BleLink session=ble;final Map<String,Object> selectedArgs=manualArgs==null?Collections.emptyMap():new LinkedHashMap<>(manualArgs);
        setBusy(true);worker.execute(()->{boolean attempted=false;try{
            final Network localNetwork=useBle||virtual?null:NetRoute.local(this,host);
            if(!virtual&&!useBle&&localNetwork==null)throw new Exception("未找到到目标设备的本地 Wi-Fi 路由；为避免误走公网，本次请求已阻止");
            DeviceProtocol device=virtual?new DeviceProtocol(new VirtualLink(virtualBoard,SystemClock::elapsedRealtime),VirtualLink.TOKEN):new DeviceProtocol(raw->useBle?session.exchange(raw):Api.post(localNetwork,"http://"+host+"/command","",new JSONObject(raw)),secret);
            Capabilities caps=device.describe();main.post(()->{capView.setText("设备能力："+caps.operations().size()+" 项");dashboard.showCapabilities(caps,virtual);});
            if("describe".equals(manualOp)){Map<String,Object> result=device.execute(caps,caps.validate("get_state",Collections.emptyMap()));main.post(()->{dashboard.showState(result,virtual);status.setText(virtual?"模拟设备已就绪":"设备已响应");});note("能力读取成功："+caps.operations().keySet());return;}
            Capabilities.Action action=manualOp==null?Api.action(NetRoute.internet(this),address,k,m,text,caps):caps.validate(manualOp,selectedArgs);
            if(!automatic)note("拟执行："+action);attempted=true;
            Map<String,Object> result=device.execute(caps,action);if(!automatic)note((virtual?"模拟回执 / 状态：":"设备回执 / 状态：")+result);
            main.post(()->{dashboard.showState(result,virtual);status.setText(virtual?"模拟 · 回执已验证":useBle?"BLE · 回执已验证":"Wi-Fi · 回执已验证");});
        }catch(Exception e){note((attempted?"执行未确认，请勿盲目重试：":"未发送控制命令：")+e.getMessage());main.post(()->{status.setText("本次请求未完成");dashboard.invalidateState();live.setChecked(false);});}finally{main.post(()->setBusy(false));}});
    }
    @Override public void onRequestPermissionsResult(int n,String[] p,int[] r){super.onRequestPermissionsResult(n,p,r);for(int x:r)if(x!=PackageManager.PERMISSION_GRANTED){note("权限未授权");return;}if(r.length==0)return;if(n==10)listen();if(n==11)scanBle();}
    @Override protected void onStart(){super.onStart();foreground=true;main.postDelayed(poll,3000);}
    @Override protected void onStop(){foreground=false;main.removeCallbacks(poll);super.onStop();}
    @Override public void onDestroy(){if(scanning!=null&&scanner!=null){try{scanner.stopScan(scanning);}catch(Exception ignored){}scanning=null;}if(speech!=null)speech.destroy();if(ble!=null)ble.close();worker.shutdownNow();super.onDestroy();}
}
