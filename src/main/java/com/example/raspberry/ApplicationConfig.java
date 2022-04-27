package com.example.raspberry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ApplicationConfig {

    @Value("${motor.speed}")
    public float motorSpeed;
    @Value("${myhome.path}")
    public String home;
    @Value("${save.picture}")
    public boolean savePicture;
    @Value("${picture.capture.delay}")
    public int pictureCaptureDelay;

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
}
