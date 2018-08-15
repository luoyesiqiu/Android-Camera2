package com.luoye.camera;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import rebus.permissionutils.AskAgainCallback;
import rebus.permissionutils.FullCallback;
import rebus.permissionutils.PermissionEnum;
import rebus.permissionutils.PermissionManager;

public class MainActivity extends AppCompatActivity {

    private CameraManager cameraManager;
    protected CameraDevice mCameraDevice;
    private TextureView textureView;

    private CaptureRequest.Builder mPreViewBuilder;
    private CaptureRequest.Builder mCaptureBuilder;
    private Surface mSurface;
    private  CameraCaptureSession mSession;
    private final static String TAG="CameraDemo";
    private HandlerThread mBackgroundThread;
    private BackgroundHandler mBackgroundHandler;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textureView=(TextureView)findViewById(R.id.texture_view);
        textureView.setSurfaceTextureListener(surfaceTextureListener);

        textureView.setOnClickListener(new DoubleClickListener(){
            @Override
            public void onDoubleClick(View view) {
                takePhoto();
            }
        });


        cameraManager=(CameraManager)getApplication().getSystemService(CAMERA_SERVICE);
        requirePermission();
    }


    private  class OnImageAvailableHandler extends  Handler{
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    }

    /**
     * 拍照
     */
    private  void takePhoto(){

        FileOutputStream fileOutputStream=null;
        SimpleDateFormat simpleDateFormat=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String date=simpleDateFormat.format(new Date());
        File file=new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/Pictures/luoye/"+date+".jpg");
        if(!file.getParentFile().exists()){
            file.getParentFile().mkdirs();
        }
        try {
            fileOutputStream=new FileOutputStream(file);
            textureView.getBitmap().compress(Bitmap.CompressFormat.JPEG,100,fileOutputStream);
            toast("拍照成功！");
        } catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            if(fileOutputStream!=null){
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    private abstract class DoubleClickListener implements View.OnClickListener{
        private long preClickTime=0;
        @Override
        public void onClick(View view) {
            long currentClickTime=System.currentTimeMillis();
            if(currentClickTime-preClickTime<500){
                onDoubleClick(view);
                preClickTime=0;
            }
            else{
                preClickTime=currentClickTime;
            }
        }

        /**
         * 双击事件
         * @param view
         */
        public abstract void onDoubleClick(View view);
    }

    private TextureView.SurfaceTextureListener surfaceTextureListener=new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int w, int h) {
            try {
                Log.d(TAG,"onSurfaceTextureAvailable");
                openCamera();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int w, int h) {
            Log.d(TAG,"onSurfaceTextureSizeChanged");

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            surfaceTexture.release();
            Log.d(TAG,"onSurfaceTextureDestroyed");
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
            //Log.d(TAG,"onSurfaceTextureUpdated");
        }
    };

    private  void requirePermission(){
        PermissionManager.Builder()
                .permission(PermissionEnum.READ_PHONE_STATE,PermissionEnum.WRITE_EXTERNAL_STORAGE,PermissionEnum.CAMERA)
                .askAgain(true)
                .askAgainCallback(new AskAgainCallback() {
                    @Override
                    public void showRequestPermission(UserResponse response) {

                    }
                })
                .callback(new FullCallback() {
                    @Override
                    public void result(ArrayList<PermissionEnum> permissionsGranted, ArrayList<PermissionEnum> permissionsDenied, ArrayList<PermissionEnum> permissionsDeniedForever, ArrayList<PermissionEnum> permissionsAsked) {

                    }
                })
                .ask(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionManager.handleResult(this, requestCode, permissions, grantResults);
    }

    private  CameraDevice.StateCallback stateCallback=new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            Log.d(TAG,"onOpened");
            MainActivity.this.mCameraDevice=cameraDevice;
            try {
                MainActivity.this.mPreViewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                SurfaceTexture texture = textureView.getSurfaceTexture();
                texture.setDefaultBufferSize(1080, 1920);
                MainActivity.this.mSurface = new Surface(texture);
                MainActivity.this.mPreViewBuilder.addTarget(mSurface);
                cameraDevice.createCaptureSession(Arrays.asList(mSurface), mSessionStateCallBack, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            Log.d(TAG,"onDisconnected");
            if(mCameraDevice!=null){
                mCameraDevice.close();
                mCameraDevice=null;
            }
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            Log.d(TAG,"onError");
            if(mCameraDevice!=null){
                mCameraDevice.close();
                mCameraDevice=null;
            }
        }
    };

    private CameraCaptureSession.CaptureCallback mSessionCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {

        }
    };

    private CameraCaptureSession.StateCallback mSessionStateCallBack=new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            Log.d(TAG,"onConfigured");
            mSession =cameraCaptureSession;
            mPreViewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            try {
                mSession.setRepeatingRequest(mPreViewBuilder.build(), null, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
            Log.d(TAG,"onConfigureFailed");
        }
    };

    private  void openCamera() throws CameraAccessException{
        cameraManager.openCamera(cameraManager.getCameraIdList()[0], stateCallback,null);
    }

    public void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new BackgroundHandler(mBackgroundThread.getLooper());
    }


    private  static class BackgroundHandler extends  Handler{
        public BackgroundHandler(Looper looper){
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {

        }
    };

    private Toast toast;
    private  void toast(CharSequence msg){
        if(toast==null){
            toast=Toast.makeText(getApplicationContext(),msg,Toast.LENGTH_SHORT);
        }
        else{
            toast.setText(msg);
        }
        toast.show();
    }
}
