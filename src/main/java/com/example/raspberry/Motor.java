package com.example.raspberry;

import com.diozero.api.DigitalOutputDevice;
import com.diozero.api.PwmOutputDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class Motor {

    private final Logger logger = LoggerFactory.getLogger(Motor.class);

    private static L298NMotor motorLeft = null;
    private static L298NMotor motorRight = null;

    private final ApplicationConfig applicationConfig;

    public Motor(ApplicationConfig applicationConfig) {
        this.applicationConfig = applicationConfig;
    }

    public void init() {
        if (motorLeft == null) {
            DigitalOutputDevice motorForwardControlPin = new DigitalOutputDevice(23, true, false);
            DigitalOutputDevice motorBackwardControlPin = new DigitalOutputDevice(24, true, false);
            PwmOutputDevice motorPwmControl = new PwmOutputDevice(25, applicationConfig.getPwmFrequency(), 0.0f);
            motorLeft = new L298NMotor(motorForwardControlPin, motorBackwardControlPin, motorPwmControl);
        }

        if (motorRight == null) {
            DigitalOutputDevice motorForwardControlPin = new DigitalOutputDevice(5, true, false);
            DigitalOutputDevice motorBackwardControlPin = new DigitalOutputDevice(6, true, false);
            PwmOutputDevice motorPwmControl = new PwmOutputDevice(17, applicationConfig.getPwmFrequency(), 0.0f);
            motorRight = new L298NMotor(motorForwardControlPin, motorBackwardControlPin, motorPwmControl);
        }
    }

    public void forward() {
        Optional.ofNullable(motorLeft).ifPresent(m -> m.forward(applicationConfig.getMotorSpeed()));
        Optional.ofNullable(motorRight).ifPresent(m -> m.forward(applicationConfig.getMotorSpeed()));
    }

    public void forward(float speed) {
        Optional.ofNullable(motorLeft).ifPresent(m -> m.forward(speed));
        Optional.ofNullable(motorRight).ifPresent(m -> m.forward(speed));
    }

    public void forward(int delay) {
        forward();
        while (delay > 0) {
            delay--;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                logger.error("motor forward error", e);
            }
        }
        stop();
    }

    public void backward(int delay) {
        backward();
        while (delay > 0) {
            delay--;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                logger.error("motor backward error", e);
            }
        }
        stop();
    }

    public void backward() {
        Optional.ofNullable(motorLeft).ifPresent(m -> m.backward(applicationConfig.getMotorSpeed()));
        Optional.ofNullable(motorRight).ifPresent(m -> m.backward(applicationConfig.getMotorSpeed()));
    }

    public void stop() {
        Optional.ofNullable(motorLeft).ifPresent(m -> m.stop());
        Optional.ofNullable(motorRight).ifPresent(m -> m.stop());
    }

    public void left() {
        Optional.ofNullable(motorLeft).ifPresent(m -> m.forward(applicationConfig.getMotorSpeed()));
        Optional.ofNullable(motorRight).ifPresent(m -> m.backward(applicationConfig.getMotorSpeed()));
    }

    public void left(int delay) {
        left();
        while (delay > 0) {
            delay--;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                logger.error("motor left error", e);
            }
        }
        stop();
    }

    public void right() {
        Optional.ofNullable(motorRight).ifPresent(m -> m.forward(applicationConfig.getMotorSpeed()));
        Optional.ofNullable(motorLeft).ifPresent(m -> m.backward(applicationConfig.getMotorSpeed()));
    }

    public void right(int delay) {
        right();
        while (delay > 0) {
            delay--;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                logger.error("motor left error", e);
            }
        }
        stop();
    }

}
