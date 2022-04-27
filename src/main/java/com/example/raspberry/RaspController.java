package com.example.raspberry;

import static com.example.raspberry.RaspOpenCV.FACE_DETECTION_FILE;
import static uk.co.caprica.picam.CameraConfiguration.cameraConfiguration;

import com.diozero.api.DigitalInputDevice;
import com.diozero.api.DigitalOutputDevice;
import com.diozero.api.GpioPullUpDown;
import com.diozero.devices.LED;
import com.diozero.devices.PCA9685;
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

    private DigitalOutputDevice trig = null;
    private DigitalInputDevice echo = null;
    private Thread ultrasonicRunThread = null;

    private boolean isInitOpenCV = false;
    private Thread openCVTread = null;

    @GetMapping("/led/{id}")
    public String led(@PathVariable int id) {
        interruptBlink();
        switch (id) {
            case 0:
                getLed().off();
                break;
            case 1:
                getLed().on();
                break;
            case 2:
                blink();
                break;
            case 3:
                servo();
                break;
            default:
                break;
        }
        return "led: " + id;
    }

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
            initL2C().setValue(RaspOpenCV.X_SERVO_CAMERA, (float) angle / 1000.0f);
        } else if ("y".equalsIgnoreCase(axis)) {
            initL2C().setValue(RaspOpenCV.Y_SERVO_CAMERA, (float) angle / 1000.0f);
        }

        getLed().on();
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
            getLed().off();
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
        initUltra();
        initMotor();
        if (on == 1) {
            blinkSlowlyRun();
            UltrasonicRun ultrasonic = new UltrasonicRun(trig, echo, motor);
            ultrasonicRunThread = new Thread(ultrasonic);
            ultrasonicRunThread.start();
        } else {
            blinkSlowlyStop();
            stopUltra();
        }
        return "ultrasonic: " + on;
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
        return "OpenCV : " + on;
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

    private void stopUltra() {
        if (ultrasonicRunThread != null ) {
            ultrasonicRunThread.interrupt();
            ultrasonicRunThread = null;
        }
    }

    private void initUltra() {
        if (trig == null || echo == null) {
            trig = new DigitalOutputDevice(16);
            echo = DigitalInputDevice.Builder.builder(26)
                    .setActiveHigh(true)
                    .setPullUpDown(GpioPullUpDown.PULL_DOWN)
                    .build();
        }
    }

    private void initMotor() {
        if (motor == null) {
            motor = new Motor(applicationConfig);
            motor.init();
        }
    }

    private ResponseEntity<byte[]> getResponseException(String message) {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noCache().getHeaderValue());
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.setContentLength(message.getBytes().length);
        return new ResponseEntity<>(message.getBytes(), headers, HttpStatus.OK);
    }

    private void blink() {
        BlinkRun blinkRun = new BlinkRun(getLed());
        blinkThread = new Thread(blinkRun);
        blinkThread.start();
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

    private LED getLed() {
        if (led == null) {
            led = new LED(22);
        }
        return led;
    }

    private void interruptBlink() {
        if (blinkThread != null) {
            blinkThread.interrupt();
            blinkThread = null;
            logger.info("Interrupt blink");
        }
    }

}