package com.example.raspberry;

import static uk.co.caprica.picam.CameraConfiguration.cameraConfiguration;

import com.diozero.api.DigitalInputDevice;
import com.diozero.api.DigitalOutputDevice;
import com.diozero.api.GpioPullUpDown;
import com.diozero.devices.LED;
import com.diozero.devices.PCA9685;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_java;
import org.opencv.core.Core;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import uk.co.caprica.picam.*;
import uk.co.caprica.picam.enums.AutomaticWhiteBalanceMode;
import uk.co.caprica.picam.enums.Encoding;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@RestController
public class RaspController {

    private final Logger logger = LoggerFactory.getLogger(RaspController.class);

    @Autowired
    private ApplicationConfig applicationConfig;
    //
    private Thread blinkThread = null;
    private Thread servoThread = null;
    private PCA9685 l2cBoard = null; // L2C
    private LED led = null;
    private boolean isNotInstalledCamLibrary = true;
    private CameraConfiguration cameraConfiguration;
    private Camera camera;

    private Motor motor = null;

    private BlinkSlowlyRun blinkSlowlyRun = null;
    private Thread blinkSlowlyRunThread = null;

    private DigitalOutputDevice ultrasonicTrigger = null;
    private DigitalInputDevice ultrasonicEcho = null;
    private Thread ultrasonicRunThread = null;

    private DigitalInputDevice infraredInputRight = null;
    private DigitalInputDevice infraredInputLeft = null;
    private Thread infraredRunThread = null;

    private boolean isInitOpenCV = false;
    private Thread openCVTread = null;
    private Thread recognizeTread = null;

    @RequestMapping(value = "/cam/{axis}/{angle}", method = RequestMethod.GET)
    public ResponseEntity<byte[]> getImageAsResponseEntity(@PathVariable String axis, @PathVariable int angle) {
        try {
            initPiCamera();
        } catch (CameraException e) {
            logger.error("Error installTempLibrary ", e);
            return getResponseException(e.getMessage());
        }

        interruptBlink();
        if ("x".equalsIgnoreCase(axis)) {
            initL2C().setValue(RaspConstant.X_SERVO_CAMERA, (float) angle / 1000.0f);
        } else if ("y".equalsIgnoreCase(axis)) {
            initL2C().setValue(RaspConstant.Y_SERVO_CAMERA, (float) angle / 1000.0f);
        }

        camera.open();
        try {
            final ByteArrayPictureCaptureHandler pictureCaptureHandler = new ByteArrayPictureCaptureHandler();
            camera.takePicture(pictureCaptureHandler, 500);
            final byte[] media = pictureCaptureHandler.result();
            //
            HttpHeaders headers = new HttpHeaders();
            headers.setCacheControl(CacheControl.noCache().getHeaderValue());
            headers.setContentType(MediaType.IMAGE_JPEG);
            headers.setContentLength(media.length);
            ResponseEntity<byte[]> responseEntity = new ResponseEntity<>(media, headers, HttpStatus.OK);
            return responseEntity;
        } catch (CaptureFailedException e) {
            logger.error("Error takePicture ", e);
            return getResponseException(e.getMessage());
        } finally {
            camera.close();
        }
    }

    @RequestMapping(value = "/face/{filename}", method = RequestMethod.GET)
    public ResponseEntity<byte[]> getDetectionFace(@PathVariable String filename) {
        File file = new File(applicationConfig.getHome() + filename);
        try {
            byte[] fileContent = Files.readAllBytes(file.toPath());
            HttpHeaders headers = new HttpHeaders();
            headers.setCacheControl(CacheControl.noCache().getHeaderValue());
            headers.setContentType(MediaType.IMAGE_JPEG);
            headers.setContentLength(fileContent.length);
            ResponseEntity<byte[]> responseEntity = new ResponseEntity<>(fileContent, headers, HttpStatus.OK);
            return responseEntity;
        } catch (IOException e) {
            return getResponseException(e.getMessage());
        }
    }

    @RequestMapping(value = "/motor/{direct}", method = RequestMethod.GET)
    public String motorControl(@PathVariable String direct) {
        initMotor();
        blinkSlowlyRun();
        switch (direct) {
            case "f":
                motor.forward();
                break;
            case "b":
                motor.backward();
                break;
            case "s":
                blinkSlowlyStop();
                motor.stop();
                break;
            case "l":
                motor.left();
                break;
            case "r":
                motor.right();
                break;
        }
        return "motor: " + direct;
    }

    @RequestMapping(value = "/ultra/{on}", method = RequestMethod.GET)
    public String ultrasonicControl(@PathVariable int on) {
        initUltrasonic();
        initMotor();
        if (on == 1) {
            blinkSlowlyRun();
            UltrasonicRun ultrasonic = new UltrasonicRun(new Ultrasonic(ultrasonicTrigger, ultrasonicEcho), motor);
            ultrasonicRunThread = new Thread(ultrasonic);
            ultrasonicRunThread.start();
        } else {
            blinkSlowlyStop();
            stopUltrasonic();
            stopMotor();
        }
        return "ultrasonic: " + on;
    }

    @RequestMapping(value = "/infra/{on}", method = RequestMethod.GET)
    public String infraredControl(@PathVariable int on) {
        initInfraredInput();
        initUltrasonic();
        initMotor();
        if (on == 1) {
            InfraredRun infraredRun = new InfraredRun(infraredInputRight, infraredInputLeft,
                    new Ultrasonic(ultrasonicTrigger, ultrasonicEcho), motor);
            infraredRunThread  = new Thread(infraredRun);
            infraredRunThread.start();
        } else {
            stopInfrared();
            stopMotor();
        }
        return "infrared: " + on;
    }

    @RequestMapping(value = "/cv/{on}", method = RequestMethod.GET)
    public String openCvControl(@PathVariable int on) {
        if (!isInitOpenCV) {
            //OpenCV.loadShared();
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            isInitOpenCV = true;
        }
        try {
            initPiCamera();
        } catch (Exception e) {
            logger.error(e.getMessage());
            return e.getMessage();
        }
        initMotor();
        //
        if (on == 1) {
            RaspOpenCV raspOpenCV = new RaspOpenCV(applicationConfig, initL2C(), camera, motor);
            openCVTread = new Thread(raspOpenCV);
            openCVTread.start();
        } else if (openCVTread != null){
            openCVTread.interrupt();
            openCVTread = null;
        }
        return "OpenCV: " + on;
    }

    @RequestMapping(value = "/train/{on}", method = RequestMethod.GET)
    public String trainControl(@PathVariable int on) {
        if (!isInitOpenCV) {
            //OpenCV.loadShared();
            Loader.load(opencv_java.class);
//            WorkspaceConfiguration mmap = WorkspaceConfiguration.builder()
//                    .tempFilePath(applicationConfig.getHome() + "tempFile")
//                    .initialSize(2_000_000_000)
//                    .policyLocation(LocationPolicy.MMAP)
//                    .policyLearning(LearningPolicy.NONE)
//                    .build();
//            Nd4j.getWorkspaceManager().createNewWorkspace(mmap);

            //System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            isInitOpenCV = true;
        }
        try {
            initPiCamera();
        } catch (Exception e) {
            logger.error(e.getMessage());
            return e.getMessage();
        }
        initMotor();
        //
        if (on == 1) {
            RaspRecognize raspRecognize = new RaspRecognize(applicationConfig, initL2C(), camera, motor);
            recognizeTread = new Thread(raspRecognize);
            recognizeTread.start();
        } else if (recognizeTread != null){
            recognizeTread.interrupt();
            recognizeTread = null;
        }
        return "recognize : " + on;
    }

    @GetMapping("/info")
    public String info() {
        final String info = String.format("motorSpeed=%s, home=%s", applicationConfig.getMotorSpeed(), applicationConfig.getHome());
        logger.info(info);
        return info;
    }

    private void initPiCamera() throws CameraException {
        if (isNotInstalledCamLibrary) {
            try {
                //installTempLibrary();
                System.load("/home/pi/java/picam-2.0.1.so");
                isNotInstalledCamLibrary = false;
                //
                cameraConfiguration = cameraConfiguration()
                        .width(1280)   //1920
                        .height(1024)  //1080
                        .automaticWhiteBalance(AutomaticWhiteBalanceMode.AUTO)
                        .encoding(Encoding.JPEG)
                        .quality(85);
                camera = new Camera(cameraConfiguration);
            } catch (CameraException e) {
                logger.error("Error installTempLibrary ", e);
                throw e;
            }
        }
    }

    private void stopUltrasonic() {
        if (ultrasonicRunThread != null ) {
            ultrasonicRunThread.interrupt();
            ultrasonicRunThread = null;
        }
    }

    private void initUltrasonic() {
        if (ultrasonicTrigger == null || ultrasonicEcho == null) {
            ultrasonicTrigger = new DigitalOutputDevice(16);
            ultrasonicEcho = DigitalInputDevice.Builder.builder(26)
                    //.setActiveHigh(true)
                    .setPullUpDown(GpioPullUpDown.PULL_DOWN)
                    .build();
        }
    }

    private void initInfraredInput() {
        if (infraredInputRight == null || infraredInputLeft == null) {
            infraredInputRight = DigitalInputDevice.Builder.builder(27)
                    .setActiveHigh(true)
                    .build();
            infraredInputLeft = DigitalInputDevice.Builder.builder(22)
                    .setActiveHigh(true)
                    .build();
        }
    }

    private void stopInfrared() {
        if (infraredRunThread != null) {
            infraredRunThread.interrupt();
            infraredRunThread = null;
        }
    }

    private void initMotor() {
        if (motor == null) {
            motor = new Motor(applicationConfig);
            motor.init();
        }
    }

    private void stopMotor() {
        if (motor != null) {
            motor.stop();
        }
    }

    private ResponseEntity<byte[]> getResponseException(String message) {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noCache().getHeaderValue());
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.setContentLength(message.getBytes().length);
        return new ResponseEntity<>(message.getBytes(), headers, HttpStatus.OK);
    }

    private PCA9685 initL2C() {
        if (l2cBoard == null) {
            l2cBoard = new PCA9685(60);
        }
        return l2cBoard;
    }

    private void servo() {
        final ServoRun servoRun = new ServoRun(initL2C());
        if (servoThread != null) {
            servoThread.interrupt();
        }
        servoThread = new Thread(servoRun);
        servoThread.start();
    }

    private void blinkSlowlyRun() {
        final BlinkSlowlyRun blinkSlowlyRun = new BlinkSlowlyRun(initL2C());
        if (blinkSlowlyRunThread != null) {
            blinkSlowlyRunThread.interrupt();
        }
        blinkSlowlyRunThread = new Thread(blinkSlowlyRun);
        blinkSlowlyRunThread.start();
    }

    private void blinkSlowlyStop() {
        if (blinkSlowlyRunThread != null) {
            blinkSlowlyRunThread.interrupt();
            blinkSlowlyRunThread = null;
        }
    }

    private void interruptBlink() {
        if (blinkThread != null) {
            blinkThread.interrupt();
            blinkThread = null;
            logger.info("Interrupt blink");
        }
    }

}