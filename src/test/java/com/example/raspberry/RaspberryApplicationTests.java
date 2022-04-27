package com.example.raspberry;

import org.junit.jupiter.api.Test;
import org.opencv.core.Rect;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RaspberryApplicationTests {

    private static double W = 1024;
    private static int W_CENTER = (int) (W/2);

    @Test
    void contextLoads() {
    }

    private static final int DEFAULT_PWM_FREQUENCY = 50;
    private static final int RANGE = (int) Math.pow(2, 12);
    private int periodUs = 1_000_000 / DEFAULT_PWM_FREQUENCY;

    @Test
    public void floor() {
        System.out.println(Math.floor(0.04f * RANGE));
        System.out.println(Math.floor(0.15f * RANGE));
    }

    @Test
    public void test() {
        int dutyUs = 2400;
        // TODO Bounds checking

        int off = (int) Math.floor(dutyUs / periodUs / (double) RANGE);
        System.out.println(off);
    }

    @Test
    public void testRect() {
        Rect rect = new Rect(100, 200, 50, 60);
        final int i = centerFaceX(rect);
        System.out.println(i);
        if (rect.x < centerFaceX(rect) && (Math.abs(centerFaceX(rect) - rect.x)) > 10) {
            ;
        }
    }

    private int centerFaceX(Rect rect) {
        return W_CENTER + rect.width/2;
    }


}
