package com.example.raspberry;

import com.diozero.devices.PCA9685;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import uk.co.caprica.picam.ByteArrayPictureCaptureHandler;
import uk.co.caprica.picam.Camera;
import uk.co.caprica.picam.CaptureFailedException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

import static com.example.raspberry.RaspConstant.LED_DETECT;
import static com.example.raspberry.RaspConstant.LED_NO_DETECT;

public class RaspUtils {

    private final PCA9685 l2cBoard;
    private final Camera camera;
    private final ApplicationConfig applicationConfig;

    public RaspUtils(PCA9685 l2cBoard, Camera camera, ApplicationConfig applicationConfig) {
        this.l2cBoard = l2cBoard;
        this.camera = camera;
        this.applicationConfig = applicationConfig;
    }

    public void setLedDetect(boolean isDetect) {
        if (isDetect) {
            l2cBoard.setValue(LED_DETECT, 0.9f);
            l2cBoard.setValue(LED_NO_DETECT, 0f);
        } else {
            l2cBoard.setValue(LED_DETECT, 0f);
            l2cBoard.setValue(LED_NO_DETECT, 0.9f);
        }
    }

    public Mat takePictureGray() throws CaptureFailedException {
        final ByteArrayPictureCaptureHandler pictureCaptureHandler = new ByteArrayPictureCaptureHandler();
        camera.takePicture(pictureCaptureHandler, applicationConfig.getPictureCaptureDelay());
        final byte[] media = pictureCaptureHandler.result();
        Mat matrix = Imgcodecs.imdecode(new MatOfByte(media), Imgcodecs.IMREAD_UNCHANGED);
        Mat matrixGray = new Mat();
        Imgproc.cvtColor(matrix, matrixGray, Imgproc.COLOR_BGR2GRAY);
        return matrixGray;
    }

    public Mat takePicture() throws CaptureFailedException {
        final ByteArrayPictureCaptureHandler pictureCaptureHandler = new ByteArrayPictureCaptureHandler();
        camera.takePicture(pictureCaptureHandler, applicationConfig.getPictureCaptureDelay());
        final byte[] media = pictureCaptureHandler.result();
        return Imgcodecs.imdecode(new MatOfByte(media), Imgcodecs.IMREAD_UNCHANGED);
    }

    public Optional<Rect> getFaceRectMax(Rect[] rects) {
        return Arrays.stream(rects).max(Comparator.comparing(Rect::area));
    }


}
