package com.app.mark.drawables;

import android.app.Activity;
import android.app.AlertDialog;
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

public class MainActivity extends Activity {

    private DrawableSurfaceView.DrawableThread mDrawableThread;
    private DrawableSurfaceView mDrawableSurfaceView;

    private ELM327 mELM327;

    Button mAddGaugeButton;


    Bundle myB = new Bundle();                 //used for creating the msgs
    public Handler mHandler = new Handler(){   //handles the INcoming msgs
        @Override public void handleMessage(Message msg)
        {
            myB = msg.getData();
            Log.i("MainAct Handlr", "Handler got message"+ myB.getInt("THREAD DELIVERY"));
        }
    };

    void sendMsgToThread(String tag, String cmd) {
        Message msg = mELM327.getHandler().obtainMessage();
        myB.putString(tag, cmd);
        msg.setData(myB);
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

        // Bluetooth!!
        //ArrayList deviceStrs = new ArrayList();
        //final ArrayList devices = new ArrayList();

        String alertMessage = "";


        Log.d("MA", "before bluetooth");
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        if (pairedDevices.size() > 0)
        {
            for (BluetoothDevice device : pairedDevices)
            {
                if(device.getName().equalsIgnoreCase("OBDII")) {
                    Log.d("MA", "after bluetooth");
                    Log.d("MA", "starting ELM327");
                    mELM327.start();
                    Log.d("MA", "issuing connect cmd");
                    sendMsgToThread("ADDRESS", device.getAddress());
                    Log.d("MA", "post connect in MA!!!");
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

    public void logMe(String str) {
        Log.d("MainActivity", str);
    }
}






