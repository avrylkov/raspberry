package com.example.raspberry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InfraredRun implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(InfraredRun.class);

    private final Infrared infrared;
    private final Ultrasonic ultrasonic;
    private final Motor motor;

    public InfraredRun(Infrared infrared, Ultrasonic ultrasonic, Motor motor) {
        this.infrared = infrared;
        this.ultrasonic = ultrasonic;
        this.motor = motor;
    }


    @Override
    public void run() {
        logger.info("infraredInput start");
        ultrasonic.trigger(false);
        try {
            //Thread.sleep(200);
            while (true) {
                final double inFront = obstacleInFront();
                if (inFront < 0) {
                    motor.stop();
                    Thread.sleep(500);
                    continue;
                } else if(inFront < 40) {
                    motor.stop();
                    if (!obstacleInRight()) {
                        logger.info("Right");
                        motor.left();
                    } else if (!obstacleInLeft()) {
                        logger.info("Left");
                        motor.right();
                    }
                } else {
                    logger.info("forward");
                    motor.forward();
                }
                Thread.sleep(500);
            }
        } catch (InterruptedException e) {
            logger.error("InfraredInput, Ultrasonic stop", e);
            ultrasonic.close();
            infrared.close();
            motor.stop();
        }
    }

    private double obstacleInFront() throws InterruptedException {
        double d = ultrasonic.getDistance2();
        logger.info("Distance " + d);
        return d;
    }

    private boolean obstacleInRight() {
        return !infrared.getInputRight();
    }

    private boolean obstacleInLeft() {
        return !infrared.getInputLeft();
    }

}
