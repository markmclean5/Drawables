package com.app.mark.drawables;

/**
 * Created by markm on 12/31/2016.
 */

import java.util.List;

public class PID {
    public enum PIDType{
        SUPPORT, PARAMETER
    }
    private enum ElementType {
        BOOLEAN, VALUE, ENUM, SPARE
    }
    String mIdent;
    int numBytes;           // ex - 2
    String mode;            // ex - 01
    String command;         // ex - 011 (only use first reply)

    // Container for all elements within PID
    List<Element> ElementList;

    public PID(String ident) {
        // PID constructor
        mIdent = ident;
        parsePID();
    }

    public void update(String data) {
        // Update all elements within the PID
        for (Element E : ElementList) {
            E.update(data);
        }
    }

    public

    private void parsePID() {
        // Parse PID contents from PIDConf xml using identifier
    }
    private class Element {
        // Element class definition - piece of data within a PID
        ElementType mType;
        String mStartPosition;          // ex - B7
        int mBitLength;
        String mShortName;
        String mLongName;
        String mDescription;

        // Enum & Boolean
        int mNumStates;
        String[] mEnumStates;
        int[] mEnumVals;
        int mEnumElementVal;

        // Value
        float mValueElementValue;
        String mUnits;


        public void update(String data) {
            // Update element with data string
            switch (mType) {
                case BOOLEAN: {
                    // Update boolean type
                }
                case VALUE: {
                    // Update value type
                }
                case ENUM: {
                    // Update enumeration type
                }
                case SPARE: {
                    // Do nothing
                }
            }
        }

        public void parsePIDElement(int ident) {
            // Parse individual PID element from PIDConf xml using element identifier
            String elementTag = "element" + ident;

            /* Parse common element attributes:
                - Type
                - Start bit position
                - Length in bits
                - Names (short, long, description)
             */

            // determine element type

            // perform enum & boolean type parsing

            // perform value type parsing
        }



    }
}
