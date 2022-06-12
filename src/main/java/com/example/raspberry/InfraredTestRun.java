package com.example.raspberry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InfraredTestRun implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(InfraredTestRun.class);

    private final Infrared infrared;

    public InfraredTestRun(Infrared infrared) {
        super();
        this.infrared = infrared;
    }

    @Override
    public void run() {
        while (true) {
            logger.info("right " + infrared.getInputRight());
            logger.info("left " + infrared.getInputLeft());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logger.info("InfraredTestRun stop");
                infrared.close();
            }
        }
    }

}
