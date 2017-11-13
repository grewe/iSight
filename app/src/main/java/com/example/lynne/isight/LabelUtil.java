package com.example.lynne.isight;

/**
 * Class dealing with manipulation of Label --like converting to the icon base filename or checking
 * if the input label exists in the text file for Tensorflow output labels
 * Created by Lynne on 11/12/2017.
 */

public class LabelUtil {


    /**
     * converts any blank spaces to underscore _   to reflect that in the corresponding icon
     * image for a label.  So if a label is "stop sign"   the name of the drawable resource
     * file is stop_sign.png
     * @param label
     * @return
     */
    public static String convertLabelToFileName(String label){

        String s =  label.replaceAll("\\s+","_");

        if(s == "???")
            s = "other";

        return s;


    }
}
