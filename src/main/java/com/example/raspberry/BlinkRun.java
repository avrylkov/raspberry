package com.example.raspberry;

import com.diozero.devices.LED;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlinkRun implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(BlinkRun.class);
    private final LED led;

    public BlinkRun(LED led) {
        this.led = led;
    }


    @Override
    public void run() {
        logger.info("Start blink");
        while (true) {
            led.toggle();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logger.info("Blink Interrupted");
                break;
            }
        }
    }

}
