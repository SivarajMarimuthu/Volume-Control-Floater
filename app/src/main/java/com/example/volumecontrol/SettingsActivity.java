// SettingsActivity.java (replace your existing)
package com.example.volumecontrol;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    RadioGroup sizeGroup;
    RadioButton sizeSmall, sizeMedium, sizeLarge;
    Switch hideIconSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sizeGroup = findViewById(R.id.sizeGroup);
        sizeSmall = findViewById(R.id.sizeSmall);
        sizeMedium = findViewById(R.id.sizeMedium);
        sizeLarge = findViewById(R.id.sizeLarge);
        hideIconSwitch = findViewById(R.id.switchHideIcon);

        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        int current = prefs.getInt("bubble_size", 56);

        if (current == 40) sizeSmall.setChecked(true);
        else if (current == 56) sizeMedium.setChecked(true);
        else sizeLarge.setChecked(true);

        sizeGroup.setOnCheckedChangeListener((g, id) -> {
            int size = 56;
            if (id == R.id.sizeSmall) size = 40;
            else if (id == R.id.sizeMedium) size = 56;
            else if (id == R.id.sizeLarge) size = 72;

            prefs.edit().putInt("bubble_size", size).apply();

            // broadcast to service to update size immediately
            Intent update = new Intent(FloatingService.ACTION_UPDATE_BUBBLE);
            sendBroadcast(update);

            Toast.makeText(this, "Bubble size updated", Toast.LENGTH_SHORT).show();
        });

        // initialize and handle hide/show icon switch
        boolean iconHidden = isIconHidden();
        hideIconSwitch.setChecked(iconHidden);

        hideIconSwitch.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            if (isChecked) hideIcon();
            else showIcon();
        });
    }

    private boolean isIconHidden() {
        PackageManager pm = getPackageManager();
        int state = pm.getComponentEnabledSetting(
                new ComponentName(this, LauncherAlias.class));
        return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }

    private void hideIcon() {
        PackageManager pm = getPackageManager();
        pm.setComponentEnabledSetting(
                new ComponentName(this, LauncherAlias.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
        );
        Toast.makeText(this, "App hidden from menu", Toast.LENGTH_SHORT).show();
    }

    private void showIcon() {
        PackageManager pm = getPackageManager();
        pm.setComponentEnabledSetting(
                new ComponentName(this, LauncherAlias.class),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
        );
        Toast.makeText(this, "App visible in menu", Toast.LENGTH_SHORT).show();
    }
}
