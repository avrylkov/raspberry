package com.example.raspberry;

import com.diozero.api.DigitalInputDevice;
import com.diozero.api.DigitalOutputDevice;

public class Ultrasonic {

    private long REJECTION_START = 1000, REJECTION_TIME = 1000; //ns;

    private final DigitalOutputDevice trigger;
    private final DigitalInputDevice echo;

    public Ultrasonic(DigitalOutputDevice trigger, DigitalInputDevice echo) {
        this.trigger = trigger;
        this.echo = echo;
    }

    public void trigger(boolean value) {
        if (value) {
            trigger.on();
        } else {
            trigger.off();
        }
    }

    public double getDistance() throws InterruptedException {
        trigger.on();
        Thread.sleep(0, 10_000);
        trigger.off();
        //
        long start = System.nanoTime();
        long end = System.nanoTime();
        //
        while (!echo.getValue()) {
            start = System.nanoTime();
        }

        while (echo.getValue()) {
            end = System.nanoTime();
        }

        return (((end - start) / 1e3) / 2) / 29.1;
    }

    public double getDistance2() throws InterruptedException { //in milimeters
        double distance = 0;
        long start_time = 0, end_time = 0, rejection_start = 0, rejection_time = 0;
        //Start ranging- trig should be in high state for 10us to generate ultrasonic signal
        //this will generate 8 cycle sonic burst.
        // produced signal would looks like, _|-----|
        trigger.on();
        Thread.sleep(0, 10_000);
        trigger.off();

        //echo pin high time is propotional to the distance _|----|
        //distance calculation
        while (!echo.getValue()) { //wait until echo get high
            Thread.sleep(0, 1);
            start_time = System.nanoTime();
            rejection_start++;
            if (rejection_start == REJECTION_START) return -1; //something wrong
        }

        while (echo.getValue()) { //wait until echo get low
            Thread.sleep(0, 1);
            end_time = System.nanoTime();
            rejection_time++;
            if (rejection_time == REJECTION_TIME) return -2; //infinity
        }

        distance = ((end_time - start_time) / 5882.35294118); //distance in mm
        //distance = (((end_time - start_time) / 1e3) / 2) / 29.1;
        //distance=(end_time-start_time)/(200*29.1); //distance in mm
        return distance / 10.0;
    }

}
