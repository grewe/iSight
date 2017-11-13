package com.example.lynne.isight;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroupOverlay;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.extensions.android.json.AndroidJsonFactory;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequest;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;

import junit.framework.Assert;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.demo.Classifier;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

//Add Google Cloud API and related imports
//imports for TensorFLow


public class MainActivityBackup extends AppCompatActivity implements CvCameraViewListener2, OnItemSelectedListener
{


    /**
     * overlay ImageView that is part of parent_Activity's interface that is overlayed on top
     * of the OpenCV CameraView widget
     */
    ImageView overlay_ImageView;
    Handler handler;
    ViewGroupOverlay VGO;
    String runMode = "ICON";  //options are ICON, ENHANCE, SPLIT
    //number of seconds want to delay between start of display of special info and end
    int ICON_DISPLAY_TIME_MS;




    int count = 0;
    CameraBridgeViewBase mOpenCvCameraView;// will point to our View widget for our image
    Spinner spinner_menu;

    //grab array of possible menu items from strings.xml file
    String[] menu_items;

    String menu_item_selected;

    //declare Goolge Cloud Vision objects overlay_ImageView.setImageResource(R.drawable.person);
    //overlay_ImageView.setImageResource(drawable_image_id);
    //overlay_ImageView.setVisibility(View.VISIBLE);
    Vision vision;
    private TextView mImageDetails;
    private static final String TAG = MainActivityBackup.class.getSimpleName();
    int COUNT_GOOGLE_CLOUD_TRIGGER = 300;

    //declare Tensorflow objects
    Detector tensorFlowDetector = new Detector();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //grab a handle to text view for placing image details
        mImageDetails = (TextView) findViewById(R.id.textView);

        //get display time for special display
        //grab the number of seconds want to delay between start of display of special info and end
        ICON_DISPLAY_TIME_MS = this.getResources().getInteger(R.integer.ICON_DISPLAY_TIME_MS);
        handler = new Handler();

        //setup menu from strings.xml file
        this.menu_items = getResources().getStringArray(R.array.spinner_menu);
        this.menu_item_selected = menu_items[0];  //initialize to first item in arry
        Log.i("SPINNER", "menu item is " + this.menu_item_selected);


        //grab a handle to spinner_menu in the XML interface
        spinner_menu = (Spinner)findViewById(R.id.spinner_menu);
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.spinner_menu, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        spinner_menu.setAdapter(adapter);
        spinner_menu.setSelection(0);//initialize to first item in menu
        //set this activity to listen to the menu choice in spinner
        spinner_menu.setOnItemSelectedListener(this);


        //grab a handle to the overlay ImageView for potential use later in drawing
        overlay_ImageView = (ImageView) findViewById(R.id.OverlayImageView);
        overlay_ImageView.setImageResource(R.drawable.car);


        //grab a "handle" to the OpenCV class responsible for viewing Image
        // look at the XML the id of our CamerBridgeViewBase is HelloOpenCVView
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.HelloOpenCvView);
        mOpenCvCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);  //the activity will listen to events on Camera



        //setup google Cloud Vision stuff
        Vision.Builder visionBuilder = new Vision.Builder(
                new NetHttpTransport(),
                new AndroidJsonFactory(),
                null);

        visionBuilder.setVisionRequestInitializer(
                new VisionRequestInitializer(getString(R.string.CLOUD_VISION_API_KEY)));


        vision = visionBuilder.build();
    }


    //Code will tell us when camera connected it will enable the mOpenCVCameraView
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i("OPENCV", "OpenCV loaded successfully");

                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };


    @Override  public void onResume()  {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, this, mLoaderCallback);
    }


    @Override
    public void onPause()   {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }


    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();   }

    public void onCameraViewStarted(int width, int height) {
        //in case the TensorFlow option is chosen you must create instance of Detector
        //depending on value of MODE variable in Detector class will load one of a few hard
        // coded types of tensorflow models (ObjectDetectionAPIModel or Yolo or Multibox) and
        // the associated asset files representing the pre-trained model file (.pb extension) and
        // the class labels --objects we are detecting (*.txt) see Detector class for details
        this.tensorFlowDetector.setupDetector(this);


        //some code to grab the ViewGroupOverlay associated with LinearLayout cameraLinearLayout where normally have camera preview

        //THIS WORKS
        VGO =  ((ViewGroup) findViewById(R.id.cameraLinearLayout)).getOverlay();
        this.overlay_ImageView.setImageResource(R.drawable.person);
        this.overlay_ImageView.measure(View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
        this.overlay_ImageView.layout(0, 0, 100, 100);
        VGO.add(this.overlay_ImageView);
        this.overlay_ImageView.setVisibility(View.VISIBLE);


/*
        ViewGroupOverlay VGO =  ((ViewGroup) findViewById(R.id.cameraLinearLayout)).getOverlay();
        Drawable myIcon = getResources().getDrawable(R.drawable.person);
        VGO.add(myIcon);
*/


    }

    public void onCameraViewStopped() {   }

    // THIS IS THE main method that is called each time you get a new Frame/Image
    // it should return a Mat that will be displayed in the corresponding JavaCameraView widget that should be part of the
    //  xml interface for this activity that is associated with this class's variable mOpenCvCameraView  which is
    //  associated with JavaCameraView widget (see onCreate method above)
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        Mat imageMat = inputFrame.rgba();

        Mat gray = inputFrame.gray();



        // now you use the Mat class to represent the Image and you can use method calls

        // make calls like to get a pixel at i,j   imageMat.get
        // double pixel[] = new double[3];
        // pixel = imageMat.get(20,10);  this wil retrieve pixel and column = 20, row =10
        //similarly can set a pixel in Mat  via imageMat.set(i,j,pixel);
        // read API on Mat class for OPenCV

        // A VERY USEFUL class to call image processing routines is ImagProc
        // This code in comments shows how to do the Sobel Edge Detection on our image in imageMat
       /*
            Mat gray = inputFrame.gray();
            Mat mIntermediateMat = new Mat();
            Imgproc.Sobel(gray, mIntermediateMat, CvType.CV_8U, 1, 1);
            Core.convertScaleAbs(mIntermediateMat, mIntermediateMat, 10, 0);
            Imgproc.cvtColor(mIntermediateMat, imageMat, Imgproc.COLOR_GRAY2BGRA, 4);

         */


        //Based on spinner menu selected item stored as this.menu_item_selected perform appropriate
        // operation and return a Mat
        if(this.menu_item_selected.equals("Random")) {   //Random

            //return imageMat;
            //create random number 0 to 1 and return color if < .5 and grey otherwise
            Random rand = new Random(System.currentTimeMillis());

            if (rand.nextDouble() < 0.5)
            {    Log.d("SPINNER", "return color");  return imageMat;}
            else
            {    Log.d("SPINNER", "return greyscale"); return gray;}
        }
        if(this.menu_item_selected.equals("TensorFlow")) {   //Random


            //going to use created instance of Detector class to perform detection
            //convert imageMat to a bitmap
            Bitmap bitmap = Bitmap.createBitmap(imageMat.width(),  imageMat.height(),Bitmap.Config.ARGB_8888);;
            Utils.matToBitmap(imageMat,bitmap);



            //now call detection on bitmap depending on the MODE you are running it will doand it will return bitmap that has results drawn into it
            // ICON - returns List<Classifier.Recognition> that can cycle through
            //         to create a time sequence of ICONs displayed in the Overlay View
            //         and also at the end returns the original bitmap
            //
            //
            // ENHANCE - returns List<Classifier.Recognition> that can cycle through
            //         to create a time sequence of Enhanced images displayed in the Overlay View
            //         and also at the end returns the original bitmap
            //
            //
            // SPLIT - returns List<Classifier.Recognition> that can cycle through
            //         to create a time sequence of ICONs + ENHANCED images displayed in the Overlay View
            //         and also at the end returns the original bitmap
            //
            //
            // NONE of ABOVE - will call processImage to actualy return a bitmap that
            //                 contains bounding boxes and labels with confidence levels displayed

            Bitmap resultsBitmap = Bitmap.createBitmap(imageMat.width(),  imageMat.height(),Bitmap.Config.ARGB_8888);;
            Utils.matToBitmap(imageMat,resultsBitmap);


            if(runMode == "ICON"){
                //recognize using tensorflow detector on the bitmap -get list of Recongition instances
               List <Classifier.Recognition> recognitionList = this.tensorFlowDetector.recognizeImage(bitmap);

               //now cycle through each recognition instance and display related ICON on overaly View
                //cycle through each recognition result and if confidence > minimumConfidence then draw the location as a rectange and display info
                // REMEMBER that the order of the results are the most confident first
                for (final Classifier.Recognition result : recognitionList) {
                   this.displayIconForRecognition(result);
                }

            }
            else if(runMode == "ENHANCE"){
               // newResultsBitmap = EnhanceImage.enhanceImage(imageBitmap, location, result.getTitle());
            }
            else if(runMode == "SPLIT"){
               // newResultsBitmap = Bitmap.createBitmap(imageBitmap);
            }
            else {
                resultsBitmap = this.tensorFlowDetector.processImage(bitmap);
            }



            //convert the bitmap back to Mat
            Mat returnImageMat = new Mat();
            Utils.bitmapToMat(resultsBitmap, returnImageMat);
            //return imageMat;
            //create random number 0 to 1 and return color if < .5 and grey otherwise
            Random rand = new Random(System.currentTimeMillis());

            Log.d("SPINNER", "return tensorflowresults");
            return returnImageMat;

        }
        else if(this.menu_item_selected.equals("Google Cloud Vision API")) { //Google Cloud Vision API --call for label detection
            Log.d("SPINNER", "return Google Cloud Vision API");

            //only trigger processing through Google Cloud Vision API
            if(count % COUNT_GOOGLE_CLOUD_TRIGGER != 0)
                return imageMat;

            count++; //increment trigger counter

            //process the color image imageMat


            //convert imageMat to a bitmap
            Bitmap bitmap = Bitmap.createBitmap(imageMat.width(),  imageMat.height(),Bitmap.Config.ARGB_8888);;
            Utils.matToBitmap(imageMat,bitmap);

            //scale it down to 640x480 so it is smaller
            Bitmap smallerBitmap = this.scaleBitmapDown(bitmap, 640);

            //call method to pass bitmap to cloudVision
            try {
                callCloudVision(smallerBitmap, imageMat);
            }catch(IOException i){Log.e(TAG, "it is failing"+i.getMessage());}

            return imageMat;

        }
        else if(this.menu_item_selected.equals("Greyscale")) { //Greyscale
            Log.d("SPINNER", "return greyscale");
            return gray;
        }

        else if(this.menu_item_selected.equals("My Fast Greyscale")) {   //using method of converting to Java array for faster processing
            int threshold = 100;

          /*
             SPECIAL NOTE
             If CvType.CV_8U would use byte array BUT, warning java has signed not unsiged bytes
              so pixel values would go from -128 to 128 and not the normal 0 to 255
                  byte[] imageB = new byte[(int) (gray.total() * gray.channels())];
                 imageMat.get(0, 0, imageB);

                 to avoid this first convert the Mat to 16UC4 and then create short[] array
                    imageMat.convertTo(imageMat, CvType.CV_16UC4);
                    short[] image = new short[(int) (gray.total() * gray.channels())];
                    imageMat.get(0,0, image);

             If CvType.CV_32S  use int array
             If  CvType.CV_32F use float array
             If CvType.CV_64F use double array

             IMPORTANT: it doesn't matter how many channels - gray = 1, color =3 the code below will appropriately
             handle it.   For example, we have an imageMat that is color and of type CvType.CV_8UC4
         */


            //to avoid issues with Java's SIGNED byte array convert Mat from 8 to 16 and
            //save in a short[] array
            imageMat.convertTo(imageMat, CvType.CV_16UC4);
            short[] image = new short[(int) (imageMat.total() * imageMat.channels())];
            imageMat.get(0,0, image);

            //PROCESS the image --do a simple threshold
            // if wanted to do it as row, col would write
            int width = imageMat.width();
            int height = imageMat.height();
            int num_pixels = (int) imageMat.total();
            int channels = imageMat.channels();
            int sum;
            for (int r = 0; r < height; r++)
                for (int c = 0; c < width; c++)
                {   sum = 0;
                    for (int i = 0; i < 3; i++)  //processing first 3 channels of rgba - 4 channel color
                    {
                        //convert color to grey by averaging the 3 color values and dividing by 3
                        sum += image[r * (width * channels) + c * channels + i];
                    }

                    for (int i = 0; i < 3; i++)  //for color set only 3 color channels  dont touch alpha channel
                        image[r *(width*channels) + c*channels +i ] = (short) (sum / 3);

                }


            //put back the image calculated into the Mat gray object that is class variable
            imageMat.put(0,0, image);

            //seems we need to convert back to original gray for it to display correctly in JavaCameraView
            imageMat.convertTo(imageMat,CvType.CV_8UC4);
            Log.i("what", "wrong");

            return imageMat;
        }
        else if(this.menu_item_selected.equals("My Slow Greyscale")) {   //using method of converting to Java array for faster processing
            int threshold = 100;



            //PROCESS the image --do a simple threshold
            // if wanted to do it as row, col would write
            int width = imageMat.width();
            int height = imageMat.height();
            int num_pixels = (int) imageMat.total();
            double [] pixel = new double[3];
            int channels = imageMat.channels();
            int sum;
            for (int r = 0; r < height; r++)
                for (int c = 0; c < width; c++)
                {   sum = 0;
                    pixel = imageMat.get(r,c);
                    for (int i = 0; i < 3; i++)  //processing first 3 channels of rgb
                    {    //convert color to grey by averaging the 3 color values and dividing by 3
                        sum += pixel[i];
                    }

                    sum = sum /3;


                    for (int i = 0; i < 3; i++)  //for color set only 3 color channels  dont touch alpha channel
                        pixel[i] = sum;

                    imageMat.put(r,c,pixel);

                }



            Log.i("what", "wrong");

            return imageMat;
        }
        else if(this.menu_item_selected.equals("Threshold")) {  //currently always threshold the greyscale image at value of 50

            /* FROM OPENCV DOCUMENTATION :   threshold(Mat src,Mat dst,double thresh, double maxval,int type)

               The function applies fixed-level thresholding to a single-channel array.
               The function is typically used to get a bi-level (binary) image out of a grayscale image


                type = THRESH_BINARY
                   if src(x,y) > thresh  then dest(x,y) = maxval; 0 otherwise
            */
            Log.i("SPINNER", "performing thresholding");
            Imgproc.threshold(gray,gray, 50.0, 255.0, Imgproc.THRESH_BINARY );
            return gray;

        }
        else if(this.menu_item_selected.equals("Edge Detect")) {  //edge detect
            Mat edge = gray.clone(); //copy of gray Mat
            Log.i("SPINNER", "performing edge detection");
            Imgproc.Laplacian(gray,edge, gray.depth());
            return edge;
        }
        else if(this.menu_item_selected.equals("Edgy")){ //edgy processing
            Mat edge = gray.clone(); //copy of gray Mat
            Mat binary_edge = edge.clone();
            Mat red_image = imageMat.clone();
            red_image.setTo(new Scalar(255.0,0.0,0.0));
            Log.i("SPINNER", "performing edgy processing");
            Imgproc.Laplacian(gray,edge, gray.depth()); //Laplacian edge detect
            Imgproc.threshold(edge,binary_edge, 80,255,Imgproc.THRESH_BINARY); //keep only strongest edges and make a binary image

            Core.bitwise_or(red_image, imageMat, imageMat, binary_edge); //create red image
            //   Core.bitwise_and(new Scalar(255.0,0.0,0.0), imageMat, imageMat, binary_edge); //create red image
            return imageMat;


        }
        else if(this.menu_item_selected.equals("Best Lines")) {   //find best hough lines
            //step 1 perform laplacian on gray image
            Log.i("SPINNER", "performing Best Lines");
            //Imgproc.Laplacian(gray,gray, gray.depth()); //Laplacian edge detect
            Imgproc.Canny(gray, gray, 80, 255);


            //step 2 perform LhouLines
            Mat lines = new Mat();
            int threshold_line = Math.min(gray.cols(), gray.rows()) / 6; //equal to 1/4th of the width or height of image
            Imgproc.HoughLines(gray,lines,1, Math.PI/180, threshold_line);

            //step 3 draw the lines in blue on top of original image
            for(int i=0; i<lines.rows(); i++) //cycle through the lines
            {
                double[] values = lines.get(i,0); //get ith line
                double rho = values[0];
                double theta = values[1];
                double a = Math.cos(theta);
                double b = Math.sin(theta);
                double x0 = a*rho;
                double y0 = b*rho;
                double x1,y1,x2,y2;
                x1 = x0 + 1000*(-b);
                y1 = y0 + 1000*a;
                x2 = x0 - 1000*(-b);
                y2 = y0 - 1000*a;

                Point pt1 = new Point(x1,y1);
                Point pt2 = new Point(x2,y2);

                //draw line son the image
                Imgproc.line(imageMat, pt1,pt2, new Scalar(0,0,255),1);

            }





            //Probablistic HoughLines
            //hough store in lines where resoltuion of rho is pi/180  and theta is 50 cells, where 20 is min votes and 20 is max gap
           /* Imgproc.HoughLinesP(gray, lines, 1, Math.PI/180, 50, 10, 10);


            //step 3 draw the lines in blue on top of original image
            for(int i=0; i<lines.rows(); i++) //cycle through the lines
            {
                double[] points = lines.get(i,0); //get ith line
                double x1,y1,x2,y2;
                x1 = points[0];
                y1 = points[1];
                x2 = points[2];
                y2 = points[3];
                Point pt1 = new Point(x1,y1);
                Point pt2 = new Point(x2,y2);

                //draw line son the image
                Imgproc.line(imageMat, pt1,pt2, new Scalar(255,0,0),1);

            }
            */


            return imageMat;


        }
        else if(this.menu_item_selected.equals("My Slow Threshold")) {   //do tons of OpenCV Mat.get and Mat.put calls--expensive JNI calls --SLOW
            int threshold = 100;
            double[] zero = new double[3];
            zero[0] = zero [1] = zero[2] = 0.0;

            double[] white = new double[3];
            white[0] = white[1] = white[2] = 255.0;
            // if wanted to do it as row, col would write
            int width = gray.width();
            int height = gray.height();
            int num_pixels = (int) gray.total();
            int channels = gray.channels();
            double[] pixel = new double[3];
            int[][] image = new int[height][width];
            for (int r = 0; r < height; r++)
                for (int c = 0; c < width; c++)
                {        double value = gray.get(r,c)[0];
                    //to get pixel corresponding to row r and column c
                    if ( gray.get(r,c)[0]  < threshold)
                    {  gray.put(r,c, zero);
                        image[r][c] = 0;
                        // gray.put(r,c, new byte[]{0,0,0});
                    }
                    else {
                        gray.put(r, c, white);
                        image[r][c] = 255;
                        //.put(r,c, new byte[]{(byte) 255,(byte) 255, (byte) 255});
                    }
                    pixel = gray.get(r,c);
                    Log.i("pixel", pixel[0] + "");
                }

            Log.i("ending", "now");
            return gray;

        }

        else if(this.menu_item_selected.equals("My Fast Threshold")) {   //using method of converting to Java array for faster processing
            int threshold = 100;


          /*
             SPECIAL NOTE
             If CvType.CV_8U would use byte array BUT, warning java has signed not unsiged bytes
              so pixel values would go from -128 to 128 and not the normal 0 to 255
                  byte[] imageB = new byte[(int) (gray.total() * gray.channels())];
                 gray.get(0, 0, imageB);

                 to avoid this first convert the Mat to 16UC1 and then create short[] array
                    gray.convertTo(gray, CvType.CV_16UC1);
                    short[] image = new short[(int) (gray.total() * gray.channels())];
                    gray.get(0,0, image);

             If CvType.CV_32S  use int array
             If  CvType.CV_32F use float array
             If CvType.CV_64F use double array

             IMPORTANT: it doesn't matter how many channels - gray = 1, color =3 the code below will appropriately
             handle it.   For example, we have an imageMat that is color and of type CvType.CV_8UC4
         */


            //to avoid issues with Java's SIGNED byte array convert Mat from 8 to 16 and
            //save in a short[] array
            gray.convertTo(gray,CvType.CV_16UC1);
            short[] image = new short[(int) (gray.total() * gray.channels())];
            gray.get(0, 0, image);


            //PROCESS the image --do a simple threshold
            // if wanted to do it as row, col would write
            int width = gray.width();
            int height = gray.height();
            int num_pixels = (int) gray.total();
            int channels = gray.channels();
            for (int r = 0; r < height; r++)
                for (int c = 0; c < width; c++)
                    for (int i = 0; i < channels; i++)  //in case color channels =3 otherwise for gray =1
                    {
                        //to get pixel corresponding to row r and column c
                        if (image[r * (width * channels) + c * channels + i] < threshold)
                            image[r * (width * channels) + c * channels + i] = 0;
                        else
                            image[r * (width * channels) + c * channels + i] = 255;
                    }


            //put back the image
            gray.put(0,0, image);

            //seems we need to convert back to original gray for it to display correctly in JavaCameraView
            gray.convertTo(gray,CvType.CV_8U);
            return gray;
        }
        else if(this.menu_item_selected.equals("My Best Lines")) {
            int threshold = 100;

            short[] image = new short[(int) (gray.total() * gray.channels())];

            // gray.convertTo(gray,CvType.CV_32FC1);
            //  double[] image = new double[(int) (gray.total() * gray.channels())];

            gray.get(0, 0, image);

            Log.i("SPINNER", "performing My Best Lines");
            //Imgproc.Laplacian(gray,gray, gray.depth()); //Laplacian edge detect
            // Imgproc.Canny(gray, gray, 80, 255);
            //cycle through array
            // if wanted to do it as row, col would write
            int width = gray.width();
            int height = gray.height();
            int num_pixels = (int) gray.total();
            int channels = gray.channels();
            for (int r = 0; r < height; r++)
                for (int c = 0; c < width; c++)
                    for (int i = 0; i < channels; i++)  //in case color channels =3 otherwise for gray =1
                    {
                        //to get pixel corresponding to row r and column c
                        if (image[r * (width * channels) + c * channels + i] < threshold)
                            image[r * (width * channels) + c * channels + i] = 0;
                        else
                            image[r * (width * channels) + c * channels + i] = 255;
                    }
            //if simply visiting each pixel and not visiting neighbors where need to know row, column
            for (int index = 0; index < num_pixels; index++)
                for (int i = 0; i < channels; i++)   //in case color channels =3 otherwise for gray =1
                {
                    //to get pixel corresponding to row r and column c
                    if (image[index * channels + i] < threshold)
                        image[index * channels + i] = 0;
                    else
                        image[index * channels + i] = (byte) 255;
                }

            //place back into a Mat for OpenCV calls
            gray.put(0, 0, image);
            return gray;
        }

        //step 2 perform LhouLines
       /*     Mat lines = new Mat();
            int threshold_line = Math.min(gray.cols(), gray.rows()) / 6; //equal to 1/4th of the width or height of image
            Imgproc.HoughLines(gray,lines,1, Math.PI/180, threshold_line);

            //step 3 draw the lines in blue on top of original image
            for(int i=0; i<lines.rows(); i++) //cycle through the lines
            {
                double[] values = lines.get(i,0); //get ith line
                double rho = values[0];
                double theta = values[1];
                double a = Math.cos(theta);
                double b = Math.sin(theta);
                double x0 = a*rho;
                double y0 = b*rho;
                double x1,y1,x2,y2;
                x1 = x0 + 1000*(-b);
                y1 = y0 + 1000*a;
                x2 = x0 - 1000*(-b);
                y2 = y0 - 1000*a;

                Point pt1 = new Point(x1,y1);
                Point pt2 = new Point(x2,y2);

                //draw line son the image
                Imgproc.line(imageMat, pt1,pt2, new Scalar(0,0,255),1);

            }





            //Probablistic HoughLines
            //hough store in lines where resoltuion of rho is pi/180  and theta is 50 cells, where 20 is min votes and 20 is max gap
           /* Imgproc.HoughLinesP(gray, lines, 1, Math.PI/180, 50, 10, 10);


            //step 3 draw the lines in blue on top of original image
            for(int i=0; i<lines.rows(); i++) //cycle through the lines
            {
                double[] points = lines.get(i,0); //get ith line
                double x1,y1,x2,y2;
                x1 = points[0];
                y1 = points[1];
                x2 = points[2];
                y2 = points[3];
                Point pt1 = new Point(x1,y1);
                Point pt2 = new Point(x2,y2);

                //draw line son the image
                Imgproc.line(imageMat, pt1,pt2, new Scalar(255,0,0),1);

            }
            */


        //   return imageMat;



        //}
        else if(this.menu_item_selected.equals("Corners")) {
            Log.i("SPINNER", "performing Corners");
            //convert gray to binary
            Imgproc.threshold(gray,gray, 50.0, 255.0, Imgproc.THRESH_BINARY );



            //now perform corner detection
            return this.HarrisCorner(gray);


        }
        else if(this.menu_item_selected.equals("Dilate")) {
            Log.i("SPINNER", "performing Dilate");
            //convert gray to binary
            Imgproc.threshold(gray,gray, 50.0, 255.0, Imgproc.THRESH_BINARY );

            //get edges
            //     Imgproc.Laplacian(gray,gray, gray.depth());

            //now perform corner detection
            Mat kernelDilate = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(7, 7));
            Imgproc.dilate(gray, gray, kernelDilate);
            return gray;

        }
        else if(this.menu_item_selected.equals("Erode")) {
            Log.i("SPINNER", "performing Erode");
            //convert gray to binary
            Imgproc.threshold(gray,gray, 50.0, 255.0, Imgproc.THRESH_BINARY );

            //get edges
            //     Imgproc.Laplacian(gray,gray, gray.depth());

            //now perform corner detection
            Mat kernelErode = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(7, 7));
            Imgproc.erode(gray, gray, kernelErode);
            return gray;


        }
        else if(this.menu_item_selected.equals("Blur")) {
            Log.i("SPINNER", "performing Erode");
            Imgproc.blur(gray,gray, new Size(9,9));
            return gray;


        }
        else if(this.menu_item_selected.equals("GaussBlur")) {
            Log.i("SPINNER", "performing GaussBLur");

            Imgproc.GaussianBlur(gray, gray, new Size(9, 9), 0);

            return gray;
        }
        else if(this.menu_item_selected.equals("Canny")) {
            Log.i("SPINNER", "performing Canny");

            Imgproc.Canny(gray, gray, 80, 255);

            return gray;
        }
        else if(this.menu_item_selected.equals("Hough")) {
            Log.i("SPINNER", "performing Hough");



            return this.HoughLinesP(gray, imageMat);
        }
        else  //for now return color for all other choices
        {
            return gray;
        }

    }



    //Spinner Menu Selection response method
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {

        //if previous item selected was Google Cloud vision than reinitialize count and blank
        // out message related to detected objects
        if(this.menu_item_selected.equals("Google Cloud Vision API")){
            ///initialize counter to 0
            count = 0;

            //set message TextView to app name
            mImageDetails.setText(R.string.app_name);
        }


        // An item was selected. You can retrieve the selected item using
        this.menu_item_selected = parent.getItemAtPosition(pos).toString();






        Log.i("SPINNER", "choice is" + this.menu_item_selected);


    }


    public void onNothingSelected(AdapterView<?> parent) {
        this.menu_item_selected = this.menu_items[0];
    }

    /**
     * service method to perform Harris Corner detection and display results
     */
    public Mat HarrisCorner(Mat gray) {


        Mat corners = new Mat();

        Mat tempDst = new Mat();
        //finding corners
        Imgproc.cornerHarris(gray, tempDst, 2, 3, 0.04);

        //Normalizing harris corner's output
        Mat tempDstNorm = new Mat();
        Core.normalize(tempDst, tempDstNorm, 0, 255, Core.NORM_MINMAX);
        Core.convertScaleAbs(tempDstNorm, corners);

        //Drawing corners on a new image
        Random r = new Random();
        for (int i = 0; i < tempDstNorm.cols(); i++) {
            for (int j = 0; j < tempDstNorm.rows(); j++) {
                double[] value = tempDstNorm.get(j, i);
                if (value[0] > 150)
                    Imgproc.circle(corners, new Point(i, j), 5, new Scalar(r.nextInt(255)), 2);
            }
        }


        return corners;
    }

    /**
     * service method to return HoughLines drawn on top of gray image
     * @param gray
     * @return
     */
    public Mat HoughLines(Mat gray, Mat color){

        Mat edge = new Mat();
        //Imgproc.Laplacian(gray,edge, gray.depth()); //Laplacian edge detect
        Imgproc.Canny(gray, edge, 80, 255);


        //step 2 perform LhouLines
        Mat lines = new Mat();
        int threshold_line = Math.min(gray.cols(), gray.rows()) / 6; //equal to 1/10-th of the width or height of image
        Imgproc.HoughLines(edge,lines,1, Math.PI/180, threshold_line);

        //step 3 draw the lines in blue on top of original image
        for(int i=0; i<lines.rows(); i++) //cycle through the lines
        {
            double[] values = lines.get(i,0); //get ith line
            double rho = values[0];
            double theta = values[1];
            double a = Math.cos(theta);
            double b = Math.sin(theta);
            double x0 = a*rho;
            double y0 = b*rho;
            double x1,y1,x2,y2;
            x1 = x0 + 1000*(-b);
            y1 = y0 + 1000*a;
            x2 = x0 - 1000*(-b);
            y2 = y0 - 1000*a;

            Point pt1 = new Point(x1,y1);
            Point pt2 = new Point(x2,y2);

            //draw line son the image
            Imgproc.line(color, pt1,pt2, new Scalar(0,0,255),1);

        }
        return color;
    }
    /**
     * returns image with HoughLines drawn on top
     * @return
     */
    public Mat HoughLinesP(Mat gray) {


        Mat cannyEdges = new Mat();
        Mat lines = new Mat();



        Imgproc.Canny(gray, cannyEdges, 10, 100);

        Imgproc.HoughLinesP(cannyEdges, lines, 1, Math.PI / 180, 50, 20, 20);

        Mat houghLines = new Mat();
        houghLines.create(cannyEdges.rows(), cannyEdges.cols(), CvType.CV_8UC1);

        //Drawing lines on the image
        for (int i = 0; i < lines.cols(); i++) {
            double[] points = lines.get(0, i);
            double x1, y1, x2, y2;

            x1 = points[0];
            y1 = points[1];
            x2 = points[2];
            y2 = points[3];

            Point pt1 = new Point(x1, y1);
            Point pt2 = new Point(x2, y2);

            //Drawing lines on an image
            Imgproc.line(houghLines, pt1, pt2, new Scalar(255, 0, 0), 1);
        }

        return houghLines;
    }

    /**
     * returns image with HoughLines drawn on top
     * @return
     */
    public Mat HoughLinesP(Mat gray, Mat color) {


        Mat cannyEdges = new Mat();
        Mat lines = new Mat();



        Imgproc.Canny(gray, cannyEdges, 10, 100);

        Imgproc.HoughLinesP(cannyEdges, lines, 1, Math.PI / 180, 20, 50, 20);


        //Drawing lines on the image
        for (int i = 0; i < lines.rows(); i++) {
            double[] points = lines.get(i,0);
            double x1, y1, x2, y2;

            x1 = points[0];
            y1 = points[1];
            x2 = points[2];
            y2 = points[3];

            Point pt1 = new Point(x1, y1);
            Point pt2 = new Point(x2, y2);

            //Drawing lines on an image
            Imgproc.line(color, pt1, pt2, new Scalar(255, 0, 0), 1);
        }

        return color;
    }


    /**
     * this method uses the classes vision object to make a request to Google Cloud Vision API
     * @param bitmap  input Bitmap image
     * @param imageMat  output Mat to be displayed via OpenCV
     */
    public void callCloudVision(final Bitmap bitmap, final Mat imageMat) throws IOException {
        // Switch text to loading
        //     mImageDetails.setText(R.string.loading_message);
        //        mImageDetails.setText("loading");

        Log.i("TEST", "before cloud api call");

        // Do the real work in an async task, because we need to use the network anyway
        new AsyncTask<Object, Void, String>() {
            @Override
            protected String doInBackground(Object... params) {
                try {
                    HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
                    JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

                    VisionRequestInitializer requestInitializer =
                            new VisionRequestInitializer(getString(R.string.CLOUD_VISION_API_KEY)) {
                                /**
                                 * We override this so we can inject important identifying fields into the HTTP
                                 * headers. This enables use of a restricted cloud platform API key.
                                 */
                                @Override
                                protected void initializeVisionRequest(VisionRequest<?> visionRequest)
                                        throws IOException {
                                    super.initializeVisionRequest(visionRequest);

                                    String packageName = getPackageName();
                                    visionRequest.getRequestHeaders().set(getString(R.string.ANDROID_PACKAGE_HEADER), packageName);

                                    String sig = PackageManagerUtils.getSignature(getPackageManager(), packageName);

                                    visionRequest.getRequestHeaders().set(getString(R.string.ANDROID_CERT_HEADER), sig);
                                }
                            };

                    Vision.Builder builder = new Vision.Builder(httpTransport, jsonFactory, null);
                    builder.setVisionRequestInitializer(requestInitializer);

                    Vision vision = builder.build();

                    BatchAnnotateImagesRequest batchAnnotateImagesRequest =
                            new BatchAnnotateImagesRequest();
                    batchAnnotateImagesRequest.setRequests(new ArrayList<AnnotateImageRequest>() {{
                        AnnotateImageRequest annotateImageRequest = new AnnotateImageRequest();

                        // Add the image
                        Image base64EncodedImage = new Image();
                        // Convert the bitmap to a JPEG
                        // Just in case it's a format that Android understands but Cloud Vision
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
                        byte[] imageBytes = byteArrayOutputStream.toByteArray();

                        // Base64 encode the JPEG
                        base64EncodedImage.encodeContent(imageBytes);
                        annotateImageRequest.setImage(base64EncodedImage);

                        // add the features we want
                        annotateImageRequest.setFeatures(new ArrayList<Feature>() {{
                            Feature labelDetection = new Feature();
                            labelDetection.setType("LABEL_DETECTION");
                            labelDetection.setMaxResults(10);
                            add(labelDetection);
                        }});

                        // Add the list of one thing to the request
                        add(annotateImageRequest);
                    }});

                    Vision.Images.Annotate annotateRequest =
                            vision.images().annotate(batchAnnotateImagesRequest);
                    // Due to a bug: requests to Vision API containing large images fail when GZipped.
                    annotateRequest.setDisableGZipContent(true);
                    Log.d(TAG, "created Cloud Vision request object, sending request");

                    BatchAnnotateImagesResponse response = annotateRequest.execute();
                    return convertResponseToString(response);

                } catch (GoogleJsonResponseException e) {
                    Log.d(TAG, "failed to make API request because " + e.getContent());
                } catch (IOException e) {
                    Log.d(TAG, "failed to make API request because of other IOException " +
                            e.getMessage());
                }
                return "Cloud Vision API request failed. Check logs for details.";
            }

            protected void onPostExecute(String result) {
                mImageDetails.setText(result);
            }
        }.execute();
    }

    /**
     * method to create a String from the response of a Google Vision Label Detection on an image
     * @param response
     * @return
     */
    private String convertResponseToString(BatchAnnotateImagesResponse response) {
        String message = "I found these things:\n\n";

        List<EntityAnnotation> labels = response.getResponses().get(0).getLabelAnnotations();
        if (labels != null) {
            for (EntityAnnotation label : labels) {
                message += String.format(Locale.US, "%.3f: %s", label.getScore(), label.getDescription());
                message += "\n";
            }
        } else {
            message += "nothing";
        }

        return message;
    }


    /**
     * method to resize the original bitmap to produce a bitMap with maximum dimension of maxDimension on widht or height
     * but, also keep aspect ration the same as original
     * @param bitmap  input original bitmap
     * @param maxDimension   maximum or either Width or Height of new rescaled image keeping original aspect ratio
     * @return rescaled image with same aspect ratio
     */

    public Bitmap scaleBitmapDown(Bitmap bitmap, int maxDimension) {

        int originalWidth = bitmap.getWidth();
        int originalHeight = bitmap.getHeight();
        int resizedWidth = maxDimension;
        int resizedHeight = maxDimension;

        if (originalHeight > originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = (int) (resizedHeight * (float) originalWidth / (float) originalHeight);
        } else if (originalWidth > originalHeight) {
            resizedWidth = maxDimension;
            resizedHeight = (int) (resizedWidth * (float) originalHeight / (float) originalWidth);
        } else if (originalHeight == originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = maxDimension;
        }
        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false);
    }



    /**
     * method to display Icon For a particular recognition instance inside of the activity's Overlay View
     *    has a timer associated with res/values.xml file
     * @param recognition
     *
     */
    public void displayIconForRecognition( Classifier.Recognition recognition){


        //grab the image in label.png or label_big.png or label_small.png and display
        // inside the overlay_ImageView and turn on the imageView for like 10 seconds
        //also play a TTS = "label"

        //grab label of the recognition instance
        String label = recognition.getTitle();

        int drawable_image_id;
        //first dynamically compose the R.drawable.label
        //   fix this stupid hard coding of labels somehow
        //LLLLLL --- not getting correct drawable id
        if(label == "dog" || label == "cat" || label== "tv" || label=="laptop"||
                label == "person" || label == "cup" || label=="car" || label =="microwave" ||
                label =="oven" || label=="refrigerator")
            drawable_image_id = this.getResources().getIdentifier(label, "drawable", this.getPackageName());

        else
            drawable_image_id = this.getResources().getIdentifier("other",  "drawable", this.getPackageName());

        if(label == "tv")
           drawable_image_id = this.getResources().getIdentifier(label, "drawable", this.getPackageName());
        if(label == "person")
            drawable_image_id = this.getResources().getIdentifier(label, "drawable", this.getPackageName());




        //now in the Overlay ImageView display the image with this drawable id
        //LLLL - this is failing why???????
        //LLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLL

        handler.post((new Runnable() {
            int drawable_image_id;
            RectF location;

            public void run() {
               // overlay_ImageView.setImageResource(R.drawable.person);
                VGO.clear();  //clear anything added so far
                overlay_ImageView.setImageResource(R.drawable.person);

                //tells how BIG width and height the View should be
                overlay_ImageView.measure(View.MeasureSpec.makeMeasureSpec((int) this.location.width(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((int) this.location.height(), View.MeasureSpec.EXACTLY));

             //   overlay_ImageView.layout(0, (int) 0, (int) this.location.width(), (int) this.location.height());

                //location of the View in terms of left, top,right, bottom
                overlay_ImageView.layout((int) this.location.left, (int) this.location.top, (int) this.location.right, (int) this.location.bottom);
                VGO.add(overlay_ImageView);
                overlay_ImageView.setVisibility(View.VISIBLE);
                overlay_ImageView.setImageResource(drawable_image_id);
                overlay_ImageView.setVisibility(View.VISIBLE);
            }

            public Runnable init(int drawable_image_id, RectF location){
                this.drawable_image_id = drawable_image_id;
                this.location = location;
                return(this);
            }
        }).init(drawable_image_id, recognition.getLocation()));


        //now turn off the visibility of the overview 10 seconds later
        try { Thread.sleep(5000); }
        catch (InterruptedException ex) { Log.d("YourApplicationName", ex.toString()); }
        handler.post(new Runnable() {
                    int drawable_image_id;
                    RectF location;

                    public void run() {
                        // overlay_ImageView.setImageResource(R.drawable.person);

                        overlay_ImageView.setVisibility(View.INVISIBLE);
                    }
                });






/*
        this.runOnUiThread(new Runnable(){
            @Override
            public void run(){
                overlay_ImageView.setImageResource(R.drawable.person);
                //overlay_ImageView.setImageResource(drawable_image_id);
                overlay_ImageView.setVisibility(View.VISIBLE);
            }
        });
*/





        //do text to speech



        //display the correct Icon
        /*
        overlay_ImageView.post(new Runnable() {
            @Override
            public void run() {
                overlay_ImageView.setVisibility(View.VISIBLE);//make
                //only do display for a short time
                overlay_ImageView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        overlay_ImageView.setVisibility(View.INVISIBLE);
                    }
                }, ICON_DISPLAY_TIME_MS);
            }
        });*/



    }


    public static int getDrawable(Context context, String name)
    {
        Assert.assertNotNull(context);
        Assert.assertNotNull(name);

        return context.getResources().getIdentifier(name,
                "drawable", context.getPackageName());
    }




}
