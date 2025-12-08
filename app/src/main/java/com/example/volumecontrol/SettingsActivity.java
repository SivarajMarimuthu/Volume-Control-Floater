package com.example.volumecontrol;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    RadioGroup sizeGroup;
    RadioButton sizeSmall, sizeMedium, sizeLarge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sizeGroup = findViewById(R.id.sizeGroup);
        sizeSmall = findViewById(R.id.sizeSmall);
        sizeMedium = findViewById(R.id.sizeMedium);
        sizeLarge = findViewById(R.id.sizeLarge);

        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        int current = prefs.getInt("bubble_size", 56);

        if (current == 40) sizeSmall.setChecked(true);
        else if (current == 56) sizeMedium.setChecked(true);
        else sizeLarge.setChecked(true);

        sizeGroup.setOnCheckedChangeListener((group, id) -> {
            int size = 56;

            if (id == R.id.sizeSmall) size = 40;
            else if (id == R.id.sizeMedium) size = 56;
            else if (id == R.id.sizeLarge) size = 72;

            prefs.edit().putInt("bubble_size", size).apply();

            Toast.makeText(this, "Bubble size updated!", Toast.LENGTH_SHORT).show();
        });
    }
}
