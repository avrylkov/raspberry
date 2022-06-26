package com.example.raspberry;

import org.apache.commons.lang3.StringUtils;
import org.datavec.image.loader.NativeImageLoader;
import org.deeplearning4j.nn.conf.WorkspaceMode;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.layers.objdetect.DetectedObject;
import org.deeplearning4j.nn.layers.objdetect.Yolo2OutputLayer;
import org.deeplearning4j.nn.transferlearning.TransferLearningHelper;
import org.deeplearning4j.zoo.PretrainedType;
import org.deeplearning4j.zoo.ZooModel;
import org.deeplearning4j.zoo.model.TinyYOLO;
import org.deeplearning4j.zoo.model.VGG16;
import org.deeplearning4j.zoo.util.imagenet.ImageNetLabels;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.preprocessor.DataNormalization;
import org.nd4j.linalg.dataset.api.preprocessor.ImagePreProcessingScaler;
import org.nd4j.linalg.dataset.api.preprocessor.VGG16ImagePreProcessor;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class RaspRecognize2 implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(RaspRecognize.class);

    private NativeImageLoader _nativeImageLoader;
    private TransferLearningHelper _transferLearningHelper;
    //private DataNormalization vggImagePreProcessor;
    private DataNormalization imagePreProcessor;
    private ComputationGraph computationGraph;
    private List<VectorModel2> memberModels = new ArrayList<>();
    private AtomicReference<String> compareFace = new AtomicReference<>("");
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

    @Override
    public void run() {
        logger.info("Running Recognize, Loading DL4J.");
        ZooModel objZooModel = TinyYOLO.builder().build();
        //ZooModel objZooModel = VGG16.builder().build();
        try {
            computationGraph = (ComputationGraph) objZooModel.initPretrained(PretrainedType.IMAGENET);
            //computationGraph = (ComputationGraph) objZooModel.initPretrained(PretrainedType.VGGFACE);
        } catch (IOException e) {
            logger.error("error objZooModel", e);
            return;
        }
        System.out.println("Loaded DL4J");
        logger.info(computationGraph.summary());
        _transferLearningHelper = new TransferLearningHelper(computationGraph, "conv2d_9"); // max_pooling2d_6 conv2d_9 // TinyYOLO
        //_transferLearningHelper = new TransferLearningHelper(computationGraph, "pool4"); //pool4  fc2 vgg
        _nativeImageLoader = new NativeImageLoader(224, 224, 3); // NativeImageLoader(224, 224, 3);
        //imagePreProcessor = new VGG16ImagePreProcessor();
       //imagePreProcessor = new ImagePreProcessingScaler(0, 1);
        imagePreProcessor = new ImagePreProcessingScaler();

        try {
            if (imageNetLabels == null) {
                imageNetLabels = new ImageNetLabels();
            }
            loadTrainVectorsModel();
            while (true) {
                Thread.sleep(100);
                if (StringUtils.isNotEmpty(compareFace.get())) {
                    final File compareFaceImage = new File(applicationConfig.getTrainImagePath() + compareFace.get());
                    final double[] featuresCompareImage = getFeaturesImage(compareFaceImage);
                    INDArray array1 = Nd4j.create(featuresCompareImage);
                    //INDArray array1 = getFeaturesImage2(compareFaceImage);
                    double minimalDistance = Double.MAX_VALUE;
                    String resultName = "";
                    for(VectorModel2 personModel : memberModels) {
                        INDArray array2 = Nd4j.create(personModel.getVector());
                        double distance = euclideanDistance(array1, array2);
                        //double distance = euclideanDistance(array1, personModel.getIndArray());
                        if (distance < minimalDistance){
                            minimalDistance = distance;
                            resultName = personModel.getName();
                        }
                    }
                    System.out.println(String.format("minimalDistance=%s, name=%s", minimalDistance, resultName));
                    compareFace.set("");
                }
            }
        } catch (InterruptedException | IOException e) {
            logger.error("error", e);
            return;
        }
    }

/*
    private INDArray forwardPass(INDArray indArray) {
        Map<String, INDArray> output = computationGraph.feedForward(indArray, false);
        GraphVertex embeddings = computationGraph.getVertex("outputs");
        INDArray dense = output.get("conv2d_9");
        embeddings.setInput(0, indArray, LayerWorkspaceMgr.builder().defaultNoWorkspace().build()); //setInputs(dense);
        INDArray embeddingValues = embeddings.doForward(false, LayerWorkspaceMgr.builder().defaultNoWorkspace().build());
        logger.info("dense =                 " + dense);
        logger.info("encodingsValues =                 " + embeddingValues);
        return embeddingValues;
    }
*/


    private double euclideanDistance(INDArray array1, INDArray array2) {
        return array1.distance2(array2);
    }

    private void loadTrainVectorsModel() throws IOException {
        final File folder = new File(applicationConfig.getTrainImagePath());
        final File[] folders = folder.listFiles();
        for (final File folderEntry : folders) {
            if (folderEntry.isDirectory()) {
                final String[] split = folderEntry.getPath().split("\\\\");
                String name = split[split.length - 1];
                final File[] files = folderEntry.listFiles();
                logger.info("--------- add name=" + name);
                for (File file : files) {
                    final double[] featuresImage = getFeaturesImage(file);
                    memberModels.add(new VectorModel2(featuresImage, folderEntry.getPath(), name));
//                    INDArray indArray = getFeaturesImage2(file);
//                    memberModels.add(new VectorModel2(indArray, folderEntry.getPath(), name));
                    logger.info("add name=" + file.getName());
                }
            }
        }
    }

    private double[] getFeaturesImage(File file) throws IOException {
        INDArray imageMatrix = _nativeImageLoader.asMatrix(file);
        //INDArray imageMatrix = vggImageMatrix.ravel().dup();
        imagePreProcessor.transform(imageMatrix);
        DataSet objDataSet = new DataSet(imageMatrix, Nd4j.create(new float[]{0,0}));
        DataSet objFeaturized = _transferLearningHelper.featurize(objDataSet);
        INDArray featuresArray = objFeaturized.getFeatures();
        int reshapeDimension=1;
        for (long dimension : featuresArray.shape()) {
            reshapeDimension *= dimension;
        }
        featuresArray = featuresArray.reshape(1,reshapeDimension);

        // VGG
/*
        final List<String> recognise = recognise(imageMatrix);
        logger.info("recognise" + recognise);
*/
        // Yolo2
        Yolo2OutputLayer outputLayer = (Yolo2OutputLayer) computationGraph.getOutputLayer(0);
        INDArray results = computationGraph.outputSingle(imageMatrix);
        List<DetectedObject> detectedObjects = outputLayer.getPredictedObjects(results, 0.4);
        detectedObjects.forEach(d -> {
            logger.info(String.format("#   objects detected class=%s, x1=%s, y1=%s", labels[d.getPredictedClass()],
                    d.getTopLeftXY()[0], d.getTopLeftXY()[1]));
        });
        //
        return featuresArray.data().asDouble();
/*
        double nn[] =  {0};
        return nn;
*/
    }

    public List<String> recognise(INDArray imageMatrix) throws IOException {
        INDArray[] output = computationGraph.output(false, imageMatrix);
        return predict(output[0]);
    }

   private List<String> predict(INDArray predictions) throws IOException {
       List<String> objects = new ArrayList<>();
       int topN = 3;
       int[] topNPredictions = new int[topN];
       float[] topNProb = new float[topN];
       String[] outLabels = new String[topN];
       //brute force collect top N
       int i = 0;
       for (int batch = 0; batch < predictions.size(0); batch++) {
           INDArray currentBatch = predictions.getRow(batch).dup();
           while (i < topN) {
               topNPredictions[i] = Nd4j.argMax(currentBatch, 1).getInt(0);
               topNProb[i] = currentBatch.getFloat(batch, topNPredictions[i]);
               currentBatch.putScalar(0, topNPredictions[i], 0);
               outLabels[i] = imageNetLabels.getLabel(topNPredictions[i]);
               if (topNProb[i] > 0.4) {
                   objects.add(String.format("Label=%s, %s, %s", outLabels[i],
                           outLabels[i], topNProb[i]));
               }
               i++;
           }
       }
       return objects;
   }

/*
    private INDArray getFeaturesImage2(File file) throws IOException {
        INDArray imageMatrix = _nativeImageLoader.asMatrix(file);
        _scaler.transform(imageMatrix);
        return forwardPass(normalize(imageMatrix));
    }

    private static INDArray normalize(INDArray read) {
        return read.div(255.0);
    }
*/


}
