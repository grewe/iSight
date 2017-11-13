package com.example.lynne.isight;

import android.widget.ImageView;

/**
 * Created by Lynne on 11/12/2017.
 */

public class EnhanceUtil {

    MainActivity parent_Activity;
    ImageView overlay_ImageView;



    public EnhanceUtil(MainActivity ma, ImageView overlay_ImageView){
        this.overlay_ImageView = overlay_ImageView;
        this.parent_Activity = ma;

    }
}
