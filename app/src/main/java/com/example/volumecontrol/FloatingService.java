package com.example.volumecontrol;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.core.app.NotificationCompat;

public class FloatingService extends Service {

    private WindowManager windowManager;
    private View bubbleView;
    private AudioManager audioManager;
    private Handler handler = new Handler();

    private boolean isBubbleVisible = true;

    @Override
    public void onCreate() {
        super.onCreate();

        startForegroundServiceNotification();

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        LayoutInflater inflater = LayoutInflater.from(this);
        bubbleView = inflater.inflate(R.layout.bubble_layout, null);

        int LAYOUT_FLAG;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_PHONE;
        }

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                dpToPx(getBubbleSize()),
                dpToPx(getBubbleSize()),
                LAYOUT_FLAG,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 50;
        params.y = 200;

        ImageView bubbleIcon = bubbleView.findViewById(R.id.bubbleIcon);

        setupBubbleTouch(bubbleIcon, params);
        startHideTimer();

        windowManager.addView(bubbleView, params);
    }

    // -------------------------
    // BUBBLE TOUCH & GESTURES
    // -------------------------
    private void setupBubbleTouch(ImageView bubbleIcon, WindowManager.LayoutParams params) {

        bubbleIcon.setOnLongClickListener(v -> {
            bubbleIcon.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            openSettings();
            return true;
        });

        bubbleIcon.setOnTouchListener(new View.OnTouchListener() {

            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isDragging = false;

            private static final long DOUBLE_TAP_TIME = 250;
            private long lastTapTime = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {

                    case MotionEvent.ACTION_DOWN:
                        showBubble();
                        startHideTimer();

                        long now = SystemClock.elapsedRealtime();

                        // DOUBLE TAP
                        if (now - lastTapTime < DOUBLE_TAP_TIME) {
                            toggleMute();
                            lastTapTime = 0;
                            return true;
                        }
                        lastTapTime = now;

                        // Drag starting point
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;

                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = Math.abs(event.getRawX() - initialTouchX);
                        float dy = Math.abs(event.getRawY() - initialTouchY);

                        // If moved enough → dragging
                        if (dx > 10 || dy > 10) {
                            isDragging = true;
                        }

                        if (isDragging) {
                            params.x = initialX + (int) (event.getRawX() - initialTouchX);
                            params.y = initialY + (int) (event.getRawY() - initialTouchY);
                            windowManager.updateViewLayout(bubbleView, params);
                        }

                        return true;

                    case MotionEvent.ACTION_UP:
                        startHideTimer();

                        if (!isDragging) {
                            // SINGLE TAP
                            showSystemVolumePanel();
                        }

                        snapToEdge(params);
                        return true;
                }
                return false;
            }
        });
    }

    // -------------------------
    // SNAP TO SCREEN EDGE
    // -------------------------
    private void snapToEdge(WindowManager.LayoutParams params) {
        int screenWidth = windowManager.getDefaultDisplay().getWidth();

        if (params.x + bubbleView.getWidth() / 2 < screenWidth / 2) {
            params.x = 0; // left side
        } else {
            params.x = screenWidth - bubbleView.getWidth(); // right side
        }

        windowManager.updateViewLayout(bubbleView, params);
    }

    // -------------------------
    // AUTO HIDE + SHOW
    // -------------------------
    private void startHideTimer() {
        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, 3000);
    }

    private final Runnable hideRunnable = () -> hideBubble();

    private void hideBubble() {
        if (bubbleView != null && isBubbleVisible) {
            bubbleView.animate().alpha(0.1f).setDuration(300).start();
            isBubbleVisible = false;
        }
    }

    private void showBubble() {
        if (bubbleView != null && !isBubbleVisible) {
            bubbleView.animate().alpha(1f).setDuration(200).start();
            isBubbleVisible = true;
        }
    }

    // -------------------------
    // VOLUME + ACTIONS
    // -------------------------
    private void toggleMute() {
        boolean muted = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0;

        if (muted) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 5, AudioManager.FLAG_SHOW_UI);
        } else {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI);
        }
    }

    private void showSystemVolumePanel() {
        audioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
    }

    // -------------------------
    // SETTINGS SCREEN
    // -------------------------
    private void openSettings() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    // -------------------------
    // FOREGROUND SERVICE
    // -------------------------
    private void startForegroundServiceNotification() {
        String channelId = "volume_control";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel =
                    new NotificationChannel(channelId, "Volume Bubble",
                            NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Floating Volume Control")
                .setContentText("Running in background")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .build();

        startForeground(1, notification);
    }

    // -------------------------
    // UTILITIES
    // -------------------------
    private int getBubbleSize() {
        return getSharedPreferences("settings", MODE_PRIVATE)
                .getInt("bubble_size", 50);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (bubbleView != null) {
            windowManager.removeView(bubbleView);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
