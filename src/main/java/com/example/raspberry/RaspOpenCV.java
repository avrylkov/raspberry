package com.example.raspberry;

import com.diozero.devices.PCA9685;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.caprica.picam.ByteArrayPictureCaptureHandler;
import uk.co.caprica.picam.Camera;
import uk.co.caprica.picam.CaptureFailedException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.opencv.imgproc.Imgproc.*;

public class RaspOpenCV implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(RaspOpenCV.class);

    private final ApplicationConfig applicationConfig;
    private final PCA9685 l2cBoard;
    private final Camera camera;
    private final Motor motor;

    public RaspOpenCV(ApplicationConfig applicationConfig, PCA9685 l2cBoard, Camera camera, Motor motor) {
        this.applicationConfig = applicationConfig;
        this.l2cBoard = l2cBoard;
        this.camera = camera;
        this.motor = motor;
    }

    public static final String FACE_DETECTION_FILE = "detect.jpg";
    public static final String FACE_NO_DETECTION_FILE = "no-detect.jpg";
    public static final String FACE_DETECTION_FILE_2 = "detect-2.jpg";

    private static final int W = 1280;
    private static final int H = 1024;
    private static final int W_CENTER = W/2;
    private static final int H_CENTER = H/2;

    public static final int LED_DETECT = 0;
    public static final int LED_NO_DETECT = 1;
    public static final int Y_SERVO_CAMERA = 2;
    public static final int X_SERVO_CAMERA = 3;
    // X
    private static final float X_SERVO_MIN = 0.1f; //0.08f
    private static final float X_SERVO_MAX = 0.134f; // 0.15f
    private static final float X_SERVO_MIDDLE_START = 0.117f;
    // Y
    private static final float Y_SERVO_MIN = 0.029f;
    private static final float Y_SERVO_MAX = 0.05f;
    private static final float Y_SERVO_MIDDLE_START = 0.035f;

    private static final float FULL_X = 0.04f;
    private static final float FULL_Y = 0.021f;
    private static final String CENTER = "Center";
    private static final String UP = "Up";
    private static final String DOWN = "Down";

    private float x_servo_current = X_SERVO_MIDDLE_START;
    private float y_servo_current = Y_SERVO_MIDDLE_START;



    @Override
    public void run() {
        logger.info("Running DetectFaceDemo");
        CascadeClassifier faceDetector = new CascadeClassifier();
        if (!faceDetector.load(applicationConfig.getHome() + "haarcascade_frontalface_alt.xml")) {
            logger.error("Не удалось загрузить cascade frontal face xml");
            return;
        }

        l2cBoard.setValue(X_SERVO_CAMERA, x_servo_current);
        l2cBoard.setValue(Y_SERVO_CAMERA, y_servo_current);
        camera.open();

        while (true) {
            try {
                Thread.sleep(50);
                Mat matrixGray = takePicture();
                //
                MatOfRect faceDetections = new MatOfRect();
                faceDetector.detectMultiScale(matrixGray, faceDetections, 1.3);
                logger.info(String.format("detected %s faces", faceDetections.toArray().length));
                String direction = "";
                final boolean isFaceDetect = faceDetections.toArray().length > 0;
                if (applicationConfig.isSavePictureNoDetect() && !isFaceDetect) {
                    String filename = applicationConfig.getHome() + FACE_NO_DETECTION_FILE;
                    Imgcodecs.imwrite(filename, matrixGray);
                }
                setLedDetect(isFaceDetect);
                // Draw a bounding box around each face.
                final Optional<Rect> faceRectMax = getFaceRectMax(faceDetections.toArray());
                if (faceRectMax.isPresent()) {
                    Rect rect = faceRectMax.get();
                    if (needNearer(rect)) {
                        continue;
                    }
                    if (needAway(rect)) {
                        continue;
                    }
                    direction = moveCenterFace(rect);
                    if (applicationConfig.isSavePicture()) {
                        Imgproc.rectangle(matrixGray, new Point(rect.x, rect.y),
                                new Point(rect.x + rect.width, rect.y + rect.height), new Scalar(255, 255, 255));
                        // Save the visualized
                        String filename = applicationConfig.getHome() + FACE_DETECTION_FILE;
                        Imgproc.putText(matrixGray, direction, new Point(rect.x + 3, rect.y + 10),
                                FONT_HERSHEY_PLAIN, 2, new Scalar(255, 255, 255));
                        Imgcodecs.imwrite(filename, matrixGray);
                    }
                }
                ;
                //
                if (applicationConfig.isSavePicture() && isFaceDetect && !CENTER.equalsIgnoreCase(direction)) {
                    Mat matrixGray2 = takePicture();
                    //
                    logger.info(String.format("capture after centerFace"));
                    String filename2 = applicationConfig.getHome() + FACE_DETECTION_FILE_2;
                    Imgcodecs.imwrite(filename2, matrixGray2);
                    if (applicationConfig.isStopAfterSave()) {
                        break;
                    }
                }
            } catch (InterruptedException | CaptureFailedException e) {
                logger.info(String.format("Face detections Interrupted %s", e.getMessage()));
                break;
            }
        }
        logger.info(String.format("stop detect"));
        camera.close();
        motor.stop();
    }

    private boolean needNearer(Rect rect) throws InterruptedException {
      double area = (rect.area() / (W * H));
      boolean nearer = area < applicationConfig.getNearerAreaPercent();
      if (nearer) {
          logger.info(String.format("move nearer " + area));
          motor.forward(applicationConfig.getMoveDelay());
      }
      return nearer;
    }

    private boolean needAway(Rect rect) throws InterruptedException {
        double area = (rect.area() / (W * H));
        boolean away = area > applicationConfig.getAwayAreaPercent();
        if (away) {
            logger.info(String.format("move away " + area));
            motor.backward(applicationConfig.getMoveDelay());
        }
        return away;
    }

    private String moveCenterFace(Rect rect) throws InterruptedException {
        if (inCenter(rect)) {
            logger.info("IN CENTER");
            return CENTER;
        }
        logger.info(String.format("move centerFace"));
        //
        String result = null;
        if (centerFaceX(rect) < centerFaceLeft(rect)) {
            turnRight(rect);
            result = "Right";
        } else if (centerFaceX(rect) > centerFaceRight(rect)) {
            turnLeft(rect);
            result = "Left";
        }
        //
        if (centerFaceY(rect) > centerFaceDown(rect)) {
            turnDown(rect);
            result = Optional.ofNullable(result).map(s -> s += DOWN).orElse(DOWN);
        } else if (centerFaceY(rect) < centerFaceUp(rect)) {
            turnUp(rect);
            result = Optional.ofNullable(result).map(s -> s += UP).orElse(UP);
        }
        return result;
    }

    private boolean inCenter(Rect rect) {
        return inCenterX(centerFaceX(rect), rect)
               && inCenterY(centerFaceY(rect), rect)
               ;
    }

    private int centerFaceX(Rect rect) {
        return rect.x + rect.width / 2;
    }

    private int centerFaceY(Rect rect) {
        return rect.y + rect.height / 2;
    }

    private int centerFaceLeft(Rect rect) {
        return W_CENTER - rect.width / 2;
    }

    private int centerFaceRight(Rect rect) {
        return W_CENTER + rect.width / 2;
    }

    private int centerFaceUp(Rect rect) {
        return H_CENTER - rect.height / 2;
    }

    private int centerFaceDown(Rect rect) {
        return H_CENTER + rect.height / 2;
    }

    private boolean inCenterX(int centerFaceX, Rect rect) {
        return (centerFaceX >= (W_CENTER - rect.width / 2)) && (centerFaceX <= (W_CENTER + rect.width / 2));
    }

    private boolean inCenterY(int centerFaceY, Rect rect) {
        return (centerFaceY >= (H_CENTER - rect.height / 2)) && (centerFaceY <= (H_CENTER + rect.height / 2));
    }

    private void turnLeft(Rect rect) throws InterruptedException {
        if (x_servo_current >= X_SERVO_MIN) {
            x_servo_current -= servoStepX(rect);
            logger.info("LEFT");
        } else {
            logger.info(String.format("LEFT-camera-limit %s, motor turn", x_servo_current));
            motor.right(applicationConfig.getTurnDelay());
        }
        l2cBoard.setValue(X_SERVO_CAMERA, x_servo_current);
    }

    private void turnRight(Rect rect) throws InterruptedException {
        if (x_servo_current <= X_SERVO_MAX) {
            x_servo_current += servoStepX(rect);
            logger.info("RIGHT");
        } else {
            logger.info(String.format("RIGHT-camera-limit %s, motor turn", x_servo_current));
            motor.left(applicationConfig.getTurnDelay());
        }
        l2cBoard.setValue(X_SERVO_CAMERA, x_servo_current);
    }
    //
    private void turnDown(Rect rect) {
        if (y_servo_current >= Y_SERVO_MIN) {
            y_servo_current -= servoStepY(rect);
            logger.info("DOWN");
        } else {
            logger.info("DOWN - limit " + y_servo_current);
        }
        l2cBoard.setValue(Y_SERVO_CAMERA, y_servo_current);
    }

    private void turnUp(Rect rect) {
        if (y_servo_current <= Y_SERVO_MAX) {
            y_servo_current += servoStepY(rect);
            logger.info("UP");
        } else {
            logger.info("UP-limit " + y_servo_current);
        }
        l2cBoard.setValue(Y_SERVO_CAMERA, y_servo_current);
    }

    private float servoStepX(Rect rect) {
        int faceX = centerFaceX(rect);
        int delta = Math.abs(W / 2 - faceX);
        return delta * FULL_X / W;
    }

    private float servoStepY(Rect rect) {
        int faceY = centerFaceY(rect);
        int delta = Math.abs(H / 2 - faceY);
        return delta * FULL_Y / H;
    }

    private Optional<Rect> getFaceRectMax(Rect[] rects) {
        return Arrays.stream(rects).max(Comparator.comparing(Rect::area));
    }

    private void setLedDetect(boolean isDetect) {
        if (isDetect) {
            l2cBoard.setValue(LED_DETECT, 0.9f);
            l2cBoard.setValue(LED_NO_DETECT, 0f);
        } else {
            l2cBoard.setValue(LED_DETECT, 0f);
            l2cBoard.setValue(LED_NO_DETECT, 0.9f);
        }
    }

    private Mat takePicture() throws CaptureFailedException {
        final ByteArrayPictureCaptureHandler pictureCaptureHandler = new ByteArrayPictureCaptureHandler();
        camera.takePicture(pictureCaptureHandler, applicationConfig.getPictureCaptureDelay());
        final byte[] media = pictureCaptureHandler.result();
        Mat matrix = Imgcodecs.imdecode(new MatOfByte(media), Imgcodecs.IMREAD_UNCHANGED);
        Mat matrixGray = new Mat();
        Imgproc.cvtColor(matrix, matrixGray, Imgproc.COLOR_BGR2GRAY);
        return matrixGray;
    }

}
