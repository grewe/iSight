package com.example.lynne.isight;

import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroupOverlay;
import android.widget.ImageView;
import org.tensorflow.demo.Classifier;

/**
 * Created by Lynne on 11/12/2017.
 */

public class IconUtil {


    MainActivity parent_Activity;
    ImageView overlay_ImageView;


    /**
     * Constructor that passes the calling MainActivity class and the overlay view which is an ImageView object
     * @param ma
     * @param overlay_ImageView
     */

    public IconUtil(MainActivity ma, ImageView overlay_ImageView){
        this.overlay_ImageView = overlay_ImageView;
        this.parent_Activity = ma;

    }


    /**
     * method to load the appropriate drawable image based on the recognition.label that is an iconic representation of the label.
     *
     *
     * ICONS:  assumes there is a resource  label.png, label_big.png and label_small.png that are approximately 512xA 1000XB and 256XC
     * where A,B,C keeps some natural aspect ration (like people icon is tall and narrow so A<512 maybe as little as 300 pixels.   But,
     * for other which is a box picture A is approx. 512).
     *
     * LOCATION/SIZE of icon is based on the MODE variable and has this visible for the duration limit passed.
     * If MODE = BoundingBox  then it will size and place the drawable icon within the bounding box of the recogntion.location
     *
     * DURATION of the display of the icon in the overlay_ImageView (which is in a Overlay of the ViewGroup parent_viewgroup_overlay_is_over)
     *
     * If MODE = Magnified  then it will have the icon appear in the center of the overlay_belongs_to_viewgroup
     *                  Note will use something like
     *                               overlay_ImageView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
     *                                                                                   LayoutParams.MATCH_PARENT));
     *                               overlay_ImageView.setGravity(Gravity.CENTER);
     *
     *
     * If tts_on = true then performs TTS also of the label ("person" or "tv" or "laptop")
     *
     *
     * NOTE:  the work for display must be done in the UI Thread and hence must create Runnable to hold this work
     *
     * @param parent_Activity - MainActivity of the application
     * @param VGO - ViewGroupOverlay used to contain overlay_Image to dispaly icon overtop of the underlying parent_viewgruop_overlay_is_over (which contains JCameraView)
     * @param parent_viewgroup_overlay_is_over  - ViewGroup with is a LinearLayout containing the OpenCV JCameraView that is part of parent_Activity
     * @param overlay_ImageView  - ImageView in which to display the overlay image --which will be the icon
     * @param recognition  - recognition instance
     * @param Mode
     * @param tts_on
     * @param duration
     */
    public static void iconifyReconitionResult(MainActivity parent_Activity, ViewGroupOverlay VGO, ViewGroup parent_viewgroup_overlay_is_over, ImageView overlay_ImageView , Classifier.Recognition recognition, String Mode, Boolean tts_on, int duration){


        //grab the image in label.png or label_big.png or label_small.png and display
        // inside the overlay_ImageView and turn on the imageView for like 10 seconds
        //also play a TTS = "label"

        //grab label of the recognition instance
        String label = recognition.getTitle();




        //get the basename of the icon file in the res/drawable directory for the recognition label;
        //  we can not have files with blanks in them so we must convert any lables like "stop sign"
        //    to "stop_sign"
        label = LabelUtil.convertLabelToFileName(label);

        int drawable_image_id;
        //first dynamically compose the R.drawable.label
        //   fix this stupid hard coding of labels somehow
        //LLLLLL --- not getting correct drawable id
        if(label == "dog" || label == "cat" || label== "tv" || label=="laptop"||
                label == "person" || label == "cup" || label=="car" || label =="microwave" ||
                label =="oven" || label=="refrigerator")
            drawable_image_id = parent_Activity.getResources().getIdentifier(label, "drawable",parent_Activity.getPackageName());

        else
            drawable_image_id = parent_Activity.getResources().getIdentifier("other",  "drawable", parent_Activity.getPackageName());

        if(label == "tv")
            drawable_image_id = parent_Activity.getResources().getIdentifier(label, "drawable", parent_Activity.getPackageName());
        if(label == "person")
            drawable_image_id = parent_Activity.getResources().getIdentifier(label, "drawable", parent_Activity.getPackageName());



        drawable_image_id = parent_Activity.getResources().getIdentifier(label, "drawable",parent_Activity.getPackageName());

        //now in the Overlay ImageView display the image with this drawable id
        // MUST run in the UI thread -- so to do this post to the parent_Activity's handler instance
        parent_Activity.handler.post((new Runnable() {
            int drawable_image_id;
            RectF location;
            ViewGroupOverlay VGO;
            ImageView overlay_ImageView;
            String Mode;
            Boolean tts_on;
            MainActivity parent_Activity;

            public void run() {
                // overlay_ImageView.setImageResource(R.drawable.person);
                VGO.clear();  //clear anything added so far
                overlay_ImageView.setImageResource(R.drawable.person);


                if(Mode == "BoundingBox") {

                    //tells how BIG width and height the View should be
                    overlay_ImageView.measure(View.MeasureSpec.makeMeasureSpec((int) this.location.width(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((int) this.location.height(), View.MeasureSpec.EXACTLY));

                    //   overlay_ImageView.layout(0, (int) 0, (int) this.location.width(), (int) this.location.height());

                    //location of the View in terms of left, top,right, bottom
                    overlay_ImageView.layout((int) this.location.left, (int) this.location.top, (int) this.location.right, (int) this.location.bottom);
                    VGO.add(overlay_ImageView);
                    //    overlay_ImageView.setVisibility(View.VISIBLE);
                    overlay_ImageView.setImageResource(drawable_image_id);
                    overlay_ImageView.setVisibility(View.VISIBLE);
                }
                else {
                    //determine the size of the icon image
                    Drawable drawable = parent_Activity.getResources().getDrawable(drawable_image_id);
                    int w = drawable.getIntrinsicWidth();
                    int h = drawable.getIntrinsicHeight();
                    Log.i("Drawable dim W-H", w+"-"+h);

                    //determine the width and height of the underlying JCameraView widget the overlay is to be on top of
                    int videoDisplayView_Width = ( (View) parent_Activity.findViewById(R.id.HelloOpenCvView)).getWidth();
                    int videoDisplayView_Height = (  (View) parent_Activity.findViewById(R.id.HelloOpenCvView)).getHeight();

                    //take smaller of w,h and videoDisplayView_Width,Height
                    int width = Math.min(w, videoDisplayView_Width);
                    int height = Math.min(h, videoDisplayView_Height);

                    //now determine the starting location where centerVideo_x - 1/2(width) is the left point and centerVideo_y - 1/2(height)
                    int left = (videoDisplayView_Width-width)/2;
                    int top = (videoDisplayView_Height-height)/2;

                    //setup the ImageView size
                    overlay_ImageView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));


                    //location of the View in terms of left, top,right, bottom
                    overlay_ImageView.layout(left, top, width+left, height+ top);


                    VGO.add(overlay_ImageView);
                    //    overlay_ImageView.setVisibility(View.VISIBLE);
                    overlay_ImageView.setImageResource(drawable_image_id);
                    overlay_ImageView.setVisibility(View.VISIBLE);

                }


                //do text to speech
            }

            public Runnable init(int drawable_image_id, RectF location, ViewGroupOverlay v, ImageView overlay_ImageView, String Mode, boolean tts_on, MainActivity parent_Activity){
                this.drawable_image_id = drawable_image_id;
                this.location = location;
                this.VGO = v;
                this.overlay_ImageView = overlay_ImageView;
                this.Mode = Mode;
                this.tts_on = tts_on;
                this.parent_Activity = parent_Activity;
                return(this);
            }
        }).init(drawable_image_id, recognition.getLocation(), VGO, overlay_ImageView, Mode, tts_on, parent_Activity));



        //NEXT sleep for
        //now turn off the visibility of the overview some time stipulated by duration in ms
        try { Thread.sleep(duration); }
        catch (InterruptedException ex) { android.util.Log.d("YourApplicationName", ex.toString()); }
        parent_Activity.handler.post((new Runnable() {
            int drawable_image_id;
            RectF location;
            ImageView overlay_ImageView;

            public void run() {
                //turn OFF the visibility of the ImageView which is the overlay image
                overlay_ImageView.setVisibility(View.INVISIBLE);
            }
            public Runnable init(ImageView overlay_ImageView){
                this.overlay_ImageView = overlay_ImageView;
                return(this);
            }
        }).init(overlay_ImageView) );













    }

}
