package com.example.raspberry;

import org.nd4j.linalg.api.ndarray.INDArray;

public class VectorModel2 {

    private double[] vector;
    private INDArray indArray;
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

    public VectorModel2(INDArray indArray, String faceFilePath, String name) {
        this.indArray = indArray;
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

    public INDArray getIndArray() {
        return indArray;
    }
}
