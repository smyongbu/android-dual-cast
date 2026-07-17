package com.example.androiddualcast.receiver;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;

import com.example.androiddualcast.receiver.adb.WirelessAdbManager;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.interfaces.RSAPublicKey;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 通过车机 USB Host 完成 ADB 授权并开启手机 tcpip:5555。 */
public final class UsbAdbInitializer {
    public interface Listener { void onStatus(String message); void onSuccess(); void onError(String message); }
    private static final String ACTION_USB_PERMISSION = "com.example.androiddualcast.receiver.USB_ADB_PERMISSION";
    private static final int A_CNXN=0x4e584e43, A_AUTH=0x48545541, A_OPEN=0x4e45504f;
    private static final int A_OKAY=0x59414b4f, A_WRTE=0x45545257, A_CLSE=0x45534c43;
    private static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor();

    private UsbAdbInitializer() {}

    public static void start(Activity activity, Listener listener) {
        UsbManager manager=(UsbManager)activity.getSystemService(Context.USB_SERVICE);
        DevicePort port=findAdbDevice(manager);
        if(port==null){listener.onError("没有检测到 USB ADB 设备。请确认车机 USB 口支持 OTG/主机模式，并使用数据线连接手机。");return;}
        if(manager.hasPermission(port.device)){run(activity,manager,port,listener);return;}
        listener.onStatus("请允许车机访问 USB 设备…");
        BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
            if(!ACTION_USB_PERMISSION.equals(i.getAction()))return;
            try{activity.unregisterReceiver(this);}catch(Exception ignored){}
            UsbDevice device=Build.VERSION.SDK_INT>=33?i.getParcelableExtra(UsbManager.EXTRA_DEVICE,UsbDevice.class):(UsbDevice)i.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if(i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false)&&device!=null){DevicePort granted=findAdbDevice(manager);if(granted!=null)run(activity,manager,granted,listener);else listener.onError("USB 授权后未找到 ADB 接口");}
            else listener.onError("USB 访问权限被拒绝");
        }};
        IntentFilter filter=new IntentFilter(ACTION_USB_PERMISSION);
        if(Build.VERSION.SDK_INT>=33)activity.registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED);else activity.registerReceiver(receiver,filter);
        Intent intent=new Intent(ACTION_USB_PERMISSION).setPackage(activity.getPackageName());
        int flags=PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0);
        manager.requestPermission(port.device,PendingIntent.getBroadcast(activity,0,intent,flags));
    }

    private static void run(Activity activity,UsbManager manager,DevicePort port,Listener listener){
        listener.onStatus("USB 已连接，请在手机上允许 USB 调试并勾选始终允许…");
        EXECUTOR.execute(()->{try{initialize(activity,manager,port);activity.runOnUiThread(listener::onSuccess);}catch(Exception e){AppLog.write(activity,"USB ADB","初始化失败："+e);activity.runOnUiThread(()->listener.onError(e.getMessage()==null?e.toString():e.getMessage()));}});
    }

    private static void initialize(Context context,UsbManager manager,DevicePort port)throws Exception{
        UsbDeviceConnection connection=manager.openDevice(port.device);if(connection==null)throw new Exception("无法打开 USB 设备");
        try{
            if(!connection.claimInterface(port.intf,true))throw new Exception("无法占用手机 ADB USB 接口");
            WirelessAdbManager keys=WirelessAdbManager.getInstance(context);
            send(connection,port.out,A_CNXN,0x01000001,4096,"host::features=shell_v2,cmd\0".getBytes("UTF-8"));
            boolean sentSignature=false,sentKey=false,connected=false;long end=System.currentTimeMillis()+60000;
            while(System.currentTimeMillis()<end&&!connected){Packet p=read(connection,port.in,3000);if(p==null)continue;
                if(p.command==A_CNXN){connected=true;break;}
                if(p.command==A_AUTH&&p.arg0==1){
                    if(!sentSignature){send(connection,port.out,A_AUTH,2,0,sign(keys,p.data));sentSignature=true;}
                    else if(!sentKey){send(connection,port.out,A_AUTH,3,0,publicKey(keys));sentKey=true;}
                }
            }
            if(!connected)throw new Exception("手机没有完成 USB 调试授权，请查看手机确认窗口");
            send(connection,port.out,A_OPEN,1,0,"tcpip:5555\0".getBytes("UTF-8"));
            boolean opened=false;end=System.currentTimeMillis()+10000;
            while(System.currentTimeMillis()<end){Packet p=read(connection,port.in,2000);if(p==null)continue;
                if(p.command==A_OKAY){opened=true;break;}
                if(p.command==A_WRTE){send(connection,port.out,A_OKAY,p.arg1,p.arg0,new byte[0]);opened=true;}
                if(p.command==A_CLSE){opened=true;break;}
            }
            if(!opened)throw new Exception("手机已授权，但开启网络 ADB 端口失败");
            AppLog.write(context,"USB ADB","已通过有线连接请求开启 tcpip:5555");
        }finally{try{connection.releaseInterface(port.intf);}catch(Exception ignored){}connection.close();}
    }

    private static byte[] sign(WirelessAdbManager keys,byte[] token)throws Exception{Class<?> c=Class.forName("io.github.muntashirakon.adb.AndroidPubkey");Method m=c.getDeclaredMethod("adbAuthSign",java.security.PrivateKey.class,byte[].class);m.setAccessible(true);return(byte[])m.invoke(null,keys.usbPrivateKey(),token);}
    private static byte[] publicKey(WirelessAdbManager keys)throws Exception{Class<?> c=Class.forName("io.github.muntashirakon.adb.AndroidPubkey");Method m=c.getDeclaredMethod("encodeWithName",RSAPublicKey.class,String.class);m.setAccessible(true);byte[] raw=(byte[])m.invoke(null,(RSAPublicKey)keys.usbPublicKey(),"安卓双端投屏");byte[] out=new byte[raw.length+1];System.arraycopy(raw,0,out,0,raw.length);return out;}

    private static void send(UsbDeviceConnection c,UsbEndpoint out,int command,int arg0,int arg1,byte[] data)throws Exception{ByteBuffer b=ByteBuffer.allocate(24+data.length).order(ByteOrder.LITTLE_ENDIAN);int sum=0;for(byte v:data)sum+=v&255;b.putInt(command).putInt(arg0).putInt(arg1).putInt(data.length).putInt(sum).putInt(command^0xffffffff).put(data);byte[] packet=b.array();int n=c.bulkTransfer(out,packet,packet.length,5000);if(n!=packet.length)throw new Exception("USB ADB 写入失败");}
    private static Packet read(UsbDeviceConnection c,UsbEndpoint in,int timeout)throws Exception{byte[] header=new byte[24];int n=c.bulkTransfer(in,header,24,timeout);if(n<0)return null;if(n!=24)throw new Exception("USB ADB 数据头不完整："+n);ByteBuffer b=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);Packet p=new Packet();p.command=b.getInt();p.arg0=b.getInt();p.arg1=b.getInt();int length=b.getInt();b.getInt();int magic=b.getInt();if((p.command^0xffffffff)!=magic||length<0||length>1024*1024)throw new Exception("USB ADB 数据无效");p.data=new byte[length];int offset=0;while(offset<length){byte[] part=new byte[length-offset];int got=c.bulkTransfer(in,part,part.length,timeout);if(got<=0)throw new Exception("USB ADB 数据不完整");System.arraycopy(part,0,p.data,offset,got);offset+=got;}return p;}
    private static DevicePort findAdbDevice(UsbManager manager){for(UsbDevice d:manager.getDeviceList().values())for(int i=0;i<d.getInterfaceCount();i++){UsbInterface f=d.getInterface(i);if(f.getInterfaceClass()!=255||f.getInterfaceSubclass()!=66||f.getInterfaceProtocol()!=1)continue;UsbEndpoint in=null,out=null;for(int e=0;e<f.getEndpointCount();e++){UsbEndpoint ep=f.getEndpoint(e);if(ep.getType()!=UsbConstants.USB_ENDPOINT_XFER_BULK)continue;if(ep.getDirection()==UsbConstants.USB_DIR_IN)in=ep;else out=ep;}if(in!=null&&out!=null)return new DevicePort(d,f,in,out);}return null;}
    private static final class DevicePort{final UsbDevice device;final UsbInterface intf;final UsbEndpoint in,out;DevicePort(UsbDevice d,UsbInterface f,UsbEndpoint i,UsbEndpoint o){device=d;intf=f;in=i;out=o;}}
    private static final class Packet{int command,arg0,arg1;byte[]data;}
}
