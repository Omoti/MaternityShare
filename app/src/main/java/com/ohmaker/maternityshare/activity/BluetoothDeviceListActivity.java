package com.ohmaker.maternityshare.activity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.ohmaker.maternityshare.preference.PreferenceKey;
import com.ohmaker.maternityshare.R;

import java.util.ArrayList;
import java.util.List;

/*
 * Bluetoothデバイス一覧画面
 */
public class BluetoothDeviceListActivity extends AppCompatActivity {

    //BluetoothAdapter
    private BluetoothAdapter mBluetoothAdapter = null;

    //ListAdapter
    private ArrayAdapter<BluetoothDevice> mDeviceListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_device_list);

        //Bluetooth Adapter取得
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        //Bluetoothが見つからない
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
        }

        //ListView
        ListView deviceListView = (ListView)findViewById(R.id.list_bt_device);
        List<BluetoothDevice> deviceList = new ArrayList<>();
        mDeviceListAdapter = new BluetoothDeviceListAdapter(this, deviceList);
        deviceListView.setAdapter(mDeviceListAdapter);

        //選択したデバイスを接続機器として保存
        deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                BluetoothDevice device = mDeviceListAdapter.getItem(position);

                //設定保存
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(BluetoothDeviceListActivity.this);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(PreferenceKey.PREFERENCE_DEVICE, device.getAddress());
                editor.apply();

                //前の画面に戻る
                finish();
            }
        });
    }

    @Override
    public void onResume(){
        super.onResume();

        //Bluetooth機器検索開始
        startScan();
    }

    @Override
    public void onStop(){
        super.onStop();

        //検索終了
        stopScan();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_bluetooth_device_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Bluetoothデバイスをスキャン
     */
    private void startScan(){
        mDeviceListAdapter.clear();

        //Intent FilterとBroadcastReceiverの登録
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        registerReceiver(DeviceFoundReceiver, filter);

        //検索中の場合は検出をキャンセルする
        if(mBluetoothAdapter.isDiscovering()){
            mBluetoothAdapter.cancelDiscovery();
        }

        //デバイスを検索する
        //一定時間の間検出を行う
        mBluetoothAdapter.startDiscovery();
    }

    /**
     * Bluetoothデバイススキャン停止
     */
    private void stopScan(){
        //検索中の場合は検出をキャンセルする
        if(mBluetoothAdapter.isDiscovering()){
            mBluetoothAdapter.cancelDiscovery();
        }

        unregisterReceiver(DeviceFoundReceiver);
    }

    /**
     * Bluetooth機器検索結果通知
     */
    private final BroadcastReceiver DeviceFoundReceiver = new BroadcastReceiver(){

        @Override
        public void onReceive(Context context, Intent intent){
            String action = intent.getAction();
            String deviceName = null;
            BluetoothDevice foundDevice;

            //スキャン開始
            if(BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)){
                Toast.makeText(context, "Start Scan", Toast.LENGTH_SHORT);
            }

            //新しいデバイスが検出された
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                foundDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                deviceName = foundDevice.getName();

                if(deviceName != null){
                    Log.d("ACTION_FOUND", deviceName);

                    mDeviceListAdapter.add(foundDevice);
                }
            }

            //既知のデバイスが検出された（なにもしない）
            if(BluetoothDevice.ACTION_NAME_CHANGED.equals(action)){
                foundDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                deviceName = foundDevice.getName();

                Log.d("ACTION_NAME_CHANGED", deviceName);

                //mDeviceListAdapter.add(foundDevice);
            }

            //スキャン停止
            if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
                Toast.makeText(context, "Finish Scan", Toast.LENGTH_SHORT);
            }
        }
    };

    /**
     * ListViewのAdapter
     */
    private class BluetoothDeviceListAdapter extends ArrayAdapter<BluetoothDevice>{
        LayoutInflater mInflater;

        public BluetoothDeviceListAdapter(Context context, List<BluetoothDevice> objects) {
            super(context, 0, objects);
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.list_item_device, null);
            }

            BluetoothDevice device = getItem(position);

            ((TextView)convertView.findViewById(R.id.text_device_name)).setText(device.getName());
            ((TextView)convertView.findViewById(R.id.text_device_mac)).setText(device.getAddress());

            //機器ステータス
            switch (device.getBondState()){
                case BluetoothDevice.BOND_NONE:
                    ((TextView)convertView.findViewById(R.id.text_device_bond_state)).setText("未登録");
                    break;
                case BluetoothDevice.BOND_BONDING:
                    ((TextView)convertView.findViewById(R.id.text_device_bond_state)).setText("接続中");
                    break;
                case BluetoothDevice.BOND_BONDED:
                    ((TextView)convertView.findViewById(R.id.text_device_bond_state)).setText("登録済み");
                    break;
            }


            return convertView;
        }
    }
}
