package iconcreator.pidroid_sesa;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

/*This Program is written by : Pratik Prajapati for Research Project in MST*/
public class splash_screen extends AppCompatActivity {
    // Splash screen timer
    private static int SPLASH_TIME_OUT = 3000;  //3 second timer for launching main activity
    private String TAG = "Splash Screen : ";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Remove title bar
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        //Remove notification bar
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_splash_screen);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                // starting  main activity
                Intent i = new Intent(getApplicationContext(), MainActivity.class);
                startActivity(i);
                Log.d(TAG, "Starting Main Activity in " + SPLASH_TIME_OUT + " Milliseconds");
                // close this splash_activity
                finish();
            }
        }, SPLASH_TIME_OUT);
    }
}
