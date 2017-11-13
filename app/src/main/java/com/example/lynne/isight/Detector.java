package com.example.lynne.isight;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import junit.framework.Assert;

import org.tensorflow.demo.*;
import org.tensorflow.demo.Classifier;
import org.tensorflow.demo.env.BorderedText;
import org.tensorflow.demo.env.ImageUtils;
import org.tensorflow.demo.env.Logger;
import org.tensorflow.demo.tracking.MultiBoxTracker;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * Created by Lynne on 11/7/2017.
 */

public class Detector {

    /**
     * parent_Activity
     */
    Activity parent_Activity;


    private static final Logger LOGGER = new Logger();

    // Configuration values for the prepackaged multibox model.
    private static final int MB_INPUT_SIZE = 224;
    private static final int MB_IMAGE_MEAN = 128;
    private static final float MB_IMAGE_STD = 128;
    private static final String MB_INPUT_NAME = "ResizeBilinear";
    private static final String MB_OUTPUT_LOCATIONS_NAME = "output_locations/Reshape";
    private static final String MB_OUTPUT_SCORES_NAME = "output_scores/Reshape";
    private static final String MB_MODEL_FILE = "file:///android_asset/multibox_model.pb";
    private static final String MB_LOCATION_FILE =
            "file:///android_asset/multibox_location_priors.txt";

    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final String TF_OD_API_MODEL_FILE =
            "file:///android_asset/ssd_mobilenet_v1_android_export.pb";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/coco_labels_list.txt";

    // Configuration values for tiny-yolo-voc. Note that the graph is not included with TensorFlow and
    // must be manually placed in the assets/ directory by the user.
    // Graphs and models downloaded from http://pjreddie.com/darknet/yolo/ may be converted e.g. via
    // DarkFlow (https://github.com/thtrieu/darkflow). Sample command:
    // ./flow --model cfg/tiny-yolo-voc.cfg --load bin/tiny-yolo-voc.weights --savepb --verbalise
    private static final String YOLO_MODEL_FILE = "file:///android_asset/graph-tiny-yolo-voc.pb";
    private static final int YOLO_INPUT_SIZE = 416;
    private static final String YOLO_INPUT_NAME = "input";
    private static final String YOLO_OUTPUT_NAMES = "output";
    private static final int YOLO_BLOCK_SIZE = 32;

    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.  Optionally use legacy Multibox (trained using an older version of the API)
    // or YOLO.
    private enum DetectorMode {
        TF_OD_API, MULTIBOX, YOLO;
    }
    private static final Detector.DetectorMode MODE = Detector.DetectorMode.TF_OD_API;

    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.6f;
    private static final float MINIMUM_CONFIDENCE_MULTIBOX = 0.1f;
    private static final float MINIMUM_CONFIDENCE_YOLO = 0.25f;

    private static final boolean MAINTAIN_ASPECT = MODE == Detector.DetectorMode.YOLO;

    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);

    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;

    private Integer sensorOrientation;

    private org.tensorflow.demo.Classifier detector;

    private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private boolean computingDetection = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private MultiBoxTracker tracker;

    private byte[] luminanceCopy;

    private BorderedText borderedText;


    int cropSize;


    /**
     * method to setup instance of a Classifier stored in class variable detector depending on the MODE
     * value set for this class (for YOLO, MULTIBOX or Object Detection API (uses SSD trained model))
     * expects the trained model (.pb file)  and the trained labels list (.txt file) to be located
     * in the applications assets directory --see above for hardcoded locations for each type of
     * detector
     * @param parent_Activity parent Activity that invokes this method as need to be able to
     *                        grab assets folder of application as well as output a toast message to
     *                        Activity in case problems with creating the Detector.
     */
    public void setupDetector(Activity parent_Activity){


        // save parent_Activity for use later for drawing
        this.parent_Activity = parent_Activity;


        cropSize = TF_OD_API_INPUT_SIZE;

        //create Detector as instance of either TensorFlowYoloDetect,
        // TensorFlowMultiBoxDetector or TensorFlowObjectDetectionAPIModel
        //depending on the MODE set for this class

        if (MODE == Detector.DetectorMode.YOLO) {
            detector =
                    TensorFlowYoloDetector.create(
                            parent_Activity.getAssets(),
                            YOLO_MODEL_FILE,
                            YOLO_INPUT_SIZE,
                            YOLO_INPUT_NAME,
                            YOLO_OUTPUT_NAMES,
                            YOLO_BLOCK_SIZE);
            cropSize = YOLO_INPUT_SIZE;
        } else if (MODE == Detector.DetectorMode.MULTIBOX) {
            detector =
                    TensorFlowMultiBoxDetector.create(
                            parent_Activity.getAssets(),
                            MB_MODEL_FILE,
                            MB_LOCATION_FILE,
                            MB_IMAGE_MEAN,
                            MB_IMAGE_STD,
                            MB_INPUT_NAME,
                            MB_OUTPUT_LOCATIONS_NAME,
                            MB_OUTPUT_SCORES_NAME);
            cropSize = MB_INPUT_SIZE;
        } else {
            try {
                detector = TensorFlowObjectDetectionAPIModel.create(
                        parent_Activity.getAssets(), TF_OD_API_MODEL_FILE, TF_OD_API_LABELS_FILE, TF_OD_API_INPUT_SIZE);
                cropSize = TF_OD_API_INPUT_SIZE;
            } catch (final IOException e) {
                LOGGER.e("Exception initializing classifier!", e);
                Toast toast =
                        Toast.makeText(
                                parent_Activity.getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
                toast.show();

            }
        }


        //LLL**************************************************************************
        //LLL - add code to setup the croppedBitmap size as cropSizexcropSize
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);



    }


    /**
     * method to process the Image by passing it to the detector (Classifier instance previously setup)
     * using as input the imageBitmap
     * @param imageBitmap image want to
     *
     * @return Bitmap which is bitmap which has drawn on top rectangles to represent the locations of
     * detected objects in the image along with their identity and confidence value
     */
    protected Bitmap processImage(Bitmap imageBitmap) {


        ++timestamp;
        final long currTimestamp = timestamp;
        //if detector not set up do not process the image
        if (this.detector == null)
            return imageBitmap;




        //LLL- *************************************************************************
        //LLL -- add code to do creation of scaled image using Matrix transformation
        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        imageBitmap.getWidth(), imageBitmap.getHeight(),
                        cropSize, cropSize,
                        0, false);

        /*frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        imageBitmap.getWidth(), imageBitmap.getHeight(),
                        cropSize, cropSize,
                        0, MAINTAIN_ASPECT);*/

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

             //now create a canvas from a new bitmap that will be the size of cropSizexcropSize

        final Canvas canvas1 = new Canvas(croppedBitmap);
        canvas1.drawBitmap(imageBitmap, frameToCropTransform, null);  //copy over and scale to get cropSize x cropSize in canvas
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);//saves to "preview.png"  NOT NEEDED
        }

        //END-**********************************************************

        //downsize input imageBitMap to appropriate size cropSize
        //LLL- this.croppedBitmap = ImageUtil.scaleBitmapDown(imageBitmap, this.cropSize);


        //Now pass this to Classifier detector to perform inference (recognition)
        LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");


        //run in background -- LYNNE TO DO make in background
        LOGGER.i("Running detection on image " + currTimestamp);
        final long startTime = SystemClock.uptimeMillis();
        final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;


        //Now create a new image where display the image and the results of the recognition
        Bitmap newResultsBitmap = Bitmap.createBitmap(imageBitmap);


        final Canvas canvas = new Canvas(newResultsBitmap);  //grab canvas for drawing associated with the newResultsBitmap
        final Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);
        paint.setTextSize(paint.getTextSize()*2);  //double default text size
        Random rand = new Random();
        float label_x, label_y;  //location of where will print the recogntion result label+confidence


        //setup thresholds on confidence --anything less and will not draw that recognition result
        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
        switch (MODE) {
            case TF_OD_API:
                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                break;
            case MULTIBOX:
                minimumConfidence = MINIMUM_CONFIDENCE_MULTIBOX;
                break;
            case YOLO:
                minimumConfidence = MINIMUM_CONFIDENCE_YOLO;
                break;
        }

        final List<Classifier.Recognition> mappedRecognitions =
                new LinkedList<Classifier.Recognition>();

        //dummy paint
        paint.setColor(Color.RED);
        canvas.drawRect(new RectF(100,100,200,200), paint);
        canvas.drawText("dummy",150,150, paint);


        //cycle through each recognition result and if confidence > minimumConfidence then draw the location as a rectange and display info
        // REMEMBER that the order of the results are the most confident first
        for (final Classifier.Recognition result : results) {
            final RectF location = result.getLocation();
           // if (location != null && result.getConfidence() >= minimumConfidence) {
            if (location != null && result.getConfidence() >= 0.1) {


                    newResultsBitmap = Bitmap.createBitmap(imageBitmap);

                //setup color of paint randomly
                // generate the random integers for r, g and b value
                paint.setARGB(255, rand.nextInt(255), rand.nextInt(255), rand.nextInt(255));

                //we must scale the original location to correctly resize
                //RectF scaledLocation = this.scaleBoundingBox(location, croppedBitmap.getWidth(), croppedBitmap.getHeight(),imageBitmap.getWidth(), imageBitmap.getHeight());
                // canvas.drawRect(scaledLocation, paint);
                cropToFrameTransform.mapRect(location);
                RectF scaledLocation  = new RectF();
                scaledLocation.set(location);
                canvas.drawRect(scaledLocation, paint);

                result.setLocation(scaledLocation);
                //draw out the recognition label and confidence
                label_x = (scaledLocation.left + scaledLocation.right)/2;
                label_y = scaledLocation.top + 16;
                canvas.drawText(result.toString(), label_x, label_y, paint);

                mappedRecognitions.add(result);  ///do we need this??
            }
        }



        return newResultsBitmap;

    }



    /**
     * method to process the Image by passing it to the detector (Classifier instance previously setup)
     * using as input the imageBitmap
     * @param imageBitmap image want to
     *
     * @return List<Classifier.Recognition> which is the list of Recognition instances
     *         above the confidence level set.
     */
    protected List<Classifier.Recognition> recognizeImage(Bitmap imageBitmap) {
        final List<Classifier.Recognition> mappedRecognitions =
                new LinkedList<Classifier.Recognition>();

        ++timestamp;
        final long currTimestamp = timestamp;
        //if detector not set up do not process the image
        if (this.detector == null)
            return mappedRecognitions;




        //LLL- *************************************************************************
        //LLL -- add code to do creation of scaled image using Matrix transformation
        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        imageBitmap.getWidth(), imageBitmap.getHeight(),
                        cropSize, cropSize,
                        0, false);

        /*frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        imageBitmap.getWidth(), imageBitmap.getHeight(),
                        cropSize, cropSize,
                        0, MAINTAIN_ASPECT);*/

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        //now create a canvas from a new bitmap that will be the size of cropSizexcropSize

        final Canvas canvas1 = new Canvas(croppedBitmap);
        canvas1.drawBitmap(imageBitmap, frameToCropTransform, null);  //copy over and scale to get cropSize x cropSize in canvas
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);//saves to "preview.png"  NOT NEEDED
        }

        //END-**********************************************************

        //downsize input imageBitMap to appropriate size cropSize
        //LLL- this.croppedBitmap = ImageUtil.scaleBitmapDown(imageBitmap, this.cropSize);


        //Now pass this to Classifier detector to perform inference (recognition)
        LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");


        //run in background -- LYNNE TO DO make in background
        LOGGER.i("Running detection on image " + currTimestamp);
        final long startTime = SystemClock.uptimeMillis();
        final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
        LOGGER.i("recognizeImage:" + "time to process" + lastProcessingTimeMs);

        //setup thresholds on confidence --anything less and will remove from the results
        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
        switch (MODE) {
            case TF_OD_API:
                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                break;
            case MULTIBOX:
                minimumConfidence = MINIMUM_CONFIDENCE_MULTIBOX;
                break;
            case YOLO:
                minimumConfidence = MINIMUM_CONFIDENCE_YOLO;
                break;
        }




        //cycle through each recognition result and if confidence > minimumConfidence then
        // add to the mappedRecognitions List.
        // REMEMBER that the order of the results are the most confident first
        for (final Classifier.Recognition result : results) {
            final RectF location = result.getLocation();
            if (location != null && result.getConfidence() >= minimumConfidence) {
            //if (location != null && result.getConfidence() >= 0.1) {


                //we must scale the original location to correctly resize
                //RectF scaledLocation = this.scaleBoundingBox(location, croppedBitmap.getWidth(), croppedBitmap.getHeight(),imageBitmap.getWidth(), imageBitmap.getHeight());
                // canvas.drawRect(scaledLocation, paint);
                cropToFrameTransform.mapRect(location);
                RectF scaledLocation  = new RectF();
                scaledLocation.set(location);

                result.setLocation(scaledLocation);

                mappedRecognitions.add(result);  ///add results with new scaled location to mappedRecogntions
            }
        }



        return mappedRecognitions;

    }




    /**
     * have Rectangle boundingBox represented in a scaled space of
     *  width x height  we need to convert it to scaledBoundingBox rectangle in
     * a space of scaled_width x scaled_height
     * @param boundingBox
     * @param width
     * @param height
     * @param scaled_width
     * @param scaled_height
     * @return scaledBoundingBox
     */
    public RectF scaleBoundingBox( RectF boundingBox, int width, int height, int scaled_width, int scaled_height){


        float widthScaleFactor = ((float) scaled_width)/width;
        float heightScaleFactor = ((float) scaled_height)/height;

        RectF scaledBoundingBox = new RectF(boundingBox.left*scaled_width, boundingBox.top*heightScaleFactor,
                                            boundingBox.right*scaled_width, boundingBox.bottom*heightScaleFactor);

        return scaledBoundingBox;

    }




}

