package com.example.raspberry;

import com.github.javafaker.Faker;
import org.junit.jupiter.api.Test;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.Rect;
import org.opencv.features2d.BOWImgDescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FastFeatureDetector;
import org.opencv.features2d.Feature2D;
import org.opencv.imgcodecs.Imgcodecs;
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

    @Test
    public void fakerTest() {
        Faker faker = new Faker();
        System.out.println(faker.name().firstName());
        System.out.println(faker.name().firstName());
        System.out.println(faker.name().firstName());
    }

    private int centerFaceX(Rect rect) {
        return W_CENTER + rect.width/2;
    }


    public void openCvKeyPoints() {
        Mat img1 = Imgcodecs.imread("C:\\book\\opencv\\foto3.png");
        Mat img2 = Imgcodecs.imread("C:\\book\\opencv\\foto3.png");
        //
        MatOfKeyPoint kp1 = new MatOfKeyPoint();
        MatOfKeyPoint kp2 = new MatOfKeyPoint();
        FastFeatureDetector fuFeatureDetector = FastFeatureDetector.create();
        fuFeatureDetector.detect(img1, kp1);
        fuFeatureDetector.detect(img2, kp2);



        DescriptorMatcher descriptorMatcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
        MatOfDMatch matches = new MatOfDMatch();
        descriptorMatcher.match(img1, img2, matches);
    }




}
