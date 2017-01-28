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
    private enum CMD_TYPE {
        NONE,
        BT_ELM_CONNECT,
        ELM_TIMEOUT,
        ELM_RESET,
        ECU_CONNECT,
        ELM_REQUEST_DATA
    }

    private CMD_TYPE mLatestCmd = CMD_TYPE.NONE;            // Latest unprocessed command from inHandler
    private String mDataRequestCmd = "";                    // Data request command string
    private int mELMTimeoutVal = 3000;                      // ELM327 response timeout (ms)
    private boolean btConnected = false;                    // State of bluetooth connection
    private boolean elmConnected = false;                   // State of ELM connection
    private boolean ecuConnected = false;                   // State of ECU connection

    private String ecuProto;

    private InputStream mBTinStream;                        // InputStream for bluetooth communication
    private OutputStream mBToutStream;                      // OutputStream for bluetooth communication

    private enum DATA_STS {         // Used for data request
        READY,                      // Ready to make a data request - last data received
        REQUESTED                   // Data is currently requested - no new requests
    }

    private boolean btConnect(String deviceAddress) {
        // Connect to ELM327
        // TODO save deviceAddress
        boolean success;
        Log.d("ELMThread", "connect bluetooth device address: " + deviceAddress);
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = btAdapter.getRemoteDevice(deviceAddress);
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
        try {
            BluetoothSocket socket = device.createInsecureRfcommSocketToServiceRecord(uuid);
            socket.connect();
            mBTinStream = socket.getInputStream();
            mBToutStream = socket.getOutputStream();
            success = true;
            Log.d("ELMThread", "connect connected: " + deviceAddress);
        } catch (Throwable e) {
            Log.d("ELMThread", "connect exception");
            success = false;
            e.printStackTrace();
        }
        return success;
    }

    private String ecuConnect() {
        // Connect to ecu, return protocol
        // Issue first PID support command
        send("0100");
        String resp = receive();
        Log.d("ELM327", "0100 resp: " + resp);
        if (resp.contains("41 00")) {
            // Check to see if response begins with expected characters
            send("atdp");
            // Request selected protocol
            return receive();
        } else
            return "";
    }

    private String mBluetoothAddr;


    Context mContext;

    private Bundle mBundle = new Bundle();                 //used for creating the msgs

    private Handler outHandler;

    // ELM327 Constructor
    public ELM327(Context context, Handler handler) {
        Log.d("ELMThread", "called thread constructor");
        mContext = context;
        outHandler = handler;
    }

    // Handler for commands issued to ELM327 thread
    private Handler inHandler = new Handler() {
        // Handler for message received by ELM327 thread
        @Override
        public void handleMessage(Message msg) {
            mBundle = msg.getData();
            if (mBundle.containsKey("CMD")) {
                String cmd = mBundle.getString("CMD");
                switch (cmd) {
                    // Handle valid commands - set up initial conditions for run() to perform
                    case "BT_ELM_CONNECT":
                        Log.d("ELM327", "Connect command received");
                        mBluetoothAddr = mBundle.getString("DEVICE_ADDR");
                        mLatestCmd = CMD_TYPE.BT_ELM_CONNECT;
                        break;
                    case "ELM_TIMEOUT":
                        Log.d("ELM327", "Set timeout command received");
                        mELMTimeoutVal = mBundle.getInt("TIMEOUT_VAL");
                        Log.d("ELM327", "Timeout val set to " + mELMTimeoutVal + "ms");
                        break;
                    case "ELM_RESET":
                        Log.d("ELM327", "Reset command received");
                        mLatestCmd = CMD_TYPE.ELM_RESET;
                        break;
                    case "ECU_CONNECT":
                        Log.d("ELM327", "ECU connect command received");
                        mLatestCmd = CMD_TYPE.ECU_CONNECT;
                        break;
                    case "ELM_REQUEST_DATA":
                        mDataRequestCmd = mBundle.getString("ELM_REQUEST_DATA");
                        Log.d("ELM327", "ECU request data command received: " + mDataRequestCmd);
                        break;
                    default:
                        Log.d("ELM327", "Unknown command type received");
                        break;
                }
            } else {
                Log.d("ELM327", "inHandler message received without command");
            }
        }
    };


    // Returns input handler
    public Handler getHandler() {
        return inHandler;
    }

    // Convert a boolean to "FAIL" (false) or "OK" (true)
    private String fail_okay(boolean in) {
        if (in)
            return "OK";
        else
            return "FAIL";
    }

    // Send a response detailing results of PID_SUPPORT_QUERY
    private void pidSupportResp() {
        Log.d("ELM327", "Responding to ECU_CONNECT");
        Bundle b = new Bundle();
        b.putString("RESP", "ECU_CONNECT");
        b.putString("ECU_CONNECT_RESULT", fail_okay(ecuConnected));
        b.putString("ECU_PROTO_STRING", ecuProto);
        sendMessage(b);
    }



    // Send a response detailing results of ECU_CONNECT
    private void ecuConnectResp(boolean ecuConnected, String ecuProto) {
        Log.d("ELM327", "Responding to ECU_CONNECT");
        Bundle b = new Bundle();
        b.putString("RESP", "ECU_CONNECT");
        b.putString("ECU_CONNECT_RESULT", fail_okay(ecuConnected));
        b.putString("ECU_PROTO_STRING", ecuProto);
        sendMessage(b);
    }

    // Send a response detailing results of BT_ELM_CONNECT
    private void btConnectResp(boolean btConn, boolean elmConn, String elmVers) {
        Log.d("ELM327", "Responding to BT_ELM_CONNECT");
        Bundle b = new Bundle();
        b.putString("RESP", "BT_ELM_CONNECT");
        b.putString("BT_CONNECT_RESULT", fail_okay(btConn));
        b.putString("ELM_CONNECT_RESULT", fail_okay(elmConn));
        b.putString("ELM_VERSION_STRING", elmVers);
        sendMessage(b);
    }

    // Send a response detailing results of ELM_RESET
    private void elmResetResp(boolean resetResult) {
        Log.d("ELM327", "Responding to ELM_RESET");
        Bundle b = new Bundle();
        b.putString("RESP", "ELM_RESET");
        b.putString("ELM_RESET_RESULT", fail_okay(resetResult));
        sendMessage(b);
    }

    // Send a Bundle from thread in a Message using outHandler
    private void sendMessage(Bundle b) {
        Message msgOut = outHandler.obtainMessage();
        msgOut.setData(b);
        outHandler.sendMessage(msgOut);
    }


    // send (string Data) - Send Data over Bluetooth!
    private void send(String Data) {
        if (btConnected) {
            // Append with CR + NL
            Data += "\r\n";
            try {
                mBToutStream.write(Data.getBytes());
            } catch (Throwable e) {
                Log.d("ELMThread", "send exception");
                e.printStackTrace();
            }
        } else Log.d("ELMThread", "send error - called while disconnected");
    }

    // receive string Data - Receive Data over Bluetooth!
    private String receive() {
        byte[] inBytes = new byte[50];
        String Data = "";
        Boolean complete = false;
        if (btConnected) {
            try {
                while (!complete) {
                    int numBytes = mBTinStream.available();
                    if (numBytes > 0) {
                        //Log.d("ELMThread", "receive bytes available" + numBytes);
                        byte[] bytes = new byte[numBytes];
                        int num = mBTinStream.read(bytes, 0, numBytes);

                        String In = new String(bytes, "UTF-8");
                        //Log.d("ELMThread", "receive" + In );
                        Data += In;
                        if (Data.indexOf('>') != -1) {
                            Data = Data.replace(">", "");
                            complete = true;
                        }
                    }

                }
            } catch (Throwable e) {
                Log.d("ELMThread", "receive exception");
                e.printStackTrace();
            }
        } else Log.d("ELMThread", "receive error - called while disconnected");
        return Data;
    }

    // Issue reset command to ELM327
    public String reset() {
        send("atz");                // issue actual reset command
        String resp = receive();
        send("ate0");               // set echo off
        resp = receive();
        send("ati");                // request version
        resp = receive();
        return resp;
    }

    //
    public void setProtoAuto() {
        String cmd = "atsp0";
        Log.d("ELMThread", "Set Proto Auto Requested data " + cmd);
        send(cmd);
        String resp = receive();
        Log.d("ELMThread", "Set Proto Auto Received data " + resp);
    }

    public void displayProto() {
        String cmd = "atdp";
        send(cmd);
        String resp = receive();
    }

    public String request(String PID) {
        send(PID);
        return receive();
    }


    @Override
    public void run() {
        while (true) {
            //Run loop!!

            switch (mLatestCmd) {
                case NONE:
                    // Nothing to do here - move right along
                    break;
                case BT_ELM_CONNECT:
                    if (!btConnected && !elmConnected)
                        btConnected = btConnect(mBluetoothAddr);
                        if(!btConnected) {
                            Log.d("ELM327", "Bluetooth connect error");
                        }
                    else
                        Log.d("ELM327", "Connect called while already connected");
                    if (btConnected && !elmConnected) {
                        Log.d("ELMThread", "Bluetooth connect success, connecting to ELM");
                        String elmVers = reset();
                        Log.d("ELM327", "ELM Version string: " + elmVers);
                        if (elmVers.startsWith("ELM327 v")) {
                            elmConnected = true;
                            Log.d("ELM327", "ELM connect success");
                            btConnectResp(btConnected, elmConnected, elmVers);
                        } else {
                            Log.d("ELM327", "ELM connect error");
                            elmConnected = false;
                        }

                    }
                    mLatestCmd = CMD_TYPE.NONE;             // Reset
                    break;
                case ELM_RESET:
                    mLatestCmd = CMD_TYPE.NONE;             // Reset
                    break;
                case ECU_CONNECT:
                    if(!btConnected || !elmConnected)
                        Log.d("ELMThread", "ECU connect called while BT or ELM disconnected");
                    else if(!ecuConnected){
                        ecuConnected = false;
                        String proto = ecuConnect();
                        if(!proto.isEmpty()) {
                            ecuConnected = true;
                        }
                        ecuConnectResp(ecuConnected, proto);
                    } else
                        Log.d("ELMThread", "ECU connect called while already connected");
                    mLatestCmd = CMD_TYPE.NONE;             // Reset
                    break;
                case ELM_REQUEST_DATA:
                    break;
                default:
                    Log.d("ELMThread", "Run encountered invalid latest command case");
                    mLatestCmd = CMD_TYPE.NONE;             // Reset
                    break;
            }












            /*
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
        } */
        }
    }
}