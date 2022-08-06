package com.example.raspberry;

import com.diozero.devices.PCA9685;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.SystemUtils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.caprica.picam.ByteArrayPictureCaptureHandler;
import uk.co.caprica.picam.Camera;
import uk.co.caprica.picam.CaptureFailedException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.example.raspberry.RaspConstant.LED_DETECT;
import static com.example.raspberry.RaspConstant.LED_NO_DETECT;

public class RaspUtils {

    private final static Logger logger = LoggerFactory.getLogger(RaspUtils.class);

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

    public static Optional<Rect> getFaceRectMax(Rect[] rects) {
        return Arrays.stream(rects).max(Comparator.comparing(Rect::area));
    }

    public static File getFileByType(File[] files, String type) {
        List<File> faceFiles = Arrays.stream(files)
                .filter(f -> type.equalsIgnoreCase(FilenameUtils.getExtension(f.getName()))).collect(Collectors.toList());
        if (!faceFiles.isEmpty()) {
            return faceFiles.get(0);
        }
        return null;
    }

    public static File getFileByType(File[] files, String fileName, String type) {
        List<File> faceFiles = Arrays.stream(files)
                .filter(f -> type.equalsIgnoreCase(FilenameUtils.getExtension(f.getName()))
                        && FilenameUtils.removeExtension(fileName).equalsIgnoreCase(FilenameUtils.removeExtension(f.getName()))
                ).collect(Collectors.toList());
        if (!faceFiles.isEmpty()) {
            return faceFiles.get(0);
        }
        return null;
    }

    public static List<File> getFilesByType(File[] files, String type) {
        return Arrays.stream(files)
                .filter(f -> type.equalsIgnoreCase(FilenameUtils.getExtension(f.getName())))
                .collect(Collectors.toList());
    }

    public static void playShell(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return;
        }
        String cmd;
        if (SystemUtils.IS_OS_WINDOWS) {
            //
            cmd = String.format("powershell -c (New-Object Media.SoundPlayer \"%s\").PlaySync()", path);
        } else {
            cmd =  "aplay " + path;
        }
        try {
            Runtime.getRuntime().exec(cmd);
        } catch (IOException e) {
            logger.error("error play", e);
        }
    }

}
