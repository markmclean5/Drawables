package com.app.mark.drawables;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import java.util.ArrayList;
import java.util.Set;

import android.os.Message;
import android.widget.ListView;

public class MainActivity extends Activity {

    // Main objects: graphics, ELM327, threads
    private DrawableSurfaceView mDrawableSurfaceView;
    private DrawableSurfaceView.DrawableThread mDrawableThread;
    private ELM327 mELM327;

    // Status dialog
    AlertDialog statusDialog;

    // For communicaton logging: listview, adapter, and arraylist of strings
    ListView commLogListView;
    ArrayAdapter<String> commLogAdapter;
    ArrayList<String> commLogItems = new ArrayList<String>();

    // For list of supported parameters (elements of supported PIDs)
    ListView parameterListView;
    ArrayAdapter<String> parameterListAdapter;
    ArrayList<String> parameterListItems = new ArrayList<String>();

    // Message handler: receives all messages coming into the main activity
    public Handler mHandler = new Handler(){   //handles the INcoming msgs
        @Override public void handleMessage(Message msg)
        {
            Bundle inputBundle = msg.getData();
            if(inputBundle.containsKey("RESP")) {
                ELM327.CMD_TYPE cmdResp = (ELM327.CMD_TYPE)inputBundle.getSerializable("RESP");
                if(cmdResp != null) {
                    switch (cmdResp) {
                        // Handle valid commands - set up initial conditions for run() to perform
                        case BT_ELM_CONNECT:
                            if(inputBundle.getString("ELM_CONNECT_RESULT").equals("OK")) {
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
                            if(inputBundle.getString("ECU_CONNECT_RESULT").equals("OK")) {
                                statusDialog.setMessage("ECU Connected - " + inputBundle.getString("ECU_PROTO_STRING"));
                                //sendEcuConnectCmd();
                                sendRequestSupportedPIDsCmd();
                            }
                            else
                                statusDialog.setMessage("Failed to connect to ECU");
                            // Dismiss status dialog after two seconds
                            new Handler().postDelayed(new Runnable() {
                                public void run() {
                                    statusDialog.dismiss();
                                }
                            }, 1500);
                            break;
                        case ELM_REQUEST_DATA:
                            Log.d("MA", "ECU request data command response received");
                            break;
                        case ELM_REQUEST_SUPPORTED_PARAMS:
                            String name = inputBundle.getString("NAME");
                            if(!name.contains("PID")) {
                                Log.d("MA", "ECU Request supported PIDs response received: " + name);
                                parameterListAdapter.add(name);
                                parameterListAdapter.notifyDataSetChanged();
                            }

                            break;
                        default:
                            Log.d("ELM327", "Unknown command type received");
                            break;
                    }
                }
            }
            // Processing of communication log events
            else if (inputBundle.containsKey("COMM_STRING")) {
                Log.d("MA","Comm string received");
                commLogAdapter.add(inputBundle.getString("COMM_STRING"));
                commLogAdapter.notifyDataSetChanged();
                commLogListView.setSelection(commLogAdapter.getCount()-1); // auto scroll
            }
            // Error case
            else {
                Log.d("MA", "Invalid message received");
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        /* **********************************************
         * DrawableSurfaceView and DrawableThread setup
         * **********************************************/
        // get handle to the DrawableSurfaceView XML
        mDrawableSurfaceView = (DrawableSurfaceView) findViewById(R.id.drawable_surface);
        // get handle to the DrawableSurfaceView DrawableThread
        mDrawableThread = mDrawableSurfaceView.getThread();
        if (savedInstanceState == null) {
            // initial launch
            mDrawableThread.setState(DrawableSurfaceView.DrawableThread.STATE_READY);
            Log.w(this.getClass().getName(), "SIS is null");
        } else {
            // restored
            mDrawableThread.restoreState(savedInstanceState);
            Log.w(this.getClass().getName(), "SIS is nonnull");
        }
        // start the thread
        mDrawableThread.doStart();
        Handler drawableHandler = mDrawableThread.getHandler();

        /* **********************************************
         * ELM327 setup
         * **********************************************/
        mELM327 = new ELM327(this, mHandler, drawableHandler);
        mELM327.start();


        /* **********************************************
         * Communication Logging
         ***********************************************/
        // ListView for communication log display - default invisible
        commLogListView = (ListView) findViewById(R.id.comm_view);
        commLogListView.setVisibility(View.INVISIBLE);
        // Listview header - needs to be view, also before adapter
        View commViewHeaderView  = View.inflate(this, R.layout.log_header, null);
        commLogListView.addHeaderView(commViewHeaderView);
        // Adapter places items (Strings) in listview
        commLogAdapter = new ArrayAdapter<String>(this, R.layout.log_item, R.id.item_text, commLogItems);
        commLogListView.setAdapter(commLogAdapter);

        /***********************************************
         * List of supported PIDs
         ***********************************************/
        // ListView containing supported PIDs - default visible
        parameterListView = (ListView) findViewById(R.id.pid_view);
        parameterListView.setVisibility(View.VISIBLE);
        // Listview header - needs to be view, also before adapter
        View pidListHeaderView  = View.inflate(this, R.layout.pid_list_header, null);
        parameterListView.addHeaderView(pidListHeaderView);
        // Adapter places items (Strings) in listview
        parameterListAdapter = new ArrayAdapter<String>(this, R.layout.pid_list_item, R.id.pid_list_item_text, parameterListItems);
        parameterListView.setAdapter(parameterListAdapter);

        parameterListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String value = parameterListAdapter.getItem(position);
                Log.d("MA", "Selected Parameter: " + value);
                sendRequestDataCmd(value);
                sendAddCmd(DrawableSurfaceView.VIEW_OBJ_TYPE.READOUT, value);
            }
        });

        connect();

        /* **********************************************
         * Buttons
         * **********************************************/
        // Toggle Log Button
        Button toggleLogButton = (Button) findViewById(R.id.toggle_log_button);
        toggleLogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleCommView();
            }
        });
    }

    /***********************************************
     * COMMUNICATION: Messages to ELM327
     ***********************************************/
    // Construct and send message to initiate bluetooth ELM327 connection
    void sendElmConnectCmd(String addr) {
        Bundle connectBundle = new Bundle();
        Message msg = mELM327.getHandler().obtainMessage();
        connectBundle.putSerializable("CMD", ELM327.CMD_TYPE.BT_ELM_CONNECT);
        connectBundle.putString("DEVICE_ADDR", addr);
        msg.setData(connectBundle);
        mELM327.getHandler().sendMessage(msg);
    }
    // Construct and send message to initiate ELM327 to ECU connection
    void sendEcuConnectCmd() {
        Bundle connectBundle = new Bundle();
        Message msg = mELM327.getHandler().obtainMessage();
        connectBundle.putSerializable("CMD", ELM327.CMD_TYPE.ECU_CONNECT);
        msg.setData(connectBundle);
        mELM327.getHandler().sendMessage(msg);
    }
    // Construct and send message to request all supported parameters
    void sendRequestSupportedPIDsCmd() {
        Bundle requestBundle = new Bundle();
        Message msg = mELM327.getHandler().obtainMessage();
        requestBundle.putSerializable("CMD", ELM327.CMD_TYPE.ELM_REQUEST_SUPPORTED_PARAMS);
        msg.setData(requestBundle);
        mELM327.getHandler().sendMessage(msg);
    }
    // Construct and send message to request
    void sendRequestDataCmd(String param) {
        Bundle requestBundle = new Bundle();
        Message msg = mELM327.getHandler().obtainMessage();
        requestBundle.putSerializable("CMD", ELM327.CMD_TYPE.ELM_REQUEST_DATA);
        requestBundle.putString("PARAM_REQ", param);
        msg.setData(requestBundle);
        mELM327.getHandler().sendMessage(msg);
    }
    void sendResetDataCmd() {
        Bundle resetBundle = new Bundle();
        Message msg = mELM327.getHandler().obtainMessage();
        resetBundle.putSerializable("CMD", ELM327.CMD_TYPE.ELM_RESET_DATA);
        mELM327.getHandler().sendMessage(msg);
    }

    /***********************************************
     * COMMUNICATION: Messages to DrawableThread
     ***********************************************/
    // Send message to add a display object to the DrawableSurfaceView
    void sendAddCmd(DrawableSurfaceView.VIEW_OBJ_TYPE type, String identifier) {
        Bundle connectBundle = new Bundle();
        Message msg = mDrawableThread.getHandler().obtainMessage();
        connectBundle.putSerializable("CMD", DrawableSurfaceView.VIEW_CMD_TYPE.ADD);
        connectBundle.putSerializable("OBJ", type);
        connectBundle.putString("IDENT", identifier);
        msg.setData(connectBundle);
        mDrawableThread.getHandler().sendMessage(msg);
    }
    // Send a message to destroy a DrawableSurfaceView display object
    void sendDestroyCmd(DrawableSurfaceView.VIEW_OBJ_TYPE type, String identifier) {
        Bundle connectBundle = new Bundle();
        Message msg = mDrawableThread.getHandler().obtainMessage();
        connectBundle.putSerializable("CMD", DrawableSurfaceView.VIEW_CMD_TYPE.DESTROY);
        connectBundle.putSerializable("OBJ", type);
        connectBundle.putString("IDENT", identifier);
        msg.setData(connectBundle);
        mDrawableThread.getHandler().sendMessage(msg);
    }
    // Send a message to update a DrawableSurfaceView display object value
    void sendUpdateCmd(DrawableSurfaceView.VIEW_OBJ_TYPE type, String identifier, float value) {
        Bundle connectBundle = new Bundle();
        Message msg = mDrawableThread.getHandler().obtainMessage();
        connectBundle.putSerializable("CMD", DrawableSurfaceView.VIEW_CMD_TYPE.UPDATE);
        connectBundle.putSerializable("OBJ", type);
        connectBundle.putString("IDENT", identifier);
        connectBundle.putFloat("VAL", value);
        msg.setData(connectBundle);
        mDrawableThread.getHandler().sendMessage(msg);
    }

    void sendResetDisplayCmd() {
        Bundle resetBundle = new Bundle();
        Message msg = mDrawableThread.getHandler().obtainMessage();
        resetBundle.putSerializable("CMD", DrawableSurfaceView.VIEW_CMD_TYPE.RESET);
        mDrawableThread.getHandler().sendMessage(msg);
    }

    /***********************************************
     * Views and controls
     ***********************************************/
    // Toggle visibility of communication log display
    void toggleCommView() {
        if(commLogListView.getVisibility() == View.INVISIBLE) {
            commLogListView.setVisibility(View.VISIBLE);
            parameterListView.setVisibility(View.INVISIBLE);
        }

        else {
            commLogListView.setVisibility(View.INVISIBLE);
            parameterListView.setVisibility(View.VISIBLE);
        }
    }


    /***********************************************
     * Connect to and status Bluetooth "OBDII" device
     ***********************************************/
    void connect() {

        // Create an AlertDialog
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
                    String addr = device.getAddress();
                    statusDialog.setMessage("BT: Connecting to " + addr);
                    sendElmConnectCmd(addr);
                    break;
                }
            }

        } else
            Log.d("MainActivity", "Error - no paired bluetooth devices");
    }


    /***********************************************
     * Start screen
     ***********************************************/
    void start() {
        setContentView(R.layout.start);
        while(true);
    }

}




