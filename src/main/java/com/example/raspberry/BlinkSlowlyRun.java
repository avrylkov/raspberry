package com.example.raspberry;

import com.diozero.devices.PCA9685;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlinkSlowlyRun implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(BlinkSlowlyRun.class);
    private final PCA9685 l2cBoard;
    private float min = 0f;
    private float max = 0.2f;
    private float start;

    public BlinkSlowlyRun(PCA9685 l2cBoard) {
        this.l2cBoard = l2cBoard;
    }

    @Override
    public void run() {
        logger.info("Start Blink Slowly");

        while (true) {
            start = min;

            if (brighter()) {
                return;
            }
            //
            if (darker()) {
                return;
            }
        }
    }

    private boolean brighter() {
        while (start <= max) {
            l2cBoard.setValue(1, start);
            start += 0.01f;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                logger.info("Blink Slowly Interrupted");
                l2cBoard.setValue(1, 0);
                return true;
            }
        }
        return false;
    }

    private boolean darker() {
        while (start >= min) {
            l2cBoard.setValue(1, start);
            start -= 0.01f;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                logger.info("Blink Slowly Interrupted");
                l2cBoard.setValue(1, 0);
                return true;
            }
        }
        return false;
    }

}
