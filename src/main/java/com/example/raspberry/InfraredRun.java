package com.example.raspberry;

import com.diozero.api.DigitalInputDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InfraredRun implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(InfraredRun.class);

    private final DigitalInputDevice infraredInputRight;
    private final DigitalInputDevice infraredInputLeft;
    private final Ultrasonic ultrasonic;
    private final Motor motor;

    public InfraredRun(DigitalInputDevice infraredInputRight,
                       DigitalInputDevice infraredInputLeft, Ultrasonic ultrasonic, Motor motor) {
        this.infraredInputRight = infraredInputRight;
        this.infraredInputLeft = infraredInputLeft;
        this.ultrasonic = ultrasonic;
        this.motor = motor;
    }


    @Override
    public void run() {
        logger.info("infraredInput start");
        ultrasonic.trigger(false);
        try {
            Thread.sleep(200);
            while (true) {
                if (obstacleInFront()) {
                    motor.stop();
                    if (!obstacleInRight()) {
                        logger.info("Right");
                        motor.right();
                    } else if (!obstacleInLeft()) {
                        logger.info("Left");
                        motor.left();
                    }
                } else {
                    logger.info("forward");
                    motor.forward();
                }
                Thread.sleep(500);
            }
        } catch (InterruptedException e) {
            logger.error("infraredInput stop", e);
            motor.stop();
        }
    }

    private boolean obstacleInFront() throws InterruptedException {
        double d = ultrasonic.getDistance2();
        logger.info("Distance " + d);
        return d < 45.0;
    }

    private boolean obstacleInRight() {
        return !infraredInputRight.getValue();
    }

    private boolean obstacleInLeft() {
        return !infraredInputLeft.getValue();
    }

}
