package com.example.raspberry;

import com.diozero.devices.PCA9685;
import com.diozero.util.SleepUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServoRun implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(ServoRun.class);

    private final PCA9685 l2cBoard;

    public ServoRun(PCA9685 l2cBoard) {
        this.l2cBoard = l2cBoard;
    }


    @Override
    public void run() {
        logger.info("Start servo");
        float start = 0.04f;
        float end = 0.15f;
        while (start < end) {
            l2cBoard.setValue(0, start);
            start += 0.01f;
            SleepUtil.sleepMillis(1000);
        }
        logger.info("Stop servo");
    }

}
