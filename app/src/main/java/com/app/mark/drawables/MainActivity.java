package com.app.mark.drawables;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import java.util.ArrayList;
import java.util.Set;
import java.lang.ref.WeakReference;
import android.os.Message;

public class MainActivity extends Activity {

    private DrawableSurfaceView.DrawableThread mDrawableThread;
    private DrawableSurfaceView mDrawableSurfaceView;

    private ELM327.ELMThread mELMThread;
    private ELM327 mELM327;

    Button mAddGaugeButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // tell system to use the layout defined in our XML file
        setContentView(R.layout.activity_main);

        // get handles to the LunarView from XML, and its LunarThread
        mDrawableSurfaceView = (DrawableSurfaceView) findViewById(R.id.lunar);

        mELM327 = new ELM327(this);

        mELMThread = mELM327.getThread();

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

        // Bluetooth!!
        //ArrayList deviceStrs = new ArrayList();
        //final ArrayList devices = new ArrayList();

        String alertMessage = "";

        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setTitle("Connecting");
        alertDialog.setMessage(alertMessage);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "CANCEL",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        alertDialog.show();

        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        if (pairedDevices.size() > 0)
        {
            for (BluetoothDevice device : pairedDevices)
            {
                if(device.getName().equalsIgnoreCase("OBDII")) {
                    alertDialog.setMessage("Found OBDII bluetooth device: " + device.getAddress());
                    mELMThread.connect(device.getAddress());
                    mELMThread.setRunning(true);
                    mELMThread.start();
                    break;
                }
            }

        } else
            Log.d("MainActivity", "Error - no paired bluetooth devices");
        alertDialog.dismiss();

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

    public void logMe(String str) {
        Log.d("MainActivity", str);
    }
}






