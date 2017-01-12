package com.app.mark.drawables;

/**
 * Created by markm on 12/31/2016.
 */

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.StringTokenizer;

public class PID {
    public enum PIDType{
        SUPPORT, PARAMETER
    }
    private enum ElementType {
        NONE, BOOLEAN, VALUE, ENUM, SPARE
    }
    Context mContext;
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

    public void update(String data) {
        // Update all elements within the PID
        for (Element E : ElementList) {
            E.update(data);
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
                            // stop parsing if end tag is desired identifier otherwise parse all
                            if (tagname.equalsIgnoreCase(pidIdent)) {
                                parsing = false;
                                break;
                            } else if (tagname.equalsIgnoreCase("name")) {
                                mName = text;
                            } else if (tagname.equalsIgnoreCase("num_bytes")) {
                                mNumBytes = Integer.parseInt(text);
                            } else if (tagname.equalsIgnoreCase("num_elements")) {
                                mNumElements = Integer.parseInt(text);
                            } else if(parsingElement) {
                                // Parse element
                                if (tagname.equalsIgnoreCase("element" + currentElement)) {
                                    // done parsing current element, add to list

                                    Log.d("PID", "adding element to PID");
                                    ElementList.add(el);
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
                                    if (tagname.equalsIgnoreCase("position")) {
                                        el.mStartPosition = text;
                                    } else if(tagname.equalsIgnoreCase("short_name")) {
                                        el.mShortName = text;
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

        public Element() {
            mType = ElementType.NONE;
        }


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
    }
}
