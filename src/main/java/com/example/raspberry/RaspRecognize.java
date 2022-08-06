package com.example.raspberry;

import static com.example.raspberry.RaspConstant.*;

import com.diozero.devices.PCA9685;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.datavec.image.loader.NativeImageLoader;
import org.deeplearning4j.nn.conf.WorkspaceMode;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.transferlearning.TransferLearningHelper;
import org.deeplearning4j.zoo.PretrainedType;
import org.deeplearning4j.zoo.ZooModel;
import org.deeplearning4j.zoo.model.NASNet;
import org.deeplearning4j.zoo.model.TinyYOLO;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.preprocessor.DataNormalization;
import org.nd4j.linalg.dataset.api.preprocessor.VGG16ImagePreProcessor;
import org.nd4j.linalg.factory.Nd4j;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.objdetect.CascadeClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.caprica.picam.Camera;
import uk.co.caprica.picam.CaptureFailedException;
import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class RaspRecognize implements Runnable  {

    private NativeImageLoader _nativeImageLoader;
    private TransferLearningHelper _transferLearningHelper;
    private DataNormalization _scaler;
    private List<VectorModel> trainVectorsModel = new ArrayList<>();

    private final ApplicationConfig applicationConfig;
    private final PCA9685 l2cBoard;
    private final Camera camera;
    private final RaspUtils raspUtils;
    private final RaspMovement raspMovement;

    private DateTimeFormatter face_folder_formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private final Logger logger = LoggerFactory.getLogger(RaspRecognize.class);
    private File[] listFiles;

    public RaspRecognize(ApplicationConfig applicationConfig, PCA9685 l2cBoard, Camera camera, Motor motor) {
        this.applicationConfig = applicationConfig;
        this.l2cBoard = l2cBoard;
        this.camera = camera;
        raspUtils = new RaspUtils(l2cBoard, camera, applicationConfig);
        raspMovement = new RaspMovement(applicationConfig, l2cBoard, motor);
    }


    @Override
    public void run() {
        logger.info("Running Recognize, Loading DL4J.");
        ZooModel objZooModel = TinyYOLO.builder().workspaceMode(WorkspaceMode.ENABLED).build();
        ComputationGraph objComputationGraph = null;
        try {
            objComputationGraph = (ComputationGraph) objZooModel.initPretrained(PretrainedType.IMAGENET);
        } catch (IOException e) {
            logger.error("error objZooModel", e);
            return;
        }
        System.out.println("Loaded DL4J");
        _transferLearningHelper = new TransferLearningHelper(objComputationGraph, "conv2d_9");
        _nativeImageLoader = new NativeImageLoader(224, 224, 3);
        _scaler = new VGG16ImagePreProcessor();

        CascadeClassifier faceDetector = new CascadeClassifier();
        if (!faceDetector.load(applicationConfig.getHome() + HAAR_CASCADE_XML)) {
            logger.error("Не удалось загрузить cascade frontal face xml");
            return;
        }
        //
        loadTrainVectorsModel();
        //
        l2cBoard.setValue(X_SERVO_CAMERA, raspMovement.getX_servo_current());
        l2cBoard.setValue(Y_SERVO_CAMERA, raspMovement.getY_servo_current());
        camera.open();

        while (true) {
            try {
                Thread.sleep(60);
                Mat matrixGray = raspUtils.takePicture();
                //
                MatOfRect faceDetections = new MatOfRect();
                faceDetector.detectMultiScale(matrixGray, faceDetections, 1.3);
                logger.info(String.format("detected %s faces", faceDetections.toArray().length));
                String direction = "";
                final boolean isFaceDetect = faceDetections.toArray().length > 0;
                raspUtils.setLedDetect(isFaceDetect);
                // Draw a bounding box around each face.
                final Optional<Rect> faceRectMax = raspUtils.getFaceRectMax(faceDetections.toArray());
                if (faceRectMax.isPresent()) {
                    final Rect rect = faceRectMax.get();
                    final Mat faceImage = matrixGray.submat(rect);
                    direction = raspMovement.moveCenterFace(rect);
                    logger.info("direction=" + direction);
                    //
                    logger.info("prepare matrix");
                    final INDArray imageMatrix = _nativeImageLoader.asMatrix(faceImage);
                    final double[] inputVector = createInputVector(imageMatrix);
                    logger.info("begin find");
                    final VectorModel similarFaceModel = findSimilarFace(inputVector);
                    if (similarFaceModel == null) {
                        final String folder = face_folder_formatter.format(LocalDateTime.now());
                        String pathFolder = applicationConfig.getTrainImagePath() + folder;
                        FileUtils.forceMkdir(new File(pathFolder));
                        String faceFullFilename = pathFolder + "/face.jpg";
                        Imgcodecs.imwrite(faceFullFilename, faceImage);
                        final VectorModel vectorModel = new VectorModel(inputVector, faceFullFilename);
                        trainVectorsModel.add(vectorModel);
                        final ObjectMapper objectMapper = new ObjectMapper();
                        objectMapper.writeValue(new File(pathFolder + "/" + "face.json"), vectorModel);
                        logger.info("create VectorModel=" + faceFullFilename);
                    } else {
                        logger.info(String.format("similarFace=%s, wav=%s", similarFaceModel.getFaceFilePath(), similarFaceModel.getWavFilePath()));
                        if (similarFaceModel.getWavFilePath() != null) {
                            RaspUtils.playShell(similarFaceModel.getWavFilePath());
                        }
                    }
                }
            } catch (InterruptedException | CaptureFailedException | IOException e) {
                logger.error(String.format("Face detections Interrupted %s", e.getMessage()), e);
                break;
            }
        }
        logger.info("stop detect");
        camera.close();
    }


    private VectorModel findSimilarFace(double[] faceVector) {
        INDArray array1 = Nd4j.create(faceVector);
        //
        double minimalDistance = Double.MAX_VALUE;
        VectorModel vectorSimilarModel = null;
        for(VectorModel vectorModel : trainVectorsModel) {
            INDArray array2 = Nd4j.create(vectorModel.getVector());
            double distance = euclideanDistance(array1, array2);
            if (distance < minimalDistance) {
                minimalDistance = distance;
                vectorSimilarModel = vectorModel;
            }
        }
        if (minimalDistance <= applicationConfig.getThresholdDistance()) {
            logger.info("#euclidean distance found= " + minimalDistance);
        } else {
            if (vectorSimilarModel != null) {
                logger.info(String.format("euclidean threshold distance great=%s, face=%s, wav=%s",
                        minimalDistance, vectorSimilarModel.getFaceFilePath(), vectorSimilarModel.getWavFilePath()));
            }
            vectorSimilarModel = null;
        }
        return vectorSimilarModel;
    }

    private double[] createInputVector(INDArray imageMatrix) throws IOException {
        _scaler.transform(imageMatrix);
        DataSet objDataSet = new DataSet(imageMatrix, Nd4j.create(new float[] {0, 0}));

        DataSet objFeaturized = _transferLearningHelper.featurize(objDataSet);
        INDArray featuresArray = objFeaturized.getFeatures();

        int reshapeDimension = 1;
        for (long dimension : featuresArray.shape()) {
            reshapeDimension *= dimension;
        }

        featuresArray = featuresArray.reshape(1, reshapeDimension);

        return featuresArray.data().asDouble();
    }

    private void loadTrainVectorsModel() {
        final File folder = new File(applicationConfig.getTrainImagePath());
        final File[] folders = folder.listFiles();
        logger.info("listFiles=" + folders.length);
        for (final File folderEntry : folders) {
            logger.info("path=" + folderEntry.getPath());
            try {
                if (folderEntry.isDirectory()) {
                    listFiles = folderEntry.listFiles();
                    //logger.info("listFiles=" + listFiles);
                    final File faceFileJpg = RaspUtils.getFileByType(listFiles, "jpg");
                    if (faceFileJpg != null) {
                        //logger.info("load faceFile=" + faceFileJpg.getPath());
                        File jsonFile = RaspUtils.getFileByType(listFiles, "json");
                        final VectorModel vectorModel;
                        final ObjectMapper objectMapper = new ObjectMapper();
                        if (jsonFile != null) {
                            vectorModel = objectMapper.readValue(jsonFile, VectorModel.class);
                        } else {
                            INDArray imageMatrix = _nativeImageLoader.asMatrix(faceFileJpg);
                            final double[] vector = createInputVector(imageMatrix);
                            vectorModel = new VectorModel(vector, faceFileJpg.getPath());
                            jsonFile = new File("/" + FilenameUtils.getPath(faceFileJpg.getPath()) + "face.json");
                            objectMapper.writeValue(jsonFile, vectorModel);
                        }
                        //
                        final File wavFile = RaspUtils.getFileByType(listFiles, "wav_");
                        if (wavFile != null) {
                            //final String wavPath = FileUtils.readFileToString(wavFile, "UTF8");
                            String wav = applicationConfig.getTrainVoicePath() + FilenameUtils.removeExtension(wavFile.getName()) + ".wav";
                            vectorModel.setWavFilePath(wav);
                            objectMapper.writeValue(jsonFile, vectorModel);
                        }
                        trainVectorsModel.add(vectorModel);
                    }
                }
            } catch (IOException e) {
                logger.error("error loadTrainVectorsModel", e);
            }

        }
    }

    private double euclideanDistance(INDArray array1, INDArray array2) {
        return array1.distance2(array2);
    }

    public void play(String path) {
        AudioInputStream audioIn;
        try {
            logger.info("play " + path);
            audioIn = AudioSystem.getAudioInputStream(new File(path));
            Clip clip;
            clip = AudioSystem.getClip();
            clip.open(audioIn);
            clip.start();
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            logger.error("error play", e);
        }
    }

}
