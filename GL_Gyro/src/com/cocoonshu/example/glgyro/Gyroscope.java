package com.cocoonshu.example.glgyro;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.List;

import android.app.Service;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

/**
 * Gyroscope data provider
 * @author Cocoonshu
 * @date   2016-07-01 13:16:57
 */
public class Gyroscope {

    protected static final String TAG = "Gyroscope";
    
    private SensorManager         mSensorManager         = null;
    private Sensor                mAccelerometerSensor   = null;
    private Sensor                mGeomagneticSensor     = null;
    private SensorEventListener   mSensorEventListener   = null;
    private OnGyroChangedListener mOnGyroChangedListener = null;
    private boolean               mNeedToInvertYZ        = false;
    private float[]               mBufferedGravity       = null;
    private float[]               mBufferedGeomagnetic   = null;
    private float[]               mBufferedOrientation   = new float[3];
    private float[]               mMatrixR               = new float[16];
    private float[]               mMatrixI               = new float[16];
    
    public interface OnGyroChangedListener {
        void onGyroChanged(float[] matrixRotate, float[] orientation);
    }
    
    public Gyroscope(Context context) {
        mSensorManager = (SensorManager) context.getSystemService(Service.SENSOR_SERVICE);
        setupActiveSensors();
        setupSensorListener();
    }
    
    private void setupActiveSensors() {
        mAccelerometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGeomagneticSensor   = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    private void setupSensorListener() {
        mSensorEventListener = new SensorEventListener() {
            
            @Override
            public void onSensorChanged(SensorEvent event) {
                switch (event.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    mBufferedGravity = event.values;
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    mBufferedGeomagnetic = event.values;
                    break;
                }
                computeOrientation();
                Log.i(TAG, String.format("[onSensorChanged] Orientation = (%3.1f°, %3.1f°, %3.1f°)",
                        Math.toDegrees(mBufferedOrientation[1]),
                        Math.toDegrees(mBufferedOrientation[2]),
                        Math.toDegrees(mBufferedOrientation[0])));
                
                if (mOnGyroChangedListener != null) {
                    mOnGyroChangedListener.onGyroChanged(mMatrixR, mBufferedOrientation);
                }
            }
            
            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                // 略
            }
            
        };
    }

    public void resume() {
        if (mAccelerometerSensor != null) {
            mSensorManager.registerListener(mSensorEventListener, mAccelerometerSensor, SensorManager.SENSOR_DELAY_UI);
        }
        if (mGeomagneticSensor != null) {
            mSensorManager.registerListener(mSensorEventListener, mGeomagneticSensor, SensorManager.SENSOR_DELAY_UI);
        }
        resetMatrix(mMatrixR);
        resetMatrix(mMatrixI);
    }
    
    public void pause() {
        if (mAccelerometerSensor != null) {
            mSensorManager.unregisterListener(mSensorEventListener, mAccelerometerSensor);
        }
        if (mAccelerometerSensor != null) {
            mSensorManager.unregisterListener(mSensorEventListener, mGeomagneticSensor);
        }
    }
    
    public void setYZInvertEnabled(boolean enabled) {
        mNeedToInvertYZ = enabled;
    }
    
    public void setOnGyroChangedListener(OnGyroChangedListener listener) {
        mOnGyroChangedListener = listener;
    }
    
    public float[] getData() {
        return mBufferedOrientation;
    }
    
    private void computeOrientation() {
        if (mBufferedGravity == null || mBufferedGeomagnetic == null) {
            return;
        }
        if (mNeedToInvertYZ) { 
            SensorManager.getRotationMatrix(mMatrixR, mMatrixI, mBufferedGravity, mBufferedGeomagnetic);
            SensorManager.remapCoordinateSystem(mMatrixR, SensorManager.AXIS_X, SensorManager.AXIS_Y, mMatrixR);
            SensorManager.getOrientation(mMatrixR, mBufferedOrientation);
        } else {
            SensorManager.getRotationMatrix(mMatrixR, mMatrixI, mBufferedGravity, mBufferedGeomagnetic);
            SensorManager.getOrientation(mMatrixR, mBufferedOrientation);
        }
    }
    
    private static float[] resetMatrix(float[] matrix) {
        if (matrix == null) {
            return null;
        } else if (matrix.length == 9) {
            matrix[0] = 1; matrix[1] = 0; matrix[2] = 0;
            matrix[3] = 0; matrix[4] = 1; matrix[5] = 0;
            matrix[6] = 0; matrix[7] = 0; matrix[8] = 1;
        } else if (matrix.length == 16) {
            matrix[ 0] = 1; matrix[ 1] = 0; matrix[ 2] = 0; matrix[ 3] = 0;
            matrix[ 4] = 0; matrix[ 5] = 1; matrix[ 6] = 0; matrix[ 7] = 0;
            matrix[ 8] = 0; matrix[ 9] = 0; matrix[10] = 1; matrix[11] = 0;
            matrix[12] = 0; matrix[13] = 0; matrix[14] = 0; matrix[15] = 1;
        }
        return matrix;
    }
}
