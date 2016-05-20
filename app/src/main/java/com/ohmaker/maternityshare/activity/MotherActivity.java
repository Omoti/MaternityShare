package com.ohmaker.maternityshare.activity;

import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import com.ohmaker.maternityshare.dialog.FFTDialog;
import com.ohmaker.maternityshare.view.FftGraphView;
import com.ohmaker.maternityshare.dialog.PeerListDialog;
import com.ohmaker.maternityshare.preference.PreferenceKey;
import com.ohmaker.maternityshare.R;
import com.ohmaker.maternityshare.dialog.ThresholdDialog;
import com.ohmaker.maternityshare.view.VolumeGraphView;
import com.ohmaker.maternityshare.bluesky.BlueSkyService;

import org.jtransforms.fft.DoubleFFT_1D;


public class MotherActivity extends AppCompatActivity implements PeerListDialog.PeerListDialogInterface, BlueSkyService.IServiceCallback, ThresholdDialog.ThresholdDialogInterface, FFTDialog.FFTDialogInterface {
    private Handler mHandler = new Handler();

    //Bluetooth Service
    private BlueSkyService mBlueSkyService = null;
    private ServiceConnection mServiceConnection = null;

    //Audio
    private final static int SAMPLING_RATE = 44100;//8000;//11025;
    private AudioRecord mAudioRec = null;
    private int mBufSize;
    private boolean isRecording = false;

    private long mLastTimeMills = 0;
    private static final long AUDIO_INTERVAL = 1000;
    private int mThreshold = 50000;
    private int mMin = 300;
    private int mMax = 800;

    private boolean isDemo = false;
    private int mDelay = 200;

    final static int FFT_SIZE = 4096;

    //Log
    private StringBuilder mLogBuilder = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mother);

        //デモモード判定
        isDemo = getIntent().getBooleanExtra("IS_DEMO", false);

        //Service Connection
        mServiceConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder service) {
                mBlueSkyService = ((BlueSkyService.DeviceConnectionServiceBinder)service).getService();
                mBlueSkyService.registerCallback(MotherActivity.this);

                if (isDemo) {
                    //接続済みBluetooth Device
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MotherActivity.this);
                    String address = preferences.getString(PreferenceKey.PREFERENCE_DEVICE, null);

                    if (address != null) {
                        mBlueSkyService.connectBluetooth(address);
                    }
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

        //音声切り替え
        findViewById(R.id.bt_audio_switch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRecording){
                    stopRecording();
                }else{
                    startRecording();
                }
                //startActivity(new Intent(MotherActivity.this, BluetoothDeviceListActivity.class));
            }
        });

        //SkyWay接続
        findViewById(R.id.bt_sky_connect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isDemo) {
                    mBlueSkyService.requestSkyWayPeerList();
                }else{
                    startActivity(new Intent(MotherActivity.this, BluetoothDeviceListActivity.class));
                }
            }
        });

        //ママクリック
        findViewById(R.id.img_mama).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startChildAnimation();

                if (isDemo) {
                    //デモ用にBluetoothで直接通信
                    mBlueSkyService.sendCommandToBluetoothDevice(BlueSkyService.SIGNAL_ON);

                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mBlueSkyService.sendCommandToBluetoothDevice(BlueSkyService.SIGNAL_OFF);
                        }
                    },mDelay);
                }else{
                    mBlueSkyService.sendCommandToSkyWay(BlueSkyService.SIGNAL_ON);
                }
            }
        });

        //音量設定
        findViewById(R.id.volume_graph).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ThresholdDialog dialog = ThresholdDialog.newInstance(mThreshold);
                dialog.show(getFragmentManager(), null);
            }
        });

        //FFT設定
        findViewById(R.id.fft_graph).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FFTDialog dialog = new FFTDialog().newInstance(mThreshold,mMin,mMax);
                dialog.show(getFragmentManager(), null);
            }
        });

        ((FftGraphView)findViewById(R.id.fft_graph)).setThreshold(mMin, mMax, mThreshold);
    }

    @Override
    protected void onResume(){
        super.onResume();

        // バッファサイズの計算
        mBufSize = AudioRecord.getMinBufferSize(
                SAMPLING_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        // AudioRecordの作成
        mAudioRec = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLING_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                mBufSize);

        if (FFT_SIZE > mBufSize) mBufSize = FFT_SIZE;

        //録音開始
        startRecording();
    }

    @Override
    protected void onStop(){
        super.onStop();

        //録音終了
        stopRecording();
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
        getMenuInflater().inflate(R.menu.menu_mother, menu);
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
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MotherActivity.this);
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
                addLog("Bluetooth : Start (" + device.getName() + ")");
                break;
            case CONNECTED:
                addLog("Bluetooth : Connected (" + device.getName() + ")");
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        ((TextView) findViewById(R.id.text_skyway_id)).setText(device.getName());
                    }
                });
                break;
            case DISCONNECTED:
                addLog("Bluetooth : Disconnected");
                break;
            case COMMAND_READY:
                addLog("Bluetooth : Ready");
                break;
            case ERROR:
                addLog("Bluetooth Error");
                break;
            default:
        }
    }

    @Override
    public void onSendBluetoothCommand(String command) {
        //addLog("Bluetooth : Send (" + command + ")");
    }

    @Override
    public void onReceiveBluetoothCommand(String command) {
        //addLog("Bluetooth : Receive (" + command + ")");

        if (mBlueSkyService != null){
            mBlueSkyService.sendCommandToSkyWay(command);
        }

        if (command != null && !command.startsWith("0")){
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    startChildAnimation();
                }
            });
        }
    }

    /*
     * SkyWay Callbacks
     */

    @Override
    public void updateSkyWayStatus(BlueSkyService.SKY_WAY_STATUS status, final String id) {
        switch (status){
            case OPEN:
                addLog("SkyWay : Open (" + id + ")");
                break;
            case CLOSE:
                addLog("SkyWay : Close");
                break;
            case DISCONNECTED:
                addLog("SkyWay : Disconnected");
                break;
            case CONNECTION_OPEN:
                addLog("SkyWay : Connection Open");
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        ((TextView) findViewById(R.id.text_skyway_id)).setText("Connected");
                    }
                });
                break;
            case CONNECTION_CLOSE:
                addLog("SkyWay : Connection Close");
                break;
            case ERROR:
                addLog("SkyWay : Error");
                break;
            default:
        }
    }

    @Override
    public void onSendSkyWayCommand(String command) {
        //addLog("SkyWay : Send (" + command + ")");
    }

    @Override
    public void onReceiveSkyWayCommand(String command) {
        //addLog("SkyWay : Receive (" + command + ")");
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

    /**
     * 録音開始
     */
    private void startRecording(){
        mAudioRec.startRecording();
        setRecording(true);

        addLog("AudioRecord : Start");

        new Thread(new Runnable() {
            @Override
            public void run() {
                short buf[] = new short[mBufSize];

                //録音データ取得
                while (isRecording()) {
                    mAudioRec.read(buf, 0, buf.length);

                    final short volume[] = buf;

                    //FFT
                    DoubleFFT_1D fft = new DoubleFFT_1D(FFT_SIZE) ;
                    final double[] fftData = new double[FFT_SIZE];
                    for(int i=0;i<FFT_SIZE;i++){
                        fftData[i] = (double) buf[i];
                    }
                    fft.realForward(fftData);

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            ((FftGraphView) findViewById(R.id.fft_graph)).updateData(fftData);
                            ((VolumeGraphView) findViewById(R.id.volume_graph)).updateData(volume);
                        }
                    });

                    long now = System.currentTimeMillis();

                    if (now - mLastTimeMills > AUDIO_INTERVAL) {
                        if (fftData.length > mMax) {

                            //閾値チェック
                            double max = 0;
                            for (int i = mMin; i < mMax; i++) {
                                if (fftData[i] > max){
                                    max = fftData[i];
                                }
                            }

                            Log.d("AudioRecord", String.valueOf(max));

                            //閾値を超えたらポコン
                            if (max > mThreshold) {
                                if (mBlueSkyService != null) {
                                    if (isDemo) {
                                        mBlueSkyService.sendCommandToBluetoothDevice(BlueSkyService.SIGNAL_ON);

                                        mHandler.postDelayed(new Runnable() {
                                            @Override
                                            public void run() {
                                                mBlueSkyService.sendCommandToBluetoothDevice(BlueSkyService.SIGNAL_OFF);
                                            }
                                        },mDelay);
                                    }else{
                                        mBlueSkyService.sendCommandToSkyWay(BlueSkyService.SIGNAL_ON);
                                    }
                                }

                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        startChildAnimation();
                                    }
                                });

                                mLastTimeMills = System.currentTimeMillis();
                            }
                        }
                    }
                }

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        ((VolumeGraphView)findViewById(R.id.volume_graph)).setThreshold(mThreshold);
                    }
                });
            }
        }).start();
    }

    /**
     * 録音停止
     */
    private void stopRecording(){
        setRecording(false);

        if (mAudioRec != null){
            mAudioRec.stop();
        }

        addLog("AudioRecord : Stop");
    }

    /**
     * 録音中フラグ
     * @param recording
     */
    private synchronized void setRecording(boolean recording){
        isRecording = recording;

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isRecording){
                    ((TextView)findViewById(R.id.text_audio_status)).setText("ON");
                }else{
                    ((TextView)findViewById(R.id.text_audio_status)).setText("OFF");
                }
            }
        });
    }

    /**
     * 録音中フラグ取得
     * @return
     */
    private synchronized boolean isRecording(){
        return isRecording;
    }

    @Override
    public void onThresholdChanged(int threshold) {
        mThreshold = threshold;
    }

    @Override
    public void onThresholdChanged(int threshold, int min, int max) {
        mThreshold = threshold;
        mMin = min;
        mMax = max;

        ((FftGraphView)findViewById(R.id.fft_graph)).setThreshold(mMin, mMax, mThreshold);
    }
}
