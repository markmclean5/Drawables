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

public class MainActivity extends Activity {

    private DrawableSurfaceView.DrawableThread mDrawableThread;
    private DrawableSurfaceView mDrawableSurfaceView;

    private ELM327 mELM327;

    Button mAddGaugeButton;
    AlertDialog statusDialog;

    public Handler mHandler = new Handler(){   //handles the INcoming msgs
        @Override public void handleMessage(Message msg)
        {
            Bundle inputBundle = new Bundle();
            inputBundle = msg.getData();

            if(inputBundle.containsKey("RESP")) {
                String resp = inputBundle.getString("RESP");
                switch (resp) {
                    // Handle valid commands - set up initial conditions for run() to perform
                    case "BT_ELM_CONNECT":
                        if(inputBundle.getString("ELM_CONNECT_RESULT") == "OK") {
                            statusDialog.setMessage("ELM Connected - " + inputBundle.getString("ELM_VERSION_STRING"));
                            sendEcuConnectCmd();
                        }
                        else
                            statusDialog.setMessage("Failed to connect to ELM");
                        break;
                    case "ELM_TIMEOUT":
                        Log.d("MA", "Set timeout command response received");

                        break;
                    case "ELM_RESET":
                        Log.d("MA", "Reset command response received");

                        break;
                    case "ECU_CONNECT":
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
                    case "ELM_REQUEST_DATA":
                        Log.d("MA", "ECU request data command response received: ");
                        break;
                    default:
                        Log.d("ELM327", "Unknown command type received");
                        break;
                }
            } else {
                Log.d("MA", "Invalid message received");
            }

        }
    };


    void sendElmConnectCmd(String addr) {
        Bundle connectBundle = new Bundle();
        Message msg = mELM327.getHandler().obtainMessage();
        connectBundle.putString("CMD", "BT_ELM_CONNECT");
        connectBundle.putString("DEVICE_ADDR", addr);
        msg.setData(connectBundle);
        mELM327.getHandler().sendMessage(msg);
    }


    void sendEcuConnectCmd() {
        Bundle connectBundle = new Bundle();
        Message msg = mELM327.getHandler().obtainMessage();
        connectBundle.putString("CMD", "ECU_CONNECT");
        msg.setData(connectBundle);
        mELM327.getHandler().sendMessage(msg);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // tell system to use the layout defined in our XML file
        setContentView(R.layout.activity_main);

        // get handles to the LunarView from XML, and its LunarThread
        mDrawableSurfaceView = (DrawableSurfaceView) findViewById(R.id.lunar);

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

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Connection Status");
        builder.setMessage("Initialize connection");
        statusDialog = builder.create();

        statusDialog.show();




        Log.d("MA", "before bluetooth");
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
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

        mAddGaugeButton = new Button(this);

        mAddGaugeButton = (Button) findViewById(R.id.button);

        mAddGaugeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mDrawableThread.addGauge("calc_eng_load");
                mDrawableThread.updateGauge("calc_eng_load", (float)100.1);
            }
        });

    }



}






