/*
 Copyright (c) 2011, Sony Ericsson Mobile Communications AB
 Copyright (c) 2011-2013, Sony Mobile Communications AB

 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met:

 * Redistributions of source code must retain the above copyright notice, this
 list of conditions and the following disclaimer.

 * Redistributions in binary form must reproduce the above copyright notice,
 this list of conditions and the following disclaimer in the documentation
 and/or other materials provided with the distribution.

 * Neither the name of the Sony Ericsson Mobile Communications AB nor the names
 of its contributors may be used to endorse or promote products derived from
 this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package youten.redo.smartextension.straight;

import java.util.ArrayList;
import java.util.List;

import youten.redo.smartextension.straight.sensor.ExtensionMotion;
import youten.redo.smartextension.straight.sensor.ExtensionMotion.MotionListener;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.sonyericsson.extras.liveware.aef.control.Control;
import com.sonyericsson.extras.liveware.aef.registration.Registration.SensorTypeValue;
import com.sonyericsson.extras.liveware.aef.sensor.Sensor;
import com.sonyericsson.extras.liveware.extension.util.control.ControlExtension;
import com.sonyericsson.extras.liveware.extension.util.control.ControlTouchEvent;
import com.sonyericsson.extras.liveware.extension.util.registration.DeviceInfoHelper;
import com.sonyericsson.extras.liveware.extension.util.sensor.AccessorySensor;
import com.sonyericsson.extras.liveware.extension.util.sensor.AccessorySensorEvent;
import com.sonyericsson.extras.liveware.extension.util.sensor.AccessorySensorEventListener;
import com.sonyericsson.extras.liveware.extension.util.sensor.AccessorySensorException;
import com.sonyericsson.extras.liveware.extension.util.sensor.AccessorySensorManager;

/**
 * The sample sensor control handles the accelerometer sensor on an accessory. This class exists in one instance for
 * every supported host application that we have registered to
 */
class StraightSensorControl extends ControlExtension implements MotionListener {

    private static final Bitmap.Config BITMAP_CONFIG = Bitmap.Config.RGB_565;

    private int mWidth = 220;
    private int mHeight = 176;
    private int mCurrentSensor = 0;
    private int mCount = 0;
    private String mPosition = ExtensionMotion.POSITION_UNKNOWN;
    private float mSeikenScore = 0;

    private ExtensionMotion mExtensionMotion = new ExtensionMotion(this);

    private List<AccessorySensor> mSensors = new ArrayList<AccessorySensor>();

    private final AccessorySensorEventListener mListener = new AccessorySensorEventListener() {

        @Override
        public void onSensorEvent(AccessorySensorEvent sensorEvent) {
            // Log.d(StraightExtensionService.LOG_TAG, "Listener: OnSensorEvent");
            mExtensionMotion.pushEvent(sensorEvent);
        }
    };

    @Override
    public void onPositionChanged(String fromPosition, String toPosition) {
        Log.d(StraightExtensionService.LOG_TAG, "Position " + fromPosition + " -> " + toPosition);
        mPosition = toPosition;
        if (toPosition != ExtensionMotion.POSITION_FRONT_UP) {
            mSeikenScore = 0;
        }
        updateCurrentDisplay(null);
    };

    @Override
    public void onFling(String fling) {
        Log.d(StraightExtensionService.LOG_TAG, "Fling " + fling);
    }

    @Override
    public void onSeiken(float score) {
        Log.d(StraightExtensionService.LOG_TAG,
                "New Seiken Score=" + String.format("%.2f", score));
        Notify.notify(mContext, "SEIKEN", "Score:" + String.format("%.2f", score));
        mSeikenScore = score;
        updateCurrentDisplay(null);
    };

    @Override
    public void onJump() {
        Log.d(StraightExtensionService.LOG_TAG, "Jump!");
    };

    /**
     * Create sample sensor control.
     * 
     * @param hostAppPackageName Package name of host application.
     * @param context The context.
     */
    StraightSensorControl(final String hostAppPackageName, final Context context) {
        super(context, hostAppPackageName);

        AccessorySensorManager manager = new AccessorySensorManager(context, hostAppPackageName);
        // Add accelerometer, if supported
        if (DeviceInfoHelper.isSensorSupported(context, hostAppPackageName,
                SensorTypeValue.ACCELEROMETER)) {
            Log.d(StraightExtensionService.LOG_TAG, "ACCELEROMETER supported");
            mSensors.add(manager.getSensor(SensorTypeValue.ACCELEROMETER));
        }
        // Add magnetic field sensor, if supported
        if (DeviceInfoHelper.isSensorSupported(context, hostAppPackageName,
                SensorTypeValue.MAGNETIC_FIELD)) {
            Log.d(StraightExtensionService.LOG_TAG, "MAGNETIC_FIELD supported");
            mSensors.add(manager.getSensor(SensorTypeValue.MAGNETIC_FIELD));
        }
        // Add light sensor, if supported
        if (DeviceInfoHelper.isSensorSupported(context, hostAppPackageName, SensorTypeValue.LIGHT)) {
            Log.d(StraightExtensionService.LOG_TAG, "LIGHT supported");
            mSensors.add(manager.getSensor(SensorTypeValue.LIGHT));
        }

        // Determine screen size
        determineSize(context, hostAppPackageName);
    }

    @Override
    public void onResume() {
        Log.d(StraightExtensionService.LOG_TAG, "Starting control");

        // Note: Setting the screen to be always on will drain the accessory
        // battery. It is done here solely for demonstration purposes
        setScreenState(Control.Intents.SCREEN_STATE_ON);

        // Start listening for sensor updates.
        register();

        updateCurrentDisplay(null);
    }

    @Override
    public void onPause() {
        // Stop sensor
        unregister();
    }

    @Override
    public void onDestroy() {
        // Stop sensor
        unregisterAndDestroy();
    }

    /**
     * Check if control supports the given width
     * 
     * @param context The context.
     * @param int width The width.
     * @return true if the control supports the given width
     */
    public static boolean isWidthSupported(Context context, int width) {
        return width == context.getResources().getDimensionPixelSize(
                R.dimen.smart_watch_2_control_width)
                || width == context.getResources().getDimensionPixelSize(
                        R.dimen.smart_watch_control_width);
    }

    /**
     * Check if control supports the given height
     * 
     * @param context The context.
     * @param int height The height.
     * @return true if the control supports the given height
     */
    public static boolean isHeightSupported(Context context, int height) {
        return height == context.getResources().getDimensionPixelSize(
                R.dimen.smart_watch_2_control_height)
                || height == context.getResources().getDimensionPixelSize(
                        R.dimen.smart_watch_control_height);
    }

    @Override
    public void onTouch(ControlTouchEvent event) {
        super.onTouch(event);
        if (event.getAction() == Control.Intents.TOUCH_ACTION_RELEASE) {
            mSeikenScore = 0;
            updateCurrentDisplay(null);
        }
    }

    private void determineSize(Context context, String hostAppPackageName) {
        Log.d(StraightExtensionService.LOG_TAG, "Now determine screen size.");

        boolean smartWatch2Supported = DeviceInfoHelper.isSmartWatch2ApiAndScreenDetected(context,
                hostAppPackageName);
        if (smartWatch2Supported) {
            mWidth = context.getResources().getDimensionPixelSize(
                    R.dimen.smart_watch_2_control_width);
            mHeight = context.getResources().getDimensionPixelSize(
                    R.dimen.smart_watch_2_control_height);
        }
        else {
            mWidth = context.getResources()
                    .getDimensionPixelSize(R.dimen.smart_watch_control_width);
            mHeight = context.getResources().getDimensionPixelSize(
                    R.dimen.smart_watch_control_height);
        }
    }

    private AccessorySensor getCurrentSensor() {
        return mSensors.get(mCurrentSensor);
    }

    private void register() {
        Log.d(StraightExtensionService.LOG_TAG, "Register listener");
        AccessorySensor sensor = getCurrentSensor();
        if (sensor != null) {
            try {
                if (sensor.isInterruptModeSupported()) {
                    sensor.registerInterruptListener(mListener);
                } else {
                    sensor.registerFixedRateListener(mListener,
                            Sensor.SensorRates.SENSOR_DELAY_UI);
                }
            } catch (AccessorySensorException e) {
                Log.d(StraightExtensionService.LOG_TAG, "Failed to register listener", e);
            }
        }
    }

    private void unregister() {
        AccessorySensor sensor = getCurrentSensor();
        if (sensor != null) {
            sensor.unregisterListener();
        }
    }

    private void unregisterAndDestroy() {
        unregister();
        mSensors.clear();
        mSensors = null;
    }

    private void updateCurrentDisplay(AccessorySensorEvent sensorEvent) {
        if (mSeikenScore > 0) {
            updateSeikenDisplay();
        } else {
            updateDirectionDisplay();
        }
    }

    /**
     * Update the display Seiken Score
     */
    private void updateSeikenDisplay() {
        // Create bitmap to draw in.
        Bitmap bitmap = Bitmap.createBitmap(mWidth, mHeight, BITMAP_CONFIG);

        // Set default density to avoid scaling.
        bitmap.setDensity(DisplayMetrics.DENSITY_DEFAULT);

        LinearLayout root = new LinearLayout(mContext);
        root.setLayoutParams(new ViewGroup.LayoutParams(mWidth, mHeight));
        root.setGravity(Gravity.CENTER);

        LayoutInflater inflater = (LayoutInflater) mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        LinearLayout sampleLayout = (LinearLayout) inflater.inflate(R.layout.seiken_score,
                root, true);

        TextView seikenTitle = (TextView) sampleLayout.findViewById(R.id.seiken_title);
        seikenTitle.setTypeface(null, Typeface.BOLD);

        TextView seikenScore = (TextView) sampleLayout.findViewById(R.id.seiken_score);
        seikenScore.setTypeface(null, Typeface.BOLD);
        seikenScore.setText(String.format("%.2f", mSeikenScore));

        root.measure(mWidth, mHeight);
        root.layout(0, 0, mWidth, mHeight);

        Canvas canvas = new Canvas(bitmap);
        sampleLayout.draw(canvas);

        showBitmap(bitmap);
    }

    /**
     * Update the display Seiken Score
     */
    private void updateDirectionDisplay() {
        // Create bitmap to draw in.
        Bitmap bitmap = Bitmap.createBitmap(mWidth, mHeight, BITMAP_CONFIG);

        // Set default density to avoid scaling.
        bitmap.setDensity(DisplayMetrics.DENSITY_DEFAULT);

        LinearLayout root = new LinearLayout(mContext);
        root.setLayoutParams(new ViewGroup.LayoutParams(mWidth, mHeight));
        root.setGravity(Gravity.CENTER);

        LayoutInflater inflater = (LayoutInflater) mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        LinearLayout sampleLayout = (LinearLayout) inflater.inflate(R.layout.direction,
                root, true);

        TextView label = (TextView) sampleLayout.findViewById(R.id.direction_label);
        label.setTypeface(null, Typeface.BOLD);
        label.setText(mPosition);

        ImageView icon = (ImageView) sampleLayout.findViewById(R.id.direction_icon);
        if (ExtensionMotion.POSITION_FRONT_UP.equals(mPosition)) {
            icon.setImageResource(R.drawable.punch);
        } else if (ExtensionMotion.POSITION_BACK_UP.equals(mPosition)) {
            icon.setImageResource(R.drawable.iconmonstr_circle_dashed8);
        } else if (ExtensionMotion.POSITION_LEFT_UP.equals(mPosition)) {
            icon.setImageResource(R.drawable.iconmonstr_arrow4);
            icon.setRotation(180);
        } else if (ExtensionMotion.POSITION_RIGHT_UP.equals(mPosition)) {
            icon.setImageResource(R.drawable.iconmonstr_arrow4);
        } else if (ExtensionMotion.POSITION_BOTTOM_UP.equals(mPosition)) {
            icon.setImageResource(R.drawable.iconmonstr_arrow4);
            icon.setRotation(90);
        } else if (ExtensionMotion.POSITION_TOP_UP.equals(mPosition)) {
            icon.setImageResource(R.drawable.iconmonstr_arrow4);
            icon.setRotation(270);
        } else {
            icon.setImageResource(R.drawable.punch);
        }

        root.measure(mWidth, mHeight);
        root.layout(0, 0, mWidth, mHeight);

        Canvas canvas = new Canvas(bitmap);
        sampleLayout.draw(canvas);

        showBitmap(bitmap);
    }
}
