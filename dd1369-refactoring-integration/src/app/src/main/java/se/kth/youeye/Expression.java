package se.kth.youeye;

import android.annotation.SuppressLint;
import android.media.Image;
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

public class Expression {
    // We use the @IntDef notation to ensure safer handling of our our magic constants
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({EYES_CLOSED, EYES_OPEN, MOUTH_CLOSED, MOUTH_OPEN})
    public @interface ExpressionTypeDef {}
    // Magic constant definitions
    public static final int EYES_CLOSED = 0;
    public static final int EYES_OPEN = 1;
    public static final int MOUTH_CLOSED = 2;
    public static final int MOUTH_OPEN = 3;


    public final long timestamp;

    private final Float leftEyeOpenProbability;
    private final Float rightEyeOpenProbability;

    private final FaceLandmark mouthBottom;
    private final FaceLandmark mouthRight;
    private final FaceLandmark mouthLeft;
    private final Float smilingProbability;

    private final Float eulerAngleX;
    private final Float eulerAngleY;
    private final Float eulerAngleZ;

    public Expression(Float leftEyeOpenProbability, Float rightEyeOpenProbability,
                      Float eulerAngleX, Float eulerAngleY, Float eulerAngleZ,
                      FaceLandmark mouthBottom, FaceLandmark mouthRight, FaceLandmark mouthLeft, Float smilingProbability) {
        this.leftEyeOpenProbability = leftEyeOpenProbability;
        this.rightEyeOpenProbability = rightEyeOpenProbability;

        this.eulerAngleX = eulerAngleX;
        this.eulerAngleY = eulerAngleY;
        this.eulerAngleZ = eulerAngleZ;

        this.mouthBottom = mouthBottom;
        this.mouthRight = mouthRight;
        this.mouthLeft = mouthLeft;
        this.smilingProbability = smilingProbability;

        timestamp = System.currentTimeMillis();
    }

    /**
     * Checks if the face held by this Expression is currently performing a given expression.
     *
     * @param expressionId The id of the expression, valid ids are given by Expression.expressionName
     * @return True if the face has that expression, false otherwise
     */
    public boolean has(int expressionId) {
        switch(expressionId) {
            case Expression.EYES_CLOSED:
                return getEyesClosed();
            case Expression.EYES_OPEN:
                return !getEyesClosed();
            case Expression.MOUTH_CLOSED:
                return getMouthClosed();
            case Expression.MOUTH_OPEN:
                return !getMouthClosed();
            default:
                return false;
        }
    }

    private boolean getEyesClosed() {
        return leftEyeOpenProbability < 0.5 && rightEyeOpenProbability < 0.5;
    }

    private boolean getMouthClosed() {
        //Just a somewhat logical code for the actual logic we'll implement later.
        //return mouthBottom.getPosition().y > (mouthRight.getPosition().y + mouthLeft.getPosition().y)/2;
        return smilingProbability > 0.5;
    }


    /**
     * Finds faces in an image and uses the given callback to return an Expression
     * regarding that face
     *
     * @param expressionCallback The callback to call when done
     * @param imageProxy The image proxy
     */
    public static void detect(ExpressionCallback expressionCallback, ImageProxy imageProxy) {
        @SuppressLint("UnsafeExperimentalUsageError") Image mediaImage = imageProxy.getImage();
        InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

        // Process da image
        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // We need this for getLeftEyeOpenProbability etc
                        .build();
        FaceDetector detector = FaceDetection.getClient(options);


        //Listeners are standard async, so maybe those are as well.
        detector.process(image)
                .addOnSuccessListener(
                        new OnSuccessListener<List<Face>>() {
                            @Override
                            public void onSuccess(List<Face> faces) {
                                if (faces != null && faces.size() != 0) {
                                    Face face = faces.get(0);

                                    Expression expression = new Expression(face.getLeftEyeOpenProbability(),
                                            face.getRightEyeOpenProbability(),
                                            face.getHeadEulerAngleX(),
                                            face.getHeadEulerAngleY(),
                                            face.getHeadEulerAngleZ(),
                                            face.getLandmark(FaceLandmark.MOUTH_BOTTOM), // Should we change to just send the PointF and not the entire landmark?
                                            face.getLandmark(FaceLandmark.MOUTH_RIGHT),
                                            face.getLandmark(FaceLandmark.MOUTH_LEFT),
                                            face.getSmilingProbability());
                                    expressionCallback.handleExpression(expression);
                                }
                                imageProxy.close();
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                e.printStackTrace();
                                Log.d("EYE", "onFailure: NOT SUCCESS!" + e.getMessage());
                                // Maybe close imageProxy here as well?
                            }
                        });
    }
}
