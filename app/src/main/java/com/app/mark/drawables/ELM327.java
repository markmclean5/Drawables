package com.app.mark.drawables;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import static android.os.SystemClock.currentThreadTimeMillis;

/**
 * Created by mark on 12/22/2016.
 */


public class ELM327 {
    public enum Connection {
        DISCONNECTED, CONNECTING, CONNECTED
    }

    public enum Request {
        NOT_READY, READY, WAITING
    }

    Context mContext;


    public class ELMThread extends Thread {

        private boolean mRun = false;
        private final Object mRunLock = new Object();


        Connection mBTconn;
        Connection mELMconn;
        Connection mECUconn;

        InputStream mBTinStream;
        OutputStream mBToutStream;



        String mRXbuffer;
        Request mDataRequest;

        // ELMThread Constructor
        public void ELMThread(Context context) {
            Log.d("ELMThread", "called thread constructor");
            mBTconn = Connection.DISCONNECTED;
            mECUconn = Connection.DISCONNECTED;
            mELMconn = Connection.DISCONNECTED;
            mDataRequest = Request.NOT_READY;
            mRXbuffer = "";
        }

        // send (string Data) - Send Data over Bluetooth!
        public void send(String Data) {
            if(mBTconn == Connection.CONNECTED) {
                // Append with CR + NL
                Data += "\r\n";
                Log.d("ELMThread", "send " + Data);
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
            if(mBTconn == Connection.CONNECTED) {
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
                mBTconn = Connection.CONNECTED;
                mDataRequest = Request.READY;
                Log.d("ELMThread", "connect connected: " + deviceAddress);
            } catch (Throwable e) {
                Log.d("ELMThread", "connect exception");
                e.printStackTrace();
            }
        }

        // Disconnect from ELM327, close connections
        public void disconnect() {
            mECUconn = Connection.DISCONNECTED;
            mELMconn = Connection.DISCONNECTED;
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
            Log.d("ELMThread", "Display Proto Requested data " + cmd);
            send(cmd);
            String resp = receive();
            Log.d("ELMThread", "Display Proto Received data "+ resp);
        }
        public void requestMode1(String PID) {
            String cmd = "01" + PID + "1";
            Log.d("ELMThread", "Display Proto Requested data " + cmd);
            send(cmd);
            String resp = receive();
            Log.d("ELMThread", "Display Proto Received data "+ resp);
        }






        @Override
        public void run() {
            Log.d("ELMThread", "called run " + mRun);
            while (mRun) {
                synchronized (mRunLock) {
                    if (mRun) {
                        // Run thread logic
                        //Log.d("ELMThread", "if mRun");

                        if(mDataRequest == Request.READY) {
                            reset();
                            setProtoAuto();
                            requestMode1("01");
                            mDataRequest = Request.NOT_READY;
                            displayProto();

                            PID Pid0100 = new PID(mContext, "0100");
                        }
                        while(true) {
                            long start = SystemClock.currentThreadTimeMillis();
                            //requestMode1("01");
                            long end = SystemClock.currentThreadTimeMillis();

                            //Log.d("ELMThread", "loop complete " + (end - start));
                        }


                    }
                }
            }
        }
        /**
         * Used to signal the thread whether it should be running or not.
         * Passing true allows the thread to run; passing false will shut it
         * down if it's already running. Calling start() after this was most
         * recently called with false will result in an immediate shutdown.
         *
         * @param b true to run, false to shut down
         */
        public void setRunning(boolean b) {
            // Do not allow mRun to be modified while any canvas operations
            // are potentially in-flight. See doDraw().
            synchronized (mRunLock) {
                mRun = b;
            }
        }
    }
    // The thread
    private ELMThread thread;

    // ELM327 Constructor
    public ELM327(Context context) {
        mContext = context;
        thread = new ELMThread();
    }

    // get the ELMThread
    public ELMThread getThread() {
        return thread;
    }

}
