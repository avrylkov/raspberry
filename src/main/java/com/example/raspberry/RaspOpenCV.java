package com.example.raspberry;

import com.diozero.devices.PCA9685;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.caprica.picam.Camera;
import uk.co.caprica.picam.CaptureFailedException;
import java.util.Optional;

import static com.example.raspberry.RaspConstant.*;
import static org.opencv.imgproc.Imgproc.*;

public class RaspOpenCV implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(RaspOpenCV.class);

    private final ApplicationConfig applicationConfig;
    private final PCA9685 l2cBoard;
    private final Camera camera;
    private final Motor motor;
    private final RaspUtils raspUtils;
    private final RaspMovement raspMovement;

    public RaspOpenCV(ApplicationConfig applicationConfig, PCA9685 l2cBoard, Camera camera, Motor motor) {
        this.applicationConfig = applicationConfig;
        this.l2cBoard = l2cBoard;
        this.camera = camera;
        this.motor = motor;
        raspUtils = new RaspUtils(l2cBoard, camera, applicationConfig);
        raspMovement = new RaspMovement(applicationConfig, l2cBoard, motor);
    }

    @Override
    public void run() {
        logger.info("Running DetectFaceDemo");
        CascadeClassifier faceDetector = new CascadeClassifier();
        if (!faceDetector.load(applicationConfig.getHome() + HAAR_CASCADE_XML)) {
            logger.error("Не удалось загрузить cascade frontal face xml");
            return;
        }

        l2cBoard.setValue(X_SERVO_CAMERA, raspMovement.getX_servo_current());
        l2cBoard.setValue(Y_SERVO_CAMERA, raspMovement.getY_servo_current());
        camera.open();

        while (true) {
            try {
                Thread.sleep(50);
                Mat matrixGray = raspUtils.takePictureGray();
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
                raspUtils.setLedDetect(isFaceDetect);
                // Draw a bounding box around each face.
                final Optional<Rect> faceRectMax = raspUtils.getFaceRectMax(faceDetections.toArray());
                if (faceRectMax.isPresent()) {
                    Rect rect = faceRectMax.get();
                    if (needNearer(rect)) {
                        continue;
                    }
                    if (needAway(rect)) {
                        continue;
                    }
                    direction = raspMovement.moveCenterFace(rect);
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
                //
                if (applicationConfig.isSavePicture() && isFaceDetect && !CENTER.equalsIgnoreCase(direction)) {
                    Mat matrixGray2 = raspUtils.takePictureGray();
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
        logger.info(String.format("stop recognize"));
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

}
