/*
 * Copyright (C) 2015 OMRON Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package omron.StandardDemo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import omron.HVC.BleDeviceSearch;
import omron.HVC.HVC;
import omron.HVC.HVC_BLE;
import omron.HVC.HVC_PRM;
import omron.HVC.HVCBleCallback;

public class MainActivity extends Activity
implements OnItemClickListener{
    private static HVC_PRM mHVCParam = null;
    private static HVC_BLE mHVCDevice = null;
    private BluetoothAdapter mBluetoothAdapter = null;
    private HVCDeviceThread hvcThread = null;

    private CustomView cView = null;
    private ProgressDialog progressDialog = null;
    private WaitThread waitThread = null;
    private BluetoothDevice device = null;

    private static int nSelectDeviceNo = -1;
    private static boolean ThreadStop = false;
    private static List<BluetoothDevice> deviceList = null;
    private static DeviceDialogFragment newFragment = null;

    // リストビューに表示するデータ
    private String[] words = new String[] { "",
                                            "",
                                            "",
                                            "",
                                            "",
                                            "",
                                            "",
                                            "",
                                            ""
                                };
    private int mExecuteFlag = 0;

    public static final String TAG = "StandardDemo";
    public static SharedPreferences sharedpreferences = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        words[0] = getString(R.string.list_body);
        words[1] = getString(R.string.list_hand);
        words[2] = getString(R.string.list_face);
        words[3] = getString(R.string.list_dir);
        words[4] = getString(R.string.list_age);
        words[5] = getString(R.string.list_gender);
        words[6] = getString(R.string.list_gaze);
        words[7] = getString(R.string.list_blink);
        words[8] = getString(R.string.list_exp);

        // リストアダプターを作成
        ArrayAdapter<String> adp = new ArrayAdapter<String>(this, 
        android.R.layout.simple_list_item_multiple_choice, words); 
        
        // 作成したリストアダプターをリストビューにセットする
        ListView lv = (ListView)findViewById(R.id.listview);
        // アダプターをセット
        lv.setAdapter(adp);
        // クリックイベントを取得
        lv.setOnItemClickListener(this);
        // Listview内部のViewがフォーカスできないようにする
        lv.setItemsCanFocus(false); 
        // 複数選択出来るようにする
        lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE); 

        // 選択状態を取得する
        SparseBooleanArray checked = 
            lv.getCheckedItemPositions();
        int nBit = 1;
        for (int i = 0; i < words.length; i++) {
            if ( (mExecuteFlag & nBit) != 0 ) {
                checked.put(i, true);
            }
            nBit <<= 1;
        }

        // SharedPreferencesオブジェクトの取得
        sharedpreferences = getSharedPreferences("content01", Context.MODE_PRIVATE);

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }
        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (!mBluetoothAdapter.isEnabled()) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mHVCParam = new HVC_PRM();

        ThreadStop = false;
        newFragment = null;
        nSelectDeviceNo = -1;
        hvcThread = new HVCDeviceThread();
        hvcThread.start();
    }

    @Override
    public void onDestroy() {
        if ( mHVCDevice != null ) {
            try {
                mHVCDevice.finalize();
            } catch (Throwable e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        mHVCDevice = null;
        super.onDestroy();
    }

    private class HVCDeviceThread extends Thread {
        @Override
        public void run()
        {
            do {
                if ( device == null ) {
                    device = SelectHVCDevice("OMRON_HVC.*|omron_hvc.*");

                    // リストからHVCデバイスを選択後
                    if ( ( device != null ) && ( mHVCDevice == null ) ) {
                        mHVCDevice = new HVC_BLE();
                        mHVCDevice.setCallBack(hvcCallback);
                        // Connect
                        mHVCDevice.connect(getApplicationContext(), device);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if ( progressDialog != null ) {
                                    progressDialog.dismiss();
                                }

                                // ProgressDialogインスタンスを生成
                                progressDialog = new ProgressDialog(MainActivity.this);
                                // プログレススタイルを設定
                                progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                                // キャンセル不可に設定
                                progressDialog.setCancelable(false);
                                // タイトルを設定
                                progressDialog.setTitle(getString(R.string.loading));
                                // メッセージを設定
                                progressDialog.setMessage(getString(R.string.waiting));
                                // ダイアログを表示
                                progressDialog.show();
                            }
                        });
    
                        waitThread = new WaitThread();
                        waitThread.start();
                    }
                }
            } while(!ThreadStop);
        }
    }

    private class WaitThread extends Thread {
        public int mCount = 0;

        @Override
        public void run()
        {
            do {
                try {
                    sleep(1000);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                if ( !mHVCDevice.IsBusy() ) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mHVCDevice.getParam(mHVCParam);
                        }
                    });
                    return;
                }
                mCount++;
            } while ( mCount < 15 );

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if ( progressDialog != null ) {
                        progressDialog.dismiss();
                    }

                    progressDialog = new ProgressDialog(MainActivity.this);
                    // プログレススタイルを設定
                    progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                    // キャンセル可能に設定
                    progressDialog.setCancelable(true);
                    // タイトルを設定
                    progressDialog.setTitle(getString(R.string.popup_error));
                    // メッセージを設定
                    progressDialog.setMessage(getString(R.string.popup_errmes));
                    // ダイアログを表示
                    progressDialog.show();
                }
            });
        }
    }

    private BluetoothDevice SelectHVCDevice(String regStr) {
        if ( nSelectDeviceNo < 0 ) {
            if ( newFragment != null ) {
                BleDeviceSearch bleSearch = new BleDeviceSearch(getApplicationContext());
                // トースト表示
                showToast("You can select a device");
                while ( newFragment != null ) {
                    deviceList = bleSearch.getDevices();
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
                bleSearch.stopDeviceSearch(getApplicationContext());
            }

            if ( nSelectDeviceNo > -1 ) {
                // Generate pattern to determine
                Pattern p = Pattern.compile(regStr);
                Matcher m = p.matcher(deviceList.get(nSelectDeviceNo).getName());
                if ( m.find() ) {
                    // Find HVC device
                    return deviceList.get(nSelectDeviceNo);
                }
                nSelectDeviceNo = -1;
            }
            return null;
        }
        return deviceList.get(nSelectDeviceNo);
    }

    private final HVCBleCallback hvcCallback = new HVCBleCallback() {
        @Override
        public void onConnected() {
            // トースト表示
            showToast("Selected device has connected");
        }

        @Override
        public void onDisconnected() {
            // トースト表示
            showToast("Selected device has disconnected");
        }

        @Override
        public void onPostSetParam(int nRet, byte outStatus) {
            // トースト表示
            showToast("Set parameters");
        }

        @Override
        public void onPostGetParam(int nRet, byte outStatus) {
            // トースト表示
            showToast("Get parameters");
            if ( (nRet == HVC.HVC_NORMAL) && (outStatus == 0) ) {
                SharedPreferences.Editor editor = sharedpreferences.edit();
                editor.putString("DATA1", String.valueOf(mHVCParam.body.MinSize));
                editor.putString("DATA2", String.valueOf(mHVCParam.body.MaxSize));
                editor.putString("DATA3", String.valueOf(mHVCParam.body.Threshold));
                editor.putString("DATA4", String.valueOf(mHVCParam.hand.MinSize));
                editor.putString("DATA5", String.valueOf(mHVCParam.hand.MaxSize));
                editor.putString("DATA6", String.valueOf(mHVCParam.hand.Threshold));
                editor.putString("DATA7", String.valueOf(mHVCParam.face.MinSize));
                editor.putString("DATA8", String.valueOf(mHVCParam.face.MaxSize));
                editor.putString("DATA9", String.valueOf(mHVCParam.face.Threshold));
                editor.commit();
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if ( progressDialog != null ) {
                        // ダイアログ "HVC端末 接続中です" の消去
                        progressDialog.dismiss();
                    }
                }
            });
        }

        @Override
        public void onPostExecute(int nRet, byte outStatus) {
            if ( cView != null ) {
                cView.onPostExecute(nRet, outStatus);
            }
        }
    };

    public void showToast(final String str) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), str, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // onItemClickをオーバーライドする
    @Override
    public void onItemClick(
        AdapterView<?> parent, 
        View view, int position, long id) {
        
        ListView listView = (ListView)parent;
        
        // 選択状態を取得する
        SparseBooleanArray checked = 
                listView.getCheckedItemPositions();
        
        int nBit = (1<<position);
        if ( checked.get(position) ) {
            mExecuteFlag |= nBit;
            if ( (position > 2) &&
                 ((mExecuteFlag & HVC.HVC_ACTIV_FACE_DETECTION) == 0) ) {
                listView.setItemChecked(2, true);
                mExecuteFlag |= HVC.HVC_ACTIV_FACE_DETECTION;
            }
        } else {
            mExecuteFlag &= ~nBit;
            if ( (position == 2) &&
                 (mExecuteFlag > HVC.HVC_ACTIV_FACE_DETECTION) ) {
                listView.setItemChecked(2, true);
                mExecuteFlag |= HVC.HVC_ACTIV_FACE_DETECTION;
            }
        }
        nBit <<= 1;
    }

    // [デモ実行]押下
    public void onClick1(View v)
    {
        if ( (mHVCDevice != null) && !mHVCDevice.IsBusy() ) {
            mHVCParam.body.MinSize = Integer.parseInt(sharedpreferences.getString("DATA1", "30"));
            mHVCParam.body.MaxSize = Integer.parseInt(sharedpreferences.getString("DATA2", "480"));
            mHVCParam.body.Threshold = Integer.parseInt(sharedpreferences.getString("DATA3", "500"));
            mHVCParam.hand.MinSize = Integer.parseInt(sharedpreferences.getString("DATA4", "40"));
            mHVCParam.hand.MaxSize = Integer.parseInt(sharedpreferences.getString("DATA5", "480"));
            mHVCParam.hand.Threshold = Integer.parseInt(sharedpreferences.getString("DATA6", "500"));
            mHVCParam.face.MinSize = Integer.parseInt(sharedpreferences.getString("DATA7", "64"));
            mHVCParam.face.MaxSize = Integer.parseInt(sharedpreferences.getString("DATA8", "480"));
            mHVCParam.face.Threshold = Integer.parseInt(sharedpreferences.getString("DATA9", "500"));
            mHVCDevice.setParam(mHVCParam);

            // 作成したカスタムビューのオブジェクトを生成
            cView = new CustomView(this, mHVCDevice, mExecuteFlag);
            // 生成したカスタムビューを表示する
            setContentView(cView);
        }
    }

    // [パラメータ設定]押下
    public void onClick2(View v)
    {
        // ダイアログを表示する
        DialogFragment newFragment = new TestDialogFragment();
        newFragment.show(getFragmentManager(), getString(R.string.button2));
    }

    // [HVCデバイス選択]押下
    public void onClick3(View v)
    {
        if ( mHVCDevice != null ) {
            try {
                mHVCDevice.finalize();
            } catch (Throwable e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            mHVCDevice = null;
        }

        device = null;
        nSelectDeviceNo = -1;
        newFragment = new DeviceDialogFragment();
        newFragment.setCancelable(false);
        newFragment.show(getFragmentManager(), getString(R.string.button3));
    }

    public class DeviceDialogFragment extends DialogFragment {
        String[] deviceNameList = null;
        ArrayAdapter<String> ListAdpString = null;

        @SuppressLint("InflateParams")
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            LayoutInflater inflater = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View content = inflater.inflate(R.layout.device_list, null);
            builder.setView(content);

            ListView listView = (ListView)content.findViewById(R.id.new_devices);
            // Set adapter
            ListAdpString = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_single_choice); 
            listView.setAdapter(ListAdpString);

            // Set the click event in the list view
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener(){
                /**
                 * It is called when you click on an item
                 */
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    nSelectDeviceNo = position;
                    newFragment = null;
                    dismiss();
                }
            });

            DeviceDialogThread dlgThread = new DeviceDialogThread();
            dlgThread.start();

            builder.setMessage(getString(R.string.button3))
                   .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    newFragment = null;
                }
            });
            // Create the AlertDialog object and return it
            return builder.create();
        }

        private class DeviceDialogThread extends Thread {
            @Override
            public void run()
            {
                do {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if ( ListAdpString != null ) {
                                ListAdpString.clear();
                                if ( deviceList == null ) {
                                    deviceNameList = new String[] { "null" };
                                } else {
                                    synchronized (deviceList) {
                                        deviceNameList = new String[deviceList.size()];
                                        
                                        int nIndex = 0;
                                        for (BluetoothDevice device : deviceList) {
                                            if (device.getName() == null ) {
                                                deviceNameList[nIndex] = "no name";
                                            } else {
                                                deviceNameList[nIndex] = device.getName();
                                            }
                                            nIndex++;
                                        }
                                    }
                                }
                                ListAdpString.addAll(deviceNameList);
                                ListAdpString.notifyDataSetChanged();
                            }
                        }
                    });
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                } while(!ThreadStop);
            }
        }
    }

    public static class TestDialogFragment extends DialogFragment {
        @SuppressLint("InflateParams")
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            LayoutInflater inflater = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final View content = inflater.inflate(R.layout.sub, null);

            builder.setView(content);

            final EditText editText1 = (EditText)content.findViewById(R.id.editText1);
            final EditText editText2 = (EditText)content.findViewById(R.id.editText2);
            final EditText editText3 = (EditText)content.findViewById(R.id.editText3);
            final EditText editText4 = (EditText)content.findViewById(R.id.editText4);
            final EditText editText5 = (EditText)content.findViewById(R.id.editText5);
            final EditText editText6 = (EditText)content.findViewById(R.id.editText6);
            final EditText editText7 = (EditText)content.findViewById(R.id.editText7);
            final EditText editText8 = (EditText)content.findViewById(R.id.editText8);
            final EditText editText9 = (EditText)content.findViewById(R.id.editText9);
            // プリファレンスからの読み込み
            editText1.setText(sharedpreferences.getString("DATA1", "30"));
            editText2.setText(sharedpreferences.getString("DATA2", "480"));
            editText3.setText(sharedpreferences.getString("DATA3", "500"));
            editText4.setText(sharedpreferences.getString("DATA4", "40"));
            editText5.setText(sharedpreferences.getString("DATA5", "480"));
            editText6.setText(sharedpreferences.getString("DATA6", "500"));
            editText7.setText(sharedpreferences.getString("DATA7", "64"));
            editText8.setText(sharedpreferences.getString("DATA8", "480"));
            editText9.setText(sharedpreferences.getString("DATA9", "500"));

            builder.setMessage(getString(R.string.button2))
                    .setNegativeButton(getString(R.string.param_button), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            SharedPreferences.Editor editor = sharedpreferences.edit();
                            editor.putString("DATA1", editText1.getText().toString());
                            editor.putString("DATA2", editText2.getText().toString());
                            editor.putString("DATA3", editText3.getText().toString());
                            editor.putString("DATA4", editText4.getText().toString());
                            editor.putString("DATA5", editText5.getText().toString());
                            editor.putString("DATA6", editText6.getText().toString());
                            editor.putString("DATA7", editText7.getText().toString());
                            editor.putString("DATA8", editText8.getText().toString());
                            editor.putString("DATA9", editText9.getText().toString());
                            editor.commit();
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
        }
    }

    @Override
    public void onBackPressed() {
        if ( cView != null ) {
            setContentView(R.layout.main);
            cView.onDetachedFromWindow();

            // リストアダプターを作成
            ArrayAdapter<String> adp = new ArrayAdapter<String>(this, 
            android.R.layout.simple_list_item_multiple_choice, words); 

            // 作成したリストアダプターをリストビューにセットする
            ListView lv = (ListView)findViewById(R.id.listview);
            // アダプターをセット
            lv.setAdapter(adp);
            // クリックイベントを取得
            lv.setOnItemClickListener(this);
            // Listview内部のViewがフォーカスできないようにする
            lv.setItemsCanFocus(false); 
            // 複数選択出来るようにする
            lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE); 

            // 選択状態を取得する
            SparseBooleanArray checked = 
                lv.getCheckedItemPositions();
            int nBit = 1;
            for (int i = 0; i < words.length; i++) {
                if ( (mExecuteFlag & nBit) != 0 ) {
                    checked.put(i, true);
                }
                nBit <<= 1;
            }

            cView = null;
        } else {
            new AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.popup_title)
            .setMessage(R.string.popup_message)
            .setPositiveButton(R.string.popup_yes, new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        finish();
                        ThreadStop = true;
                    } catch (Throwable e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            })
            .setNegativeButton(R.string.popup_no, null)
            .show();
        }
    }
}
