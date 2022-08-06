package com.example.raspberry;

public class VectorModel2 {

    private double[] vector;
    private String faceFilePath;
    private String name;

    public VectorModel2() {
        // for json deserialize
    }

    public VectorModel2(double[] vector, String faceFilePath, String name) {
        this.vector = vector;
        this.faceFilePath = faceFilePath;
        this.name = name;
    }

    public double[] getVector() {
        return vector;
    }

    public String getFaceFilePath() {
        return faceFilePath;
    }

    public String getName() {
        return name;
    }

}
