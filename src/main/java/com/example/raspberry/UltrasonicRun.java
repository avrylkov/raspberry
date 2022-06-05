package com.example.raspberry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UltrasonicRun implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(UltrasonicRun.class);

    private final Motor motor;
    private Ultrasonic ultrasonic;

    public UltrasonicRun(Ultrasonic ultrasonic, Motor motor) {
        this.ultrasonic = ultrasonic;
        this.motor = motor;
    }

    @Override
    public void run() {
        logger.info("Ultrasonic Run");
        ultrasonic.trigger(false);
        try {
            Thread.sleep(500);
            //
            while (true) {
                logger.info("trigger");
                //
                final double d = ultrasonic.getDistance2(); //ultrasonic.send(); getDistance
                if (d > 35.0 || d < 0) {
                    motor.stop();
                } else {
                    motor.forward(0.4f - (float) (d / 100.0f));
                }
                logger.info(("Distance :"+ d +" cm")); //Printing out the distance in cm

                Thread.sleep(1000);
            }
        } catch (Exception e) {
            logger.info("Ultrasonic Interrupted");
            motor.stop();
            return;
        }

    }
}
