package com.app.mark.drawables;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import java.util.ArrayList;
import java.util.Set;
import java.lang.ref.WeakReference;
import android.os.Message;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private DrawableSurfaceView.DrawableThread mDrawableThread;
    private DrawableSurfaceView mDrawableSurfaceView;

    private ELM327 mELM327;

    Button mAddGaugeButton;
    Button mConnectButton;
    AlertDialog statusDialog;

    // For communicaton logging: listview, adapteer, and arraylist of strings
    ListView commView;
    ArrayAdapter<String> commViewAdapter;
    ArrayList<String> commViewItems = new ArrayList<String>();

    // Message handler: receives all messages coming into the main activity
    public Handler mHandler = new Handler(){   //handles the INcoming msgs
        @Override public void handleMessage(Message msg)
        {
            Bundle inputBundle = new Bundle();
            inputBundle = msg.getData();
            // Processing of ELM327 Responses
            if(inputBundle.containsKey("RESP")) {
                ELM327.CMD_TYPE cmdResp = (ELM327.CMD_TYPE)inputBundle.getSerializable("RESP");
                switch (cmdResp) {
                    // Handle valid commands - set up initial conditions for run() to perform
                    case BT_ELM_CONNECT:
                        if(inputBundle.getString("ELM_CONNECT_RESULT") == "OK") {
                            statusDialog.setMessage("ELM Connected - " + inputBundle.getString("ELM_VERSION_STRING"));
                            sendEcuConnectCmd();
                        }
                        else
                            statusDialog.setMessage("Failed to connect to ELM");
                        break;
                    case ELM_RESET:
                        Log.d("MA", "Reset command response received");
                        break;
                    case ECU_CONNECT:
                        Log.d("MA", "ECU connect command response received");
                        if(inputBundle.getString("ECU_CONNECT_RESULT") == "OK") {
                            statusDialog.setMessage("ECU Connected - " + inputBundle.getString("ECU_PROTO_STRING"));
                            sendEcuConnectCmd();
                        }
                        else
                            statusDialog.setMessage("Failed to connect to ECU");
                        // Dismiss status dialog after two seconds
                        new Handler().postDelayed(new Runnable() {
                            public void run() {
                                statusDialog.dismiss();
                            }
                        }, 2000);


                        break;
                    case ELM_REQUEST_DATA:
                        Log.d("MA", "ECU request data command response received");
                        break;

                    case ELM_REQUEST_SUPPORTED_PIDS:
                        Log.d("MA", "ECU Request supported PIDs response received");
                        break;

                    default:
                        Log.d("ELM327", "Unknown command type received");
                        break;
                }
            }
            // Processing of communication log events
            else if (inputBundle.containsKey("COMM_STRING")) {
                Log.d("MA","Comm string received");
                commViewAdapter.add(inputBundle.getString("COMM_STRING"));
                commViewAdapter.notifyDataSetChanged();
            }
            // Error case
            else {
                Log.d("MA", "Invalid message received");
            }

        }
    };


    void sendElmConnectCmd(String addr) {
        Bundle connectBundle = new Bundle();
        Message msg = mELM327.getHandler().obtainMessage();
        connectBundle.putSerializable("CMD", ELM327.CMD_TYPE.BT_ELM_CONNECT);
        connectBundle.putString("DEVICE_ADDR", addr);
        msg.setData(connectBundle);
        mELM327.getHandler().sendMessage(msg);
    }


    void sendEcuConnectCmd() {
        Bundle connectBundle = new Bundle();
        Message msg = mELM327.getHandler().obtainMessage();
        connectBundle.putSerializable("CMD", ELM327.CMD_TYPE.ECU_CONNECT);
        msg.setData(connectBundle);
        mELM327.getHandler().sendMessage(msg);
    }





    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // tell system to use the layout defined in our XML file
        setContentView(R.layout.activity_main);

        // get handles to the drawable surface from XML
        mDrawableSurfaceView = (DrawableSurfaceView) findViewById(R.id.drawable_surface);


        // *************
        // Communication logging items
        // *************
        // ListView for communication log display - default invisible
        commView = (ListView) findViewById(R.id.comm_view);
        commView.setVisibility(View.INVISIBLE);
        // Listview header - needs to be view, also before adapter
        View commViewHeaderView  = View.inflate(this, R.layout.log_header, null);
        commView.addHeaderView(commViewHeaderView);
        // Adapter places items (Strings) in listview
        commViewAdapter = new ArrayAdapter<String>(this, R.layout.log_item, R.id.item_text, commViewItems);
        commView.setAdapter(commViewAdapter);


        mELM327 = new ELM327(this, mHandler);


        mDrawableThread = mDrawableSurfaceView.getThread();
        if (savedInstanceState == null) {
            // we were just launched: set up a new game
            mDrawableThread.setState(DrawableSurfaceView.DrawableThread.STATE_READY);
            Log.w(this.getClass().getName(), "SIS is null");
        } else {
            // we are being restored: resume a previous game
            mDrawableThread.restoreState(savedInstanceState);
            Log.w(this.getClass().getName(), "SIS is nonnull");
        }
        mDrawableThread.doStart();


        // ECU Connect Button
        mConnectButton = (Button) findViewById(R.id.connect_button);
        mConnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connect();
            }
        });

        // PID Support Request Button
        Button requestButton = (Button) findViewById(R.id.request_button);
        requestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleCommView();
            }
        });

        // ECU Connect Button
        mConnectButton = (Button) findViewById(R.id.connect_button);
        mConnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connect();
            }
        });

        // Add Gauge Button
        mAddGaugeButton = (Button) findViewById(R.id.add_gauge_button);
        mAddGaugeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("buttons", "add gauge button pressed!!");
                mDrawableThread.addGauge("calc_eng_load");
                mDrawableThread.updateGauge("calc_eng_load", (float)100.1);
            }
        });

    }

    void connect() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Connection Status");
        builder.setMessage("Initialize connection");
        statusDialog = builder.create();

        statusDialog.show();

        Log.d("MA", "before bluetooth");
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        Log.d("MA", "Paired Devices: " + pairedDevices.size());
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if(device.getName().equalsIgnoreCase("OBDII")) {
                    mELM327.start();
                    String addr = device.getAddress();
                    statusDialog.setMessage("BT: Connecting to " + addr);
                    sendElmConnectCmd(addr);
                    break;
                }
            }

        } else
            Log.d("MainActivity", "Error - no paired bluetooth devices");
    }

    void toggleCommView() {
        if(commView.getVisibility() == View.INVISIBLE)
            commView.setVisibility(View.VISIBLE);
        else commView.setVisibility(View.INVISIBLE);
    }


}






