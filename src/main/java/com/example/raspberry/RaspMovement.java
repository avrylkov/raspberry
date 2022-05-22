package com.example.raspberry;

import com.diozero.devices.PCA9685;
import org.opencv.core.Rect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static com.example.raspberry.RaspConstant.*;

public class RaspMovement {

    private final Logger logger = LoggerFactory.getLogger(RaspMovement.class);

    private float x_servo_current = X_SERVO_MIDDLE_START;
    private float y_servo_current = Y_SERVO_MIDDLE_START;

    private final ApplicationConfig applicationConfig;
    private final PCA9685 l2cBoard;
    private final Motor motor;

    public RaspMovement(ApplicationConfig applicationConfig, PCA9685 l2cBoard, Motor motor) {
        this.applicationConfig = applicationConfig;
        this.l2cBoard = l2cBoard;
        this.motor = motor;
    }

    public String moveCenterFace(Rect rect) throws InterruptedException {
        if (inCenter(rect)) {
            logger.info("IN CENTER");
            return CENTER;
        }
        logger.info(String.format("move centerFace"));
        //
        String result = null;
        if (centerFaceX(rect) < centerFaceLeft(rect)) {
            turnRight(rect);
            result = "Right";
        } else if (centerFaceX(rect) > centerFaceRight(rect)) {
            turnLeft(rect);
            result = "Left";
        }
        //
        if (centerFaceY(rect) > centerFaceDown(rect)) {
            turnDown(rect);
            result = Optional.ofNullable(result).map(s -> s += DOWN).orElse(DOWN);
        } else if (centerFaceY(rect) < centerFaceUp(rect)) {
            turnUp(rect);
            result = Optional.ofNullable(result).map(s -> s += UP).orElse(UP);
        }
        return result;
    }

    private boolean inCenter(Rect rect) {
        return inCenterX(centerFaceX(rect), rect)
                && inCenterY(centerFaceY(rect), rect)
                ;
    }

    private int centerFaceX(Rect rect) {
        return rect.x + rect.width / 2;
    }

    private int centerFaceY(Rect rect) {
        return rect.y + rect.height / 2;
    }

    private int centerFaceLeft(Rect rect) {
        return W_CENTER - rect.width / 2;
    }

    private int centerFaceRight(Rect rect) {
        return W_CENTER + rect.width / 2;
    }

    private int centerFaceUp(Rect rect) {
        return H_CENTER - rect.height / 2;
    }

    private int centerFaceDown(Rect rect) {
        return H_CENTER + rect.height / 2;
    }

    private boolean inCenterX(int centerFaceX, Rect rect) {
        return (centerFaceX >= (W_CENTER - rect.width / 2)) && (centerFaceX <= (W_CENTER + rect.width / 2));
    }

    private boolean inCenterY(int centerFaceY, Rect rect) {
        return (centerFaceY >= (H_CENTER - rect.height / 2)) && (centerFaceY <= (H_CENTER + rect.height / 2));
    }

    private void turnLeft(Rect rect) throws InterruptedException {
        if (x_servo_current >= X_SERVO_MIN) {
            x_servo_current -= servoStepX(rect);
            logger.info("LEFT");
        } else {
            logger.info(String.format("LEFT-camera-limit %s, motor turn", x_servo_current));
            if (applicationConfig.isMotorTurn()) {
                motor.right(applicationConfig.getTurnDelay());
            }
        }
        l2cBoard.setValue(X_SERVO_CAMERA, x_servo_current);
    }

    private void turnRight(Rect rect) throws InterruptedException {
        if (x_servo_current <= X_SERVO_MAX) {
            x_servo_current += servoStepX(rect);
            logger.info("RIGHT");
        } else {
            logger.info(String.format("RIGHT-camera-limit %s, motor turn", x_servo_current));
            if (applicationConfig.isMotorTurn()) {
                motor.left(applicationConfig.getTurnDelay());
            }
        }
        l2cBoard.setValue(X_SERVO_CAMERA, x_servo_current);
    }
    //
    private void turnDown(Rect rect) {
        if (y_servo_current >= Y_SERVO_MIN) {
            y_servo_current -= servoStepY(rect);
            logger.info("DOWN");
        } else {
            logger.info("DOWN - limit " + y_servo_current);
        }
        l2cBoard.setValue(Y_SERVO_CAMERA, y_servo_current);
    }

    private void turnUp(Rect rect) {
        if (y_servo_current <= Y_SERVO_MAX) {
            y_servo_current += servoStepY(rect);
            logger.info("UP");
        } else {
            logger.info("UP-limit " + y_servo_current);
        }
        l2cBoard.setValue(Y_SERVO_CAMERA, y_servo_current);
    }

    private float servoStepX(Rect rect) {
        int faceX = centerFaceX(rect);
        int delta = Math.abs(W / 2 - faceX);
        return delta * FULL_X / W;
    }

    private float servoStepY(Rect rect) {
        int faceY = centerFaceY(rect);
        int delta = Math.abs(H / 2 - faceY);
        return delta * FULL_Y / H;
    }

    public float getX_servo_current() {
        return x_servo_current;
    }

    public float getY_servo_current() {
        return y_servo_current;
    }
}
