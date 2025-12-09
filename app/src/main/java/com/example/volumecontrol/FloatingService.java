// FloatingService.java (replace or update your existing service with this)
package com.example.volumecontrol;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.ImageView;

public class FloatingService extends Service {

    public static final String ACTION_UPDATE_BUBBLE = "com.example.volumecontrol.UPDATE_BUBBLE";

    private WindowManager wm;
    private View bubble;
    private AudioManager audio;
    private WindowManager.LayoutParams bubbleParams;

    private BroadcastReceiver updateReceiver;

    private long lastTap = 0;
    private boolean isHidden = false;
    private final int HIDE_DELAY = 2500;

    // Long press
    private static final int LONG_PRESS_TIMEOUT = 500;
    private Handler longPressHandler = new Handler(Looper.getMainLooper());
    private boolean longPressed = false;

    @Override
    public void onCreate() {
        super.onCreate();

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);

        // register receiver for runtime updates (size change)
        IntentFilter f = new IntentFilter(ACTION_UPDATE_BUBBLE);
        updateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // read new size and apply
                applySizeFromPrefs();
            }
        };
        registerReceiver(updateReceiver, f);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else {
            createBubble();
        }

        startForegroundServiceCompat();
    }

    private int getSavedBubbleDp() {
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        return prefs.getInt("bubble_size", 56); // default 56dp
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void createBubble() {
        // inflate
        bubble = LayoutInflater.from(this).inflate(R.layout.floating_bubble, null);
        ImageView icon = bubble.findViewById(R.id.bubbleIcon);

        // ensure circular background from drawable
        bubble.setBackgroundResource(R.drawable.bubble_bg);
        bubble.setClipToOutline(true);

        // read size
        int bubbleSizeDp = getSavedBubbleDp();
        int bubbleSizePx = dpToPx(bubbleSizeDp);

        // LayoutParams using size from prefs
        bubbleParams = new WindowManager.LayoutParams(
                bubbleSizePx,
                bubbleSizePx,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = 100;
        bubbleParams.y = 300;

        wm.addView(bubble, bubbleParams);

        bubble.setOnTouchListener(new View.OnTouchListener() {

            private int initX, initY;
            private float initTouchX, initTouchY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {

                switch (e.getAction()) {

                    case MotionEvent.ACTION_DOWN:
                        moved = false;
                        longPressed = false;

                        initX = bubbleParams.x;
                        initY = bubbleParams.y;
                        initTouchX = e.getRawX();
                        initTouchY = e.getRawY();

                        longPressHandler.postDelayed(() -> {
                            if (!moved) {
                                longPressed = true;

                                Intent i = new Intent(FloatingService.this, SettingsActivity.class);
                                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(i);
                            }
                        }, LONG_PRESS_TIMEOUT);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int)(e.getRawX() - initTouchX);
                        int dy = (int)(e.getRawY() - initTouchY);

                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) moved = true;

                        bubbleParams.x = initX + dx;
                        bubbleParams.y = initY + dy;

                        try { wm.updateViewLayout(bubble, bubbleParams); } catch (Exception ignored){ }
                        return true;

                    case MotionEvent.ACTION_UP:
                        longPressHandler.removeCallbacksAndMessages(null);

                        if (!moved && !longPressed) handleTap();

                        snapToEdge();
                        autoHide();
                        return true;
                }
                return false;
            }
        });

        autoHide();
    }

    /** Apply current saved size while service is running */
    private void applySizeFromPrefs() {
        if (bubbleParams == null || bubble == null) return;

        int bubbleSizeDp = getSavedBubbleDp();
        int bubbleSizePx = dpToPx(bubbleSizeDp);

        // update params
        bubbleParams.width = bubbleSizePx;
        bubbleParams.height = bubbleSizePx;

        // If bubble has been snapped to edge previously, adjust x so it still fits on screen
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metrics);
        int screenW = metrics.widthPixels;
        if (bubbleParams.x > screenW - bubbleParams.width) {
            bubbleParams.x = screenW - bubbleParams.width;
        }

        try {
            wm.updateViewLayout(bubble, bubbleParams);
        } catch (Exception e) {
            // update may fail if view not attached; ignore safely
        }
    }

    private void handleTap() {
        long now = System.currentTimeMillis();

        if (now - lastTap < 300) {
            audio.adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
            return;
        }

        lastTap = now;

        audio.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
        showBubble();
    }

    private void snapToEdge() {
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metrics);

        int screenWidth = metrics.widthPixels;
        int middle = screenWidth / 2;

        if (bubbleParams.x >= middle) {
            bubbleParams.x = screenWidth - bubble.getWidth();  // Right
        } else {
            bubbleParams.x = 0;  // Left
        }

        try { wm.updateViewLayout(bubble, bubbleParams); } catch (Exception ignored){}
    }

    private void autoHide() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (bubble != null) {
                bubble.animate().alpha(0.25f).setDuration(300).start();
                isHidden = true;
            }
        }, HIDE_DELAY);
    }

    private void showBubble() {
        if (isHidden && bubble != null) {
            bubble.animate().alpha(1f).setDuration(200).start();
            isHidden = false;
        }
    }

    private void startForegroundServiceCompat() {
        String channelId = "bubble_channel";
        String name = "Volume Bubble";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    channelId,
                    name,
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager mgr = getSystemService(NotificationManager.class);
            mgr.createNotificationChannel(ch);
        }

        Notification n = new Notification.Builder(this, channelId)
                .setContentTitle("Volume Bubble Running")
                .setSmallIcon(R.drawable.ic_volume)
                .setOngoing(true)
                .build();

        startForeground(1, n);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(updateReceiver);
        } catch (Exception ignored) {}
        try {
            if (bubble != null) wm.removeView(bubble);
        } catch (Exception ignored) {}
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
