package com.app.mark.drawables;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;

import java.util.Vector;

// DrawableSurfaceView class: SurfaceView with thread for drawing operations
// Receives messages to create/update/destroy display objects (gauges)
//
class DrawableSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    // Definition of compatible commands key "CMD" sent to the thread
    enum VIEW_CMD_TYPE {
        NONE,
        ADD,
        UPDATE,
        DESTROY,
    }

    enum VIEW_OBJ_TYPE {
        GAUGE,
        READOUT
    }

    // Thread for all draw operations
    class DrawableThread extends Thread {
        // Returns input handler
        public Handler getHandler() {
            return handler;
        }

        // Message handler: receives all messages coming into DrawableThread
        private final Handler handler = new Handler() {
            public void handleMessage(Message msg) {
                Bundle mBundle = msg.getData();
                if(mBundle.containsKey("CMD")) {
                    VIEW_CMD_TYPE latestCmd = (VIEW_CMD_TYPE) mBundle.getSerializable("CMD");
                    String ident = mBundle.getString("IDENT");
                    VIEW_OBJ_TYPE type = (VIEW_OBJ_TYPE) mBundle.getSerializable("OBJ");
                    switch(latestCmd) {
                        case NONE:
                            // empty command received
                            break;
                        case ADD:
                            // Add a gauge to the DrawableSurfaceView
                            switch(type) {
                                case GAUGE:
                                    addGauge(ident);
                                    break;
                                case READOUT:
                                    addReadout(ident);
                                    break;
                            }
                            break;
                        case DESTROY:
                            switch(type) {
                                case GAUGE:
                                    destroyGauge(ident);
                                    break;
                                case READOUT:
                                    destroyReadout(ident);
                                    break;
                            }
                            break;
                        case UPDATE:
                            Float updVal= mBundle.getFloat("VAL");
                            switch(type){
                                case GAUGE:
                                    updateGauge(ident, updVal);
                                    break;
                                case READOUT:
                                    updateReadout(ident, updVal);
                                    break;
                            }
                            break;
                        default:
                            Log.d("DrawableThread", "Unknown CMD type received");
                            break;
                    }
                }
            }
        };


        private void threadMsg(String msg) {
            if (msg!=null && !msg.equals("")) {
                Message msgObj = handler.obtainMessage();
                Bundle b = new Bundle();
                b.putString("message", msg);
                msgObj.setData(b);
                handler.sendMessage(msgObj);
            }
        }

        /*
        * State-tracking constants
        */
        public static final int STATE_PAUSE = 1;
        public static final int STATE_READY = 2;
        public static final int STATE_RUNNING = 3;

        private Context mContext;


        Vector<Gauge> mGaugeVector = new Vector<Gauge>();
        Vector<Readout> mReadoutVector = new Vector<>();



        /** Used to figure out elapsed time between frames */
        private long mLastTime;

        /** The state of the game. One of READY, RUNNING, PAUSE, LOSE, or WIN */
        private int mMode;

        int mCanvasWidth = 1;
        int mCanvasHeight = 1;


        /** Test line paint */
        private Paint mLinePaint;


        private boolean mRun = false;
        private final Object mRunLock = new Object();
        /** Handle to the surface manager object we interact with */
        private SurfaceHolder mSurfaceHolder;


        public void addGauge(String ident) {
            synchronized (mSurfaceHolder) {
                mGaugeVector.add(new Gauge(getContext(), ident));
                Log.d("gauges:", "added gauge" + mGaugeVector.size());
            }

        }

        public void destroyGauge(String ident) {
            synchronized (mSurfaceHolder) {
                if(!mGaugeVector.isEmpty()) {
                    for(Gauge g:mGaugeVector){
                        if(g.getIdent().equals(ident)) {
                            mGaugeVector.remove(g);
                        }
                    }
                }
            }

        }

        public void updateGauge(String ident, float value) {
            synchronized (mSurfaceHolder) {
                for (Gauge G : mGaugeVector) {
                    if (G.getIdent() == ident) {
                        G.setValue(value);
                        break;
                    } else
                        Log.d("gauge update:", "could not find" + ident);
                }
            }
        }


        public void addReadout(String ident) {
            mReadoutVector.add(new Readout(getContext(), ident));

        }

        public void destroyReadout(String ident) {
            if(!mReadoutVector.isEmpty()) {
                for(Readout r : mReadoutVector){
                    if(r.getIdent().equals(ident)) {
                        mReadoutVector.remove(r);
                    }
                }
            }

        }

        public void updateReadout(String ident, float value) {
            for (Readout R : mReadoutVector) {
                if (R.getIdent() == ident) {
                    R.setValue(value);
                    break;
                } else
                    Log.d("readout update:", "could not find" + ident);
            }
        }




        public DrawableThread(SurfaceHolder surfaceHolder, Context context) {
            // get handles to some important objects
            mSurfaceHolder = surfaceHolder;
            mContext = context;
            Resources res = context.getResources();
            // Initialize paint
            mLinePaint = new Paint();
            mLinePaint.setAntiAlias(true);
            mLinePaint.setARGB(255, 255, 255, 0);
        }
        /**
         * Starts the game, setting parameters for the current difficulty.
         */
        public void doStart() {
            synchronized (mSurfaceHolder) {
                mLastTime = System.currentTimeMillis() + 100;
                setState(STATE_RUNNING);

            }
        }
        /**
         * Pauses the physics update & animation.
         */
        public void pause() {
            synchronized (mSurfaceHolder) {
                if (mMode == STATE_RUNNING) setState(STATE_PAUSE);
            }
        }
        /**
         * Restores game state from the indicated Bundle. Typically called when
         * the Activity is being restored after having been previously
         * destroyed.
         *
         * @param savedState Bundle containing the game state
         */
        public synchronized void restoreState(Bundle savedState) {
            synchronized (mSurfaceHolder) {
                setState(STATE_PAUSE);
            }
        }
        @Override
        public void run() {
            threadMsg("TEST TEST TEST");
            while (mRun) {
                Canvas c = null;
                try {
                    c = mSurfaceHolder.lockCanvas(null);
                    synchronized (mSurfaceHolder) {
                        if (mMode == STATE_RUNNING)
                        // Critical section. Do not allow mRun to be set false until
                        // we are sure all canvas draw operations are complete.
                        //
                        // If mRun has been toggled false, inhibit canvas operations.
                        synchronized (mRunLock) {
                            if (mRun) doDraw(c);
                        }
                    }
                } finally {
                    // do this in a finally so that if an exception is thrown
                    // during the above, we don't leave the Surface in an
                    // inconsistent state
                    if (c != null) {
                        mSurfaceHolder.unlockCanvasAndPost(c);
                    }
                }
            }
        }
        /**
         * Dump game state to the provided Bundle. Typically called when the
         * Activity is being suspended.
         *
         * @return Bundle with this view's state
         */
        public Bundle saveState(Bundle map) {
            synchronized (mSurfaceHolder) {
                if (map != null) {

                }
            }
            return map;
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
        /**
         * Sets the game mode. That is, whether we are running, paused, in the
         * failure state, in the victory state, etc.
         *
         * @see #setState(int, CharSequence)
         * @param mode one of the STATE_* constants
         */
        public void setState(int mode) {
            synchronized (mSurfaceHolder) {
                setState(mode, null);
            }
        }
        /**
         * Sets the mode. That is, whether we are running, paused, in the
         * failure state, in the victory state, etc.
         *
         * @param mode one of the STATE_* constants
         * @param message string to add to screen or null
         */
        public void setState(int mode, CharSequence message) {
            /*
             * This method optionally can cause a text message to be displayed
             * to the user when the mode changes. Since the View that actually
             * renders that text is part of the main View hierarchy and not
             * owned by this thread, we can't touch the state of that View.
             * Instead we use a Message + Handler to relay commands to the main
             * thread, which updates the user-text View.
             */
            synchronized (mSurfaceHolder) {
                mMode = mode;
                if (mMode == STATE_RUNNING) {
                }
                else {
                }
            }
        }
        /* Callback invoked when the surface dimensions change. */
        public void setSurfaceSize(int width, int height) {
            // synchronized to make sure these all change atomically
            synchronized (mSurfaceHolder) {
                mCanvasWidth = width;
                mCanvasHeight = height;
            }
        }
        /**
         * Resumes from a pause.
         */
        public void unpause() {
            // Move the real time clock up to now
            synchronized (mSurfaceHolder) {
                mLastTime = System.currentTimeMillis() + 100;
            }
            setState(STATE_RUNNING);
        }

        /**
         * Do drawing here!!
         */
        private void doDraw(Canvas canvas) {
            canvas.drawColor(Color.BLACK);
            long currTime = System.currentTimeMillis();
            int c = (int)(currTime/10L) % 127;
            int deltaTime = (int)(currTime - mLastTime);
            String dTimeString = Integer.toString(deltaTime);
            mLinePaint.setTextSize(40);
            canvas.drawText(dTimeString, 50, 50, mLinePaint);


            // Draw all objects
            for(Gauge G : mGaugeVector) {
                G.draw(canvas);
            }
            for(Readout R : mReadoutVector) {
                R.draw(canvas);
            }



            mLastTime = currTime;
        }
    }
    /** The thread that actually draws the animation */
    private DrawableThread thread;
    public DrawableSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // register our interest in hearing about changes to our surface
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);
        // create thread only; it's started in surfaceCreated()
        thread = new DrawableThread(holder, context);
        setFocusable(true); // make sure we get key events
    }
    /**
     * Fetches the animation thread corresponding to this LunarView.
     *
     * @return the animation thread
     */
    public DrawableThread getThread() {
        return thread;
    }
    /**
     * Standard window-focus override. Notice focus lost so we can pause on
     * focus lost. e.g. user switches to take a call.
     */
    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        //if (!hasWindowFocus) thread.pause();
    }
    /* Callback invoked when the surface dimensions change. */
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
        thread.setSurfaceSize(width, height);
    }
    /*
     * Callback invoked when the Surface has been created and is ready to be
     * used.
     */
    public void surfaceCreated(SurfaceHolder holder) {
        // start the thread here so that we don't busy-wait in run()
        // waiting for the surface to be created
        thread.setRunning(true);
        thread.start();
    }
    /*
     * Callback invoked when the Surface has been destroyed and must no longer
     * be touched. WARNING: after this method returns, the Surface/Canvas must
     * never be touched again!
     */
    public void surfaceDestroyed(SurfaceHolder holder) {
        // we have to tell thread to shut down & wait for it to finish, or else
        // it might touch the Surface after we return and explode
        boolean retry = true;
        thread.setRunning(false);
        while (retry) {
            try {
                thread.join();
                retry = false;
            } catch (InterruptedException e) {
            }
        }
    }
}