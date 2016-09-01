package app.com.cargram;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBAttribute;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBHashKey;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBScanExpression;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBTable;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.PaginatedScanList;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapLongClickListener;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.koushikdutta.ion.Ion;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class MainActivity extends Activity implements OnMapLongClickListener, GoogleMap.OnMarkerDragListener, GoogleMap.OnMarkerClickListener {

    public final static String IMAGE = "app.com.cargram.IMAGE";
    public final static String MODEL = "app.com.cargram.MODEL";
    public final static String USER = "app.com.cargram.USER";
    public final static String DATE = "app.com.cargram.DATE";

    public ArrayList<Car> list = new ArrayList<>();
    private GoogleMap mMap;
    private LatLng cord = new LatLng(0, 0);
    public String pictureName = "";
    public String userName = "";
    ProgressDialog dialog;

    AmazonDynamoDB ddb = null;
    AmazonS3Client s3Client = null;
    DynamoDBMapper mapper = null;

    SharedPreferences prefs = null;

    private static int getPowerOfTwoForSampleRatio(double ratio) {
        int k = Integer.highestOneBit((int) Math.floor(ratio));
        if (k == 0) return 1;
        else return k;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.my, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.action_sync:
                new loadMarkers().execute();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);

        prefs = getSharedPreferences("com.carspot.danny.Carspot", MODE_PRIVATE);

        CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                this, // get the context for the current activity
                Constants.ACCOUNT_ID, // your AWS Account id
                Constants.IDENTITY_POOL_ID, // your identity pool id
                Constants.UNAUTH_ROLE_ARN,// an authenticated role ARN
                Constants.AUTH_ROLE_ARN, // an unauthenticated role ARN
                Regions.US_EAST_1 //Region
        );

        ddb = new AmazonDynamoDBClient(credentialsProvider);
        s3Client = new AmazonS3Client(credentialsProvider);

        mapper = new DynamoDBMapper(ddb);

        setUpMapIfNeeded();

        Intent intent = getIntent();
        userName = intent.getStringExtra(Login.NAME);
        Log.d("Test", userName);

    }

    @Override
    public void onPause() {
        super.onPause();

        if ((dialog != null) && dialog.isShowing())
            dialog.dismiss();
        dialog = null;

        setUpMapIfNeeded();
    }

    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            mMap = ((MapFragment) getFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                map();
            }
        }
    }

    public void map() {
        mMap = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();
        mMap.setMyLocationEnabled(true);

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();

        Location location = locationManager.getLastKnownLocation(locationManager.getBestProvider(criteria, false));
        if (location != null) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(location.getLatitude(), location.getLongitude()), 14));

        }

        new loadMarkers().execute();
        mMap.setOnMapLongClickListener(this);
        mMap.setOnMarkerClickListener(this);
        mMap.setOnMarkerDragListener(this);
    }

    private void loadMarkers() {
        // LatLng center = mMap.getCameraPosition().target;

        for (int x = 0; x < list.size(); x++) {
            // LatLng marker = new LatLng(list.get(x).getLatitude(), list.get(x).getLongitude());
            //  if (CalculationByDistance(center, marker) < 50) {
            try {
                Bitmap bmImg = Ion.with(this)
                        .load(list.get(x).getImage()).asBitmap().get();

                mMap.addMarker(new MarkerOptions().position(new LatLng(list.get(x).getLatitude(), list.get(x).getLongitude()))
                        .icon(BitmapDescriptorFactory.fromBitmap(scaleBitMap(bmImg, 0.15))).draggable(true).title(list.get(x).getKey()));

            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
            // }
        }
    }

    public void onMapLongClick(LatLng point) {
        cord = point;
        selectImage();
    }

    public void addMarker(LatLng location, Bitmap bitmap) {
        Date date = new Date(
          System.currentTimeMillis());

        String url = "https://s3.amazonaws.com/dannycarspottest/"+pictureName;
        mMap.addMarker(new MarkerOptions()
                        .position(location)
                        .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
                        .draggable(true)
                        .title(pictureName)
        );

        Car car = new Car();
        car.setModel(UUID.randomUUID().toString());
        car.setLatitude(location.latitude);
        car.setLongitude(location.longitude);
        car.setImage(url);
        car.setKey(pictureName);
        car.setDate(date.toString());
        car.setName(userName);
        list.add(car);
        new uploadCar().execute(car);
    }

    public Bitmap scaleBitMap(Bitmap img, double percent) {
        double newHeight = img.getHeight() * percent;
        double newWidth = img.getWidth() * percent;

        return Bitmap.createScaledBitmap(img, (int) newWidth, (int) newHeight, true);
    }

    public void selectImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, Constants.REQUEST_IMAGE_GET);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Constants.REQUEST_IMAGE_GET && resultCode == RESULT_OK) {
            Uri fullPhotoUri = data.getData();
            Bitmap bitmap = null;

            pictureName = UUID.randomUUID().toString();

            try {
                bitmap = getThumbnail(fullPhotoUri);
            } catch (IOException e) {
                e.printStackTrace();
            }

            new S3PutPictureTask().execute(fullPhotoUri);
            addMarker(cord, bitmap);
        }
    }

    public Bitmap getThumbnail(Uri uri) throws IOException {
        InputStream input = this.getContentResolver().openInputStream(uri);

        BitmapFactory.Options onlyBoundsOptions = new BitmapFactory.Options();
        onlyBoundsOptions.inJustDecodeBounds = true;
        onlyBoundsOptions.inDither = true;//optional
        onlyBoundsOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;//optional
        BitmapFactory.decodeStream(input, null, onlyBoundsOptions);
        input.close();
        if ((onlyBoundsOptions.outWidth == -1) || (onlyBoundsOptions.outHeight == -1))
            return null;

        int originalSize = (onlyBoundsOptions.outHeight > onlyBoundsOptions.outWidth) ? onlyBoundsOptions.outHeight : onlyBoundsOptions.outWidth;

        double ratio = (originalSize > 100) ? (originalSize / 100) : 1.0;

        BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
        bitmapOptions.inSampleSize = getPowerOfTwoForSampleRatio(ratio);
        bitmapOptions.inDither = true;//optional
        bitmapOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;//optional
        input = this.getContentResolver().openInputStream(uri);
        Bitmap bitmap = BitmapFactory.decodeStream(input, null, bitmapOptions);
        input.close();
        return bitmap;
    }

    @Override
    public void onMarkerDragStart(Marker marker) {
        deleteMarker(marker);
    }

    @Override
    public void onMarkerDrag(Marker marker) {

    }

    @Override
    public void onMarkerDragEnd(Marker marker) {

    }

    public void deleteMarker(Marker marker) {
        marker.setVisible(false);
        marker.remove();
        for (int x = 0; x < list.size(); x++) {
            if (marker.getTitle().equals(list.get(x).getKey())) {
                new deleteMarkers().execute(list.get(x));
                list.remove(x);
            }
        }
    }

    protected void displayErrorAlert(String title, String message) {

        AlertDialog.Builder confirm = new AlertDialog.Builder(this);
        confirm.setTitle(title);
        confirm.setMessage(message);

        confirm.setNegativeButton(
                MainActivity.this.getString(R.string.ok),
                new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {

                        MainActivity.this.finish();
                    }
                });

        confirm.show().show();
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        String image = "";
        String date = "";
        String name = "";
      //  String model = "";
        Intent intent = new Intent(this, Display.class);
        for (int x = 0; x < list.size(); x++) {
            if (marker.getTitle().equals(list.get(x).getKey())) {
                image = list.get(x).getImage();
                date = list.get(x).getDate();
             //   model = list.get(x).getModel();
                name = list.get(x).getName();
            }
        }
        intent.putExtra(IMAGE, image);
        intent.putExtra(DATE, date);
       // intent.putExtra(MODEL, model);
        intent.putExtra(USER, name);
        startActivity(intent);
        return true;
    }

    private class S3PutPictureTask extends AsyncTask<Uri, Void, S3TaskResult> {


        protected void onPreExecute() {
            dialog = new ProgressDialog(MainActivity.this);
            dialog.setMessage(MainActivity.this
                    .getString(R.string.uploading));
            dialog.setCancelable(false);
            dialog.show();
        }

        protected S3TaskResult doInBackground(Uri... uris) {

            if (uris == null || uris.length != 1) {
                return null;
            }
            Uri selectedImage = uris[0];
            ContentResolver resolver = getContentResolver();
            String fileSizeColumn[] = {OpenableColumns.SIZE};

            Cursor cursor = resolver.query(selectedImage,
                    fileSizeColumn, null, null, null);

            cursor.moveToFirst();

            int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);

            String size = null;
            if (!cursor.isNull(sizeIndex)) {

                size = cursor.getString(sizeIndex);
            }

            cursor.close();

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(resolver.getType(selectedImage));
            if (size != null) {
                metadata.setContentLength(Long.parseLong(size));
            }

            S3TaskResult result = new S3TaskResult();
            try {
                s3Client.createBucket(Constants.getPictureBucket());

                PutObjectRequest por = new PutObjectRequest(
                        Constants.getPictureBucket(), pictureName,
                        resolver.openInputStream(selectedImage), metadata);

                por.setCannedAcl(CannedAccessControlList.PublicReadWrite);

                s3Client.putObject(por);
            } catch (Exception exception) {

                result.setErrorMessage(exception.getMessage());
            }

            return result;
        }

        protected void onPostExecute(S3TaskResult result) {

            if ((dialog != null) && dialog.isShowing()) {
                dialog.dismiss();
            }
            if (result.getErrorMessage() != null) {

                displayErrorAlert(
                        MainActivity.this
                                .getString(R.string.upload_failure_title),
                        result.getErrorMessage());
            }
        }
    }

    private class S3TaskResult {
        String errorMessage = null;

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }

    private class uploadCar extends AsyncTask<Car, Void, Void> {

        @Override
        protected Void doInBackground(Car... params) {
            Car car = params[0];
            mapper.save(car);

            return null;
        }
    }

    private class loadMarkers extends AsyncTask<Void, Void, Integer> {

        protected void onPreExecute() {
            dialog = new ProgressDialog(MainActivity.this);
            dialog.setMessage("Downloading Images");
            dialog.setCancelable(false);
            dialog.show();
        }

        @Override
        protected Integer doInBackground(Void... params) {
            DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
            PaginatedScanList<Car> result = mapper.scan(Car.class, scanExpression);
            list.addAll(result);
            return 0;
        }

        protected void onPostExecute(Integer result) {
            loadMarkers();
            if ((dialog != null) && dialog.isShowing()) {
                dialog.dismiss();
            }
        }

    }

    private class deleteMarkers extends AsyncTask<Car, Void, Void> {
        protected Void doInBackground(Car... params) {
            mapper.delete(params[0]);
            s3Client.deleteObject(Constants.PICTURE_BUCKET, params[0].getKey());
            return null;
        }

    }

    @DynamoDBTable(tableName = "carspot")
    public static class Car {
        private String model;
        private String image;
        private String name;
        private double latitude;
        private double longitude;
        private String key;
        private String date;

        @DynamoDBHashKey(attributeName = "Model")
        public String getModel() {
            return model;
        }
        public void setModel(String model) {
            this.model = model;
        }

        @DynamoDBAttribute(attributeName = "Name")
        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }

        @DynamoDBAttribute(attributeName = "Image")
        public String getImage() {
            return image;
        }

        public void setImage(String image) {
            this.image = image;
        }

        @DynamoDBAttribute(attributeName = "latitude")
        public double getLatitude() {
            return latitude;
        }

        public void setLatitude(double latitude) {
            this.latitude = latitude;
        }

        @DynamoDBAttribute(attributeName = "longitude")
        public double getLongitude() {
            return longitude;
        }

        public void setLongitude(double longitude) {
            this.longitude = longitude;
        }

        @DynamoDBAttribute(attributeName = "key")
        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        @DynamoDBAttribute(attributeName = "date")
        public void setDate(String date) {
            this.date = date;
        }
        public String getDate() {
            return date;
        }
    }
}



