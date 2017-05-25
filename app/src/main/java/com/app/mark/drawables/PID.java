package com.app.mark.drawables;

/**
 * Created by markm on 12/31/2016.
 */

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;


public class PID {
    private enum PIDType{
        UNK, SUPPORT, PARAMETER
    }
    enum ElementType {
        NONE, BOOLEAN, VALUE, ENUM, SPARE
    }
    private Context mContext;

    PIDType mType = PIDType.UNK;

    String mIdent;
    String mName = "unnamed";// ex "00"
    int mNumBytes;           // ex - 2
    String mMode;            // ex - "01"
    String mCommand;         // ex - "1" (only use first reply)

    int mNumElements;
    // Container for all elements within PID
    List<Element> ElementList;


    public PID(Context context, String ident) {
        // PID constructor
        mContext = context;
        mIdent = ident;
        ElementList = new ArrayList<Element>();
        parsePID();
    }

    public void printData() {
        if(mType == PIDType.SUPPORT) {
            // Support type PID - print out which elements are supported
            Log.d ("PID", "Support Type PID printout " + mMode + mCommand + " - " + mName + ":");
            for(Element E : ElementList) {
                if(E.mType == ElementType.BOOLEAN && E.mBoolState) {
                    Log.d("PID", "    " + E.mShortName + " - " + E.mLongName);
                }
            }
        }
        else if(mType == PIDType.PARAMETER) {
            Log.d ("PID", "Parameter Type PID printout " + mMode + mCommand + " - " + mName + ":");
            for(Element E : ElementList) {
                if(E.mType == ElementType.BOOLEAN) {
                    Log.d("PID", "    " + E.mShortName + " - " + E.mLongName + ": " + E.mBoolState);
                }
                else if(E.mType == ElementType.VALUE) {
                    Log.d("PID", "    "+ E.mShortName + " - " + E.mLongName + ": " + E.mValueElementValue + " (" + E.mValueUnits + ")");
                }
                else if(E.mType == ElementType.ENUM) {
                    Log.d("PID", "    "+ E.mShortName + " - " + E.mLongName + ": " + E.mEnumCurrentState);
                }
            }
        }

    }

    public int getNumElements() {
        return ElementList.size();
    }


    public ArrayList<Element> getAllElements(){
        ArrayList<Element> elements = new ArrayList<>();
        for(Element E : ElementList) {
            elements.add(E);
        }
        return elements;
    }



    public String getCommand() {
        String command = mMode + mCommand;
        return command;
    }

    public void update(String response) {
        // validate data string - ensure it is a good response
        // Strip all whitespace
        response = response.replaceAll("\\s+","");
        boolean validResponse = true;
        String modeResp = "4" + mMode.substring(1);
        String data = "";
        String expectedResp = modeResp + mCommand;
        if(!response.startsWith(expectedResp)){
            Log.d("PID", "Error - update response unexpected chars");
            validResponse = false;
        }
        else {
            int dataStartPos = mMode.length() + mCommand.length();
            data = response.substring(dataStartPos-1);
            if(data.length() < mNumBytes*2) {
                Log.d("PID", "Error - update response too short");
                validResponse = false;
            }
        }
        if(validResponse) {
            // specify format and convert to integer
            data = data.trim();
            String hexStringData = "0x0" + data;
            Long longData = Long.decode(hexStringData);
            // Update all elements within the PID
            for (Element E : ElementList) {
                E.update(longData);
            }
        }
    }

    private boolean parsePID() {
        // Parse PID contents from PIDConf xml using identifier
        XmlPullParserFactory factory = null;
        XmlPullParser parser = null;
        String text = "";
        // parsing complete
        boolean success = false;
        // parsing in progress - between desired start and stop tags
        boolean parsing = false;

        boolean parsingElement = false;
        int currentElement = 0;
        Element el = new Element();

        try {
            factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            parser = factory.newPullParser();
            // xml file for pids is in raw resource dir
            InputStream is = mContext.getResources().openRawResource(R.raw.pids);
            parser.setInput(is, null);
            int eventType = parser.getEventType();
            // parse entire file

            String pidIdent = "pid_" + mIdent;
            while (eventType != XmlPullParser.END_DOCUMENT) {
                //grab tag
                String tagname = parser.getName();
                switch (eventType) {
                    // handle start tags
                    case XmlPullParser.START_TAG:
                        if (tagname.equalsIgnoreCase(pidIdent)) {
                            // start parsing pid if start tag is desired identifier
                            parsing = true;
                            currentElement = 1;
                        } else if(tagname.equalsIgnoreCase("element" + currentElement)) {
                            parsingElement = true;
                            el = new Element();
                        }
                        break;
                    // capture text
                    case XmlPullParser.TEXT:
                        text = parser.getText();
                        break;
                    // handle end tags - populate values
                    case XmlPullParser.END_TAG:
                        if(parsing) {

                            if(!parsingElement) {
                                // stop parsing if end tag is desired identifier otherwise parse all PID properties
                                if (tagname.equalsIgnoreCase(pidIdent)) {
                                    parsing = false;
                                    break;
                                } else if (tagname.equalsIgnoreCase("name")) {
                                    mName = text;
                                } else if (tagname.equalsIgnoreCase("num_bytes")) {
                                    mNumBytes = Integer.parseInt(text);
                                } else if (tagname.equalsIgnoreCase("num_elements")) {
                                    mNumElements = Integer.parseInt(text);
                                } else if(tagname.equalsIgnoreCase("type")) {
                                    if(text.equalsIgnoreCase("support"))
                                        mType = PIDType.SUPPORT;
                                    else if(text.equalsIgnoreCase("parameter")){
                                        mType = PIDType.PARAMETER;
                                    }
                                } else if(tagname.equalsIgnoreCase("mode")) {
                                    mMode = text;
                                } else if(tagname.equalsIgnoreCase("command")) {
                                    mCommand = text;
                                }

                            }
                            else if(parsingElement) {
                                // Parse element properties
                                if (tagname.equalsIgnoreCase("element" + currentElement)) {
                                    // done parsing current element, add to list if it validates
                                    if(el.validate()) {
                                        ElementList.add(el);
                                    }
                                    // move to next element
                                    parsingElement = false;
                                    currentElement++;
                                } else if(tagname.equalsIgnoreCase("type")) {
                                    // parse PID type
                                    if(text.equalsIgnoreCase("boolean")) {
                                        el.mType = ElementType.BOOLEAN;
                                        // boolean type element basic setup
                                        el.mBitLength = 1;
                                        el.mNumStates = 2;
                                        el.mEnumStates = new String[2];
                                        el.mEnumStates[0] = "false";
                                        el.mEnumStates[1] = "true";
                                        el.mBitLength = 1;

                                    } else if(text.equalsIgnoreCase("value")) {
                                        el.mType = ElementType.VALUE;
                                    } else if(text.equalsIgnoreCase("enum")) {
                                        el.mType = ElementType.ENUM;
                                    } else if(text.equalsIgnoreCase("spare")) {
                                        el.mType = ElementType.SPARE;
                                    }
                                } else if (tagname.equalsIgnoreCase("position")) {
                                    el.mStartPosition = text;
                                } else if (tagname.equalsIgnoreCase("num_bits")) {
                                    el.mBitLength = Integer.parseInt(text);
                                } else if(tagname.equalsIgnoreCase("short_name")) {
                                    el.mShortName = text;
                                } else if(tagname.equalsIgnoreCase("long_name")) {
                                    el.mLongName = text;
                                } else if (el.mType == ElementType.VALUE) {
                                    // Value type element parsing
                                    if(tagname.equalsIgnoreCase("min")) {
                                        el.mValueMin = Float.parseFloat(text);
                                    } else if(tagname.equalsIgnoreCase("max")) {
                                        el.mValueMax = Float.parseFloat(text);
                                    } else if(tagname.equalsIgnoreCase("units")) {
                                        el.mValueUnits = text;
                                    } else if(tagname.equalsIgnoreCase("scaling")) {
                                        el.mValueScaling = Float.parseFloat(text);
                                    } else if(tagname.equalsIgnoreCase("offset")) {
                                        el.mValueOffset = Float.parseFloat(text);
                                    }
                                } else if(el.mType == ElementType.ENUM) {
                                    // Enumerated type element parsing
                                    if(tagname.equalsIgnoreCase("num_states")) {
                                        el.mNumStates = Integer.parseInt(text);
                                        el.mEnumVals = new int[el.mNumStates];
                                        // One more state than values for invalid case
                                        el.mEnumStates = new String[el.mNumStates + 1];
                                        el.mParsedStates = 0;
                                    } else if (el.mParsedStates < el.mNumStates) {
                                        // parse state value and state strings
                                        if(tagname.equalsIgnoreCase("enum_" + (el.mParsedStates + 1) + "_value")) {
                                            el.mEnumVals[el.mParsedStates] = Integer.parseInt(text);
                                        } else if(tagname.equalsIgnoreCase("enum_" + (el.mParsedStates + 1) + "_state")) {
                                            el.mEnumStates[el.mParsedStates] = text;
                                            el.mParsedStates++;
                                        }
                                    } else if(tagname.equalsIgnoreCase("enum_invalid_state")) {
                                        // Invalid state string stored in last position of array
                                        el.mEnumStates[el.mNumStates] = text;
                                    }
                                }

                            }
                        }
                        break;

                    default:
                        break;
                }
                eventType = parser.next();
            }

        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return success;
    }
    /*  ------------------------------
        PID::Element class definition
        An Element is a piece of data within a PID
            - Boolean
            - Value
            - Enumeration
            - Spare
        ------------------------------ */
    public class Element {
        // Element properties - applicable to all types of elements
        ElementType mType;
        String mStartPosition;          // ex - B7
        int mBitLength;
        String mShortName;
        String mLongName = "";
        String mDescription = "";

        // Enum & Boolean properties
        boolean mBoolState = false;
        int mNumStates;
        int mParsedStates;
        String[] mEnumStates;
        int[] mEnumVals;
        int mEnumCurrentVal;
        String mEnumCurrentState;

        // Value properties
        float mValueElementValue;
        float mValueMin;
        float mValueMax;
        String mValueUnits;
        float mValueScaling;
        float mValueOffset;
        boolean mValueInRange;



        // Element constructor
        public Element() {
            mType = ElementType.NONE;
        }

            // Element update method - accepts payload from response message
        public void update(Long data) {
            // Locate appropriate region of data
            int byteNumber = (int)mStartPosition.charAt(0) - 65;    // 'A'->0...
            if(byteNumber >= mNumBytes || byteNumber < 0)
                Log.d("PID Element Update", "Error: boolean - invalid start byte position");
            int bitNumber = (int)mStartPosition.charAt(1) - 48;     // '0'->0
            if(bitNumber > 7 || bitNumber < 0) {
                Log.d("PID Element Update", "Error: boolean - invalid start bit position");
            }
            // going to right shift this much
            int startBit = 8*(byteNumber) + (7 - bitNumber) + mBitLength;
            data = data >> 8*(mNumBytes) - startBit;
            int mask = (1 << mBitLength) - 1;
            data &= mask;
            switch (mType) {
                case BOOLEAN: {
                    // Update boolean type - nonzero data -> true
                    if(data != 0){
                        mBoolState = true;
                    }
                    else {
                        mBoolState = false;
                    }
                    break;
                }
                case VALUE: {
                    // Update value type
                    mValueElementValue = mValueScaling*data + mValueOffset;
                    if(mValueElementValue > mValueMax || mValueElementValue < mValueMin) {
                        mValueInRange = false;
                        Log.d("PID", "Error - element update value element out of range");
                    }
                    else
                        mValueInRange = true;
                    break;
                }
                case ENUM: {
                    // Update enumeration type
                    mEnumCurrentVal = data.intValue();
                    // default to invalid
                    mEnumCurrentState = mEnumStates[mNumStates];
                    for(int V : mEnumVals) {
                        if(V == mEnumCurrentVal) {
                            mEnumCurrentState = mEnumStates[V];
                            break;
                        }
                    }
                    break;
                }
                case SPARE: {
                    // Do nothing
                    break;
                }
                default: {
                    Log.d ("PID", "Error - element update type error");
                    break;
                }
            }
        }

        boolean validate() {
            // determine if element is properly configured & parsed, safe for usage
            boolean valid = false;
            switch (mType) {
                case BOOLEAN: {
                    // Validate boolean type
                    if(!mStartPosition.isEmpty() && !mShortName.isEmpty())
                        // long name and description are optional at the moment
                        valid = true;
                    break;
                }
                case VALUE: {
                    // TODO: Add value type validation logic
                    if(!mStartPosition.isEmpty() && !mShortName.isEmpty() && mValueScaling != 0 && mBitLength > 0 && (mValueMin < mValueMax))
                        valid = true;
                    break;
                }
                case ENUM: {
                    // TODO: Add enumeration type validation logic
                    valid = true;
                    for(String S : mEnumStates) {
                        if(S.isEmpty()) {
                            valid = false;
                            break;
                        }
                    }
                    if(mNumStates < 1 || mParsedStates != mNumStates)
                        valid = false;
                    break;
                }
                case SPARE: {
                    // Validate spare type (always valid)
                    valid = true;
                    break;
                }
            }
            if(!valid) {
                // error case - element did not validate
                Log.d("PID Element", "Error - PID Element failed validation");
            }
            return valid;
        }
    }
}
