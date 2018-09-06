package com.example.user.grpcdemo4;

import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.common.primitives.Bytes;
import com.google.protobuf.ByteString;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.main.MainServiceGrpc;
import io.grpc.main.Request;
import io.grpc.main.Response;
import io.grpc.stub.StreamObserver;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class ClientActivity extends AppCompatActivity implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private SurfaceView sv;
    private SurfaceView drawView;
    private Camera mCamera;
    private SurfaceHolder mHolder;
    private SurfaceHolder drawHolder;
    private byte[] mData;
    private static byte[] frameData;
    private boolean previewCheck = true;
    private static String ip = "ServerIP";
    private static int port = 9912;
    private static int port2 = 9914;
    private static String audioIp = "192.168.0.10";
    private static int audioPort = 50051;
    private int autoFocusCheck = 1;
    private TextView resultText;
    private String imageCaptionFag = "0";
    private String object = "";

    //----------------------------BoundingBox
    private List<Integer> classes = new ArrayList<>();
    private List<Float> bbox1 = new ArrayList<>(); // P1의 x좌표 절대 값
    private List<Float> bbox2 = new ArrayList<>(); // P1의 y좌표 절대 값
    private List<Float> bbox3 = new ArrayList<>(); // P2의 x좌표 절대 값
    private List<Float> bbox4 = new ArrayList<>(); // P2의 y좌표 절대 값
    private int drawSize = 0;
    private int heightPixels;
    private int widthPixels;
    //---------------------------------------
    //----------------------------AudioRecord
    private AudioRecord recorder;
    private int sampleRate = 16000;
    private int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_MONO;
    private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private int minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
    private byte[] audioData;
    private String audioThreadResult = "";
    private boolean audioStop = false;
    private int SAMPLE_DELAY = 75;
    private int lastLevel = 0;

    Queue<byte[]> byteQueue = new LinkedList<>();
    //---------------------------------------
    private byte[] audioPlayerByte;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 상태바 숨김 (Fullscreen)
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_client);
        // ActionBar 숨김
        getSupportActionBar().hide();

        // Activiy 실행 시 권한 체크
        if (checkPermission()) {
            requestPermissionAndContinue();
        } else {
            setting();
            drawView = (SurfaceView)findViewById(R.id.drawView);
            drawHolder = drawView.getHolder();
            drawHolder.setFormat(PixelFormat.TRANSPARENT);
            drawHolder.addCallback(this);
            drawView.setZOrderMediaOverlay(true);
        }

        ip = getIntent().getStringExtra("ip");
        resultText = (TextView)findViewById(R.id.resultText);

        // fullscreen 정보 받아오기
        DisplayMetrics displayMetrics = new DisplayMetrics();
        this.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        heightPixels = displayMetrics.heightPixels;
        widthPixels = displayMetrics.widthPixels;

        audioRecordStart();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        audioStop = true;
        previewCheck = false;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        audioStop = true;
        previewCheck = false;
    }

    /**
     * 권한 체크
     */
    private static final int PERMISSION_REQUEST_CODE = 200;
    private boolean checkPermission() {

        return ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(ClientActivity.this, CAMERA) != PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 권한 요청
     */
    private void requestPermissionAndContinue() {
        if (ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(ClientActivity.this, CAMERA) != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this, WRITE_EXTERNAL_STORAGE)
                    && ActivityCompat.shouldShowRequestPermissionRationale(this, READ_EXTERNAL_STORAGE)
                    && ActivityCompat.shouldShowRequestPermissionRationale(this, RECORD_AUDIO)
                    && ActivityCompat.shouldShowRequestPermissionRationale(this, CAMERA)) {
                AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
                alertBuilder.setCancelable(true);
                alertBuilder.setTitle("Permission Error");
                alertBuilder.setMessage("permission denied");
                alertBuilder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
                    public void onClick(DialogInterface dialog, int which) {
                        ActivityCompat.requestPermissions(ClientActivity.this, new String[]{WRITE_EXTERNAL_STORAGE
                                , READ_EXTERNAL_STORAGE, RECORD_AUDIO, CAMERA}, PERMISSION_REQUEST_CODE);
                    }
                });
                AlertDialog alert = alertBuilder.create();
                alert.show();
                Log.e("", "permission denied, show dialog");
            } else {
                ActivityCompat.requestPermissions(ClientActivity.this, new String[]{WRITE_EXTERNAL_STORAGE,
                        READ_EXTERNAL_STORAGE, RECORD_AUDIO, CAMERA}, PERMISSION_REQUEST_CODE);
            }
        }
    }

    /**
     * Camera 및 Surfaceview 설정
     */
    private void setting() {
        mCamera = Camera.open();
        mCamera.setDisplayOrientation(90);
        sv = (SurfaceView) findViewById(R.id.surfaceView);

        // 화면 클릭 시 오토포커스 동작
        sv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCamera.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {

                    }
                });
            }
        });
        mHolder = sv.getHolder();
        mHolder.addCallback(this);
        Camera.Parameters p = mCamera.getParameters();
        mData = new byte[(int) (640 * 480 * 1.5)];
        initBuffer();
    }

    /**
     * Stop Preview
     */
    private void stopPreview() {
        mCamera.stopPreview();
        mCamera.setPreviewCallbackWithBuffer(null);
        mCamera.release();
        mCamera = null;
    }

    /**
     * Buffer 생성
     */
    private void initBuffer() {
        mCamera.addCallbackBuffer(mData);
        mCamera.addCallbackBuffer(mData);
        mCamera.addCallbackBuffer(mData);
        mCamera.setPreviewCallbackWithBuffer(this);
    }

    /**
     * Bounding Box 그리기
     */
    private void DrawBoundingBox() {
        Canvas canvas = drawHolder.lockCanvas(null);
        // canvas 초기화
        canvas.drawColor(0, PorterDuff.Mode.CLEAR);
        drawView.invalidate();

        // Bounding Box Paint
        Paint mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);
        mPaint.setStrokeWidth(5);
        mPaint.setStyle(Paint.Style.STROKE);

        // Text Paint
        Paint mTextPaint = new Paint();
        mTextPaint.setAntiAlias(true);
        mTextPaint.setDither(true);
        mTextPaint.setTextSize(40);
        mTextPaint.setStyle(Paint.Style.FILL);

        for (int i = 0; i < drawSize; i++) {
            mPaint.setColor(setColor(classes.get(i)));
            mTextPaint.setColor(setColor(classes.get(i)));
            String label = class2label(classes.get(i));
            int x = (int)(bbox1.get(i) * widthPixels);
            int y = (int)(bbox2.get(i) * heightPixels);
            int end_x = (int)(bbox3.get(i) * widthPixels);
            int end_y = (int)(bbox4.get(i) * heightPixels);
            //canvas.drawText(label, x + 10, y + 30, mTextPaint); // 텍스트 표시
            //canvas.drawRect(x, y, end_x, end_y, mPaint); //사각형그리기

            object += (label + ",");
        }
        canvas.save();
        canvas.restore();
        drawHolder.unlockCanvasAndPost(canvas);
    }

    /**
     * Bounding Box Color 설정
     */
    private int setColor (int num) {
        switch (num) {
            case 0:
                return 0xFFFF5959;
            case 1:
                return 0xFF627F38;
            case 2:
                return 0xFF32BC97;
            case 3:
                return 0xFFCECC37;
            case 4:
                return 0xFFFFE900;
            case 5:
                return 0xFF38717C;
            case 6:
                return 0xFFDE4BFC;
            case 7:
                return 0xFF34AD7E;
            case 8:
                return 0xFFF7AA1B;
            case 9:
                return 0xFF418C25;
            case 10:
                return 0xFFFF4C8D;
            case 11:
                return 0xFFAA9233;
            case 12:
                return 0xFF3BC936;
            case 13:
                return 0xFF6B441C;
            case 14:
                return 0xFF5465FF;
            case 15:
                return 0xFF23827A;
            case 16:
                return 0xFF7448A5;
            case 17:
                return 0xFF59A0A0;
            case 18:
                return 0xFFC93DE5;
            case 19:
                return 0xFF3FFF00;
            default:
                return 0xFFFFFFFF;
        }
    }

    /**
     * Label Name
     */
    private String class2label (int num) {
        switch (num) {
            case 0:
                return "비행기";
            case 1:
                return "자전거";
            case 2:
                return "새";
            case 3:
                return "보트";
            case 4:
                return "병";
            case 5:
                return "버스";
            case 6:
                return "자동차";
            case 7:
                return "고양이";
            case 8:
                return "의자";
            case 9:
                return "소";
            case 10:
                return "식탁";
            case 11:
                return "개";
            case 12:
                return "말";
            case 13:
                return "오토바이";
            case 14:
                return "사람";
            case 15:
                return "화분";
            case 16:
                return "양";
            case 17:
                return "소파";
            case 18:
                return "기차";
            case 19:
                return "모니터";
            default:
                return null;
        }
    }

    /**
     * Bounding Box 정보 초기화
     */
    private void allClear() {
        classes.clear();
        //scores.clear();
        bbox1.clear();
        bbox2.clear();
        bbox3.clear();
        bbox4.clear();
        object = "";
    }

    /**
     * Audio Record Start
     */
    private void audioRecordStart() {
        audioData = new byte[minBufSize * 2];
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,sampleRate,channelConfig,audioFormat,minBufSize*10);
        recorder.startRecording();
        new Thread(audioRecordThread).start();
    }

    /**
     *  Release device MIC
     */
    public void releaseRecord() {
        recorder.stop();
        recorder.release();
    }

    /**
     * Main Server GRPC AsyncTask
     */
    private class FrameTask extends AsyncTask<Void, Void, List<String>> {
        private ManagedChannel channel;
        private Throwable failed;
        private int[] t = {0};

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected List<String> doInBackground(Void... voids) {
            //long timerStart = SystemClock.currentThreadTimeMillis();
            long timerStart = System.currentTimeMillis();
            try {
                if(previewCheck == false) {
                    return null;
                }
                final List<String> result = new ArrayList<>();
                final List<Byte> byteList = new ArrayList<>();
                //resultStr[0] = "1";
                channel = ManagedChannelBuilder.forAddress(ip, port).usePlaintext(true).build();
                MainServiceGrpc.MainServiceStub stub = MainServiceGrpc.newStub(channel);
                final CountDownLatch finishLatch = new CountDownLatch(1);
                // byteArray -> ByteString로 변환
                ByteString frame = ByteString.copyFrom(frameData);
                audioData = alldeQueue();
                ByteString pcmData = ByteString.copyFrom(audioData);

                StreamObserver<Response> response = new StreamObserver<Response>() {
                    @Override
                    public void onNext(Response value) {
                        if(t[0] == 0) {
                            audioThreadResult = value.getSttText();
                        } else if (t[0] == 1) {
                            result.add(value.getCaptionText());
                        } else if (t[0] == 2 && !result.get(0).equals("1")) {
                            audioPlayerByte = value.getAudioData().toByteArray();
                        } else if (t[0] > 2){
                            result.add(value.getBbox());
                        }
                        t[0]++;
                    }

                    @Override
                    public void onError(Throwable t) {
                        failed = t;
                        finishLatch.countDown();
                        System.out.println("onError() 호출");
                    }

                    @Override
                    public void onCompleted() {
                        //System.out.println("서버로부터 응답 성공");
                        finishLatch.countDown();
                    }
                };

                StreamObserver<Request> requestObserver = stub.dataStreaming(response);
                try {
                    //서버에 데이터 전송
                    if(imageCaptionFag.equals("1")) {
                        requestObserver.onNext(Request.newBuilder().setFrame(frame).setPcmData(pcmData).setCaptionFlag(imageCaptionFag).setDB(lastLevel).build());
                        imageCaptionFag = "0";
                    } else if(imageCaptionFag.equals("2")) {
                        requestObserver.onNext(Request.newBuilder().setFrame(frame).setPcmData(pcmData).setCaptionFlag(imageCaptionFag).setDB(lastLevel).setObject(object).build());
                        imageCaptionFag = "0";
                    } else {
                        requestObserver.onNext(Request.newBuilder().setFrame(frame).setPcmData(pcmData).setCaptionFlag("0").setDB(lastLevel).build());
                    }
                } catch (RuntimeException e) {
                    requestObserver.onError(e);
                    throw e;
                }

                requestObserver.onCompleted();

                if (!finishLatch.await(1, TimeUnit.MINUTES)) {
                    throw new RuntimeException("Could not finish rpc within 1 minute, the server is likely down");
                }

                if (failed != null) {
                    throw new RuntimeException(failed);
                }
                long timerEnd = System.currentTimeMillis();
                System.out.println("프레임 Thread = " +Thread.currentThread().getName() + ", 실행 시간 : " + (timerEnd - timerStart)/1000.0 + "초");
                return result;
            } catch (Exception e) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                pw.flush();
                return null;
            }
        }

        protected  void onPostExecute(List<String> result) {
            if(result == null || previewCheck == false) {
                return;
            }
            channel.shutdown();

            allClear();
            drawSize = result.size() / 5;
            for (int i = 1; i < drawSize + 1; i++) {
                int j = (i * 5) - 4;
                classes.add(Integer.parseInt(result.get(j)));
                bbox1.add(Float.parseFloat(result.get(j+1)));
                bbox2.add(Float.parseFloat(result.get(j+2)));
                bbox3.add(Float.parseFloat(result.get(j+3)));
                bbox4.add(Float.parseFloat(result.get(j+4)));
            }
            DrawBoundingBox();

            if(!result.get(0).equals("1")) {
                new Thread(audioPlayerThread).start();
                resultText.setText(result.get(0));
                imageCaptionFag = "0";
            }
        }
    }

    /**
     * Audio Record Thread
     */
    private Thread audioRecordThread = new Thread(new Runnable() {
        long timerStart;
        long timerEnd;
        short[] buffer;
        int bufferReadResult;
        byte[] bbuffer;
        @Override
        public void run() {
            while(audioStop == false) {
                timerStart = System.currentTimeMillis();
                try{Thread.sleep(SAMPLE_DELAY);}catch(InterruptedException ie){ie.printStackTrace();}
                buffer = new short[minBufSize];
                bufferReadResult = recorder.read(buffer, 0, minBufSize);
                bbuffer = new byte[minBufSize * 2];
                int sumLevel = 0;
                for (int i = 0; i < bufferReadResult; i++) {
                    sumLevel += buffer[i];
                    bbuffer [i * 2] = (byte) (buffer[i] & 0x00FF);
                    bbuffer [(i * 2) + 1] = (byte) (buffer[i] >> 8);
                }
                lastLevel= Math.abs((sumLevel / bufferReadResult));
                byteQueue.add(bbuffer);
                timerEnd = System.currentTimeMillis();
                System.out.println("오디오 입력 Thread = " +Thread.currentThread().getName() + ", 실행 시간 : " + (timerEnd - timerStart)/1000.0 + "초");
            }
            releaseRecord();
        }
    });

    /**
     * byteQueue.poll concat
     */
    private byte[] alldeQueue() {
        byte[] result = byteQueue.poll();
        while (!byteQueue.isEmpty()) {
            byte[] data = byteQueue.poll();
            result = Bytes.concat(result, data);
        }
        return result;
    }

    /**
     * Audio Player Thread
     */
    private Thread audioPlayerThread = new Thread(new Runnable() {
        long timerStart;
        long timerEnd;
        @Override
        public void run() {
            timerStart = System.currentTimeMillis();
            // 임시 파일 생성 후 삭제
            String outputFile= Environment.getExternalStorageDirectory().getAbsolutePath() + "/tts.mp3";
            File path = new File(outputFile);
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(path);
                fos.write(audioPlayerByte);
                fos.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            MediaPlayer mediaPlayer = new MediaPlayer();

            try {
                mediaPlayer.stop();
                mediaPlayer.reset();
                mediaPlayer.setDataSource(outputFile);
            } catch (IOException e) {
                e.printStackTrace();
            }

            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.start();
                }
            });
            mediaPlayer.prepareAsync();

            path.delete();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    resultText.setText("");
                }
            });
            timerEnd = System.currentTimeMillis();
            System.out.println("오디오 재생 Thread = " +Thread.currentThread().getName() + ", 실행 시간 : " + (timerEnd - timerStart)/1000.0 + "초");
        }
    });

    /**
     * Preview Frame
     */
    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        autoFocusCheck++;
        // Frame Data -> Bitmap
        Camera.Parameters params = mCamera.getParameters();
        int w = params.getPreviewSize().width;
        int h = params.getPreviewSize().height;
        int format = params.getPreviewFormat();
        YuvImage image = new YuvImage(data, format, w, h, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Rect area = new Rect(0, 0, w, h);
        image.compressToJpeg(area, 100, out);
        Bitmap captureImg = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size());

        // Bitmap resize
        Bitmap resize = Bitmap.createScaledBitmap(captureImg, 300, 300, true);

        // Alpha 제거
        byte[] bytes = convertRGB(resize);

        mData = new byte[data.length];
        frameData = bytes;

        if (audioThreadResult.equals("Caption")) {
            imageCaptionFag = "1";
            audioThreadResult = "";
        } else if (audioThreadResult.equals("Object")) {
            imageCaptionFag = "2";
            audioThreadResult = "";
        }

        // AsyncTask 동작
        if (previewCheck == true) {
            try {
                // 병렬처리
                //new FrameTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                new FrameTask().execute();
            } catch (Exception e) {
                // asynctask 큐가 가득 찼을 경우 무시
            }
        }

        // 약 5초마다 오토포커스 동작
        if (autoFocusCheck % 150 == 0) {
            mCamera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {

                }
            });
        }
        mCamera.addCallbackBuffer(mData);
    }

    /**
     * Bitmap Alpha 제거
     */
    public byte[] convertRGB(Bitmap bitmap) {
        int bytes = bitmap.getByteCount();
        ByteBuffer buffer = ByteBuffer.allocate(bytes);
        bitmap.copyPixelsToBuffer(buffer);
        byte[] temp = buffer.array();
        byte[] pixels = new byte[(temp.length / 4) * 3];

        for (int i = 0; i < (temp.length / 4); i++) {
            pixels[i * 3] = temp[i * 4 + 2];     // B
            pixels[i * 3 + 1] = temp[i * 4 + 1]; // G
            pixels[i * 3 + 2] = temp[i * 4 + 0]; // R
        }
        return pixels;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            mCamera.setPreviewDisplay(holder);
            initBuffer();
            mCamera.startPreview();
        } catch (IOException e) {
            Log.d("APP",
                    "Error setting camera preview: " + e.getMessage());
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (mHolder.getSurface() == null) {
            return;
        }

        try {
            mCamera.stopPreview();
        } catch (Exception e) {
        }

        Camera.Parameters p = mCamera.getParameters();
        p.setPreviewFormat(ImageFormat.NV21);
        p.setPreviewSize(640,480);
        mCamera.setParameters(p);

        try {
            mCamera.setPreviewDisplay(mHolder);
            initBuffer();
            mCamera.startPreview();
        } catch (Exception e) {
            Log.d("APP",
                    "Error starting camera preview: " + e.getMessage());
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if(mCamera != null){
            stopPreview();
        }
    }
}
