# CustomCamera
1.获取拍照权限：
~~~
<uses-permission android:name="android.permission.CAMERA" /> <!--相机权限-->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" /> <!--存储权限-->
~~~  

2.在TextureView的SurfaceTextureListener回调监听中，监听到
~~~
public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height){}
~~~
回调之后执行打开相机操作，包括以下内容
>1.获取相机管理器CameraManager实例。  
~~~
CameraManager cameraManager=(CameraManager)getService(Context.CAMERA_SERVICE);
~~~
>2.在打开相机之前可以对相机进行预览尺寸设置。  
~~~
 //获取相机特征
 CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(cameraTarget);
 //获取所有预览支持尺寸
 StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);  
~~~
可以在这些尺寸中遍历到自己需要的预览大小
~~~
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
~~~
>3.打开相机。  
    这里注意状态回调这个设计，由于相机很多都是和底层设备打交道，不可避免的有延迟。  
    通过接口的方式获取设备状态的回调，可以优化我们的处理。  
    CameraDevice（相当于Camera1中的Camera对象），CameraCaptureSession,CaptureRequest都是通过这种方式来获取状态的。  
~~~
  //cameraTarget:相机前后置标识; 
  //mStateCallBack：相机打开状态回调
  //mCameraHandler;执行在HandlerThread的Handler
  mCameraManager.openCamera(cameraTarget, mStateCallback, mCameraHandler);
~~~
>4.获取CameraDevice.
    在CameraDevice.StateCallBack()的回调中，我们可以获取到通过Opened()获取到相机实例
~~~
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
~~~
   获取到相机实例之后我们可以通过相机实例去创建会话CameraCaptureSession.建立与底层相机设备之间的会话通道，通过发送CaptureRequest完成我们想要的预览，拍照，录像等模式。

