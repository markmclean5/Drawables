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
import java.util.ArrayList;
import java.util.List;


public class PID {
    public enum PIDType{
        UNK, SUPPORT, PARAMETER
    }
    private enum ElementType {
        NONE, BOOLEAN, VALUE, ENUM, SPARE
    }
    Context mContext;

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
        Log.d("PID", "constructor");
        mContext = context;
        mIdent = ident;
        ElementList = new ArrayList<Element>();
        parsePID();
    }

    public void update(String response) {
        // validate data string - ensure it is a good response
        // Strip all whitespace
        response.replaceAll("\\s+","");
        boolean validResponse = true;
        String modeResp = "4" + mMode.substring(1);
        String data = "";
        if(!response.startsWith(mMode + mCommand + modeResp + mCommand)){
            Log.d("PID", "Error - update response unexpected chars");
            validResponse = false;
        }
        else {
            int dataStartPos = 2*(mMode.length() + mCommand.length());
            data = response.substring(dataStartPos-1);
            if(data.length() < mNumBytes*2) {
                Log.d("PID", "Error - update response too short");
                validResponse = false;
            }
        }
        if(validResponse) {
            // specify format and convert to integer
            String hexStringData = "0X" + data;
            int intData = Integer.decode(hexStringData);
            // Update all elements within the PID
            for (Element E : ElementList) {
                E.update(intData);
            }
        }
    }

    private boolean parsePID() {
        // Parse PID contents from PIDConf xml using identifier

        Log.d("PID", "parse");

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
                            Log.d("PID", "found start tag");
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
                            // stop parsing if end tag is desired identifier otherwise parse all properties
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
                            } else if(parsingElement) {
                                // Parse element
                                if (tagname.equalsIgnoreCase("element" + currentElement)) {
                                    // done parsing current element, add to list if it validates
                                    if(el.validate()) {
                                        Log.d("PID", "adding element to PID");
                                        ElementList.add(el);
                                    }
                                    // move to next element
                                    parsingElement = false;
                                    currentElement++;
                                }
                                else if(tagname.equalsIgnoreCase("type")) {
                                    if(text.equalsIgnoreCase("boolean")) {
                                        el.mType = ElementType.BOOLEAN;
                                    } else if(text.equalsIgnoreCase("value")) {
                                        el.mType = ElementType.VALUE;
                                    } else if(text.equalsIgnoreCase("enum")) {
                                        el.mType = ElementType.ENUM;
                                    } else if(text.equalsIgnoreCase("spare")) {
                                        el.mType = ElementType.SPARE;
                                    }
                                } else if (el.mType == ElementType.BOOLEAN) {
                                    // boolean type element basic setup
                                    el.mBitLength = 1;
                                    el.mNumStates = 2;
                                    el.mEnumStates = new String[2];
                                    el.mEnumStates[0] = "false";
                                    el.mEnumStates[1] = "true";
                                    el.mBitLength = 1;
                                    //
                                    if (tagname.equalsIgnoreCase("position")) {
                                        el.mStartPosition = text;
                                    } else if(tagname.equalsIgnoreCase("short_name")) {
                                        el.mShortName = text;
                                    } else if(tagname.equalsIgnoreCase("description")) {
                                        el.mDescription = text;
                                    } else if(tagname.equalsIgnoreCase("long_name")) {
                                        el.mLongName = text;
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
    private class Element {
        // Element properties - applicable to all types of elements
        ElementType mType;
        String mStartPosition;          // ex - B7
        int mBitLength;
        String mShortName;
        String mLongName = "";
        String mDescription = "";

        // Enum & Boolean properties
        int mNumStates;
        String[] mEnumStates;
        int[] mEnumVals;
        int mElementVal;

        // Value properties
        float mValueElementValue;
        String mUnits;


        // Element constructor
        public Element() {
            mType = ElementType.NONE;
        }


        // Element update method - accepts payload from response message
        public void update(int data) {
            // Locate appropriate region of data
            int byteNumber = (int)mStartPosition.charAt(0) - 65;    // 'A'->0...
            if(byteNumber >= mNumBytes || byteNumber < 0)
                Log.d("PID Element Update", "Error: boolean - invalid start byte position");
            int bitNumber = (int)mStartPosition.charAt(1) - 48;     // '0'->0
            if(bitNumber > 7 || bitNumber < 0) {
                Log.d("PID Element Update", "Error: boolean - invalid start bit position");
            }
            // going to right shift this much
            int startBit = 8*byteNumber + bitNumber - mBitLength;

            switch (mType) {
                case BOOLEAN: {
                    // Update boolean type

                    break;
                }
                case VALUE: {
                    // Update value type
                    break;
                }
                case ENUM: {
                    // Update enumeration type
                    break;
                }
                case SPARE: {
                    // Do nothing
                    break;
                }
            }
        }

        public boolean validate() {
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
                    valid = true;
                    break;
                }
                case ENUM: {
                    // TODO: Add enumeration type validation logic
                    valid = true;
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
