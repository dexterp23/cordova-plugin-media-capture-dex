package org.apache.cordova.mediacapture;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.media.MediaRecorder.OnInfoListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
//import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.media.MediaPlayer;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.os.AsyncTask;
import android.media.AudioManager;
import android.view.WindowManager;

import org.apache.cordova.mediacapture.FakeR;

				
public class VideoCamera extends Activity {
	
	private static final String TAG = "FBLOG";
    private Button startRecordingButton;
    private TextureView textureView;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    private String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest captureRequest;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;
    private File file;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private boolean mFlashSupported;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    
    private CountDownTimer cameratimer;
    private CountDownTimer cameratimerFull;
    private Toast mytoast;
    
    private Size mVideoSize;
    private MediaRecorder mMediaRecorder;
    private String mNextVideoAbsolutePath;
    private Integer mSensorOrientation;
    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();
    private Surface mRecorderSurface;
    private boolean mIsRecordingVideo;
	private FakeR fakeR;
	public static final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1;
	
	private int SetDuration = 15000;
    private int CameraFace = 0;
	long sec_chk = -1;
	
	String AudioURL;
    private int AudioStart = 0;
    MediaPlayer mediaplayer;
    ProgressBar ProgressBar;
    TextView text_shown;
    Handler seekHandler = new Handler();
	private boolean mIsPlayaudio;
	long deley_chk = 0;
	long audio_start_chk = 0;
    long millisUntilFinished = 0;

	
	@Override
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		fakeR = new FakeR(this);
		
		this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(fakeR.getId("layout", "video_camera"));
		
		//variable
		SetDuration = getIntent().getExtras().getInt("SetDuration");
		SetDuration = SetDuration + 4600;
        CameraFace = getIntent().getExtras().getInt("CameraFace");
		AudioStart = getIntent().getExtras().getInt("AudioStart");
		audio_start_chk = AudioStart;
		if (audio_start_chk < 0) AudioStart = 0;
		AudioURL = getIntent().getStringExtra("AudioURL");
		this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		
		//camera
		textureView = (TextureView) findViewById(fakeR.getId("id", "texture"));
		assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);
		
		//video
		startRecordingButton = (Button) findViewById(fakeR.getId("id", "btn_takevideo"));
		startRecordingButton.setVisibility(View.GONE);
        assert startRecordingButton != null;
        startRecordingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            	if (mIsRecordingVideo) {
                    stopRecordingVideo();
                } else {
                    //startRecordingVideo();
                }
            }
        });

		//audio
        ProgressBar = (ProgressBar) findViewById(fakeR.getId("id", "progressBar"));
        ProgressBar.setVisibility(View.GONE);
        text_shown = (TextView) findViewById(fakeR.getId("id", "text_shown"));
        text_shown.setVisibility(View.GONE);

    }
	
	@Override
    protected void onStart() {
    	super.onStart();
    	//startPlayingAudio();
	}
    @Override
    protected void onRestart() {
    	super.onRestart();
    	if (mIsPlayaudio == true) stopPlayingAudio();
    	if (mIsRecordingVideo == true) stopRecordingVideo();
    }
    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
        if (mIsPlayaudio == true) stopPlayingAudio();
    	if (mIsRecordingVideo == true) stopRecordingVideo();
    }
    @Override
    protected void onPause() {
    	super.onPause();
        closeCamera();
        stopBackgroundThread();
        if (mIsPlayaudio == true) stopPlayingAudio();
    	if (mIsRecordingVideo == true) stopRecordingVideo();
    }
    @Override
    protected void onStop() {
    	super.onStop();
    	if (mIsPlayaudio == true) stopPlayingAudio();
    	if (mIsRecordingVideo == true) stopRecordingVideo();
    }
    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	if (mIsPlayaudio == true) stopPlayingAudio();
    	if (mIsRecordingVideo == true) stopRecordingVideo();
    }
	
	
    Runnable run_1 = new Runnable() {
		@Override
		public void run() {
			seekUpdation_1();

		}
	};
	
	Runnable run_2 = new Runnable() {
		@Override
		public void run() {
			seekUpdation_2();

		}
	};
	
	
	void seekUpdation_1 () {
		
		seekHandler.postDelayed(run_1, 100);
			
		millisUntilFinished = millisUntilFinished + 100;
		long sec = millisUntilFinished / 1000;

		if (sec < 5 && sec_chk!=sec) {
			sec_chk = sec;
			ProgressBar.setVisibility(View.GONE);
			text_shown.setText("Start Recording in ... " + (5 - sec));
			text_shown.setVisibility(View.VISIBLE);
			if (sec == ((audio_start_chk/1000) * (-1))) mediaplayer.start();
		} else if (sec == 5 && sec_chk!=sec) {
			sec_chk = sec;
			text_shown.setText("GO!!!");
			if (sec == ((audio_start_chk/1000) * (-1))) mediaplayer.start();
		} else if (sec > 5 && sec_chk!=sec) {
			sec_chk = sec;
			text_shown.setVisibility(View.GONE);
			seekHandler.removeMessages(0);
		}
		
		if (mIsRecordingVideo && deley_chk==0) {
			deley_chk = 5000 - millisUntilFinished;
			//Log.i(TAG, String.valueOf("deley_chk 1: " + deley_chk));
		}
	
	}
	
	void seekUpdation_2 () {
		
		seekHandler.postDelayed(run_2, 100);

		millisUntilFinished = mediaplayer.getCurrentPosition();
		
		if (millisUntilFinished > AudioStart) {
			
			long sec = (millisUntilFinished / 1000) - (AudioStart / 1000);
			//Log.i(TAG, String.valueOf("sec: " + sec));
			
			if (sec < 5 && sec_chk!=sec) {
				sec_chk = sec;
				ProgressBar.setVisibility(View.GONE);
				text_shown.setText("Start Recording in ... " + (5 - sec));
				text_shown.setVisibility(View.VISIBLE);
			} else if (sec == 5 && sec_chk!=sec) {
				sec_chk = sec;
				text_shown.setText("GO!!!");
			} else if (sec > 5 && sec_chk!=sec) {
				sec_chk = sec;
				text_shown.setVisibility(View.GONE);
				seekHandler.removeMessages(0);
			}
			
			if (mIsRecordingVideo && deley_chk==0) {
				deley_chk = 5000 - (millisUntilFinished - AudioStart);
			}
			
		}
			
	}


    
    private void startPlayingAudio() {

    	AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {

            @Override
            protected void onPreExecute() {
            	if(mIsPlayaudio==false) {
					//ubacujemo audio sa telefona ako ga ima
					String fileName = getAudioFilePath(getActivity());
					File file = new File(fileName);
					if(file.exists()) AudioURL = fileName;
					
					//startujemo audio player
	            	mediaplayer = new MediaPlayer();
	            	mediaplayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
	            	ProgressBar.setVisibility(View.VISIBLE);
            	}
            }

            @Override
            protected Void doInBackground(Void... arg0) {
            	try {
            		//Do something...
                    //Thread.sleep(5000);
	                try 
	                {
	                	mediaplayer.setDataSource(AudioURL);
	                	mediaplayer.prepare();
	                	mediaplayer.seekTo (AudioStart);
	                	if (audio_start_chk >= 0) mediaplayer.start();
	                } 
	                catch (IOException e) {
						mytoast = Toast.makeText(getActivity(), "Error! Wrong audio.", Toast.LENGTH_LONG);
						mytoast.setGravity(Gravity.CENTER_HORIZONTAL, 0, 0);
						mytoast.show();
						seekHandler.removeMessages(0);
						mIsPlayaudio = false;
	                }
	            } catch (Exception e) {
	                // TODO Auto-generated catch block
	                e.printStackTrace();
	            }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
				millisUntilFinished = 0;
            	deley_chk = 0;
            	sec_chk = -1;
            	mIsPlayaudio=true;
				if (mIsRecordingVideo == false) startRecordingVideo();
            	if (audio_start_chk < 0) {
    				seekUpdation_1();
    			} else {
    				seekUpdation_2();
    			}
            }

        };
        task.execute((Void[])null);
        
    }
	

    public void stopPlayingAudio() {
        if (mIsPlayaudio == true) {
			mIsPlayaudio = false;
			mediaplayer.stop();
			mediaplayer.reset();
			mediaplayer = null;
			seekHandler.removeMessages(0);
			ProgressBar.setVisibility(View.VISIBLE);
		}
    }
    
    
    @Override
    public void onBackPressed() {
    	if (mIsPlayaudio == true) stopPlayingAudio();
    	if (mIsRecordingVideo == true) stopRecordingVideo();
    	finish();
    }
    
	
	private Activity getActivity() {
		return VideoCamera.this;
	}
	
	
	private String getAudioFilePath(Context context) {
    	return context.getExternalFilesDir(null).getAbsolutePath() + "/audio_file.mp3";
    }
	
	
	
	TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //open your camera here
            openCamera();
        }
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform you image captured size according to the surface width and height
        }
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };
	
	private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            //This is called when the camera is open
            //Log.e(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }
        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }
        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };
	
	protected void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback(){
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            //The camera is already closed
                            if (null == cameraDevice) {
                                return;
                            }
                            // When the session is ready, we start displaying the preview.
                            cameraCaptureSessions = cameraCaptureSession;
                            updatePreview();
                        }
                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Toast.makeText(getActivity(), "Configuration change", Toast.LENGTH_SHORT).show();
                        }
                    }, null);
				if (mIsPlayaudio == false) startPlayingAudio();
            } catch (CameraAccessException e) {
                 e.printStackTrace();
            }
    }
	
    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        //Log.e(TAG, "is camera open");
        try {
			
            cameraId = manager.getCameraIdList()[CameraFace];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
			
            //mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
            imageDimension = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), textureView.getWidth(), textureView.getHeight(), mVideoSize);

            configureTransform(textureView.getWidth(), textureView.getHeight());
            
            // Add permission for camera and let user grant the permission
            if (ContextCompat.checkSelfPermission(getActivity(),
							Manifest.permission.READ_EXTERNAL_STORAGE)
					!= PackageManager.PERMISSION_GRANTED) {
	
				// Should we show an explanation?
				if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(),
						Manifest.permission.READ_EXTERNAL_STORAGE)) {
	
					// Show an expanation to the user *asynchronously* -- don't block
					// this thread waiting for the user's response! After the user
					// sees the explanation, try again to request the permission.
	
				} else {
	
					// No explanation needed, we can request the permission.
	
					ActivityCompat.requestPermissions(getActivity(),
							new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
							MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
	
					// MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE is an
					// app-defined int constant. The callback method gets the
					// result of the request.
					
				}
			}
			
            mMediaRecorder = new MediaRecorder();
			manager.openCamera(cameraId, stateCallback, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
		
        //Log.e(TAG, "openCamera X");
		
    }
	
	protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
    
    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
	
	protected void updatePreview() {
        if(null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
	
	private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != mMediaRecorder) {
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }
    
    private static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        Log.e(TAG, "Couldn't find any suitable video size");
        return choices[choices.length - 1];
    }
    
    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<Size>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }
    
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == textureView || null == imageDimension || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, imageDimension.getHeight(), imageDimension.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / imageDimension.getHeight(),
                    (float) viewWidth / imageDimension.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        textureView.setTransform(matrix);
    }
    
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }
	
	
	
	    private void startRecordingVideo() {
        if (null == cameraDevice || !textureView.isAvailable() || null == imageDimension) {
            return;
        }
        try {
            closePreviewSession();
            setUpMediaRecorder();
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<Surface>();

            // Set up Surface for the camera preview
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            captureRequestBuilder.addTarget(previewSurface);

            // Set up Surface for the MediaRecorder
            mRecorderSurface = mMediaRecorder.getSurface();
            surfaces.add(mRecorderSurface);
            captureRequestBuilder.addTarget(mRecorderSurface);

            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                	cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // UI
                        	startRecordingButton.setVisibility(View.VISIBLE);
                            mIsRecordingVideo = true;

                            // Start recording
                            mMediaRecorder.start();
                        }
                    });
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Activity activity = getActivity();
                    if (null != activity) {
                        Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
                    }
                }

				
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    
    private void stopRecordingVideo() {
    	
    	startRecordingButton.setVisibility(View.GONE);
    	
        // UI
        mIsRecordingVideo = false;
        // Stop recording
        mMediaRecorder.stop();
        mMediaRecorder.reset();
		if (mIsPlayaudio == true) stopPlayingAudio();
		
		Intent intent = new Intent();
		intent.setData(Uri.parse(mNextVideoAbsolutePath));
        intent.putExtra("deley_chk",deley_chk);
        setResult(RESULT_OK, intent); 
		
		mNextVideoAbsolutePath = null;

        finish();
    }
    
    
    private void setUpMediaRecorder() throws IOException {
        final Activity activity = getActivity();
        if (null == activity) {
            return;
        }
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        if (mNextVideoAbsolutePath == null || mNextVideoAbsolutePath.isEmpty()) {
            mNextVideoAbsolutePath = getVideoFilePath(getActivity());
        }
        mMediaRecorder.setOutputFile(mNextVideoAbsolutePath);
        mMediaRecorder.setVideoEncodingBitRate(10000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setMaxDuration(SetDuration);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        
        switch (rotation) {
            case 0:
                if (CameraFace == 1) {
            		mMediaRecorder.setOrientationHint(270);
            	} else {
            		mMediaRecorder.setOrientationHint(90);
            	}
                break;
            case 1:
                mMediaRecorder.setOrientationHint(0);
                break;
        }
        
        mMediaRecorder.setOnInfoListener(new OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mr, int what, int extra) {
            	if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
            		stopRecordingVideo();
                }  

            }
        });
        
        mMediaRecorder.prepare();
    }
    

    private String getVideoFilePath(Context context) {
    	return context.getExternalFilesDir(null).getAbsolutePath() + "/video_file.mp4";
        //return context.getExternalFilesDir(null).getAbsolutePath() + "/" + System.currentTimeMillis() + ".mp4";
    }
    
    private void closePreviewSession() {
        if (cameraCaptureSessions != null) {
        	cameraCaptureSessions.close();
        	cameraCaptureSessions = null;
        }
    }



}