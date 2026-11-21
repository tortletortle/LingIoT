package com.lingiot.demo;
import android.bluetooth.*;
import android.content.Context;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
public class BleLink implements AutoCloseable {
    public static final UUID SERVICE=UUID.fromString("c48c0001-8444-4348-a923-342bc1eaa001"), RX=UUID.fromString("c48c0002-8444-4348-a923-342bc1eaa001"), TX=UUID.fromString("c48c0003-8444-4348-a923-342bc1eaa001"), CCC=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private volatile BluetoothGatt gatt; private volatile BluetoothGattCharacteristic rx; private volatile boolean connected;
    private final BlockingQueue<Boolean> ready=new LinkedBlockingQueue<>(), writes=new LinkedBlockingQueue<>();
    private final BlockingQueue<String> frames=new LinkedBlockingQueue<>(); private final Wire wire=new Wire();
    private final BluetoothGattCallback cb=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt g,int status,int state){
            if(status==BluetoothGatt.GATT_SUCCESS && state==BluetoothProfile.STATE_CONNECTED){if(!g.discoverServices())ready.offer(false);}
            else{connected=false; ready.offer(false); writes.offer(false);}
        }
        @Override public void onServicesDiscovered(BluetoothGatt g,int status){
            BluetoothGattService svc=g.getService(SERVICE); if(status!=0||svc==null){ready.offer(false);return;}
            rx=svc.getCharacteristic(RX); BluetoothGattCharacteristic tx=svc.getCharacteristic(TX);
            if(rx==null||tx==null||!g.setCharacteristicNotification(tx,true)){ready.offer(false);return;}
            BluetoothGattDescriptor d=tx.getDescriptor(CCC); if(d==null){ready.offer(false);return;}
            d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE); if(!g.writeDescriptor(d))ready.offer(false);
        }
        @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int status){connected=status==0;ready.offer(connected);}
        @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int status){writes.offer(status==0);}
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){wire.feed(c.getValue(),frames::offer);}
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value){wire.feed(value,frames::offer);}
    };
    public void connect(Context context,BluetoothDevice device)throws Exception{
        gatt=device.connectGatt(context,false,cb,BluetoothDevice.TRANSPORT_LE); if(gatt==null||!Boolean.TRUE.equals(ready.poll(15,TimeUnit.SECONDS))){close();throw new Exception("BLE连接或订阅失败");}
    }
    public synchronized String exchange(String request)throws Exception{
        if(!connected||rx==null||gatt==null)throw new Exception("BLE未连接");
        try{frames.clear(); wire.reset(); byte[] b=(request+"\n").getBytes(StandardCharsets.UTF_8); if(b.length>Wire.LIMIT+1)throw new Exception("指令过长");
            for(int i=0;i<b.length;i+=Wire.CHUNK){writes.clear();rx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);rx.setValue(Arrays.copyOfRange(b,i,Math.min(i+Wire.CHUNK,b.length)));if(!gatt.writeCharacteristic(rx)||!Boolean.TRUE.equals(writes.poll(4,TimeUnit.SECONDS)))throw new Exception("BLE写入失败");}
            String reply=frames.poll(8,TimeUnit.SECONDS);if(reply==null)throw new Exception("设备响应超时，执行状态未知");return reply;
        }catch(Exception e){close();throw e;}
    }
    @Override public void close(){connected=false;BluetoothGatt g=gatt;gatt=null;if(g!=null){try{g.disconnect();g.close();}catch(Exception ignored){}}}
}
