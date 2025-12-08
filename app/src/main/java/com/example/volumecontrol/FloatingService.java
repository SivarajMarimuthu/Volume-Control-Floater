package com.example.volumecontrol;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * FloatingService — minimal Option D implementation
 * - shrink + fade (stays on screen)
 * - expand on touch
 * - drag + snap
 * - single tap = show system volume UI
 * - double tap = mute/unmute
 * - foreground service
 */
public class FloatingService extends Service {
    private static final String TAG = "FloatingService";

    private WindowManager windowManager;
    private View bubbleView;
    private WindowManager.LayoutParams params; // single authoritative params
    private Handler handler = new Handler();

    private int bubbleSizeDp = 55;
    private int bubbleSizePx;
    private boolean isCollapsed = false;

    // auto-hide timing
    private static final long AUTO_HIDE_MS = 3000;
    private final Runnable hideRunnable = this::collapseBubble;

    // dragging
    private int startX, startY;
    private float startTouchX, startTouchY;
    private boolean isDragging = false;
    private final int DRAG_THRESHOLD_PX = 10;

    // gesture detector (single/double tap)
    private GestureDetector gestureDetector;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");

        // Require overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Overlay permission not granted — stopping service.");
            stopSelf();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        bubbleSizePx = dpToPx(bubbleSizeDp);

        LayoutInflater inflater = LayoutInflater.from(this);
        bubbleView = inflater.inflate(R.layout.floating_bubble, null);

        int layoutType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                bubbleSizePx,
                bubbleSizePx,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 50;
        params.y = 200;

        try {
            windowManager.addView(bubbleView, params);
        } catch (Exception e) {
            Log.e(TAG, "addView failed: " + e.getMessage());
            stopSelf();
            return;
        }

        // GestureDetector for reliable single/double tap detection
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                Log.d(TAG, "single tap detected by GestureDetector");
                showSystemVolumePanel();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                Log.d(TAG, "double tap detected by GestureDetector");
                toggleMute();
                return true;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return true; // must return true to receive events
            }
        });

        // Touch listener: feed gestures, then handle drag + snap + expand/collapse
        bubbleView.setOnTouchListener((v, event) -> {
            // If currently collapsed, expand on first ACTION_DOWN and consume the event
            if (isCollapsed && event.getAction() == MotionEvent.ACTION_DOWN) {
                expandBubble();
                resetHideTimer();
                return true;
            }

            // let gesture detector inspect the event (it handles taps)
            gestureDetector.onTouchEvent(event);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = params.x;
                    startY = params.y;
                    startTouchX = event.getRawX();
                    startTouchY = event.getRawY();
                    isDragging = false;
                    // ensure visible
                    bubbleView.animate().alpha(1f).setDuration(120).start();
                    resetHideTimer();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - startTouchX;
                    float dy = event.getRawY() - startTouchY;

                    if (!isDragging && (Math.abs(dx) > DRAG_THRESHOLD_PX || Math.abs(dy) > DRAG_THRESHOLD_PX)) {
                        isDragging = true;
                    }

                    if (isDragging) {
                        params.x = startX + (int) dx;
                        params.y = startY + (int) dy;
                        try {
                            windowManager.updateViewLayout(bubbleView, params);
                        } catch (Exception ex) {
                            Log.w(TAG, "updateViewLayout in MOVE failed: " + ex.getMessage());
                        }
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isDragging) {
                        snapToEdge();
                    }
                    // restart auto-hide timer
                    resetHideTimer();
                    return true;
            }
            return false;
        });

        startForegroundNotification();
        resetHideTimer();
    }

    // collapse: shrink & fade but stay fully on-screen
    private void collapseBubble() {
        if (bubbleView == null || params == null) return;
        if (isCollapsed) return;

        // visual shrink + fade
        bubbleView.animate()
                .scaleX(0.6f)
                .scaleY(0.6f)
                .alpha(0.25f)
                .setDuration(240)
                .start();

        isCollapsed = true;
        Log.d(TAG, "bubble collapsed (shrink+fade)");
    }

    // expand back to normal
    private void expandBubble() {
        if (bubbleView == null || params == null) return;
        if (!isCollapsed) return;

        bubbleView.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(180)
                .start();

        isCollapsed = false;
        Log.d(TAG, "bubble expanded");
    }

    private void resetHideTimer() {
        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, AUTO_HIDE_MS);
    }

    private void snapToEdge() {
        if (params == null || bubbleView == null) return;

        int screenWidth = windowManager.getDefaultDisplay().getWidth();
        int centerX = params.x + params.width / 2;

        if (centerX <= screenWidth / 2) {
            params.x = 0; // snap left
        } else {
            params.x = screenWidth - params.width; // snap right
        }

        try {
            windowManager.updateViewLayout(bubbleView, params);
        } catch (Exception e) {
            Log.w(TAG, "snap updateViewLayout failed: " + e.getMessage());
        }
    }

    private void showSystemVolumePanel() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am != null) am.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
    }

    private void toggleMute() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        try {
            am.adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI | AudioManager.FLAG_PLAY_SOUND);
        } catch (Exception e) {
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (cur == 0) am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(1, max / 6), AudioManager.FLAG_SHOW_UI);
            else am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI);
        }
    }

    private void startForegroundNotification() {
        String channelId = "volume_bubble_channel";
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(channelId, "Volume Bubble", NotificationManager.IMPORTANCE_LOW);
            if (nm != null) nm.createNotificationChannel(ch);
        }

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Volume Bubble")
                .setContentText("Floating volume bubble is running")
                .setSmallIcon(R.drawable.ic_volume) // ensure exists
                .setOngoing(true)
                .build();

        startForeground(42, notification);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(hideRunnable);
        try {
            if (bubbleView != null) windowManager.removeView(bubbleView);
        } catch (Exception e) {
            Log.w(TAG, "removeView failed: " + e.getMessage());
        }
    }
}
