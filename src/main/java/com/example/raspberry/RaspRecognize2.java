package com.example.raspberry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javafaker.Faker;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.datavec.image.loader.NativeImageLoader;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.layers.objdetect.DetectedObject;
import org.deeplearning4j.nn.layers.objdetect.Yolo2OutputLayer;
import org.deeplearning4j.nn.transferlearning.TransferLearningHelper;
import org.deeplearning4j.zoo.PretrainedType;
import org.deeplearning4j.zoo.ZooModel;
import org.deeplearning4j.zoo.model.*;
import org.deeplearning4j.zoo.util.imagenet.ImageNetLabels;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.preprocessor.DataNormalization;
import org.nd4j.linalg.dataset.api.preprocessor.ImagePreProcessingScaler;
import org.nd4j.linalg.factory.Nd4j;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.example.raspberry.RaspConstant.HAAR_CASCADE_XML;
import static org.opencv.imgproc.Imgproc.FONT_HERSHEY_PLAIN;

public class RaspRecognize2 implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(RaspRecognize.class);

    private DateTimeFormatter face_formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss.SSS");
    private NativeImageLoader _nativeImageLoader;
    private TransferLearningHelper _transferLearningHelper;
    //private DataNormalization vggImagePreProcessor;
    private DataNormalization imagePreProcessor;
    private ComputationGraph computationGraph;
    private List<VectorModel2> memberModels = new ArrayList<>();
    private AtomicReference<String> compareFace = new AtomicReference<>("");
    private AtomicReference<String> folder = new AtomicReference<>("");
    private VideoCapture videoCapture;
    private CascadeClassifier faceDetector;
    private static String[] labels = {"aeroplane","bicycle","bird","boat","bottle","bus","car","cat","chair","cow",
            "diningtable","dog","horse","motorbike","person","pottedplant","sheep","sofa","train","tvmonitor"};

    private ImageNetLabels imageNetLabels = null;

    private final ApplicationConfig applicationConfig;


    public RaspRecognize2(ApplicationConfig applicationConfig) {
        this.applicationConfig = applicationConfig;
    }

    public void setFace(String imageName) {
        compareFace.set(imageName);
    }

    public void setFolder(String name) {
        folder.set(name);
    }

    @Override
    public void run() {
        try {
            if (!init()) {
                return;
            }
            while (true) {
                Thread.sleep(100);
                double[] featuresCompareImage;
                if (isCompareFileFace()) {
                    // определить по файлу его класс и сравнить с ранее детектируемым множеством
                    featuresCompareImage = compareFileFace();
                    compareFace.set("");
                    compareFace(featuresCompareImage, null);
                } else if (isCompareCameraFace()) {
                    // взять изображение с камеры и сравнить с ранее детектируемым множеством один раз
                    detectCameraFace();
                    compareFace.set("");
                } else if (isCompareAutoCameraFace()) {
                    // взять изображение с камеры и сравнить с ранее детектируемым множеством постоянно
                    detectCameraFace();
                }
            }
        } catch (InterruptedException | IOException e) {
            videoCapture.release();
            logger.error("error", e);
            return;
        }
    }

    private VectorModel2 compareFace(double[] featuresCompareImage, Mat mat) throws IOException {
        INDArray array1 = Nd4j.create(featuresCompareImage);
        double minimalDistance = Double.MAX_VALUE;
        VectorModel2 vectorModelResult = null;
        for (VectorModel2 personModel : memberModels) {
            INDArray array2 = Nd4j.create(personModel.getVector());
            double distance = euclideanDistance(array1, array2);
            if (distance < minimalDistance) {
                minimalDistance = distance;
                vectorModelResult = personModel;
            }
        }
        if (vectorModelResult != null) {
            logger.info(String.format("recognize face, minimalDistance=%s, name=%s", minimalDistance, vectorModelResult.getName()));
        }
        if (vectorModelResult != null && minimalDistance <= applicationConfig.getThresholdDistance()) {
            sayHello(applicationConfig.getTrainImagePath() + vectorModelResult.getName() + "/" + vectorModelResult.getName() + ".wav");
        } else if (mat != null && minimalDistance > applicationConfig.getThresholdDistance()) {
            // новое лицо
            String newName = folder.get(); //generateNewName();
            if (StringUtils.isEmpty(newName) || StringUtils.length(newName) == 1) {
                logger.info("unknown face");
                return null;
            }
            if ("auto".equals(newName)) {
                newName = generateNewName();
            }
            final String path = applicationConfig.getTrainImagePath() + newName + "/";
            final String fileName = face_formatter.format(LocalDateTime.now());
            vectorModelResult = new VectorModel2(featuresCompareImage, path, newName);
            memberModels.add(vectorModelResult);
            //
            FileUtils.forceMkdir(new File(path));
            Imgcodecs.imwrite(path + fileName + ".jpg", mat);
            final ObjectMapper objectMapper = new ObjectMapper();
            File jsonFile = new File(path + fileName + ".json");
            objectMapper.writeValue(jsonFile, vectorModelResult);
            logger.info("Save new face=" + path);
        }
        return vectorModelResult;
    }

    private String generateNewName() {
        Faker faker = new Faker();
        String[] firstName = new String[1];
        do {
             firstName[0] = faker.name().firstName();
        } while (memberModels.stream().anyMatch(m -> firstName[0].equals(m.getName())));
        return firstName[0];
    }

    private boolean init() throws IOException {
        logger.info("Running Recognize, Loading DL4J.  ");
        ZooModel objZooModel = TinyYOLO.builder().build();
        //ZooModel objZooModel = NASNet.builder().build();
        //ZooModel objZooModel = VGG16.builder().build();
        //ZooModel objZooModel = ResNet50.builder().build();
        //ZooModel objZooModel = SqueezeNet.builder().build();
        //ZooModel objZooModel = UNet.builder().build();
        //ZooModel objZooModel = YOLO2.builder().build();
        try {
            computationGraph = (ComputationGraph) objZooModel.initPretrained(PretrainedType.IMAGENET);
            //computationGraph = (ComputationGraph) objZooModel.initPretrained(PretrainedType.SEGMENT);
            //computationGraph = (ComputationGraph) objZooModel.initPretrained(PretrainedType.VGGFACE);
        } catch (IOException e) {
            logger.error("error objZooModel", e);
            return false;
        }
        System.out.println("Loaded DL4J");
        videoCapture = new VideoCapture(0);
        logger.info(computationGraph.summary());
        //_transferLearningHelper = new TransferLearningHelper(computationGraph, "conv2d_9"); // max_pooling2d_6 conv2d_9 // TinyYOLO
        //_transferLearningHelper = new TransferLearningHelper(computationGraph, "pool4"); //pool4  fc2 vgg
        _nativeImageLoader = new NativeImageLoader(224, 224, 3); // NativeImageLoader(224, 224, 3);
        //imagePreProcessor = new VGG16ImagePreProcessor();
        imagePreProcessor = new ImagePreProcessingScaler(0, 1);
        if (imageNetLabels == null) {
            imageNetLabels = new ImageNetLabels();
        }
        faceDetector = new CascadeClassifier();
        if (!faceDetector.load(applicationConfig.getHome() + HAAR_CASCADE_XML)) {
            logger.error("Не удалось загрузить cascade frontal face xml");
            return false;
        }
        loadTrainVectorsModel();
        return true;
    }

    private void sayHello(String filePath) {
        RaspUtils.playShell(filePath);
    }

    private boolean isCompareAutoCameraFace() {
        return "auto".equals(compareFace.get());
    }

    private boolean isCompareFileFace() {
        return StringUtils.isNotEmpty(compareFace.get())
                && FilenameUtils.getExtension(compareFace.get()).equalsIgnoreCase("jpg");
    }

    private boolean isCompareCameraFace() {
        return "camera".equals(compareFace.get());
    }

    private double[] compareFileFace() throws IOException {
        final File compareFaceImage = new File(applicationConfig.getTrainImagePath() + compareFace.get());
        return getFeaturesImage(compareFaceImage);
    }

    private void detectCameraFace() throws IOException {
        List<Mat> matList = new ArrayList<>();
        if (videoCapture.isOpened()) {
            Mat matrix = new Mat();
            videoCapture.read(matrix);
            MatOfRect faceDetections = new MatOfRect();
            //
            faceDetector.detectMultiScale(matrix, faceDetections, 1.3);
            //
            if (faceDetections.toArray().length > 0) {
                logger.info(String.format("detected %s faces", faceDetections.toArray().length));
                for (Rect rect : faceDetections.toArray()) {
                    //final Optional<Rect> faceRectMax = RaspUtils.getFaceRectMax(faceDetections.toArray());
                    //if (faceRectMax.isPresent()) {
                    //    final Rect rect = faceRectMax.get();

                    Scalar colorG = new Scalar(0, 128, 0); // (B,G,R)
                    Imgproc.rectangle(matrix, new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y + rect.height),
                            colorG, 2);

                    final Mat subMat = matrix.submat(rect);
                    matList.add(subMat);
                    //
                    double[] featuresCompareImage = getFeaturesImage(subMat);
                    final VectorModel2 vectorModel = compareFace(featuresCompareImage, subMat);
                    //
                    if (vectorModel != null) {
                        Scalar colorR = new Scalar(0, 0, 128);
                        Imgproc.putText(matrix, vectorModel.getName(), new Point(rect.x, rect.y),
                                FONT_HERSHEY_PLAIN, 1.5, colorR, 2);
                    }
                }
            }
            Imgcodecs.imwrite(applicationConfig.getHome() + "camera.jpg", matrix);
        } else {
            logger.warn("camera !isOpened");
        }
        //return matList;
    }

    private double euclideanDistance(INDArray array1, INDArray array2) {
        return array1.distance2(array2);
    }

    private void loadTrainVectorsModel() throws IOException {
        final ObjectMapper objectMapper = new ObjectMapper();
        final File rootFolder = new File(applicationConfig.getTrainImagePath());
        final File[] folders = rootFolder.listFiles();
        for (final File folderEntry : folders) {
            if (folderEntry.isDirectory()) {
                String name = folderEntry.getName();
                final File[] allFiles = folderEntry.listFiles();
                final List<File> filesJpg = RaspUtils.getFilesByType(allFiles, "jpg");
                logger.info("--------- add name=" + name);
                for (File jpgFile : filesJpg) {
                    final VectorModel2 vectorModel;
                    File fileJson = new File(jpgFile.getParent() + "/" + FilenameUtils.removeExtension(jpgFile.getName()) + ".json");
                    if (fileJson.exists()) {
                        vectorModel = objectMapper.readValue(fileJson, VectorModel2.class);
                    } else {
                        final double[] featuresImage = getFeaturesImage(jpgFile);
                        vectorModel = new VectorModel2(featuresImage, folderEntry.getPath(), name);
                        File jsonFile = new File(jpgFile.getParent() + "/" + FilenameUtils.removeExtension(jpgFile.getName()) + ".json");
                        objectMapper.writeValue(jsonFile, vectorModel);
                    }
                    memberModels.add(vectorModel);
                    logger.info("add name=" + jpgFile.getName());
                }
            }
        }
    }

    private double[] getFeaturesImage(Mat matrix) throws IOException {
        INDArray imageMatrix = _nativeImageLoader.asMatrix(matrix);
        return getFeaturesImage(imageMatrix);
    }

    private double[] getFeaturesImage(File file) throws IOException {
        INDArray imageMatrix = _nativeImageLoader.asMatrix(file);
        return getFeaturesImage(imageMatrix);
    }

    private double[] getFeaturesImage(INDArray imageMatrix) throws IOException {
        imagePreProcessor.transform(imageMatrix);
        // transfer Learning
/*
        DataSet objDataSet = new DataSet(imageMatrix, Nd4j.create(new float[]{0,0}));
        DataSet objFeaturized = _transferLearningHelper.featurize(objDataSet);
        INDArray featuresArray = objFeaturized.getFeatures();
        int reshapeDimension=1;
        for (long dimension : featuresArray.shape()) {
            reshapeDimension *= dimension;
        }
        featuresArray = featuresArray.reshape(1,reshapeDimension);
        return featuresArray.data().asDouble();
*/

        // VGG
        //final INDArray recognise = recognise(imageMatrix);
        // Yolo2
        Yolo2OutputLayer outputLayer = (Yolo2OutputLayer) computationGraph.getOutputLayer(0);
        // NASnet
        final Layer outputLayer1 = computationGraph.getOutputLayer(0);
        INDArray results = computationGraph.outputSingle(imageMatrix);
        //logger.info(results.toString());
        //logger.info(results.getRow(0).dup().toString());
        //logger.info(results.argMax(1).toString());
        //
        final double[] doubles = Arrays.stream(results.data().asDouble()).sorted().toArray();
        final double[] topDoubles = new double[3];
        System.arraycopy(doubles, doubles.length - 3, topDoubles, 0, 3);
        logger.info("topDoubles= " + Arrays.asList(topDoubles[0], topDoubles[1], topDoubles[2]).toString());
        //

        List<DetectedObject> detectedObjects = outputLayer.getPredictedObjects(results, 0.4);
        final DetectedObject maxDetectedObject = getMaxDetectedObject(detectedObjects);
        if (maxDetectedObject != null) {
            logger.info(String.format("#  objects detected class=%s, x1=%s, y1=%s, confidence=%s", labels[maxDetectedObject.getPredictedClass()],
                    maxDetectedObject.getTopLeftXY()[0], maxDetectedObject.getTopLeftXY()[1], maxDetectedObject.getConfidence()));
        }
        // Yolo2
        return results.data().asDouble();
        // VGG
        //return recognise.data().asDouble();
    }



    private DetectedObject getMaxDetectedObject(List<DetectedObject> detectedObjects) {
        return detectedObjects.stream()
                .max(Comparator.comparing(DetectedObject::getConfidence))
                .orElse(null);
    }

    // VGG
    private INDArray recognise(INDArray imageMatrix) throws IOException {
        INDArray[] output = computationGraph.output(false, imageMatrix);
        //return predict(output[0]);  // image net
        // VGG face
        return predictFace(output[0]);  // face
        //INDArray outputA = Nd4j.concat(0, output);
        //return predictFace(outputA);  // face
    }

    // VGG imageNet
   private INDArray predict(INDArray predictions) throws IOException {
       List<String> objects = new ArrayList<>();
       int topN = 3;
       int[] topNPredictions = new int[topN];
       float[] topNProb = new float[topN];
       String[] outLabels = new String[topN];
       //brute force collect top N
       int i = 0;
       INDArray topPredict = predictions.getRow(0).dup();
       for (int batch = 0; batch < predictions.size(0); batch++) {
           INDArray currentBatch = predictions.getRow(batch).dup();
           while (i < topN) {
               topNPredictions[i] = Nd4j.argMax(currentBatch, 1).getInt(0);
               topNProb[i] = currentBatch.getFloat(batch, topNPredictions[i]);
               currentBatch.putScalar(0, topNPredictions[i], 0);
               outLabels[i] = imageNetLabels.getLabel(topNPredictions[i]);
               //final String s = imageNetLabels.decodePredictions(currentBatch);
               if (topNProb[i] > 0.4) {
                   objects.add(String.format("Label=%s, prob=%s", outLabels[i], topNProb[i]));
               }
               i++;
           }
       }
       logger.info("predict: " + objects);
       return topPredict;
   }

   // VGG face
    private INDArray predictFace(INDArray predictions) throws IOException {
        List<String> objects = new ArrayList<>();
        int topN = 3;
        int[] topNPredictions = new int[topN];
        float[] topNProb = new float[topN];
        int[] outLabels = new int[topN];
        //brute force collect top N
        int i = 0;
        INDArray topPredict = predictions.getRow(0).dup();
        logger.info(decodePredictions(predictions));
        return topPredict;
    }

    // VGG face
    private String decodePredictions(INDArray predictions) {
        String predictionDescription = "";
        int[] top3 = new int[3];
        float[] top3Prob = new float[3];

        //brute force collect top 5
        int i = 0;
        for (int batch = 0; batch < predictions.size(0); batch++) {
            predictionDescription += "Predictions for batch ";
            if (predictions.size(0) > 1) {
                predictionDescription += String.valueOf(batch);
            }
            predictionDescription += " :";
            INDArray currentBatch = predictions.getRow(batch).dup();
            while (i < 3) {
                top3[i] = Nd4j.argMax(currentBatch, 1).getInt(0);
                top3Prob[i] = currentBatch.getFloat(batch, top3[i]);
                currentBatch.putScalar(0, top3[i], 0);
                predictionDescription += "\n\t" + String.format("%3f", top3Prob[i] * 100) + "%, "
                        + top3[i];
                i++;
            }
        }
        return predictionDescription;
    }



}
