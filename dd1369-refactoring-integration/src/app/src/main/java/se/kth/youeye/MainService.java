package se.kth.youeye;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.util.Size;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;

public class MainService extends AccessibilityService implements ImageAnalysis.Analyzer, ExpressionCallback {
    // TODO: Stop the foregroundService when the service is disconnected. Problematic since there isn't any "onServiceDisconnected" afaik. //Arvid

    private ForegroundService foregroundService;
    private InputAnalyzer inputAnalyzer;
    private UINavigator uiNavigator;


    protected void onServiceConnected() {
        startCamera();

        // We keep the old expressions for 5 seconds, this is probably a reasonable value
        inputAnalyzer = new InputAnalyzer(5000);

        uiNavigator = new UINavigator(this);

        foregroundService = new ForegroundService();
        Intent startIntent = new Intent(this, ForegroundService.class);
        Intent stopIntent = new Intent(this, ForegroundService.class);
        startIntent.setAction(ForegroundService.ACTION_START_FOREGROUND_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // This will check the version of android, if it is 8.0 or higher, and select the appropriate start command
            startForegroundService(startIntent);
        }
        else {
            startService(startIntent);
        }
        foregroundService.onBind(startIntent);
    }


    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // TODO: Only update nodeinfos if the event is of the right type.
        Log.d("FOO", "onAccessibilityEvent: we got an event: " + event.toString());
        switch (event.getEventType()) {
            //case AccessibilityEvent.WINDOWS_CHANGE_ADDED:
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                uiNavigator.resetNodeInfos(getRootInActiveWindow());
                break;
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                uiNavigator.refreshNodeInfos(getRootInActiveWindow());
                break;
            default:
        }
    }

    @Override
    public void onInterrupt() {
        Log.d("FOO", "onAccessibilityEvent: we got an interrupt!");
        // We need to override this.
    }


    private void startCamera() {
        Log.d("EYE", "startCamera: entered");
        if (!allPermissionsGranted()) {
            Log.d("EYE", "startCamera: Not all permissions granted!");
            return;
        }

        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));
        Log.d("EYE", "startCamera: ended");
    }

    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Log.d("EYE", "bindPreview: entered");
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build(); // TODO: Change to LENS_FACING_FRONT

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder().setTargetResolution(new Size(1280, 720)).build();

        // 1st param: Executor - Something that will call analyze() (async?). Not sure if this is using anything async (other than listener created on row 252).
        // 2nd param: Instance of the analyzer we want to run analyze() in.
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), this);

        Camera camera = cameraProvider.bindToLifecycle(foregroundService, cameraSelector, imageAnalysis);
        Log.d("EYE", "bindPreview: ended");
    }


    /**
     * This method is called once for each image from the camera, and called at the
     * frame rate of the camera. Each analyze call is executed sequentially.
     *
     * The imageProxy must be closed to get another frame, if another frame is in the queue. Otherwise
     * it will be called once another frame is captured
     *
     * (Comments)
     * The default setting is that the last 1 frame(s) are saved in a FIFO queue, if they have not yet
     * been analyzed. The size of the queue may be configured by us.
     * Configuration can be done by implementing ImageAnalysis.Defaults. Also allows us to set target & max resolution (may be useful for performance?)
     *
     *
     *
     * See inherited docs for further documentation.
     */
    @Override
    public void analyze(ImageProxy imageProxy) {
        Expression.detect(this, imageProxy);
    }

    private boolean allPermissionsGranted() {
        if (ContextCompat.checkSelfPermission(this, "android.permission.CAMERA") != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return true;
    }

    public void handleExpression(Expression expression) {
        uiNavigator.handleEvent(inputAnalyzer.analyze(expression));
    }
}
