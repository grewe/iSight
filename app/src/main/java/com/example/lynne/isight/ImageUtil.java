package com.example.lynne.isight;

import android.graphics.Bitmap;

/**
 * Created by Lynne on 11/7/2017.
 */

public class ImageUtil {




    /**
     * method to resize the original bitmap to produce a bitMap with maximum dimension of maxDimension on widht or height
     * but, also keep aspect ration the same as original
     * @param bitmap  input original bitmap
     * @param maxDimension   maximum or either Width or Height of new rescaled image keeping original aspect ratio
     * @return rescaled image with same aspect ratio
     */

    public static Bitmap scaleBitmapDown(Bitmap bitmap, int maxDimension) {

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


}
