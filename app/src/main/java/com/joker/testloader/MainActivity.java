package com.joker.testloader;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        File dir = getApplicationContext().getDir("files", 0);
        File dir1 = getApplicationContext().getFilesDir();

        Log.d("MainActivity", "======>dir="+dir);
        Log.d("MainActivity", "======>dir1="+dir1);

    }
}
