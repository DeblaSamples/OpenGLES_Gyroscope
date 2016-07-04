package com.cocoonshu.example.glgyro;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES11;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.GLSurfaceView.Renderer;
import android.opengl.GLU;
import android.opengl.Matrix;
import android.os.AsyncTask;
import android.util.Log;

/**
 * A gyro model renderer for GLSurfaceView
 * @author Cocoonshu
 * @date   2016-07-01 14:20:47
 */
public class GyroRenderer implements Renderer {

    protected static final String   TAG                = "GyroRenderer";
    private   static final int      TEX_DIFFUSE        = 0;                    // 漫反射纹理ID索引
    private   static final int      TEX_SPECULAR       = 1;                    // 高光纹理ID索引
    private   static final int[]    TextureIndexes     = new int[] {           // 模型使用的纹理类型索引
        TEX_DIFFUSE                                                            //   - 漫反射纹理
    };
    private   static final String[] TextureFiles       = new String[] {        // 模型贴图文件
        "tex_gyro_diffuse.png"                                                 //   - 漫反射贴图文件
    };
    
    private GLSurfaceView mHostView                   = null;                  // 使用此渲染器的GLSurfaceView
    private BitmapLoader  mBitmapLoader               = null;                  // 图片异步加载器
    private FloatBuffer   mSphereVertexBuffer         = null;                  // 球体的顶点
    private FloatBuffer   mSphereTexcoordsBuffer      = null;                  // 球体的贴图坐标
    private FloatBuffer   mSphereNormalBuffer         = null;                  // 球体法线数据
    private ShortBuffer   mSphereIndiceBuffer         = null;                  // 球体的顶点索引
    private int           mTextureSize                = TextureFiles.length;   // 模型纹理数量
    private int           mMaxTextureUnitSize         = 1;                     // 可用的纹理单元数量
    private int[]         mTextureIDs                 = new int[mTextureSize]; // 图片的纹理ID集合
    private float[]       mCurrentAltittudeMatrix     = new float[32];         // 球体姿态矩阵
    private float[]       mAltittudeMatrix            = new float[16];         // 球体姿态矩阵
    private float[]       mCalibrationAltittudeMatrix = new float[16];         // 矫正球体姿态矩阵
    private float         mSlerpDamping               = 3F-1F;                 // 姿态插值阻尼
    private float         mSlerpThreshold             = 1F-3F;                 // 姿态插值阻尼阈值
    private float[]       mCurrentSlerpVector         = null;                  // 当前姿态插值向量
    private float[]       mTargetSlerpVector          = null;                  // 目标姿态插值向量
    private float[]       mSlerpAxis                  = null;                  // 姿态向量插值转轴
    private float[]       mLightPosition              = null;                  // 光源位置
    private float[]       mLightAmbient               = null;                  // 光源环境光颜色
    private float[]       mLightDiffuse               = null;                  // 光源散射光颜色
    private float[]       mLightSpecular              = null;                  // 光源镜面光颜色
    private float[]       mLightDirection             = null;                  // 光源方向
    private float         mLightCutOff                = 0;                     // 光源椎角
    private float         mLightExponent              = 0;                     // 光源椎角衰减度
    private float         mLightConstantAttenuation   = 0;                     // 光源距离常量衰减比
    private float         mLightLinearAttenuation     = 0;                     // 光源距离一次衰减比
    private float         mLightQuadraticAttenuation  = 0;                     // 光源距离二次衰减比
    private float[]       mAmbientColor               = null;                  // 材质环境色
    private float[]       mDiffuseColor               = null;                  // 材质散射色
    private float[]       mSpecularColor              = null;                  // 材质高光色
    private float         mShininess                  = 0;                     // 材质镜面度
    
    public GyroRenderer(GLSurfaceView hostView) {
        // 我们传入使用此渲染器的GLSurfaceView引用，主要是为了能够
        // GLSurfaceView的queue(Runnable)方法，这个方法能够把Runnable
        // 放置在OpenGLES所在的GLThread线程中执行
        mHostView     = hostView;
        mBitmapLoader = new BitmapLoader(mHostView.getResources(), this);
        
        // 初始化球体姿态矩阵
        identityAltittudeMatrix();
        // 准备模型数据
        buildMesh();
    }
    
    /**
     * 初始化球体姿态矩阵
     */
    private void identityAltittudeMatrix() {
        mCurrentSlerpVector = new float[] {0.0f, 1.0f, 0.0f, 1.0f};
        mTargetSlerpVector  = new float[] {0.0f, 1.0f, 0.0f, 1.0f};
        mSlerpAxis          = new float[] {0.0f, 1.0f, 0.0f, 1.0f};
        Matrix.setIdentityM(mCalibrationAltittudeMatrix, 0);
        Matrix.rotateM(mCalibrationAltittudeMatrix, 0, 90.0f, 1.0f, 0.0f, 0.0f);
        Matrix.setIdentityM(mCurrentAltittudeMatrix, 0);
        Matrix.setIdentityM(mCurrentAltittudeMatrix, 16);
        synchronized (mAltittudeMatrix) {
            Matrix.setIdentityM(mAltittudeMatrix, 0);
        }
    }

    /**
     * 创建模型数据
     */
    private void buildMesh() {
        int     latitudeBands    = 60;  // 纬线
        int     longitudeBands   = 60;  // 经线
        float   radio            = 10f; // 球体半径

        int     vpdItr           = 0; // VertexPosData迭代器
        int     nmdItr           = 0; // NormalData迭代器
        int     tcdItr           = 0; // TextureCoordData迭代器
        int     idxItr           = 0; // IndexData迭代器
        int     unitDataSize     = (latitudeBands + 1) * (longitudeBands + 1);
        float[] vertexPosData    = new float[3 * unitDataSize]; // 顶点坐标
        float[] normalData       = new float[3 * unitDataSize]; // 单位向量
        float[] textureCoordData = new float[2 * unitDataSize]; // 贴图坐标
        short[] indexData        = new short[6 * latitudeBands * longitudeBands]; // 索引向量
        
        // 生成地球坐标
        for (int latNum = 0; latNum <= latitudeBands; latNum++) { // 纬线圈
            float theta = (float) (latNum * Math.PI / latitudeBands);
            float sinTheta = (float) Math.sin(theta);
            float cosTheta = (float) Math.cos(theta);

            for (int longNum = 0; longNum <= longitudeBands; longNum++) { // 经线圈
                float phi = (float) (longNum * 2 * Math.PI / longitudeBands);
                float sinPhi = (float) Math.sin(phi);
                float cosPhi = (float) Math.cos(phi);

                float x = cosPhi * sinTheta;
                float y = cosTheta;
                float z = sinPhi * sinTheta;
                float u = 1f - ((float) longNum / (float) longitudeBands);
                float v = ((float) latNum / (float) latitudeBands);

                normalData[nmdItr] = x;
                nmdItr++;
                normalData[nmdItr] = y;
                nmdItr++;
                normalData[nmdItr] = z;
                nmdItr++;
                textureCoordData[tcdItr] = u;
                tcdItr++;
                textureCoordData[tcdItr] = v;
                tcdItr++;
                vertexPosData[vpdItr] = radio * x;
                vpdItr++;
                vertexPosData[vpdItr] = radio * y;
                vpdItr++;
                vertexPosData[vpdItr] = radio * z;
                vpdItr++;
            }
        }

        // 生成地球索引
        for (int latNum = 0; latNum < latitudeBands; latNum++) {
            for (int longNum = 0; longNum < longitudeBands; longNum++) {
                int first = (latNum * (longitudeBands + 1)) + longNum;
                int second = first + longitudeBands + 1;

                indexData[idxItr] = (short) first;
                idxItr++;
                indexData[idxItr] = (short) second;
                idxItr++;
                indexData[idxItr] = (short) (first + 1);
                idxItr++;

                indexData[idxItr] = (short) second;
                idxItr++;
                indexData[idxItr] = (short) (second + 1);
                idxItr++;
                indexData[idxItr] = (short) (first + 1);
                idxItr++;
            }
        }
        
        mLightPosition              = new float[] {10.0f, 10.0f, 30.0f, 1.0f};
        mLightAmbient               = new float[] {0.2f, 0.2f, 0.2f, 1.0f};
        mLightDiffuse               = new float[] {0.7f, 0.7f, 0.7f, 1.0f};
        mLightSpecular              = new float[] {1.0f, 1.0f, 1.0f, 1.0f};
        mLightDirection             = new float[] {0.0f, 0.0f, -1.0f};
        mLightCutOff                = 45.0f;
        mLightExponent              = 5.0f;
        mLightConstantAttenuation   = 0.5f;
        mLightLinearAttenuation     = 0.1f;
        mLightQuadraticAttenuation  = 0.0f;
        
        mAmbientColor               = new float[] {0.8f, 0.8f, 0.8f, 1.0f};
        mDiffuseColor               = new float[] {0.8f, 0.8f, 0.8f, 1.0f};
        mSpecularColor              = new float[] {1.0f, 1.0f, 1.0f, 1.0f};
        mShininess                  = 6.0f;
        
        // 组装成Buffer
        {// 顶点Buffer
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(vertexPosData.length * Float.SIZE / 8);
            byteBuffer.order(ByteOrder.nativeOrder());
            mSphereVertexBuffer = byteBuffer.asFloatBuffer();
            mSphereVertexBuffer.put(vertexPosData);
            mSphereVertexBuffer.rewind();
        }
        {// 贴图坐标Buffer
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(textureCoordData.length * Float.SIZE / 8);
            byteBuffer.order(ByteOrder.nativeOrder());
            mSphereTexcoordsBuffer = byteBuffer.asFloatBuffer();
            mSphereTexcoordsBuffer.put(textureCoordData);
            mSphereTexcoordsBuffer.rewind();
        }
        {// 法线Buffer
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(normalData.length * Float.SIZE / 8);
            byteBuffer.order(ByteOrder.nativeOrder());
            mSphereNormalBuffer = byteBuffer.asFloatBuffer();
            mSphereNormalBuffer.put(normalData);
            mSphereNormalBuffer.rewind();
        }
        {// 顶点索引Buffer
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(indexData.length * Short.SIZE / 8);
            byteBuffer.order(ByteOrder.nativeOrder());
            mSphereIndiceBuffer = byteBuffer.asShortBuffer();
            mSphereIndiceBuffer.put(indexData);
            mSphereIndiceBuffer.rewind();
        }
    }

    /**
     * 初始化OpenGLES： 设置OpenGLES中的各种开关和初始值
     */
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // 功能性设置
        GLES11.glEnable(GLES11.GL_DEPTH_TEST);                                  // 开启深度测试，如果我们绘制的东西有远近层次之分，就开启它
        GLES11.glDisable(GLES11.GL_ALPHA_TEST);                                 // 关闭透明测试，如果我们需要通过对比模型的透明度来觉得是否绘制它，就开启它
        GLES11.glDisable(GLES11.GL_STENCIL_TEST);                               // 关闭模板测试，如果我们需要用蒙版来遮盖某些绘制部分，就开启它
        GLES11.glDisable(GLES11.GL_BLEND);                                      // 关闭颜色混合，如果我们需要使绘制的半透明模型有颜色的混合效果，就开启它
        GLES11.glEnable(GLES11.GL_DITHER);                                      // 开启颜色抖动，如果是要显示图片，开启它，显示的颜色数量会更丰富
        GLES11.glEnable(GLES11.GL_TEXTURE_2D);                                  // 开启贴图功能，如果我们要使用贴图纹理，就开启它
        GLES11.glEnable(GLES11.GL_LIGHTING);                                    // 关闭光照效果，如果想要在模型表面呈现出光照的明暗变化，就开启它
        GLES11.glDisable(GLES11.GL_FOG);                                        // 关闭雾霾效果，如果想要在场景中绘制出雾霾的效果，就开启它
        
        // 默认值设置
        GLES11.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);                            // 设置清除颜色缓冲的色值，它会是视窗的清屏颜色
        GLES11.glClearDepthf(1.0f);                                             // 设置清除深度缓冲的深度值，它会是深度缓冲的默认深度值
        GLES11.glDepthRangef(0.1f, 100.0f);                                     // 设置深度缓冲的深度范围
        
        // 绘图效果设置
        GLES11.glHint(GLES11.GL_PERSPECTIVE_CORRECTION_HINT, GLES11.GL_NICEST); // 设置透视矫正配置为：质量最好
        GLES11.glHint(GLES11.GL_POINT_SMOOTH_HINT, GLES11.GL_NICEST);           // 设置点绘制平滑度配置为：质量最好
        GLES11.glHint(GLES11.GL_LINE_SMOOTH_HINT, GLES11.GL_NICEST);            // 设置线条绘制平滑度配置为：质量最好
        GLES11.glHint(GLES11.GL_POLYGON_SMOOTH_HINT, GLES11.GL_DONT_CARE);      // 设置模型绘制平滑度配置为：自动
        
        // 全局光照效果设置
        GLES11.glLightModelfv(GLES11.GL_LIGHT_MODEL_AMBIENT, mAmbientColor, 0); // 设置环境光颜色
        GLES11.glLightModelx (GLES11.GL_LIGHT_MODEL_TWO_SIDE, GLES11.GL_FALSE); // 设置双面照明
        
        // 获得硬件参数
        int[] integerValue = new int[1];
        GLES11.glGetIntegerv(GLES11.GL_MAX_TEXTURE_UNITS, integerValue, 0);     // 获取可用的纹理单元数量
        mMaxTextureUnitSize = integerValue[0];

        // 开始加载图片并上传纹理
        mBitmapLoader.execute(TextureFiles);
    }

    /**
     * 配置绘图窗口尺寸：重新根据新的尺寸来配置投影矩阵、视窗和绘图区域
     */
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        // 设置视窗
        GLES11.glViewport(0, 0, width, height);                           // 设置视窗尺寸为控件大小
        
        // 设置投影矩阵
        float fovy             = (float) Math.toRadians(60);              // 视野角度为120°
        float zNear            = 0.1f;                                    // 摄像机的最近端剪裁距离
        float zFar             = 100f;                                    // 摄像机的最远端剪裁距离
        float aspectRatio      = (float) width / (float) height;          // 计算视窗的显示比例
        float horizontalVolume = (float) (zNear * Math.tan(fovy * 0.5f)); // 计算视景体的宽度
        float verticalVolume   = horizontalVolume / aspectRatio;          // 计算视景体的高度
        GLES11.glMatrixMode(GLES11.GL_PROJECTION);                        // 把当前的操作矩阵切换到投影矩阵
        GLES11.glLoadIdentity();                                          // 把当前的操作矩阵重置为单位矩阵
        GLES11.glFrustumf(                                                // 设置投影矩阵为透视投影:
                -horizontalVolume, horizontalVolume,                      //   - 透视视景体的左右边位置
                -verticalVolume, verticalVolume,                          //   - 透视视景体的上下边位置
                zNear, zFar);                                             //   - 透视视景体的前后边位置
    }
    
    /**
     * 绘制一帧画面：相当于View.onDraw(Canvas)，不过这里使用OpenGLES的API来绘制
     */
    @Override
    public void onDrawFrame(GL10 gl) {
        // 重置颜色缓存和深度缓冲
        GLES11.glClear(GLES11.GL_COLOR_BUFFER_BIT | GLES11.GL_DEPTH_BUFFER_BIT);
        
        // 设置视图矩阵
        GLES11.glMatrixMode(GLES11.GL_MODELVIEW);                         // 把当前的操作矩阵切换到模型视图矩阵
        GLES11.glLoadIdentity();                                          // 把当前的操作矩阵重置为单位矩阵
        GLU.gluLookAt(gl,                                                 // 设置摄像机的姿态：
                0.0f, 0.0f, 30.0f,                                        //   - 摄像机的位置
                0.0f, 0.0f, 0.0f,                                         //   - 摄像机拍摄的点
                0.0f, 1.0f, 0.0f);                                        //   - 摄像机顶部的朝向
        
        // 设置#0光照单元
        GLES11.glEnable(GLES11.GL_LIGHT0);
        GLES11.glLightfv(GLES11.GL_LIGHT0, GLES11.GL_POSITION, mLightPosition, 0);                       // 设置光源的位置
        GLES11.glLightfv(GLES11.GL_LIGHT0, GLES11.GL_AMBIENT, mLightAmbient, 0);                         // 设置光源的环境光色
        GLES11.glLightfv(GLES11.GL_LIGHT0, GLES11.GL_DIFFUSE, mLightDiffuse, 0);                         // 设置光源的散射光色
        GLES11.glLightfv(GLES11.GL_LIGHT0, GLES11.GL_SPECULAR, mLightSpecular, 0);                       // 设置光源的镜面光色
        GLES11.glLightfv(GLES11.GL_LIGHT0, GLES11.GL_SPOT_DIRECTION, mLightDirection, 0);                // 设置光源的光照方向
        GLES11.glLightf (GLES11.GL_LIGHT0, GLES11.GL_SPOT_CUTOFF, mLightCutOff);                         // 设置光源的光锥夹角
        GLES11.glLightf (GLES11.GL_LIGHT0, GLES11.GL_SPOT_EXPONENT, mLightExponent);                     // 设置光源的光锥衰减
        GLES11.glLightf (GLES11.GL_LIGHT0, GLES11.GL_CONSTANT_ATTENUATION, mLightConstantAttenuation);   // 设置光源的常量衰减比
        GLES11.glLightf (GLES11.GL_LIGHT0, GLES11.GL_LINEAR_ATTENUATION, mLightLinearAttenuation);       // 设置光源的一次衰减比
        GLES11.glLightf (GLES11.GL_LIGHT0, GLES11.GL_QUADRATIC_ATTENUATION, mLightQuadraticAttenuation); // 设置光源的二次衰减比 
        
        {// 摆放并绘制模型，模型应该从远及近地绘图
            // 开启OpenGLES客户端指定网格数据的操作方式
            // 以便从OpenGLES客户端指定网格数据来绘制模型
            GLES11.glEnableClientState(GLES11.GL_VERTEX_ARRAY);                      // 启用OpenGLES客户端指定顶点数组的操作方式
            GLES11.glVertexPointer(3, GLES11.GL_FLOAT, 0, mSphereVertexBuffer);      // 指定顶点数组，每3个数作为一个顶点坐标
            GLES11.glEnableClientState(GLES11.GL_TEXTURE_COORD_ARRAY);               // 启用OpenGLES客户端指定贴图坐标数组的操作方式
            GLES11.glTexCoordPointer(2, GLES11.GL_FLOAT, 0, mSphereTexcoordsBuffer); // 指定贴图坐标数组，每2个数作为一个顶点坐标
            GLES11.glEnableClientState(GLES11.GL_NORMAL_ARRAY);                      // 启用OpenGLES客户端指定法线数组的操作方式
            GLES11.glNormalPointer(GLES11.GL_FLOAT, 0, mSphereNormalBuffer);         // 指定贴图坐标数组，每3个数作为一个法线方向
            
            {// 摆放并绘制模型
                GLES11.glPushMatrix();
                    // 设置模型矩阵：
                    //   - 1. 姿态矩阵平滑插值
                    //   - 2. 按照姿态矩阵旋转球体
                    //   - 3. 把长宽为(1.0f, 1.0f)的矩形片缩放到图片尺寸的宽高比
                    //   - 4. 把矩形移动到左边靠后的位置
                    if (smoothAtittudeMatrix(false)) {
                        if (mHostView != null) {
                            mHostView.requestRender();
                        }
                    } 
                    GLES11.glTranslatef(0.0f, 0.0f, 0.0f);
                    GLES11.glScalef(1.0f, 1.0f, 1.0f);
                    GLES11.glMultMatrixf(mCurrentAltittudeMatrix, 0);
                    GLES11.glMultMatrixf(mCalibrationAltittudeMatrix, 0);
    
                    // 绑定要贴到矩形上的纹理
                    for (int tex = 0; tex < mTextureSize && tex < mMaxTextureUnitSize; tex++) {
                        GLES11.glActiveTexture(GLES11.GL_TEXTURE0);                                   // 激活#tex纹理单元
                        GLES11.glBindTexture(GLES11.GL_TEXTURE_2D, mTextureIDs[TextureIndexes[tex]]); // 绑定第tex号纹理到#tex纹理单元
                    }
    
                    // 设置模型材质
                    GLES11.glMaterialfv(GLES11.GL_FRONT_AND_BACK, GLES11.GL_AMBIENT,  mAmbientColor,  0); // 材质环境色
                    GLES11.glMaterialfv(GLES11.GL_FRONT_AND_BACK, GLES11.GL_DIFFUSE,  mDiffuseColor,  0); // 材质散射色
                    GLES11.glMaterialfv(GLES11.GL_FRONT_AND_BACK, GLES11.GL_SPECULAR, mSpecularColor, 0); // 材质高光色
                    GLES11.glMaterialf (GLES11.GL_FRONT_AND_BACK, GLES11.GL_SHININESS, mShininess);       // 材质光泽度
                    
                    // 绘制这个模型
                    GLES11.glDrawElements(GLES11.GL_TRIANGLES, mSphereIndiceBuffer.capacity(), GLES11.GL_UNSIGNED_SHORT, mSphereIndiceBuffer);
                GLES11.glPopMatrix();
            }
            
            // 关闭OpenGLES客户端指定网格数据的操作方式，以便做其他绘制操作，
            // 如果没有其他绘制操作，可以一直启用这种操作方式
            GLES11.glDisableClientState(GL10.GL_VERTEX_ARRAY);        // 关闭OpenGLES客户端指定顶点数组的操作方式
            GLES11.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY); // 关闭OpenGLES客户端指定贴图坐标数组的操作方式
            GLES11.glDisableClientState(GLES11.GL_NORMAL_ARRAY);      // 关闭OpenGLES客户端指定法线数组的操作方式
        }
    }

    /**
     * 获取OpenGLES中的上传纹理可能发生的错误
     */
    private void printGLError(String location) {
        int glError = GLES11.glGetError(); // 获取上一个OpenGLES API调用出现的错误码
        if (glError != GLES11.GL_NO_ERROR) {
            Log.e(TAG, String.format("[%s] GLError = %s",
                    location, GLUtils.getEGLErrorString(glError))); // 把错误码转换为可读字符串
        } else {
            Log.i(TAG, String.format("[%s] GLError = No error", location));
        }
    }
    
    /**
     * 把Bitmap显示到OpenGLES绘制的视窗中
     * @param bitmap
     */
    public void displayBitmap(final Bitmap bitmap, final int position) {
        if (mHostView != null) {
            // 因为这个Runnable中的代码是把Bitmap上传为OpenGLES可用的纹理，
            // 需要使用到OpenGLES API，所以需要用queueEvent方法把这个
            // Runnable抛到GLThread中去执行
            mHostView.queueEvent(new Runnable() {
                
                @Override
                public void run() {
                    if (bitmap == null) {
                        Log.e(TAG, String.format("[uploadTexture] bitmap is null, position is %d", position));
                        return;
                    }

                    {// 上传纹理
                        GLES11.glGenTextures(1, mTextureIDs, position);
                        GLES11.glBindTexture(GLES11.GL_TEXTURE_2D, mTextureIDs[position]);
                        GLES11.glTexParameterx(GLES11.GL_TEXTURE_2D, GLES11.GL_TEXTURE_WRAP_S, GLES11.GL_CLAMP_TO_EDGE);
                        GLES11.glTexParameterx(GLES11.GL_TEXTURE_2D, GLES11.GL_TEXTURE_WRAP_T, GLES11.GL_CLAMP_TO_EDGE);
                        GLES11.glTexParameterx(GLES11.GL_TEXTURE_2D, GLES11.GL_TEXTURE_MIN_FILTER, GLES11.GL_LINEAR);
                        GLES11.glTexParameterx(GLES11.GL_TEXTURE_2D, GLES11.GL_TEXTURE_MAG_FILTER, GLES11.GL_LINEAR);
                        GLUtils.texImage2D(GLES11.GL_TEXTURE_2D, 0, bitmap, 0);
                        bitmap.recycle();
                    }
                    
                    // 获取OpenGLES中的上传纹理可能发生的错误
                    printGLError("displayBitmap");
                    
                    // 纹理上传完成后，通知OpenGLES去重绘一帧，以便绘制已上传的纹理
                    mHostView.requestRender();
                }
            });
        }
    }
    
    /**
     * Setup a rotation matrix for altittude
     * @param matrixRotate
     */
    public void setAltittudeMatrix(float[] matrix) {
        synchronized (mAltittudeMatrix) {
            Matrix.transposeM(mAltittudeMatrix, 0, matrix, 0);
            Matrix.invertM(mAltittudeMatrix, 0, mAltittudeMatrix, 0);
        }
        mHostView.requestRender();
    }
    
    /**
     * Record current altittude matrix as calibrateion matrix
     */
    public void recordCalibrationMatrix() {
        synchronized (mAltittudeMatrix) {
            Matrix.invertM(mCalibrationAltittudeMatrix, 0, mAltittudeMatrix, 0);
        }
        mHostView.requestRender();
    }
    
    /**
     * Smooth the current matrix to target atittude
     * @param enable set as false, this function will be disable
     * @return true if has more frames can be slerped
     */
    private boolean smoothAtittudeMatrix(boolean enable) {
		boolean hasMoreFrame = false;
		
        if (!enable) {
            synchronized (mAltittudeMatrix) {
                int matrixLength = mAltittudeMatrix.length;
                for (int i = 0; i < matrixLength; i++) {
                    mCurrentAltittudeMatrix[i] = mAltittudeMatrix[i];
                }
            }
        } else {
			// TODO
		}
        
        return hasMoreFrame;
    }
    
    /**
     * Bitmap asynchronous Loader
     * @author Cocoonshu
     * @date   2016-06-29 19:46:29
     */
    private class BitmapLoader extends AsyncTask<String, Integer, Void> {

        private Resources    mResource = null;
        private GyroRenderer mRenderer = null;
        
        public BitmapLoader(Resources resource, GyroRenderer renderer) {
            mResource = resource;
            mRenderer = renderer;
        }
        
        @Override
        protected Void doInBackground(String... imageAssetPaths) {
            if (imageAssetPaths != null) {
                for (int i = 0; i < imageAssetPaths.length; i++) {
                    String assetPath = imageAssetPaths[i];
                    Bitmap bitmap    = decodeImage(assetPath);
                    mRenderer.displayBitmap(bitmap, i);
                }
            }
            return null;
        }
        
        public Bitmap decodeImage(String assetPath) {
            try {
                AssetManager assetManager = mResource.getAssets(); 
                InputStream  inputStream  = assetManager.open(assetPath);
                Bitmap       bitmap       = BitmapFactory.decodeStream(inputStream);
                return bitmap;
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

}

