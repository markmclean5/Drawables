package com.app.mark.drawables;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;


import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Created by mark on 12/22/2016.
 */



public class ELM327 extends Thread {

    private static final int DISCONNECTED = 0;
    private static final int CONNECTING = 1;
    private static final int CONNECTED = 2;
    private static final int REQUESTED = 3;

    String mBluetoothAddr;

    private static final int NOT_READY = 0;
    private static final int READY = 1;
    private static final int WAITING = 2;

    Context mContext;

    int mBTconn;
    int mELMconn;
    int mECUconn;
    InputStream mBTinStream;
    OutputStream mBToutStream;
    String mRXbuffer;
    int mDataRequest;

    Bundle mBundle = new Bundle();                 //used for creating the msgs

    private Handler outHandler;

    // ELM327 Constructor
    public ELM327(Context context, Handler handler) {
        Log.d("ELMThread", "called thread constructor");
        mContext = context;
        outHandler = handler;
        mBTconn = DISCONNECTED;
        mECUconn = DISCONNECTED;
        mELMconn = DISCONNECTED;
        mDataRequest = NOT_READY;
        mRXbuffer = "";
    }

    // Handler for
    private Handler inHandler = new Handler(){
        // Handler for message received by ELM327 thread
        @Override public void handleMessage(Message msg)
        {
            mBundle = msg.getData();

            if(mBundle.containsKey("ADDRESS")) {
                Log.i("ELM327", "Handler got address"+ mBundle.getString("ADDRESS"));

                mBluetoothAddr  = mBundle.getString("ADDRESS");
                mBTconn = REQUESTED;
            }
        }
    };

    public Handler getHandler(){
        return inHandler;
    }

    private void sendMessage(String msg) {
        if (!msg.equals(null) && !msg.equals("")) {
            Message msgObj = outHandler.obtainMessage();
            Bundle b = new Bundle();
            b.putString("message", msg);
            msgObj.setData(b);
            outHandler.sendMessage(msgObj);
        }
    }

    // send (string Data) - Send Data over Bluetooth!
    public void send(String Data) {
        if(mBTconn == CONNECTED) {
            // Append with CR + NL
            Data += "\r\n";
            try {
                mBToutStream.write(Data.getBytes());
            }
            catch (Throwable e) {
                Log.d("ELMThread", "send exception");
                e.printStackTrace();
            }
        }
        else Log.d("ELMThread", "send error - called while disconnected");
    }

    // receive string Data - Receive Data over Bluetooth!
    public String receive() {
        byte[] inBytes = new byte[50];
        String Data = "";
        Boolean complete = false;
        if(mBTconn == CONNECTED) {
            try {
                while(!complete) {
                    int numBytes = mBTinStream.available();
                    if(numBytes > 0) {
                        //Log.d("ELMThread", "receive bytes available" + numBytes);
                        byte[] bytes = new byte[numBytes];
                        mBTinStream.read(bytes, 0, numBytes);
                        String In = new String(bytes, "UTF-8");
                        //Log.d("ELMThread", "receive" + In );
                        Data += In;
                        if(Data.indexOf('>') != -1) {
                            Data = Data.replace(">", "");
                            complete = true;
                        }
                    }

                }
            } catch (Throwable e){
                Log.d("ELMThread", "receive exception");
                e.printStackTrace();
            }
        } else Log.d("ELMThread", "receive error - called while disconnected");
        return Data;
    }


    // Connect to ELM327 and ECU
    public void connect(String deviceAddress){
        Log.d("ELMThread", "debug delay");
        try{
            sleep(10000);
        } catch (InterruptedException e){
            e.printStackTrace();
            Log.d("ELMThread", "debug delay exception");

        }

        // Connect to ELM327
        // TODO save deviceAddress
        Log.d("ELMThread", "connect device address: " + deviceAddress);
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = btAdapter.getRemoteDevice(deviceAddress);
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
        try {
            BluetoothSocket socket = device.createInsecureRfcommSocketToServiceRecord(uuid);
            socket.connect();
            mBTinStream = socket.getInputStream();
            mBToutStream = socket.getOutputStream();
            mBTconn = CONNECTED;
            mDataRequest = READY;
            Log.d("ELMThread", "connect connected: " + deviceAddress);
        } catch (Throwable e) {
            Log.d("ELMThread", "connect exception");
            e.printStackTrace();
        }
    }

    // Disconnect from ELM327, close connections
    public void disconnect() {
        mECUconn = DISCONNECTED;
        mELMconn = DISCONNECTED;
    }


    public void reset() {
        String cmd = "atz";
        Log.d("ELMThread", "Reset Requested data " + cmd);
        send(cmd);
        String resp = receive();
        Log.d("ELMThread", "Reset Received data "+ resp);
    }

    public void setProtoAuto() {
        String cmd = "atsp0";
        Log.d("ELMThread", "Set Proto Auto Requested data " + cmd);
        send(cmd);
        String resp = receive();
        Log.d("ELMThread", "Set Proto Auto Received data "+ resp);
    }
    public void displayProto() {
        String cmd = "atdp";
        send(cmd);
        String resp = receive();
    }
    public String request(String PID) {
        send(PID);
        String resp = receive();
        return resp;
    }


    @Override
    public void run() {
        while(true) {
            //Run loop!!
            if(mBTconn == REQUESTED) {
                mBTconn = CONNECTING;
                connect(mBluetoothAddr);
            } else if(mBTconn == CONNECTED){
                if(mDataRequest == READY) {
                    reset();
                    setProtoAuto();
                    request("0100");
                    mDataRequest = NOT_READY;
                    displayProto();

                    PID Pid0100 = new PID(mContext, "0100");
                    String data = request(Pid0100.getCommand());
                    Pid0100.update(data);
                    Pid0100.printData();

                    PID Pid0120 = new PID(mContext, "0120");
                    data = request(Pid0120.getCommand());
                    Pid0120.update(data);
                    Pid0120.printData();

                    PID Pid0140 = new PID(mContext, "0140");
                    data = request(Pid0140.getCommand());
                    Pid0140.update(data);
                    Pid0140.printData();


                    PID Pid0101 = new PID(mContext, "0101");
                    data = request(Pid0101.getCommand());
                    Pid0101.update(data);
                    Pid0101.printData();

                    PID Pid0103 = new PID(mContext, "0103");
                    data = request(Pid0103.getCommand());
                    Pid0103.update(data);
                    Pid0103.printData();

                    PID Pid0104 = new PID(mContext, "0104");
                    data = request(Pid0104.getCommand());
                    Pid0104.update(data);
                    Pid0104.printData();

                    PID Pid0105 = new PID(mContext, "0105");
                    data = request(Pid0105.getCommand());
                    Pid0105.update(data);
                    Pid0105.printData();

                    PID Pid0106 = new PID(mContext, "0106");
                    data = request(Pid0106.getCommand());
                    Pid0106.update(data);
                    Pid0106.printData();


                    Log.d("ELM327", "PID update completed");
                }

            }
        }


//        while (mRun) {
//            synchronized (mRunLock) {
//                if (mRun) {
//                    // Run thread logic
//                    //Log.d("ELMThread", "if mRun");
//
//
//
//
//
//                    }
//                    while(true) {
//                        long start = SystemClock.currentThreadTimeMillis();
//                        //requestMode1("01");
//                        long end = SystemClock.currentThreadTimeMillis();
//
//                        //Log.d("ELMThread", "loop complete " + (end - start));
//                    }
//                }
//            }
//        }
    }
}