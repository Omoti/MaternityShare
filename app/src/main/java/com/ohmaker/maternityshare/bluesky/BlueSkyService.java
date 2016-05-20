package com.ohmaker.maternityshare.bluesky;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Set;
import java.util.UUID;

import io.skyway.Peer.ConnectOption;
import io.skyway.Peer.DataConnection;
import io.skyway.Peer.OnCallback;
import io.skyway.Peer.Peer;
import io.skyway.Peer.PeerOption;

/*
 * BluetoothとSkyWayをはしごするService
 */
public class BlueSkyService extends Service {
    private static final String LOG_TAG = "BlueSkyService";

    public static final String SIGNAL_ON = "1"; //オン
    public static final String SIGNAL_OFF = "0"; //オフ

    //UUID
    private static final UUID UUID_SERIAL = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    //SkyWay
    private static final String SKY_WAY_KEY = "e3c962fa-600f-4ebb-8b8d-6dedcc207a7d";
    private static final String SKY_WAY_DOMAIN = "ohmaker.com";

    //Binder
    private final IBinder mBinder = new DeviceConnectionServiceBinder();

    //接続中のBluetoothデバイス
    private BluetoothDevice mDevice = null;

    //BluetoothAdapter
    private BluetoothAdapter mBluetoothAdapter = null;

    //BluetoothSocket
    private BluetoothSocket mSocket = null;

    //Bluetoothコマンド送受信
    BluetoothCommandThread mBluetoothThread = null;
    boolean isBluetoothRunning = true;

    //SkyWay Peer
    private Peer mSkyWayPeer = null;

    //SkyWay DataConnection
    private DataConnection mSkyWayDataConnection = null;

    //SkyWay ID
    private String mSkyWayID = null;

    //Debug用
    private boolean isLedOn = false;

    private IServiceCallback mCallback = null;

    /**
     * Bluetooth Status
     */
    public enum BLUETOOTH_STATUS{
        CONNECT_START,
        CONNECTED,
        DISCONNECTED,
        COMMAND_READY,
        ERROR
    }

    /**
     * SkyWay Status
     */
    public enum SKY_WAY_STATUS{
        OPEN,
        CLOSE,
        DISCONNECTED,
        CONNECTION_OPEN,
        CONNECTION_CLOSE,
        ERROR,
    }

    /**
     * Callback
     */
    public interface IServiceCallback{
        //Bluetooth
        void updateBluetoothStatus(BLUETOOTH_STATUS status, BluetoothDevice device);
        void onSendBluetoothCommand(String command);
        void onReceiveBluetoothCommand(String command);

        //SkyWay
        void updateSkyWayStatus(SKY_WAY_STATUS status, String id);
        void onSendSkyWayCommand(String command);
        void onReceiveSkyWayCommand(String command);
        void onGetPeerList(String[] peerList);
    }

    /**
     * Binder
     */
    public class DeviceConnectionServiceBinder extends Binder {

        public BlueSkyService getService() {
            return BlueSkyService.this;
        }
    }

    public BlueSkyService() {

    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(LOG_TAG, "onBind");

        /*
         * Bluetooth初期設定
         */

        //Bluetooth Adapter取得
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        //Bluetooth Adapterが見つからない
        if (mBluetoothAdapter == null) {
            Log.d(LOG_TAG, "BluetoothAdapter is not found.");
        }

        /*
         * SkyWay初期設定
         */

        // connect option
        PeerOption options = new PeerOption();

        //Enter your API Key.
        options.key = SKY_WAY_KEY;
        options.domain = SKY_WAY_DOMAIN;

        mSkyWayPeer = new Peer(this, options);
        setPeerCallback(mSkyWayPeer);

        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent){
        Log.d(LOG_TAG, "onUnbind");

        closeBluetooth();

        return super.onUnbind(intent);
    }

    /**
     * コールバック登録
     * @param callback
     */
    public void registerCallback(IServiceCallback callback){
        mCallback = callback;
    }

    /**
     * コールバック登録解除
     */
    public void unregisterCallback(){
        mCallback = null;
    }

    /**
     * Bluetooth開始
     * @param address
     */
    public void connectBluetooth(String address){
        mDevice = getBluetoothDevice(address);

        if (mDevice != null){
            startBluetoothThread();
        }
    }

    /**
     * Bluetooth切断
     */
    public void disconnectBluetooth(){
        closeBluetooth();

        mDevice = null;
        mSocket = null;

        if (mCallback != null) {
            mCallback.updateBluetoothStatus(BLUETOOTH_STATUS.DISCONNECTED, null);
        }
    }

    /**
     * MACアドレスからBluetoothデバイスを取得する
     * @param address
     * @return
     */
    private BluetoothDevice getBluetoothDevice(String address){
        //Bluetooth Adapter取得
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        //Bluetoothが見つからない
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
        }

        //接続履歴のあるデバイス
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0){
            for(BluetoothDevice device : pairedDevices){
                if (device.getAddress().equals(address)){
                    return device;
                }
            }
        }

        return null;
    }

    /**
     * Bluetooth接続スレッド開始
     */
    public void startBluetoothThread() {
        Log.d(LOG_TAG, "startBluetoothThread");

        if (mDevice != null) {
            isBluetoothRunning = true;

            new Thread(new Runnable() {
                @Override
                public void run() {
                    mCallback.updateBluetoothStatus(BLUETOOTH_STATUS.CONNECT_START, mDevice);

                    if (connectBluetooth()) {
                        Log.d(LOG_TAG, "Bluetooth : Connected");

                        mCallback.updateBluetoothStatus(BLUETOOTH_STATUS.CONNECTED, mDevice);

                        mBluetoothThread = new BluetoothCommandThread(mSocket);
                        mBluetoothThread.start();
                    } else {
                        Log.d(LOG_TAG, "Bluetooth : Cannot Connect");

                        mCallback.updateBluetoothStatus(BLUETOOTH_STATUS.DISCONNECTED, mDevice);
                    }
                }
            }).start();
        }
    };

    /**
     * BluetoothDeviceに接続
     * @return
     */
    private boolean connectBluetooth(){
        try {
            mSocket = mDevice.createRfcommSocketToServiceRecord(UUID_SERIAL);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mSocket == null){
            return false;
        }

        try{
            mSocket.connect();

            return true;
        }catch(IOException e){
            try {
                mSocket.close();
            } catch (IOException closeException) {
                closeException.printStackTrace();
            } catch (NullPointerException nullException){
                nullException.printStackTrace();
            }
        }

        return false;
    }

    /**
     * Bluetooth切断
     */
    private void closeBluetooth(){
        isBluetoothRunning = false;
        mBluetoothThread = null;

        if (mSocket != null && mSocket.isConnected()) {
            try {
                mSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * BluetoothDeviceにコマンド送信
     * @param command
     */
    public void sendCommandToBluetoothDevice(String command){
        if (mBluetoothThread != null && command != null){
            mBluetoothThread.write(command.getBytes());
        }
    }

    /**
     * Bluetoothコマンド送受信
     */
    private class BluetoothCommandThread extends Thread {

        InputStream inputStream;
        OutputStream outputStream;

        //コンストラクタの定義
        public BluetoothCommandThread(BluetoothSocket socket){
            try {
                //接続済みソケットからI/Oストリームをそれぞれ取得
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();

                mCallback.updateBluetoothStatus(BLUETOOTH_STATUS.COMMAND_READY, null);
            } catch (IOException e1) {
                mCallback.updateBluetoothStatus(BLUETOOTH_STATUS.ERROR, null);
                e1.printStackTrace();
            }
        }

        public void write(byte[] buf){
            try {
                outputStream.write(buf);

                if (buf != null && buf.length > 0){
                    mCallback.onSendBluetoothCommand(new String(buf, "UTF-8"));
                }
            } catch (IOException e) {
                mCallback.updateBluetoothStatus(BLUETOOTH_STATUS.ERROR, null);
                e.printStackTrace();
            }
        }

        public void run() {
            byte[] buf = new byte[1024];
            String data;
            int tmpBuf = 0;

            while(isBluetoothRunning){
                try {
                    tmpBuf = inputStream.read(buf);
                } catch (IOException e) {
                    mCallback.updateBluetoothStatus(BLUETOOTH_STATUS.ERROR, null);
                    e.printStackTrace();
                }
                if(tmpBuf != 0){
                    try {
                        data = new String(buf, "UTF-8");

                        if (data != null && data.length() > 0) {
                            mCallback.onReceiveBluetoothCommand(data);
                        }
                    } catch (UnsupportedEncodingException e) {
                        mCallback.updateBluetoothStatus(BLUETOOTH_STATUS.ERROR, null);
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * SkyWay
     */

    /**
     * SkyWay Peer Callback
     * @param peer
     */
    private void setPeerCallback(Peer peer)
    {
        /**
         * Open
         */
        peer.on(Peer.PeerEventEnum.OPEN, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                if (object instanceof String) {
                    mSkyWayID = (String) object;
                    mCallback.updateSkyWayStatus(SKY_WAY_STATUS.OPEN, mSkyWayID);
                }
            }
        });

        /**
         * Connection
         */
        peer.on(Peer.PeerEventEnum.CONNECTION, new OnCallback()
        {
            @Override
            public void onCallback(Object object)
            {
                if (!(object instanceof DataConnection))
                {
                    return;
                }

                mSkyWayDataConnection = (DataConnection) object;
                setDataCallback(mSkyWayDataConnection);
            }
        });

        /**
         * Close
         */
        peer.on(Peer.PeerEventEnum.CLOSE, new OnCallback()
        {
            @Override
            public void onCallback(Object object)
            {
                mCallback.updateSkyWayStatus(SKY_WAY_STATUS.CLOSE, null);
            }
        });

        /**
         * Disconnected
         */
        peer.on(Peer.PeerEventEnum.DISCONNECTED, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                mCallback.updateSkyWayStatus(SKY_WAY_STATUS.DISCONNECTED, null);
            }
        });

        /**
         * Error
         */
        peer.on(Peer.PeerEventEnum.ERROR, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                mCallback.updateSkyWayStatus(SKY_WAY_STATUS.ERROR, null);
            }
        });
    }

    /**
     * SkyWay DataCallback設定
     * @param data
     */
    void setDataCallback(DataConnection data)
    {
        if (data == null){
            return;
        }

        /**
         * Open
         */
        data.on(DataConnection.DataEventEnum.OPEN, new OnCallback()
        {
            @Override
            public void onCallback(Object object)
            {
                mCallback.updateSkyWayStatus(SKY_WAY_STATUS.CONNECTION_OPEN, null);
            }
        });

        /**
         * Receive data
         */
        data.on(DataConnection.DataEventEnum.DATA, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                String value = (String)object;

                if(value != null && value.length() > 0) {
                    mCallback.onReceiveSkyWayCommand(value);

                    if (mBluetoothThread != null) {
                        if (isLedOn) {
                            isLedOn = false;
                            mBluetoothThread.write(SIGNAL_ON.getBytes());
                        } else {
                            isLedOn = true;
                            mBluetoothThread.write(SIGNAL_OFF.getBytes());
                        }
                    }
                }
            }
        });

        /**
         * Close
         */
        data.on(DataConnection.DataEventEnum.CLOSE, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                mCallback.updateSkyWayStatus(SKY_WAY_STATUS.CONNECTION_CLOSE, null);
                mSkyWayDataConnection = null;
            }
        });

        /**
         * Error
         */
        data.on(DataConnection.DataEventEnum.ERROR, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                mCallback.updateSkyWayStatus(SKY_WAY_STATUS.ERROR, null);
            }
        });
    }

    /**
     * Peer一覧取得
     * @return
     */
    public void requestSkyWayPeerList(){

        if ((null == mSkyWayPeer) || (null == mSkyWayID) || (mSkyWayID.length() == 0))
        {
            return;
        }

        mSkyWayPeer.listAllPeers(new OnCallback() {
            @Override
            public void onCallback(Object object) {
                final String[] peerList;

                if (!(object instanceof JSONArray)) {
                    return;
                }

                JSONArray peers = (JSONArray) object;

                StringBuilder sbItems = new StringBuilder();
                for (int i = 0; peers.length() > i; i++) {
                    String strValue = "";
                    try {
                        strValue = peers.getString(i);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (mSkyWayID.compareToIgnoreCase(strValue) == 0) {
                        continue;
                    }

                    if (sbItems.length() > 0) {
                        sbItems.append(",");
                    }

                    sbItems.append(strValue);
                }

                String strItems = sbItems.toString();
                peerList = strItems.split(",");

                if ((null != peerList) && (0 < peerList.length)) {
                    mCallback.onGetPeerList(peerList);
                }
            }
        });
    }

    /**
     * SkyWay接続
     * @param strPeerId
     */
    public void connectSkyWay(String strPeerId)
    {
        if (mSkyWayPeer == null)
        {
            return;
        }

        if (mSkyWayDataConnection != null)
        {
            mSkyWayDataConnection.close();
            mSkyWayDataConnection = null;
        }

        ConnectOption option = new ConnectOption();
        option.serialization = DataConnection.SerializationEnum.BINARY;

        // connect
        mSkyWayDataConnection = mSkyWayPeer.connect(strPeerId, option);

        if (mSkyWayDataConnection != null) {
            setDataCallback(mSkyWayDataConnection);
        }
    }

    /**
     * SkyWay切断
     */
    public void disconnectSkyWay(){
        if (mSkyWayDataConnection != null){
            if (mSkyWayDataConnection.isOpen) {
                mSkyWayDataConnection.close();
            }
        }

        if (mSkyWayPeer != null){
            if (!mSkyWayPeer.isDisconnected){
                mSkyWayPeer.disconnect();
            }

            if (!mSkyWayPeer.isDestroyed){
                mSkyWayPeer.destroy();
            }
        }

        if (mCallback != null) {
            mCallback.updateSkyWayStatus(SKY_WAY_STATUS.DISCONNECTED, null);
        }
    }

    /**
     * コマンド送信
     * @param command
     */
    public void sendCommandToSkyWay(String command){
        if (mSkyWayDataConnection != null){
            mSkyWayDataConnection.send(command);
            mCallback.onSendSkyWayCommand(command);
        }
    }

}
