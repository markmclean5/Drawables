package com.app.mark.drawables;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.util.Log;


import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;

/**
 * Created by mark on 12/22/2016.
 */

/**
 *  ELM327 Class: Interface with EL327 module over bluetooth
 *  Receives message containing
 *
 */
public class ELM327 extends Thread {

    // Definition of compatible commands key "CMD"
    enum CMD_TYPE {
        NONE,
        BT_ELM_CONNECT,
        ELM_RESET,
        ECU_CONNECT,
        ELM_REQUEST_DATA,
        ELM_REQUEST_SUPPORTED_PARAMS,
        ELM_RESET_DATA
    }

    private CMD_TYPE mLatestCmd = CMD_TYPE.NONE;            // Latest unprocessed command from inHandler
    private String mDataRequestCmd = "";                         // Data request command string
    private boolean btConnected = false;                    // State of bluetooth connection
    private boolean elmConnected = false;                   // State of ELM connection
    private boolean ecuConnected = false;                   // State of ECU connection
    private String ecuProto;                                // Text string of ECU protocol
    private InputStream mBTinStream;                        // InputStream for bluetooth communication
    private OutputStream mBToutStream;                      // OutputStream for bluetooth communication
    private ArrayList<PID> supportedPIDs = new ArrayList<>(); // All supported PIDs in one location
    private String mBluetoothAddr;                          // Bluetooth address of ELM327
    Context mContext;                                       // Context
    private Bundle mBundle = new Bundle();                  // For creating the messages
    private Handler outHandlerMA;                           // Handler for output messages to MA
    private Handler outHandlerDisp;                         // Handler for output messages to display
    private ArrayList<PID> activePIDs = new ArrayList<>();  // Currently active PIDs
    private Iterator<PID> currentPID = activePIDs.iterator();

    private boolean waiting = false;                        // Waiting on data from ELM327

    public Handler inHandler;                                      // Handler for input messages

    public ELM327(Context context, Handler handler, Handler drawableHandler) {
        // ELM327 Constructor
        mContext = context;
        outHandlerMA = handler;
        outHandlerDisp = drawableHandler;

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
        // Connect to ECU, return protocol
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

    private boolean requestParameter(String desiredParameter) {
        // Determine if parameter is supported
        boolean foundParameter = false;

        for(PID p : supportedPIDs) {
            ArrayList<PID.Element> PIDElements = p.getAllElements();
            for(PID.Element e : PIDElements) {
//                Log.d("ELM327", "comparing " + e.mLongName + "against " + desiredParameter);
                if(e.mLongName.equals(desiredParameter)) {
                    foundParameter = true;
                    Log.d("ELM327", "Parameter found: " + desiredParameter);
                    makeActive(p);
                    break;
                }
            }
            if(foundParameter) break;
        }
        if(!foundParameter)
            Log.d("ELM327", "Parameter NOT found: " + desiredParameter);
        return foundParameter;
    }

    private void makeActive(PID desiredPID) {
        Log.d("ELM327", "Making PID active");
        boolean alreadyActive = false;
        for(PID p : activePIDs) {
            // Check if PID is already active
            if(p.getCommand().equals(desiredPID.getCommand())) {
                alreadyActive = true;
                Log.d("ELM327", "PID already active");
                break;
            }
        }
        if(!alreadyActive) {
            // Add the PID to activePIDs, reset iterator
            activePIDs.add(desiredPID);
            Log.d("ELM327", "PID added");
        }
        currentPID = activePIDs.iterator();
    }


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

    private void reportComm(String comm) {
        Bundle b = new Bundle();
        b.putString("COMM_STRING", comm);
        sendMessage(b);
    }

    // Send a response detailing results of PID_SUPPORT_QUERY
    private void pidSupportResp() {
        Log.d("ELM327", "Responding to ELM_REQUEST_SUPPORTED_PARAMS");
        Bundle b = new Bundle();
        b.putSerializable("RESP", CMD_TYPE.ELM_REQUEST_SUPPORTED_PARAMS);
        b.putString("ECU_CONNECT_RESULT", fail_okay(ecuConnected));
        b.putString("ECU_PROTO_STRING", ecuProto);
        sendMessage(b);
    }

    // Send a response detailing results of ECU_CONNECT
    private void ecuConnectResp(boolean ecuConnected, String ecuProto) {
        Log.d("ELM327", "Responding to ECU_CONNECT");
        Bundle b = new Bundle();
        b.putSerializable("RESP", CMD_TYPE.ECU_CONNECT);
        b.putString("ECU_CONNECT_RESULT", fail_okay(ecuConnected));
        b.putString("ECU_PROTO_STRING", ecuProto);
        sendMessage(b);
    }

    // Send a response detailing results of BT_ELM_CONNECT
    private void btConnectResp(boolean btConn, boolean elmConn, String elmVers) {
        Log.d("ELM327", "Responding to BT_ELM_CONNECT");
        Bundle b = new Bundle();
        b.putSerializable("RESP", CMD_TYPE.BT_ELM_CONNECT);
        b.putString("BT_CONNECT_RESULT", fail_okay(btConn));
        b.putString("ELM_CONNECT_RESULT", fail_okay(elmConn));
        b.putString("ELM_VERSION_STRING", elmVers);
        sendMessage(b);
    }

    // Send a response detailing results of ELM_RESET
    private void elmResetResp(boolean resetResult) {
        Log.d("ELM327", "Responding to ELM_RESET");
        Bundle b = new Bundle();
        b.putSerializable("RESP", CMD_TYPE.ELM_RESET);
        b.putString("ELM_RESET_RESULT", fail_okay(resetResult));
        sendMessage(b);
    }

    // Send a response detailing results of ELM_REQUEST_SUPPORTED_PARAMS
    private void elmSupportedPIDsResp(String PIDName) {
        Bundle b = new Bundle();
        b.putSerializable("RESP", CMD_TYPE.ELM_REQUEST_SUPPORTED_PARAMS);
        b.putString("NAME", PIDName);
        sendMessage(b);
    }

    // Send a Bundle from thread in a Message using outHandlerMA
    private void sendMessage(Bundle b) {
        Message msgOut = outHandlerMA.obtainMessage();
        msgOut.setData(b);
        outHandlerMA.sendMessage(msgOut);
    }

    // send string Data - Send Data over Bluetooth!
    private void send(String Data) {
        // send (string Data) - Send Data over Bluetooth!
        if (btConnected) {
            reportComm("TX:  " + Data);
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
        reportComm("RX:   "+ Data);
        return Data;
    }


    public String reset() {
        // Issue reset command to ELM327
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

    private int getSupportedPIDs() {
        int numSupportedPIDs = 0;
        boolean complete = false;
        String requestString = "0100";
        while(!complete) {
            PID Pid0100 = new PID(mContext, requestString);
            Log.d("ELM327", "GetSupportedPIDs command" + Pid0100.getCommand());
            String response = request(Pid0100.getCommand());
            Log.d("ELM327", "GetSupportedPIDs response" + response);
            Pid0100.update(response);
            Pid0100.printData();
            ArrayList<PID.Element> supportPidElements = Pid0100.getAllElements();
            boolean continueRequests = false;
            for(PID.Element E : supportPidElements) {
                if(E.mType==PID.ElementType.BOOLEAN)
                {
                    continueRequests = E.mBoolState;
                    if(E.mBoolState) {
                        Log.d("ELM327", "processing suported PID element" + E.mLongName);
                        Log.d("ELM327", "Corresponding command: " + E.mShortName);
                        PID p = new PID(mContext, E.mShortName);
                        ArrayList<PID.Element> el = p.getAllElements();
                        if(p.mType != PID.PIDType.SUPPORT) {
                            for(PID.Element e : el) {
                                // Send message of supported element
                                Log.d("ELM327", " --- element name: " + e.mLongName);
                                elmSupportedPIDsResp(e.mLongName);
                            }
                        }

                        // Add supported PID to ArrayList of supported PIDs
                        supportedPIDs.add(p);
                    }
                }
            }
            if(continueRequests && requestString.charAt(2) < '4') {
                StringBuilder sb = new StringBuilder(requestString);
                sb.setCharAt(2, (char)((int)requestString.charAt(2) + 2));  // 0100,0120,0140
                requestString = sb.toString();
            }
            else {
                complete = true;
            }

        }
        return numSupportedPIDs;
    }


    @Override
    public void run() {
        Log.d("ELM327", "RUNLOOP PRE LOOPER PREPARE~~~~~~~~~~");
        Looper.prepare();
        Log.d("ELM327", "RUNLOOP~~~~~~~~~~");

        Looper.myQueue().addIdleHandler(new MessageQueue.IdleHandler() {
            @Override
            public boolean queueIdle() {
                // ... Do some jazz
                Log.d("ELM327", "Idle Handler >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
                switch (mLatestCmd) {
                    case NONE:
                        // Update the active PID list
                        for(PID p : activePIDs) {
                            Log.d("ELM327", "PID Request Data for" + p.getCommand());
                            String response = request(p.getCommand());
                            p.update(response);
                            ArrayList<PID.Element> elList = p.getAllElements();
                            for(PID.Element el : elList) {
                                if(el.mType== PID.ElementType.VALUE) {
                                    sendDisplayUpdate(el.mLongName, el.mValueElementValue);
                                }
                            }
                        }
                        break;
                    case BT_ELM_CONNECT:
                        if (!btConnected && !elmConnected) {
                            Log.d("ELM327", "Run() Bluetooth connect  to " + mBluetoothAddr);
                            btConnected = btConnect(mBluetoothAddr);
                        }
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
                        if(!mDataRequestCmd.isEmpty()) {
                            requestParameter(mDataRequestCmd);
                            mLatestCmd = CMD_TYPE.NONE;
                        }
                        break;
                    case ELM_REQUEST_SUPPORTED_PARAMS:
                        int numSupportedPIDs = getSupportedPIDs();
                        mLatestCmd = CMD_TYPE.NONE;
                        break;
                    default:
                        Log.d("ELMThread", "Run encountered invalid latest command case");
                        mLatestCmd = CMD_TYPE.NONE;             // Reset
                        break;
                }
                return true;
            }
        });
        // Handler for commands issued to ELM327 thread
         inHandler = new Handler() {
            // Handler for message received by ELM327 thread
            @Override
            public void handleMessage(Message msg) {
                mBundle = msg.getData();
                if (mBundle.containsKey("CMD")) {
                    mLatestCmd = (CMD_TYPE) mBundle.getSerializable("CMD");
                    switch (mLatestCmd) {
                        // Handle valid commands - set up initial conditions for run() to perform
                        case BT_ELM_CONNECT:
                            mBluetoothAddr = mBundle.getString("DEVICE_ADDR");
                            Log.d("ELM327", "Connect command received to " + mBluetoothAddr);
                            break;
                        case ELM_RESET:
                            Log.d("ELM327", "Reset command received");
                            mLatestCmd = CMD_TYPE.ELM_RESET;
                            break;
                        case ECU_CONNECT:
                            Log.d("ELM327", "ECU connect command received");
                            mLatestCmd = CMD_TYPE.ECU_CONNECT;
                            break;
                        case ELM_REQUEST_DATA:
                            mDataRequestCmd = mBundle.getString("PARAM_REQ");
                            Log.d("ELM327", "ECU request data command received: " + mDataRequestCmd);
                            mLatestCmd = CMD_TYPE.ELM_REQUEST_DATA;
                            break;
                        case ELM_REQUEST_SUPPORTED_PARAMS:
                            mLatestCmd = CMD_TYPE.ELM_REQUEST_SUPPORTED_PARAMS;

                            Log.d("ELM327", "PID Support Request command received");
                            break;
                        case ELM_RESET_DATA:
                            activePIDs.clear();
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

        Looper.loop();



//        while (true) {
//            //Run loop!!
//            switch (mLatestCmd) {
//                case NONE:
//                    // Update the active PID list
//                    for(PID p : activePIDs) {
//                        Log.d("ELM327", "PID Request Data for" + p.getCommand());
//                        String response = request(p.getCommand());
//                        p.update(response);
//                        ArrayList<PID.Element> elList = p.getAllElements();
//                        for(PID.Element el : elList) {
//                            if(el.mType== PID.ElementType.VALUE) {
//                                sendDisplayUpdate(el.mLongName, el.mValueElementValue);
//                            }
//                        }
//                    }
//                    break;
//                case BT_ELM_CONNECT:
//                    if (!btConnected && !elmConnected) {
//                        Log.d("ELM327", "Run() Bluetooth connect  to " + mBluetoothAddr);
//                        btConnected = btConnect(mBluetoothAddr);
//                    }
//                    if(!btConnected) {
//                        Log.d("ELM327", "Bluetooth connect error");
//                    }
//                    else
//                        Log.d("ELM327", "Connect called while already connected");
//                    if (btConnected && !elmConnected) {
//                        Log.d("ELMThread", "Bluetooth connect success, connecting to ELM");
//                        String elmVers = reset();
//                        Log.d("ELM327", "ELM Version string: " + elmVers);
//                        if (elmVers.startsWith("ELM327 v")) {
//                            elmConnected = true;
//                            Log.d("ELM327", "ELM connect success");
//                            btConnectResp(btConnected, elmConnected, elmVers);
//                        } else {
//                            Log.d("ELM327", "ELM connect error");
//                            elmConnected = false;
//                        }
//                    }
//                    mLatestCmd = CMD_TYPE.NONE;             // Reset
//                    break;
//                case ELM_RESET:
//                    mLatestCmd = CMD_TYPE.NONE;             // Reset
//                    break;
//                case ECU_CONNECT:
//                    if(!btConnected || !elmConnected)
//                        Log.d("ELMThread", "ECU connect called while BT or ELM disconnected");
//                    else if(!ecuConnected){
//                        ecuConnected = false;
//                        String proto = ecuConnect();
//                        if(!proto.isEmpty()) {
//                            ecuConnected = true;
//                        }
//                        ecuConnectResp(ecuConnected, proto);
//                    } else
//                        Log.d("ELMThread", "ECU connect called while already connected");
//                    mLatestCmd = CMD_TYPE.NONE;             // Reset
//                    break;
//                case ELM_REQUEST_DATA:
//                    if(!mDataRequestCmd.isEmpty()) {
//                        requestParameter(mDataRequestCmd);
//                        mLatestCmd = CMD_TYPE.NONE;
//                    }
//                    break;
//                case ELM_REQUEST_SUPPORTED_PARAMS:
//                    int numSupportedPIDs = getSupportedPIDs();
//                    mLatestCmd = CMD_TYPE.NONE;
//                    break;
//                default:
//                    Log.d("ELMThread", "Run encountered invalid latest command case");
//                    mLatestCmd = CMD_TYPE.NONE;             // Reset
//                    break;
//            }
//
//        }



        }

        private void sendDisplayUpdate(String paramName, float value) {
            value = 100;
            Bundle updateBundle = new Bundle();
            Message msg = outHandlerDisp.obtainMessage();
            updateBundle.putSerializable("CMD", DrawableSurfaceView.VIEW_CMD_TYPE.UPDATE);
            updateBundle.putSerializable("OBJ", DrawableSurfaceView.VIEW_OBJ_TYPE.READOUT);
            updateBundle.putString("IDENT", paramName);
            updateBundle.putFloat("VAL", value);
            msg.setData(updateBundle);
            outHandlerDisp.sendMessage(msg);
        }
    }