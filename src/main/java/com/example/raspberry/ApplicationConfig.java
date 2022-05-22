package com.example.raspberry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ApplicationConfig {

    @Value("${motor.speed}")
    private float motorSpeed;
    @Value("${nearer.area}")
    private double nearerAreaPercent;
    @Value("${away.area}")
    private double awayAreaPercent;
    @Value("${myhome.path}")
    private String home;
    @Value("${save.picture}")
    private boolean savePicture;
    @Value("${save.picture.no.detect}")
    private boolean savePictureNoDetect;
    @Value("${stop.after.save}")
    private boolean stopAfterSave;
    @Value("${picture.capture.delay}")
    private int pictureCaptureDelay;
    @Value("${move.delay}")
    private int moveDelay;
    @Value("${turn.delay}")
    private int turnDelay;
    @Value("${pwm.frequency}")
    private int pwmFrequency;
    @Value("${motor.turn}")
    private boolean motorTurn;
    @Value("${train.image.path}")
    private String trainImagePath;
    @Value("${train.voice.path}")
    private String trainVoicePath;
    @Value("${threshold.distance}")
    private double thresholdDistance;

    public float getMotorSpeed() {
        return motorSpeed;
    }

    public String getHome() {
        return home;
    }

    public boolean isSavePicture() {
        return savePicture;
    }

    public int getPictureCaptureDelay() {
        return pictureCaptureDelay;
    }

    public boolean isSavePictureNoDetect() {
        return savePictureNoDetect;
    }

    public boolean isStopAfterSave() {
        return stopAfterSave;
    }

    public double getNearerAreaPercent() {
        return nearerAreaPercent;
    }

    public double getAwayAreaPercent() {
        return awayAreaPercent;
    }

    public int getMoveDelay() {
        return moveDelay;
    }

    public int getTurnDelay() {
        return turnDelay;
    }

    public int getPwmFrequency() {
        return pwmFrequency;
    }

    public boolean isMotorTurn() {
        return motorTurn;
    }

    public String getTrainImagePath() {
        return trainImagePath;
    }

    public String getTrainVoicePath() {
        return trainVoicePath;
    }

    public double getThresholdDistance() {
        return thresholdDistance;
    }
}
