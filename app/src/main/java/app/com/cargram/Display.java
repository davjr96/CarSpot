package app.com.cargram;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import com.koushikdutta.ion.Ion;

public class Display extends Activity {
    ImageView imageView;
    TextView textView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        imageView = (ImageView)findViewById(R.id.image);


        Intent intent = getIntent();
        String image = intent.getStringExtra(MainActivity.IMAGE);
        String date = intent.getStringExtra(MainActivity.DATE);
        String user = intent.getStringExtra(MainActivity.USER);
       // String model = intent.getStringExtra(MainActivity.MODEL);

        textView =  (TextView)findViewById(R.id.textView);
        textView.setTextSize(20);
        textView.setText("Date: " + date+ "\n" + "Photographer: " + user);// + "\n" + "Model: " + model);



        Ion.with(this)
                .load(image)
                .withBitmap()
                .resize(512, 512)
                .centerCrop()
                .intoImageView(imageView);
    }
}
