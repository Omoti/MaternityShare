package com.ohmaker.maternityshare.activity;

import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import com.ohmaker.maternityshare.dialog.DelayDialog;
import com.ohmaker.maternityshare.dialog.PeerListDialog;
import com.ohmaker.maternityshare.preference.PreferenceKey;
import com.ohmaker.maternityshare.R;
import com.ohmaker.maternityshare.bluesky.BlueSkyService;

public class FatherActivity extends AppCompatActivity implements PeerListDialog.PeerListDialogInterface, BlueSkyService.IServiceCallback, DelayDialog.DelayDialogInterface {
    private Handler mHandler = new Handler();

    //Bluetooth and SkyWay Service
    private BlueSkyService mBlueSkyService = null;
    private ServiceConnection mServiceConnection = null;

    private int mDelay = 200;

    //Log
    private StringBuilder mLogBuilder = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_father);

        //Service Connection
        mServiceConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder service) {
                mBlueSkyService = ((BlueSkyService.DeviceConnectionServiceBinder)service).getService();
                mBlueSkyService.registerCallback(FatherActivity.this);

                //接続済みBluetoothデバイスを設定値から取得
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(FatherActivity.this);
                String address = preferences.getString(PreferenceKey.PREFERENCE_DEVICE, null);

                //接続開始
                if (address != null) {
                    mBlueSkyService.connectBluetooth(address);
                }
            }

            public void onServiceDisconnected(ComponentName className) {
                mBlueSkyService.unregisterCallback();
                mBlueSkyService = null;
            }
        };

        //Service開始
        Intent intent = new Intent(this, BlueSkyService.class);
        this.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);

        //Bluetooth選択
        findViewById(R.id.bt_bluetooth_select).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(FatherActivity.this, BluetoothDeviceListActivity.class));
            }
        });

        //SkyWay接続
        findViewById(R.id.bt_sky_connect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBlueSkyService.requestSkyWayPeerList();
            }
        });

        //パパクリック
        findViewById(R.id.img_papa).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startChildAnimation();

                //ポコン
                mBlueSkyService.sendCommandToBluetoothDevice(BlueSkyService.SIGNAL_ON);

                //戻す
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mBlueSkyService.sendCommandToBluetoothDevice(BlueSkyService.SIGNAL_OFF);
                    }
                },mDelay);

            }
        });
    }

    @Override
    protected void onResume(){
        super.onResume();
    }

    @Override
    protected void onStop(){
        super.onStop();
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();

        try{
            unbindService(mServiceConnection);
        }catch (Exception e){

        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event){

        //戻るボタン押下時に接続解除
        if (keyCode == KeyEvent.KEYCODE_BACK){
            if (mBlueSkyService != null){
                mBlueSkyService.disconnectSkyWay();
                mBlueSkyService.disconnectBluetooth();
            }

            if (mServiceConnection != null){
                unbindService(mServiceConnection);
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_father, menu);
        return true;
    }

    //デバッグ用メニュー
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id){
            case R.id.action_bt_test_on:
                mBlueSkyService.sendCommandToBluetoothDevice(BlueSkyService.SIGNAL_ON);
                break;
            case R.id.action_bt_test_off:
                mBlueSkyService.sendCommandToBluetoothDevice(BlueSkyService.SIGNAL_OFF);
                break;
            case R.id.action_bt_on:
                //接続済みBluetooth Device
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(FatherActivity.this);
                String address = preferences.getString(PreferenceKey.PREFERENCE_DEVICE, null);

                if (address != null) {
                    mBlueSkyService.connectBluetooth(address);
                }
                break;
            case R.id.action_bt_off:
                mBlueSkyService.disconnectBluetooth();
                break;
            case R.id.action_skyway_test:
                mBlueSkyService.sendCommandToSkyWay("Hello!");
                break;
            case R.id.action_skyway_off:
                mBlueSkyService.disconnectSkyWay();
                break;
            case R.id.action_edit_delay:
                DelayDialog dialog = DelayDialog.newInstance(mDelay);
                dialog.show(getFragmentManager(),null);
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * ハートアニメーション
     */
    private void startChildAnimation(){
        ImageView childView = (ImageView)findViewById(R.id.img_child);

        childView.setVisibility(View.VISIBLE);

        AnimationSet set = new AnimationSet(true);

        AlphaAnimation alphaIn = new AlphaAnimation(0.0f, 1.0f);
        alphaIn.setDuration(300);

        AlphaAnimation alphaOut = new AlphaAnimation(1.0f, 0.0f);
        alphaOut.setStartOffset(300);
        alphaOut.setDuration(300);

        ScaleAnimation scale = new ScaleAnimation(1.0f, 10.0f, 1.0f, 10.0f,childView.getWidth() / 2, childView.getHeight() / 2);
        scale.setDuration(600);

        set.addAnimation(alphaIn);
        set.addAnimation(alphaOut);
        set.addAnimation(scale);
        set.setFillAfter(true);

        childView.startAnimation(set);
    }

    /*
     * Bluetooth Callbacks
     */

    @Override
    public void updateBluetoothStatus(BlueSkyService.BLUETOOTH_STATUS status, final BluetoothDevice device) {
        switch (status){
            case CONNECT_START:
                addLog("Bluetooth Start : " + device.getName());
                break;
            case CONNECTED:
                addLog("Bluetooth Connected : " + device.getName());
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        ((TextView)findViewById(R.id.text_device_name)).setText(device.getName());
                    }
                });
                break;
            case DISCONNECTED:
                addLog("Bluetooth Disconnected");
                break;
            case COMMAND_READY:
                addLog("Bluetooth Ready");
                break;
            case ERROR:
                addLog("Bluetooth Error");
                break;
            default:
        }
    }

    @Override
    public void onSendBluetoothCommand(String command) {
        //addLog("Bluetooth Send : " + command);
    }

    @Override
    public void onReceiveBluetoothCommand(String command) {
        //addLog("Bluetooth Receive : " + command);
    }

    /*
     * SkyWay Callbacks
     */

    @Override
    public void updateSkyWayStatus(BlueSkyService.SKY_WAY_STATUS status, final String id) {
        switch (status){
            case OPEN:
                addLog("SkyWay Open : " + id);
                break;
            case CLOSE:
                addLog("SkyWay Close");
                break;
            case DISCONNECTED:
                addLog("SkyWay Disconnected");
                break;
            case CONNECTION_OPEN:
                addLog("SkyWay Connection Open");
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        ((TextView) findViewById(R.id.text_skyway_id)).setText("Connected");
                    }
                });
                break;
            case CONNECTION_CLOSE:
                addLog("SkyWay Connection Close");
                break;
            case ERROR:
                addLog("SkyWay Error");
                break;
            default:
        }
    }

    @Override
    public void onSendSkyWayCommand(String command) {
        //addLog("SkyWay Send : " + command);
    }

    @Override
    public void onReceiveSkyWayCommand(String command) {
        //addLog("SkyWay Receive : " + command);

        if (command != null && command.length() > 0) {
            mBlueSkyService.sendCommandToBluetoothDevice(command);

            if (!command.startsWith("0")) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        startChildAnimation();
                    }
                });
            }
        }

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mBlueSkyService.sendCommandToBluetoothDevice("0");
            }
        }, mDelay);
    }

    @Override
    public void onGetPeerList(final String[] peerList) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                PeerListDialog dialog = PeerListDialog.newInstance(peerList);
                dialog.show(getFragmentManager(), null);
            }
        });
    }

    @Override
    public void onPeerSelected(String peer) {
        addLog("SkyWay Select Peer : " + peer);

        mBlueSkyService.connectSkyWay(peer);
    }

    /**
     * ログ
     * @param log
     */
    private void addLog(final String log){
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mLogBuilder.insert(0, "\n");
                mLogBuilder.insert(0, log);

                ((TextView) findViewById(R.id.text_log)).setText(mLogBuilder.toString());
            }
        });
    }

    @Override
    public void onDelayChanged(int delay) {
        mDelay = delay;
    }
}
