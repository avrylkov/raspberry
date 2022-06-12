package com.example.raspberry;

import com.diozero.api.DigitalInputDevice;

public class Infrared {

    private DigitalInputDevice infraredInputRight;
    private DigitalInputDevice infraredInputLeft;

    public Infrared() {
        super();
        init();
    }

    public void close() {
        infraredInputRight.close();
        infraredInputLeft.close();
    }

    public boolean getInputRight() {
        return infraredInputRight.getValue();
    }

    public boolean getInputLeft() {
        return infraredInputLeft.getValue();
    }

    private void init() {
        infraredInputRight = DigitalInputDevice.Builder.builder(27)
                .setActiveHigh(true)
                .build();
        infraredInputLeft = DigitalInputDevice.Builder.builder(22)
                .setActiveHigh(true)
                .build();
    }

}
