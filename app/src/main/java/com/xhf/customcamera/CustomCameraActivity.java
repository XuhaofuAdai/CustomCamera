package com.xhf.customcamera;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Camera;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;

import com.xhf.customcamera.base.BaseActivity;
import com.xhf.customcamera.views.ScaleImageView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;


public class CustomCameraActivity extends BaseActivity implements View.OnClickListener {
    String[] mPermission = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private int mCameraId = CameraCharacteristics.LENS_FACING_FRONT; //后置摄像头
    private TextureView mTextureView;
    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private HandlerThread mHandlerThread;
    private Handler mCameraHandler;
    private CameraDevice.StateCallback mStateCallback;
    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private boolean turnFlag = false;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private Size mPreviewSize;
    private CameraCaptureSession mCaptureSession; //相机预览
    private ImageReader mImageReader;
    private ImageReader.OnImageAvailableListener mOnImageAvailableListener;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;

    private TextureView.SurfaceTextureListener mTvListener;
    private ScaleImageView mSIvTakePhoto;
    private ScaleImageView mSIvTurnDirection;
    private ScaleImageView mSIvGallery;
    private File mFile;
    private static int mSensorOrientation;


    @Override
    public void initView() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        openFullScreenModel();//开启全面屏
        setContentView(R.layout.activity_custom_camera);
        mTextureView = findViewById(R.id.ttv_camera);
        mSIvTakePhoto = findViewById(R.id.siv_take_photo);
        mSIvTurnDirection = findViewById(R.id.siv_turn_direction);
        mSIvGallery = findViewById(R.id.siv_gallery);
    }

    @Override
    public void initListener() {
        mTvListener = new TextureView.SurfaceTextureListener() {     //设置TextureView监听
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                openCamera(width, height, String.valueOf(mCameraId)); //当TextureView准备就绪，创建摄像回话
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

            }
        };

        mStateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice cameraDevice) {
                mCameraDevice = cameraDevice; //获取相机对象
                createCameraPreview();
            }

            @Override
            public void onDisconnected(CameraDevice cameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }

            @Override
            public void onError(CameraDevice cameraDevice, int i) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
        };

        mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader imageReader) {
                saveBitmap(imageReader);
            }
        };
        mSIvTakePhoto.setOnClickListener(this);
        mSIvTurnDirection.setOnClickListener(this);
        mSIvGallery.setOnClickListener(this);
    }

    @Override
    public void initData() {

    }

    private void saveBitmap(ImageReader imageReader) {
        Image image = imageReader.acquireLatestImage();
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        image.close();
        writeToDisk(bytes);

    }

    public void writeToDisk(final byte[] data) {
        mFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".jpg");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    FileOutputStream fileOutputStream = new FileOutputStream(mFile);
                    fileOutputStream.write(data);
                    fileOutputStream.flush();
                    fileOutputStream.close();
                    showToast(mFile.getAbsolutePath());
                    Uri uri = Uri.fromFile(mFile);
                    sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri)); //发送广播
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void openCamera(int width, int height, String cameraTarget) {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            mSurfaceWidth = width;
            mSurfaceHeight = height;
            CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(cameraTarget);
            StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);  //获取预览最大支持的
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            int totalRotation = sensorToDeviceRotation(cameraCharacteristics, rotation);
            boolean swapRotation = totalRotation == 90 || totalRotation == 270;
            int rotatedWidth = mSurfaceWidth;
            int rotatedHeight = mSurfaceHeight;
            if (swapRotation) {
                rotatedWidth = mSurfaceHeight;
                rotatedHeight = mSurfaceWidth;
            }
            mPreviewSize = getPreferredPreviewSize(map.getOutputSizes(SurfaceTexture.class), rotatedWidth, rotatedHeight);
            SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
            if (swapRotation) {
                surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                surfaceTexture.setDefaultBufferSize(mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }
            if (mImageReader == null) {
                // 创建一个ImageReader对象，用于获取摄像头的图像数据,maxImages是ImageReader一次可以访问的最大图片数量
                mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(),
                        ImageFormat.JPEG, 2);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mCameraHandler);
            }

            mCameraManager.openCamera(cameraTarget, mStateCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void createCameraPreview() {
        try {
            // 获取texture实例 获取内容流
            SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
//        assert surfaceTexture != null;
            //我们将默认缓冲区的大小配置为我们想要的相机预览的大小。
            surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            // 用来开始预览的输出surface
            Surface surface = new Surface(surfaceTexture);
            //创建预览请求构建器
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            //将TextureView的Surface作为相机的预览显示输出
            mPreviewRequestBuilder.addTarget(surface);
            //第一个参数为为需要预览的surface提供预览流
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {


                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    if (mCameraDevice == null) {
                        return;
                    }
                    mCaptureSession = cameraCaptureSession;
                    //相机预览应该连续自动对焦。
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    // 构建上述的请求
                    mPreviewRequest = mPreviewRequestBuilder.build();
                    try {
                        mCaptureSession.setRepeatingRequest(mPreviewRequest, null, mCameraHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    showToast("预览失败");
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }


    }

    private static int sensorToDeviceRotation(CameraCharacteristics characteristics, int deviceOrientation) {
        mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceOrientation = ORIENTATIONS.get(deviceOrientation);
        return (mSensorOrientation + deviceOrientation + 360) % 360;
    }

    /**
     * 查看最佳预览尺寸
     *
     * @param sizes
     * @param width
     * @param height
     * @return
     */
    private Size getPreferredPreviewSize(Size[] sizes, int width, int height) {
        List<Size> collectorSizes = new ArrayList<>();
        for (Size option : sizes) {
            if (width > height) {
                if (option.getWidth() > width && option.getHeight() > height) {
                    collectorSizes.add(option);
                }
            } else {
                if (option.getHeight() > width && option.getWidth() > height) {
                    collectorSizes.add(option);
                }
            }
        }
        if (collectorSizes.size() > 0) {
            return Collections.min(collectorSizes, new Comparator<Size>() {
                @Override
                public int compare(Size s1, Size s2) {
                    return Long.signum(s1.getWidth() * s1.getHeight() - s2.getWidth() * s2.getHeight());
                }
            });
        }
        return sizes[0];
    }


    /**
     * 初始化相机线程
     */
    private void startBackGroundThread() {
        mHandlerThread = new HandlerThread("BackGroundThread");
        mHandlerThread.start();
        mCameraHandler = new Handler(mHandlerThread.getLooper());
    }

    /**
     * 停止相机线程
     */
    private void stopBackGroundThread() {
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
            try {
                mHandlerThread.join();
                mHandlerThread = null;
                mCameraHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 依次关闭相机，分别关闭会话，设备，以及ImageReader
     */
    private void closeCamera() {
        // 关闭捕获会话
        if (null != mCaptureSession) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        // 关闭当前相机
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        // 关闭拍照处理器
        if (null != mImageReader) {
            mImageReader.close();
            mImageReader = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        openFullScreenModel();
        getRxPermission(mPermission, new PermissionListener() {
            @Override
            public void success() {
                if (mTextureView.isAvailable()) {
                    openCamera(mTextureView.getWidth(), mTextureView.getHeight(), String.valueOf(mCameraId));
                } else {
                    mTextureView.setSurfaceTextureListener(mTvListener);
                }
                startBackGroundThread();
            }

            @Override
            public void failed() {

            }
        });
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackGroundThread();
        super.onPause();
    }


    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.siv_take_photo:  //拍照
                takePhoto();
                break;
            case R.id.siv_turn_direction:   //调整方向
                turnDirection();
                break;
            case R.id.siv_gallery:  //画廊

                break;
        }
    }

    /**
     * 交换相机摄像头方向
     */
    private void turnDirection() {
        closeCamera();
        if (turnFlag) {
            //后置摄像头
            mCameraId = CameraCharacteristics.LENS_FACING_FRONT;
            if (mTextureView.isAvailable()) {
                openCamera(mTextureView.getWidth(), mTextureView.getHeight(), String.valueOf(mCameraId));
            } else {
                mTextureView.setSurfaceTextureListener(mTvListener);
            }
            turnFlag = false;
        } else {
            //前置摄像头
            mCameraId = CameraCharacteristics.LENS_FACING_BACK;
            if (mTextureView.isAvailable()) {
                openCamera(mTextureView.getWidth(), mTextureView.getHeight(), String.valueOf(mCameraId));
            } else {
                mTextureView.setSurfaceTextureListener(mTvListener);
            }
            turnFlag = true;
        }
    }

    /**
     * 拍照
     */
    private void takePhoto() {
        if (mCameraDevice == null) {
            return;
        }
        try {
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            mPreviewRequestBuilder.addTarget(mImageReader.getSurface());  //将预览的内容放在ImageReader回调中 等待回调然后处理
            mPreviewRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, mSensorOrientation);    //根据摄像头方向对保存的照片进行旋转，使其为"自然方向"
            CaptureRequest build = mPreviewRequestBuilder.build();
            mCaptureSession.stopRepeating();
            mCaptureSession.capture(build, new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    createCameraPreview();
                }
            }, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
}
