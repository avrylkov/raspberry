package com.example.raspberry;

import com.diozero.api.DigitalInputDevice;
import com.diozero.api.DigitalOutputDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UltrasonicRun implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(UltrasonicRun.class);

    private final DigitalOutputDevice trig;
    private final DigitalInputDevice echo;
    private final Motor motor;

    public UltrasonicRun(DigitalOutputDevice trig, DigitalInputDevice echo, Motor motor) {
        this.trig = trig;
        this.echo = echo;
        this.motor = motor;
    }

    @Override
    public void run() {
        logger.info("Ultrasonic Run");
        trig.off();
        try {
            Thread.sleep(500);
            //
            while (true) {
                logger.info("trigger");
                //
                trig.on();
                Thread.sleep(0, 10_000);
                trig.off();
                //
                while (!echo.getValue()) {
                    ;
                }

                long start = System.nanoTime();

                while (echo.getValue()) {
                    ;
                }

                long end = System.nanoTime();
                final double d = (((end - start) / 1e3) / 2) / 29.1;
                if (d > 35.0) {
                    motor.stop();
                } else {
                    motor.forward(0.4f - (float) (d / 100.0f));
                }
                logger.info(("Distance :"+ d +" cm")); //Printing out the distance in cm

                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            logger.info("Ultrasonic Interrupted");
            motor.stop();
            return;
        }

    }
}
