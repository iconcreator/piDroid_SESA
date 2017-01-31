package iconcreator.pidroid_sesa;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.ValueDependentColor;
import com.jjoe64.graphview.helper.StaticLabelsFormatter;
import com.jjoe64.graphview.series.BarGraphSeries;
import com.jjoe64.graphview.series.DataPoint;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int SUCCESS_CONNECT = 1;
    private static final int MESSAGE_READ = 2;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // standard bluetooth UUID
    private String TAG = "Main Activity : ";
    private BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter(); //getting default bluetooth adapter
    private ArrayList<BluetoothDevice> foundDeviceList; //list of bluetooth devices found by bluetooth scan
    private GraphView graph;
    private BarGraphSeries series;
    private BluetoothDevice selectedDevice = null;
    private TextView textView, sensor1_value, sensor2_value, sensor3_value, sensor4_value;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                foundDeviceList.add(device);
                textView.setText("Found: " + device.getName());
                invalidateOptionsMenu();
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                textView.setText("Scanning.....");
                Log.d(TAG, "ACTION_DISCOVERY_STARTED");
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.d(TAG, "ACTION_DISCOVERY_FINISHED");
                invalidateOptionsMenu();
                if (foundDeviceList.size() > 0) {
                    if (selectedDevice == null) {
                        textView.setText("Select device from option menu");
                    }
                } else {
                    textView.setText("Scan again, No device found !!");
                }
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                Log.d(TAG, "ACTION_STATE_CHANGED");
            }
        }
    };
    private Button connectButton, disconnectButton;
    private ConnectThread my_thread;
    private saveReading save_reading;
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case SUCCESS_CONNECT:
                    Log.d(TAG, "in handler : SUCCESS_CONNECT");
                    final ConnectedThread connectedThread = new ConnectedThread((BluetoothSocket) msg.obj);
                    connectedThread.start();
                    Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_SHORT).show();
                    textView.setText("Connected to : " + selectedDevice.getName());
                    save_reading = new saveReading();
                    Log.i(TAG, "connected");
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    String string = new String(readBuf);
                    String data = stripGarbage(string);
                    List<String> data_list = Arrays.asList(data.split(","));
                    Log.i(TAG, "Received :" + data_list);
                    save_reading.writeToFile(data_list);
                    addtograph(data_list);
                    sensor1_value.setText(data_list.get(0));
                    sensor2_value.setText(data_list.get(1));
                    sensor3_value.setText(data_list.get(2));
                    sensor4_value.setText(data_list.get(3));
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Remove title bar
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        //Remove notification bar
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        //keep always screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setLogo(R.mipmap.ic_launcher);
        setSupportActionBar(toolbar);
        textView = (TextView) findViewById(R.id.status_textView);
        sensor1_value = (TextView) findViewById(R.id.sensor1_value_textView);
        sensor2_value = (TextView) findViewById(R.id.sensor2_value_textView);
        sensor3_value = (TextView) findViewById(R.id.sensor3_value_textView);
        sensor4_value = (TextView) findViewById(R.id.sensor4_value_textView);
        foundDeviceList = new ArrayList<>();
        connectButton = (Button) findViewById(R.id.connect_button);
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                my_thread = new ConnectThread(selectedDevice);
                if (selectedDevice == null) {
                    textView.setText("Please, Select Device from Option Menu !");
                } else {
                    textView.setText("Connecting to..... : " + selectedDevice.getName());
                    my_thread.start();
                }
            }
        });
        disconnectButton = (Button) findViewById(R.id.disconnect_button);
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                textView.setText("Please, Select Device from Option Menu !");
                if (mReceiver.isOrderedBroadcast()) {
                    unregisterReceiver(mReceiver);
                }
                if (save_reading != null) {
                    save_reading.close();
                }
                my_thread.cancel();
            }
        });
        graph = (GraphView) findViewById(R.id.graph);
        graph.setTitle("Sensor Readings");
        graph.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.NONE);
        graph.getViewport().setMinY(0);
        graph.getViewport().setMaxY(1023);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(4);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.setTitleColor(R.color.primary_dark);
        series = new BarGraphSeries<>();
        graph.addSeries(series);
        StaticLabelsFormatter staticLabelsFormatter = new StaticLabelsFormatter(graph);
        staticLabelsFormatter.setHorizontalLabels(new String[]{"", ""});
        staticLabelsFormatter.setVerticalLabels(new String[]{"", ""});
        graph.getGridLabelRenderer().setLabelFormatter(staticLabelsFormatter);
        if (bluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "No Bluetooth Support on Device", Toast.LENGTH_SHORT).show();
            finish();
        }
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1000);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1001); //Any number
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBtIntent);
        }
        textView.setText("Press Scan Button");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        for (int i = 0; i < foundDeviceList.size(); i++) {
            BluetoothDevice device = foundDeviceList.get(i);
            menu.add(0, i, 0, device.getName());
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.scan) {
            startScanning();
            return true;
        }
        for (int j = 0; j < foundDeviceList.size(); j++) {
            if (id == j) {
                selectedDevice = foundDeviceList.get(id);
                bluetoothAdapter.cancelDiscovery();
                Toast.makeText(getApplicationContext(), "Selected device: " + selectedDevice.getName(), Toast.LENGTH_SHORT).show();
                textView.setText("Selected Device : " + selectedDevice.getName());
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void startScanning() {
        Log.d(TAG, "in startScanning()");
        foundDeviceList.clear();
        bluetoothAdapter.cancelDiscovery();
        bluetoothAdapter.startDiscovery();

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy
        filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy
        filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy

    }

    public String stripGarbage(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if ((ch >= '0' && ch <= '9' || ch == ',')) {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    @Override
    protected void onPause() {
        if (mReceiver.isOrderedBroadcast()) {
            unregisterReceiver(mReceiver);
        }
        super.onPause();
    }

    private void addtograph(List<String> list) {

        graph.removeAllSeries();
        series = new BarGraphSeries<>(new DataPoint[]{
                new DataPoint(0.5, Double.parseDouble(list.get(0))),
                new DataPoint(1.5, Double.parseDouble(list.get(1))),
                new DataPoint(2.5, Double.parseDouble(list.get(2))),
                new DataPoint(3.5, Double.parseDouble(list.get(3)))
        });
        series.setValueDependentColor(new ValueDependentColor<DataPoint>() {
            @Override
            public int get(DataPoint data) {
                return Color.rgb(235, 121, 23);
            }
        });
        series.setDrawValuesOnTop(true);
        series.setValuesOnTopColor(R.color.primary_dark);
        series.setSpacing(5);
        graph.addSeries(series);
    }

    private class ConnectThread extends Thread {
        private BluetoothSocket mmSocket;
        private BluetoothDevice mmDevice;

        private ConnectThread(BluetoothDevice device) {
            Log.d(TAG, "in ConnectThread");
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            mmDevice = device;
            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                Log.d(TAG, "createRfcommSocketToServiceRecord for " + mmDevice.getName());
                // MY_UUID is the app's UUID string, also used by the server code
                tmp = mmDevice.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.d(TAG, "in ConnectThread : run");
            // Cancel discovery because it will slow down the connection
            bluetoothAdapter.cancelDiscovery();
            try {
                Log.d(TAG, "Try to Connect Socket");
                // Connect the device through the socket. This will block
                mmSocket.connect();
            } catch (IOException connectException) {
                try {
                    Log.i(TAG, "Trying fallback...");
                    mmSocket = (BluetoothSocket) mmDevice.getClass().getMethod("createRfcommSocket", new Class[]{int.class}).invoke(mmDevice, 1);
                    mmSocket.connect();
                } catch (Exception e2) {
                    Log.e(TAG, "Couldn't establish Bluetooth connection!");
                    try {
                        mmSocket.close();
                    } catch (IOException e3) {
                        Log.e(TAG, "unable to close()  socket during connection failure", e3);
                    }
                    return;
                }
                // Do work to manage the connection (in a separate thread)
                mHandler.obtainMessage(SUCCESS_CONNECT, mmSocket).sendToTarget();
            }
        }

        // Will cancel an in-progress connection, and close the socket
        private void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ConnectedThread extends Thread implements Runnable {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "in ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.d(TAG, "in ConnectedThread : run");
            byte[] buffer;  // buffer store for the stream
            int bytes; // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                //Log.d(TAG,"in ConnectedThread : run -- while");
                try {
                    try {
                        sleep(25);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    // Read from the InputStream
                    buffer = new byte[1024];
                    bytes = mmInStream.read(buffer);
                    // Send the obtained bytes to the UI activity
                    mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(String string) {
            try {
                mmOutStream.write(string.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class saveReading {
        private FileOutputStream fileOutputStream1, fileOutputStream2, fileOutputStream3, fileOutputStream4;
        private File sensorReading1, sensorReading2, sensorReading3, sensorReading4;
        private OutputStreamWriter myOutWriter1, myOutWriter2, myOutWriter3, myOutWriter4;

        saveReading() {
            File folder = new File(Environment.getExternalStorageDirectory() + File.separator + "piDorid");
            if (!folder.exists()) {
                folder.mkdirs();
            }
            SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy_HH.mm");
            Date current_time = new Date();
            String addToFile = formatter.format(current_time);
            try {
                sensorReading1 = new File(Environment.getExternalStorageDirectory() + File.separator + "piDorid" + File.separator + "Sensor1_" + addToFile + ".txt");
                sensorReading1.createNewFile();
                sensorReading2 = new File(Environment.getExternalStorageDirectory() + File.separator + "piDorid" + File.separator + "Sensor2_" + addToFile + ".txt");
                sensorReading2.createNewFile();
                sensorReading3 = new File(Environment.getExternalStorageDirectory() + File.separator + "piDorid" + File.separator + "Sensor3_" + addToFile + ".txt");
                sensorReading3.createNewFile();
                sensorReading4 = new File(Environment.getExternalStorageDirectory() + File.separator + "piDorid" + File.separator + "Sensor4_" + addToFile + ".txt");
                sensorReading4.createNewFile();

            } catch (Exception e) {
                Toast.makeText(getBaseContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        private void writeToFile(List data) {

            try {
                Log.d(TAG, "Writing : " + data);
                fileOutputStream1 = new FileOutputStream(sensorReading1, true);
                myOutWriter1 = new OutputStreamWriter(fileOutputStream1);
                myOutWriter1.append(data.get(0).toString());
                myOutWriter1.append(",");
                myOutWriter1.flush();

                fileOutputStream2 = new FileOutputStream(sensorReading2, true);
                myOutWriter2 = new OutputStreamWriter(fileOutputStream2);
                myOutWriter2.append(data.get(1).toString());
                myOutWriter2.append(",");
                myOutWriter2.flush();

                fileOutputStream3 = new FileOutputStream(sensorReading3, true);
                myOutWriter3 = new OutputStreamWriter(fileOutputStream3);
                myOutWriter3.append(data.get(2).toString());
                myOutWriter3.append(",");
                myOutWriter3.flush();

                fileOutputStream4 = new FileOutputStream(sensorReading4, true);
                myOutWriter4 = new OutputStreamWriter(fileOutputStream4);
                myOutWriter4.append(data.get(3).toString());
                myOutWriter4.append(",");
                myOutWriter4.flush();

                myOutWriter1.close();
                myOutWriter2.close();
                myOutWriter3.close();
                myOutWriter4.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void close() {
            try {
                fileOutputStream1.close();
                fileOutputStream2.close();
                fileOutputStream3.close();
                fileOutputStream4.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
