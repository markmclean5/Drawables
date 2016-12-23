package com.app.mark.drawables;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Layout;

import org.xml.sax.InputSource;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;


/**
 * Created by mark on 12/17/2016.
 */

public class Gauge{

    private Paint mLinePaint;
    Canvas mCanvas;
    Context mContext;

    String mIdentifier;
    boolean parsed = false;

    float mRadius = 200;

    float mBorderWidth = 0;
    float mStartAngle = 0;
    float mStartValue = 0;
    int mDecPlaces = 0;
    float mStopAngle = 0;
    float mStopValue = 0;
    float mMajorIncr = 0;
    float mMinorIncr = 0;
    float mMajorThick = 0;
    float mMinorThick = 0;
    float mMajorLen = 0;
    float mMinorLen = 0;

    float mValue = 0;


    public Gauge(Context context, String identifier) {
        mContext = context;
        mIdentifier = identifier;
        mLinePaint = new Paint();
        mLinePaint.setAntiAlias(true);
        mLinePaint.setColor(Color.WHITE);

        parsed = parse();

    }

    public void setValue(float value) {
        mValue = value;
    }

    public String getIdent() {
        return mIdentifier;
    }

    void draw( Canvas canvas) {
        mCanvas = canvas;
        mLinePaint.setTextSize(60);

        int cx = 1920/2;
        int cy = 1200/2;
        float r = 400;
        float w = 10;


        mLinePaint.setStrokeWidth(mMajorThick);

        mLinePaint.setColor(Color.WHITE);


        mCanvas.drawCircle(cx, cy, mRadius, mLinePaint);

        float sx = 0;
        float sy = 0;
        float ex = 0;
        float ey = 0;

        float ang = 0;
        float incr = 15;

        float len = mMajorLen*mRadius;
        mLinePaint.setColor(Color.RED);
        mLinePaint.setStrokeWidth(mMajorThick*mRadius);
        while(ang < 360) {
            sx = (float) (cx + mRadius * Math.cos(3.141569*ang/180));
            sy = (float) (cy + mRadius * Math.sin(3.141569*ang/180));
            ex = (float) (cx + (mRadius - len) * Math.cos(3.141569*ang/180));
            ey = (float) (cy + (mRadius - len) * Math.sin(3.141569*ang/180));
            mCanvas.drawLine(sx, sy, ex, ey, mLinePaint);
            ang += incr;
        }
        mLinePaint.setTextSize(70);
        mCanvas.drawText(Float.toString(mValue), cx, cy, mLinePaint);

        mLinePaint.setTextAlign(Paint.Align.CENTER);
        mCanvas.drawText(mIdentifier, cx, cy - 75, mLinePaint);

    }

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



}
