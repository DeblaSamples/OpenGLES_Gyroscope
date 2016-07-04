
package com.cocoonshu.example.glgyro;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;

import com.cocoonshu.example.glgyro.Gyroscope.OnGyroChangedListener;

import android.opengl.GLSurfaceView;
import android.opengl.GLSurfaceView.EGLConfigChooser;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;

public class MainActivity extends Activity {

    
    private static final int OpenGLES_1_1 = 1;       // 使用OpenGLES 1.1的API
    private static final int OpenGLES_2_0 = 2;       // 使用OpenGLES 2.0的API
    
    private GLSurfaceView mGlvOpenGLImage    = null; // 承载OpenGLES的控件
    private GyroRenderer  mGyroRenderer      = null; // 使用OpenGLES API的渲染器
    private Gyroscope     mGyroscope         = null; // 陀螺仪数据提供器
    private Dialog        mCalibrationDialog = null; // 校准提示对话框
    private Button        mBtnCalibration    = null; // 校准按钮
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeCalibrationDialog();
        initializeSensorComponents();
        initializeOpenGLComponents();
        initializeListeners();
    }

    private void initializeCalibrationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        mCalibrationDialog = builder.setMessage(R.string.calibration_dialog_message)
            .setPositiveButton(
                    R.string.calibration_dialog_button_confirm,
                    new DialogInterface.OnClickListener() {
                
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mCalibrationDialog.hide();
                    mGyroRenderer.recordCalibrationMatrix();
                }

            })
            .setCancelable(false)
            .create();
        mBtnCalibration = (Button) findViewById(R.id.Button_Calibration);
    }

    private void initializeSensorComponents() {
        mGyroscope = new Gyroscope(getApplicationContext());
        mGyroscope.setYZInvertEnabled(true);
    }

    private void initializeOpenGLComponents() {
        mGlvOpenGLImage = (GLSurfaceView) findViewById(R.id.GLSurfaceView_GLGyro);
        mGyroRenderer   = new GyroRenderer(mGlvOpenGLImage);
        
        //mGlvOpenGLImage.setEGLConfigChooser(5, 6, 5, 0, 16, 8);             // 设置OpenGLES中画布中各个buffer的位数
        mGlvOpenGLImage.setEGLConfigChooser(getFASSEGLConfigChooser());     // 设置OpenGLES中画布中各个buffer的位数
        mGlvOpenGLImage.setEGLContextClientVersion(OpenGLES_1_1);           // 设置OpenGLES API版本
        mGlvOpenGLImage.setRenderer(mGyroRenderer);                         // 设置渲染器
        mGlvOpenGLImage.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY); // 设置OpenGLES的渲染驱动模式
    }

    private void initializeListeners() {
        mGyroscope.setOnGyroChangedListener(new OnGyroChangedListener() {
            
            @Override
            public void onGyroChanged(float[] matrixRotate, float[] orientation) {
                mGyroRenderer.setAltittudeMatrix(matrixRotate);
            }
            
        });
        mBtnCalibration.setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View v) {
                mGyroRenderer.recordCalibrationMatrix();
            }
            
        });
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        mGyroscope.resume();
        mCalibrationDialog.show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mGyroscope.pause();
    }
    
    private EGLConfigChooser getFASSEGLConfigChooser() {
        return new EGLConfigChooser() {

            @Override
            public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
                EGLConfig[] eglConfigList     = new EGLConfig[1];
                int[]       eglConfigNumber   = new int[1];
                int[]       eglAttributesList = new int[] {
                        EGL10.EGL_SURFACE_TYPE,   EGL10.EGL_WINDOW_BIT,
                        EGL10.EGL_RED_SIZE,       8,
                        EGL10.EGL_GREEN_SIZE,     8,
                        EGL10.EGL_BLUE_SIZE,      8,
                        EGL10.EGL_ALPHA_SIZE,     8,
                        EGL10.EGL_DEPTH_SIZE,     16,
                        EGL10.EGL_STENCIL_SIZE,   8,
                        EGL10.EGL_SAMPLE_BUFFERS, 1,
                        EGL10.EGL_SAMPLES,        4,
                        EGL10.EGL_NONE
                };

                egl.eglChooseConfig(
                        display, 
                        eglAttributesList, 
                        eglConfigList, 
                        1, 
                        eglConfigNumber);

                return eglConfigList[0];
            }

        };
    }
}
