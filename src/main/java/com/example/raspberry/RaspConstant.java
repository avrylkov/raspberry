package com.example.raspberry;

public class RaspConstant {

    public static final String FACE_DETECTION_FILE = "detect.jpg";
    public static final String FACE_NO_DETECTION_FILE = "no-detect.jpg";
    public static final String FACE_DETECTION_FILE_2 = "detect-2.jpg";
    public static final String HAAR_CASCADE_XML = "haarcascade_frontalface_alt.xml";

    public static final int W = 1280;
    public static final int H = 1024;
    public static final int W_CENTER = W/2;
    public static final int H_CENTER = H/2;

    public static final int LED_DETECT = 0;
    public static final int LED_NO_DETECT = 1;
    public static final int Y_SERVO_CAMERA = 2;
    public static final int X_SERVO_CAMERA = 3;
    // X
    public static final float X_SERVO_MIN = 0.1f; //0.08f
    public static final float X_SERVO_MAX = 0.134f; // 0.15f
    public static final float X_SERVO_MIDDLE_START = 0.117f;
    // Y
    public static final float Y_SERVO_MIN = 0.029f;
    public static final float Y_SERVO_MAX = 0.05f;
    public static final float Y_SERVO_MIDDLE_START = 0.035f;

    public static final float FULL_X = 0.04f;
    public static final float FULL_Y = 0.021f;
    public static final String CENTER = "Center";
    public static final String UP = "Up";
    public static final String DOWN = "Down";

}
