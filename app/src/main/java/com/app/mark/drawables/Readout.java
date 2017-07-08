package com.app.mark.drawables;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;


/**
 * Created by mark on 12/17/2016.
 */

public class Readout {

    private Paint mLinePaint;
    Canvas mCanvas;
    Context mContext;

    String mIdentifier;
    Boolean parsed = false;


    // Displayed
    float mUnitString;
    float mNameString;
    float mValue = 0;

    // Location
    int mXposn;
    int mYposn;

    // Configuration
    int numDecPlaces;
    int numIntDigits;
    int valueTextSize;
    int unitTextSize;
    int nameTextSize;


    public Readout(Context context, String identifier, int x_posn, int y_posn) {
        mContext = context;
        mIdentifier = identifier;
        mLinePaint = new Paint();
        mLinePaint.setAntiAlias(true);
        mXposn = x_posn;
        mYposn = y_posn;
        //parsed = parse();
    }

    public void setValue(float value) {
        mValue = value;
    }

    public String getIdent() {
        return mIdentifier;
    }


    private String colorString;

    void draw( Canvas canvas) {
        mLinePaint.setColor((Color.parseColor("yellow")));


        mCanvas = canvas;
        //mLinePaint.setStrokeWidth(30);
        int textSize = 70;
        mLinePaint.setTextSize(textSize);
        mCanvas.drawText(Float.toString(mValue), mXposn, mYposn, mLinePaint);
        mCanvas.drawText(mIdentifier, mXposn, mYposn+textSize+10, mLinePaint);
    }


    /*
    public boolean parse() {
        XmlPullParserFactory factory = null;
        XmlPullParser parser = null;
        String text = "";
        // parsing complete
        boolean success = false;
        // parsing in progress - between desired start and stop tags
        boolean parsing = false;

        try {
            factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            parser = factory.newPullParser();
            // xml file for gauges is in raw resource dir
            InputStream is = mContext.getResources().openRawResource(R.raw.gauges);
            parser.setInput(is, null);
            int eventType = parser.getEventType();
            // parse entire file
            while (eventType != XmlPullParser.END_DOCUMENT) {
                //grab tag
                String tagname = parser.getName();
                switch (eventType) {
                    // handle start tags
                    case XmlPullParser.START_TAG:
                        if (tagname.equalsIgnoreCase(mIdentifier)) {
                            // start parsing if start tag is desired identifier
                            parsing = true;
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
                            if (tagname.equalsIgnoreCase(mIdentifier)) {
                                parsing = false;
                                break;
                            } else if (tagname.equalsIgnoreCase("radius")) {
                                mRadius = Float.parseFloat(text);
                            } else if (tagname.equalsIgnoreCase("border_width")) {
                                mBorderWidth = Float.parseFloat(text);
                            } else if (tagname.equalsIgnoreCase("start_angle")) {
                                mStartAngle = Float.parseFloat(text);
                            } else if (tagname.equalsIgnoreCase("start_value")) {
                                mStartValue = Float.parseFloat(text);
                            } else if (tagname.equalsIgnoreCase("stop_angle")) {
                                mStopAngle = Float.parseFloat(text);
                            } else if (tagname.equalsIgnoreCase("stop_value")) {
                                mStopValue = Float.parseFloat(text);
                            } else if (tagname.equalsIgnoreCase("dec_places")) {
                                mDecPlaces = Integer.parseInt(text);
                            } else if (tagname.equalsIgnoreCase("major_incr")) {
                                mMajorIncr = Float.parseFloat(text);
                            } else if (tagname.equalsIgnoreCase("minor_incr")) {
                                mMinorIncr = Float.parseFloat(text);
                            } else if (tagname.equalsIgnoreCase("major_len")) {
                                mMajorLen = Float.parseFloat(text);
                            } else if (tagname.equalsIgnoreCase("minor_len")) {
                                mMinorLen = Float.parseFloat(text);
                            } else if (tagname.equalsIgnoreCase("major_thick")) {
                                mMajorThick = Float.parseFloat(text);
                            } else if (tagname.equalsIgnoreCase("minor_thick")) {
                                mMinorThick = Float.parseFloat(text);
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

    */


}
