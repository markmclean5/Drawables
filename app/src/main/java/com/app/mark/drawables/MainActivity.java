package com.app.mark.drawables;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;


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

        mELM327 = new ELM327();

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
        //mDrawableThread.setGaugeVal("test", (float)76.1);

        mAddGaugeButton = new Button(this);

        mAddGaugeButton = (Button) findViewById(R.id.button);

        mAddGaugeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mDrawableThread.addGauge("calc_eng_load");
                mDrawableThread.updateGauge("calc_eng_load", (float)100.1);
            }
        });


        // Bluetooth!!

        ArrayList deviceStrs = new ArrayList();
        final ArrayList devices = new ArrayList();

        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        if (pairedDevices.size() > 0)
        {
            for (BluetoothDevice device : pairedDevices)
            {
                deviceStrs.add(device.getName() + "\n" + device.getAddress());
                devices.add(device.getAddress());
            }
        }

        // show list
        final AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);

        ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.select_dialog_singlechoice,
                deviceStrs.toArray(new String[deviceStrs.size()]));

        alertDialog.setSingleChoiceItems(adapter, -1, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                int position = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                String deviceAddress = (String) devices.get(position);
                mELMThread.connect(deviceAddress);
                mELMThread.setRunning(true);
                mELMThread.run();




            }
        });
        alertDialog.setTitle("Choose Bluetooth device");
        alertDialog.show();




    }





}
