package app.com.cargram;

import java.util.Locale;

public class Constants {

    static final int REQUEST_IMAGE_GET = 1;



    public static final String PICTURE_BUCKET = "dannycarspottest";

    public static final String ACCOUNT_ID = "";
    public static final String IDENTITY_POOL_ID = "";
    public static final String UNAUTH_ROLE_ARN = "";
    public static final String AUTH_ROLE_ARN = "";
    public static final String TABLE_NAME = "carspot";


    public static String getPictureBucket() {
        return (PICTURE_BUCKET).toLowerCase(Locale.US);
    }

}
