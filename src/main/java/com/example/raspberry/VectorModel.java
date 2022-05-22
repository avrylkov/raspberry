package com.example.raspberry;

import java.util.Arrays;

public class VectorModel {

    private double[] vector;
    private String faceFilePath;
    private String wavFilePath;

    public VectorModel() {
        // for json deserialize
    }

    public VectorModel(double[] vector, String faceFilePath) {
        this.vector = vector;
        this.faceFilePath = faceFilePath;
    }

    public double[] getVector() {
        return vector;
    }

    public void setVector(double[] vector) {
        this.vector = vector;
    }

    public String getFaceFilePath() {
        return faceFilePath;
    }

    public void setFaceFilePath(String faceFilePath) {
        this.faceFilePath = faceFilePath;
    }

    public String getWavFilePath() {
        return wavFilePath;
    }

    public void setWavFilePath(String wavFilePath) {
        this.wavFilePath = wavFilePath;
    }
}
