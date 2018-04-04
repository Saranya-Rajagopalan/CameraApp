package com.example.stars.mycameraapp;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private Button      low_resolution, med_resolution, high_resolution;
    private ToggleButton configure_button;
    private TextureView textureView;
    private final String TAG = "MyCameraApp";
    private boolean config;
    private CaptureRequest previous_request;

    //Check the orientation of the image
    private static final SparseArray ORIENTATION = new SparseArray (  );


    static{
        ORIENTATION.append ( Surface.ROTATION_0, 90 );
        ORIENTATION.append ( Surface.ROTATION_90, 0 );
        ORIENTATION.append ( Surface.ROTATION_180, 270 );
        ORIENTATION.append ( Surface.ROTATION_270, 180 );
    }

    //Camera hardware
    private String                 cameraId;
    private CameraDevice           cameraDevice;
    private CaptureRequest.Builder captureRequestBuilder;
    private CameraCaptureSession   cameraCaptureSession;
    private Size                   imageDimension = new Size ( 1920,1280 );
    private CameraManager          cameraManager;
    private CameraCharacteristics  characteristics;
    private ImageReader imageReader;
    private Surface surface;


    //save to file
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundHandlerThread;

    CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback ( ) {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCaptureSession ();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close ();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close ();
            cameraDevice = null;
        }
    };

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener ( ) {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate ( savedInstanceState );
        setContentView ( R.layout.activity_main );


        cameraManager = (CameraManager) getSystemService ( Context.CAMERA_SERVICE );

        textureView = (TextureView)findViewById ( R.id.cameratexture );

//        surface = new Surface ( textureView.getSurfaceTexture () );

        assert textureView != null;

        textureView.setSurfaceTextureListener (textureListener);

        low_resolution = (Button) findViewById ( R.id.btn_low );
        med_resolution = (Button) findViewById ( R.id.btn_medium );
        high_resolution = (Button) findViewById ( R.id.btn_high );
        configure_button = (ToggleButton) findViewById (  R.id.config);


        assert low_resolution != null;

        low_resolution.setOnClickListener ( new View.OnClickListener (){
            @Override
            public void onClick(View view){
                imageDimension = new Size ( 640, 480 );
                onBackground ();
                onForeground ();
            }
        });

        assert med_resolution != null;

        med_resolution.setOnClickListener ( new View.OnClickListener (){
            @Override
            public void onClick(View view){
                imageDimension = new Size ( 1280, 720 );
                onBackground ();
                onForeground ();
            }
        });

        assert high_resolution != null;

        high_resolution.setOnClickListener ( new View.OnClickListener (){
            @Override
            public void onClick(View view){
                imageDimension = new Size ( 1920, 1080 );
                onBackground ();
                onForeground ();
            }
        });


        configure_button.setOnClickListener ( new View.OnClickListener (){
            @Override
            public void onClick(View view){
                config = configure_button.isChecked ();
                onBackground ();
                onForeground ();
            }
        });
    }

//    private void getRGBIntFromPlanes(Image.Plane[] planes) {
//        ByteBuffer yPlane = planes[0].getBuffer();
//        ByteBuffer uPlane = planes[1].getBuffer();
//        ByteBuffer vPlane = planes[2].getBuffer();
//
//        int bufferIndex = 0;
//        final int total = yPlane.capacity();
//        final int uvCapacity = uPlane.capacity();
//        final int width = planes[0].getRowStride();
//
//        int yPos = 0;
//        for (int i = 0; i < imageDimension.getHeight (); i++) {
//            int uvPos = (i >> 1) * width;
//
//            for (int j = 0; j < width; j++) {
//                if (uvPos >= uvCapacity-1)
//                    break;
//                if (yPos >= total)
//                    break;
//
//                final int y1 = yPlane.get(yPos++) & 0xff;
//
//            /*
//              The ordering of the u (Cb) and v (Cr) bytes inside the planes is a
//              bit strange. The _first_ byte of the u-plane and the _second_ byte
//              of the v-plane build the u/v pair and belong to the first two pixels
//              (y-bytes), thus usual YUV 420 behavior. What the Android devs did
//              here (IMHO): just copy the interleaved NV21 U/V data to two planes
//              but keep the offset of the interleaving.
//             */
//                final int u = (uPlane.get(uvPos) & 0xff) - 128;
//                final int v = (vPlane.get(uvPos+1) & 0xff) - 128;
//                if ((j & 1) == 1) {
//                    uvPos += 2;
//                }
//
//                // This is the integer variant to convert YCbCr to RGB, NTSC values.
//                // formulae found at
//                // https://software.intel.com/en-us/android/articles/trusted-tools-in-the-new-android-world-optimization-techniques-from-intel-sse-intrinsics-to
//                // and on StackOverflow etc.
//                final int y1192 = 1192 * y1;
//                int r = (y1192 + 1634 * v);
//                int g = (y1192 - 833 * v - 400 * u);
//                int b = (y1192 + 2066 * u);
//
//                r = (r < 0) ? 0 : ((r > 262143) ? 262143 : r);
//                g = (g < 0) ? 0 : ((g > 262143) ? 262143 : g);
//                b = (b < 0) ? 0 : ((b > 262143) ? 262143 : b);
//
//                mRgbBuffer[bufferIndex++] = ((r << 6) & 0xff0000) |
//                        ((g >> 2) & 0xff00) |
//                        ((b >> 10) & 0xff);
//            }
//        }
//    }
//

    private int[] mRgbBuffer;
    private final ImageReader.OnImageAvailableListener onImageAvailableListener = new
            ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image;
                    image = reader.acquireNextImage ( );
//                    final Image.Plane[] planes = image.getPlanes();
//                    final int total = planes[0].getRowStride() * imageDimension.getHeight ();
//                    if (mRgbBuffer == null || mRgbBuffer.length < total)
//                        mRgbBuffer = new int[total];
//                    getRGBIntFromPlanes ( planes);

                    image.close();
                }


            };



    /*
A CaptureRequest defines the parameters for camera device (e.g. exposition, resolution). The Camera2
API provides templates to make it easier to prepare the best CaptureRequest, fine tuned for the
specific camera and for the purpose.

To pass a CaptureRequest to the camera device, we use a CameraCaptureSession, which provides the
context for single (e.g. taking a photo) or repeated (e.g. displaying live preview) requests.
 */
    private void createCaptureSession() {
        try{
            SurfaceTexture texture = textureView.getSurfaceTexture ();
            assert  texture !=null;
            texture.setDefaultBufferSize ( imageDimension.getWidth(), imageDimension.getHeight());
            surface = new Surface ( texture );
            captureRequestBuilder = cameraDevice.createCaptureRequest (CameraDevice.TEMPLATE_PREVIEW );

            ImageReader imageReader = ImageReader.newInstance (imageDimension.getWidth (), imageDimension.getHeight (), ImageFormat.YUV_420_888, 5);
            Surface  imgReaderSurface =  imageReader.getSurface ();

            captureRequestBuilder.addTarget ( imgReaderSurface );
            captureRequestBuilder.addTarget ( surface );
            captureRequestBuilder.set ( CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            if(config){
                captureRequestBuilder.set ( CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_USE_SCENE_MODE);
                captureRequestBuilder.set ( CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_FIREWORKS);
            }



            cameraDevice.createCaptureSession ( Arrays.asList (surface, imgReaderSurface ), new CameraCaptureSession.StateCallback ( ) {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if(cameraDevice == null)
                        return;
                    cameraCaptureSession = session;
                    try {
                        session.setRepeatingRequest ( captureRequestBuilder.build ( ), new CameraCaptureSession.CaptureCallback ( ) {
                            @Override
                            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                super.onCaptureCompleted ( session, request, result );
                                CaptureRequest current_request = result.getRequest ();
                                if(previous_request != current_request) {
                                    Log.d ( "CAMERA_TAG", "REQUEST_ID" + result.getRequest ( ) );
                                    previous_request = current_request;
                                }
                            }
                        }, mBackgroundHandler );
                    } catch (CameraAccessException e) {
                        e.printStackTrace ( );
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText ( MainActivity.this, "Configure failed!", Toast.LENGTH_SHORT ).show ();
                }
            }, null );
            imageReader.setOnImageAvailableListener ( onImageAvailableListener, mBackgroundHandler );


        } catch (CameraAccessException e) {
            e.printStackTrace ( );
        }
    }

    private void openCamera(){
        try {
            String[] cameraIdList = cameraManager.getCameraIdList ( );
            for(int i = 0; i < cameraIdList.length; i++) {
                characteristics = cameraManager.getCameraCharacteristics ( cameraIdList[i] );
                if (characteristics.get ( CameraCharacteristics.LENS_FACING ) == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = cameraIdList[i];
                    break;
                }
            }
//                StreamConfigurationMap streamConfigurationMap = characteristics.get(characteristics.SCALER_STREAM_CONFIGURATION_MAP);
                //Check realtime permission if run higher API 23
                if(ActivityCompat.checkSelfPermission ( this, Manifest.permission.CAMERA ) != PackageManager.PERMISSION_GRANTED)
                {
                    ActivityCompat.requestPermissions(this,new String[]{
                            Manifest.permission.CAMERA,
                    },REQUEST_CAMERA_PERMISSION);
                    return;
                }
                cameraManager.openCamera(cameraId, stateCallback,null);
        } catch (CameraAccessException e) {
            e.printStackTrace ( );
        }

    }

    @Override
    protected void onPause() {
        super.onPause ( );
        stopBackgroundThread();
    }

    public void onBackground() {
        if (imageReader != null) {
            imageReader.close ( );
            imageReader = null;
        }
    }

    public void onForeground() {
        if (textureView.isAvailable()) {
            createCaptureSession ();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    private void stopBackgroundThread() {
        if (mBackgroundHandlerThread != null) {
            mBackgroundHandlerThread.quitSafely ( );
            try {
                mBackgroundHandlerThread.join ( );
                mBackgroundHandlerThread = null;
                mBackgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace ( );
            }
        }
    }

    private void startBackgroundThread(){
        mBackgroundHandlerThread = new HandlerThread("Camera Background");
        mBackgroundHandlerThread.start();
        mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());

    }

    @Override
    protected  void onResume(){
        super.onResume ();
        startBackgroundThread();
        if(textureView.isAvailable())
            openCamera();
        else
            textureView.setSurfaceTextureListener(textureListener);
    }
}
