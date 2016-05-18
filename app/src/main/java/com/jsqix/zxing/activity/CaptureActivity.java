package com.jsqix.zxing.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.zxing.Result;
import com.jsqix.zxing.R;
import com.jsqix.zxing.camera.CameraManager;
import com.jsqix.zxing.decode.DecodeThread;
import com.jsqix.zxing.utils.BeepManager;
import com.jsqix.zxing.utils.CaptureActivityHandler;
import com.jsqix.zxing.utils.InactivityTimer;
import com.jsqix.zxing.utils.ScanningImageTools;
import com.jsqix.zxing.utils.Utils;

import java.io.IOException;
import java.lang.reflect.Field;

public final class CaptureActivity extends Activity implements
        SurfaceHolder.Callback {

    private static final String TAG = CaptureActivity.class.getSimpleName();

    private CameraManager cameraManager;
    private CaptureActivityHandler handler;
    private InactivityTimer inactivityTimer;
    private BeepManager beepManager;

    private SurfaceView scanPreview = null;
    private RelativeLayout scanContainer;
    private RelativeLayout scanCropView;
    private ImageView scanLine;
    private static final int REQUEST_CODE_OPEN_ALBUM = 0x100;
    private boolean hasSurface;
    private Button photoBtn, myQRBtn, flashBtn;
    private Button zoomIn, zoomOut;
    private LinearLayout progress;
    boolean flashOn; // 是否开启闪光灯

    String photo_path;//二维码图片路径
    int zoomValue = 1, zoomStep = 1;

    private Rect mCropRect = null;

    public Handler getHandler() {
        return handler;
    }

    public CameraManager getCameraManager() {
        return cameraManager;
    }


    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_capture);

        initCaptureView();
    }

    private void initCaptureView() {
        flashOn = false;
        hasSurface = false;

        scanPreview = (SurfaceView) findViewById(R.id.capture_preview);
        scanContainer = (RelativeLayout) findViewById(R.id.capture_container);
        scanCropView = (RelativeLayout) findViewById(R.id.capture_crop_view);
        scanLine = (ImageView) findViewById(R.id.capture_scan_line);

        inactivityTimer = new InactivityTimer(this);
        beepManager = new BeepManager(this);

        zoomIn = (Button) findViewById(R.id.zoomIn);
        zoomOut = (Button) findViewById(R.id.zoomOut);
        zoomIn.setOnClickListener(new ZoomClick());
        zoomOut.setOnClickListener(new ZoomClick());

        photoBtn = (Button) findViewById(R.id.photoBtn);
        myQRBtn = (Button) findViewById(R.id.myQRBtn);
        flashBtn = (Button) findViewById(R.id.flashBtn);
        flashBtn.setOnClickListener(new MyClick());
        photoBtn.setOnClickListener(new MyClick());
        myQRBtn.setOnClickListener(new MyClick());
        progress = (LinearLayout) findViewById(R.id.progress);

        TranslateAnimation animation = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 0.9f);
        animation.setDuration(4500);
        animation.setRepeatCount(-1);
        animation.setRepeatMode(Animation.RESTART);
        scanLine.startAnimation(animation);
    }

    class ZoomClick implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.zoomIn:
                    if (zoomValue > 1) {
                        zoomValue -= zoomStep;
                    } else {
                        Toast.makeText(CaptureActivity.this, "不能在缩小了", Toast.LENGTH_LONG).show();
                    }
                    break;
                case R.id.zoomOut:
                    if (zoomValue < 5) {
                        zoomValue += zoomStep;
                    } else {
                        Toast.makeText(CaptureActivity.this, "不能在放大了", Toast.LENGTH_LONG).show();
                    }
                    break;
            }
            cameraManager.get().startSmoothZoom(zoomValue);
        }
    }

    class MyClick implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            Intent intent = new Intent();
            switch (v.getId()) {
                case R.id.photoBtn:
                    intent.setAction(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("image/*");
                    intent.putExtra("return-data", true);
                    CaptureActivity.this.startActivityForResult(
                            Intent.createChooser(intent, "选择图片"),
                            REQUEST_CODE_OPEN_ALBUM);
                    break;

                case R.id.flashBtn:
                    flashOn = !flashOn;
                    cameraManager.setTorch(flashOn);
                    if (flashOn) {
                        flashBtn.setText("关灯");
                        Drawable drawable = getResources().getDrawable(
                                R.drawable.qb_scan_btn_flash_down);
                        drawable.setBounds(0, 0, drawable.getMinimumWidth(),
                                drawable.getMinimumHeight());
                        flashBtn.setCompoundDrawables(null, drawable, null, null);
                    } else {
                        flashBtn.setText("开灯");
                        Drawable drawable = getResources().getDrawable(
                                R.drawable.qb_scan_btn_flash_nor);
                        drawable.setBounds(0, 0, drawable.getMinimumWidth(),
                                drawable.getMinimumHeight());
                        flashBtn.setCompoundDrawables(null, drawable, null, null);
                    }

                    break;
                case R.id.myQRBtn:

                    break;
            }

        }

    }

    /**
     * 处理返回二维码图片
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_CODE_OPEN_ALBUM:
                    String[] proj = {MediaStore.Images.Media.DATA};
                    // 获取选中图片的路径
                    Cursor cursor = getContentResolver().query(data.getData(),
                            proj, null, null, null);
                    if (cursor.moveToFirst()) {

                        int column_index = cursor
                                .getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                        photo_path = cursor.getString(column_index);
                        if (photo_path == null) {
                            photo_path = Utils.getPath(getApplicationContext(),
                                    data.getData());
                            Log.i("123path  Utils", photo_path);
                        }
                        Log.i("123path", photo_path);

                    }

                    cursor.close();

                    ScanningImageTools.scanningImage(photo_path,
                            new ScanningImageTools.IZCodeCallBack() {

                                @Override
                                public void ZCodeCallBackUi(Result result) {
                                    // TODO Auto-generated method stub
                                    if (result == null) {
                                        Looper.prepare();
                                        Toast.makeText(getApplicationContext(),
                                                "图片格式有误", Toast.LENGTH_SHORT)
                                                .show();
                                        Looper.loop();
                                    } else {
                                        Log.i("123result", result.toString());
                                        // Log.i("123result", result.getText());
                                        // 数据返回进行仿乱码处理
                                        String recode = ScanningImageTools.recode(result.toString());
                                        Intent data = new Intent();

                                        data.setClass(CaptureActivity.this,
                                                ResultActivity.class);
                                        data.putExtra("imgPath", photo_path);
                                        data.putExtra("result", recode);
                                        setResult(300, data);
                                        startActivity(data);
                                        System.out.println("----->" + recode);
                                    }
                                }
                            });

                    break;

            }

        }

    }


    @Override
    protected void onResume() {
        super.onResume();

        // CameraManager must be initialized here, not in onCreate(). This is
        // necessary because we don't
        // want to open the camera driver and measure the screen size if we're
        // going to show the help on
        // first launch. That led to bugs where the scanning rectangle was the
        // wrong size and partially
        // off screen.
        cameraManager = new CameraManager(getApplication());

        handler = null;

        if (hasSurface) {
            // The activity was paused but not stopped, so the surface still
            // exists. Therefore
            // surfaceCreated() won't be called, so init the camera here.
            initCamera(scanPreview.getHolder());
        } else {
            // Install the callback and wait for surfaceCreated() to init the
            // camera.
            scanPreview.getHolder().addCallback(this);
        }

        inactivityTimer.onResume();
    }

    @Override
    protected void onPause() {
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        inactivityTimer.onPause();
        beepManager.close();
        cameraManager.closeDriver();
        if (!hasSurface) {
            scanPreview.getHolder().removeCallback(this);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        inactivityTimer.shutdown();
        super.onDestroy();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (holder == null) {
            Log.e(TAG,
                    "*** WARNING *** surfaceCreated() gave us a null surface!");
        }
        if (!hasSurface) {
            hasSurface = true;
            initCamera(holder);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {

    }

    /**
     * A valid barcode has been found, so give an indication of success and show
     * the results.
     *
     * @param rawResult The contents of the barcode.
     * @param bundle    The extras
     */
    public void handleDecode(Result rawResult, Bundle bundle) {
        inactivityTimer.onActivity();
        beepManager.playBeepSoundAndVibrate();

        bundle.putInt("width", mCropRect.width());
        bundle.putInt("height", mCropRect.height());
        bundle.putString("result", rawResult.getText());

        startActivity(new Intent(CaptureActivity.this, ResultActivity.class)
                .putExtras(bundle));
    }

    private void initCamera(SurfaceHolder surfaceHolder) {
        if (surfaceHolder == null) {
            throw new IllegalStateException("No SurfaceHolder provided");
        }
        if (cameraManager.isOpen()) {
            Log.w(TAG,
                    "initCamera() while already open -- late SurfaceView callback?");
            return;
        }
        try {
            cameraManager.openDriver(surfaceHolder, flashOn);
            // Creating the handler starts the preview, which can also throw a
            // RuntimeException.
            if (handler == null) {
                handler = new CaptureActivityHandler(this, cameraManager,
                        DecodeThread.ALL_MODE);
            }

            initCrop();
        } catch (IOException ioe) {
            Log.w(TAG, ioe);
            displayFrameworkBugMessageAndExit();
        } catch (RuntimeException e) {
            // Barcode Scanner has seen crashes in the wild of this variety:
            // java.?lang.?RuntimeException: Fail to connect to camera service
            Log.w(TAG, "Unexpected error initializing camera", e);
            displayFrameworkBugMessageAndExit();
        }
    }

    private void displayFrameworkBugMessageAndExit() {
        // camera error
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.app_name));
        builder.setMessage("相机打开出错，请稍后重试");
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }

        });
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {

            @Override
            public void onCancel(DialogInterface dialog) {
                finish();
            }
        });
        builder.show();
    }

    public void restartPreviewAfterDelay(long delayMS) {
        if (handler != null) {
            handler.sendEmptyMessageDelayed(R.id.restart_preview, delayMS);
        }
    }

    public Rect getCropRect() {
        return mCropRect;
    }

    /**
     * 初始化截取的矩形区域
     */
    private void initCrop() {
        int cameraWidth = cameraManager.getCameraResolution().y;
        int cameraHeight = cameraManager.getCameraResolution().x;

        /** 获取布局中扫描框的位置信息 */
        int[] location = new int[2];
        scanCropView.getLocationInWindow(location);

        int cropLeft = location[0];
        int cropTop = location[1] - getStatusBarHeight();

        int cropWidth = scanCropView.getWidth();
        int cropHeight = scanCropView.getHeight();

        /** 获取布局容器的宽高 */
        int containerWidth = scanContainer.getWidth();
        int containerHeight = scanContainer.getHeight();

        /** 计算最终截取的矩形的左上角顶点x坐标 */
        int x = cropLeft * cameraWidth / containerWidth;
        /** 计算最终截取的矩形的左上角顶点y坐标 */
        int y = cropTop * cameraHeight / containerHeight;

        /** 计算最终截取的矩形的宽度 */
        int width = cropWidth * cameraWidth / containerWidth;
        /** 计算最终截取的矩形的高度 */
        int height = cropHeight * cameraHeight / containerHeight;

        /** 生成最终的截取的矩形 */
        mCropRect = new Rect(x, y, width + x, height + y);
    }

    private int getStatusBarHeight() {
        try {
            Class<?> c = Class.forName("com.android.internal.R$dimen");
            Object obj = c.newInstance();
            Field field = c.getField("status_bar_height");
            int x = Integer.parseInt(field.get(obj).toString());
            return getResources().getDimensionPixelSize(x);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }
}