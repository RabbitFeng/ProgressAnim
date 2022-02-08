package com.rabbit.progressanim;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.animation.LinearInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.rabbit.anim.ProgressAnimWrapper;

import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private TextView tvProgress;

    private ObjectAnimator objectAnimator;
    private ValueAnimator valueAnimator;

    private ProgressHandler progressHandler;

    private Handler handler;

    private ProgressAnimWrapper progressAnimWrapper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindView();

        // 初始化Handler
        progressHandler = new ProgressHandler(new WeakReference<>(this));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            handler = Handler.createAsync(getMainLooper());
        } else {
            handler = new Handler(getMainLooper());
        }

        // 属性动画
//        executeAnimWithPropertyAnim(1);

        // Handler
//        executeAnimWithHandler(1);

        // ProgressAnimWrapper
        executeAnimWithWrapper();
    }

    private void bindView() {
        progressBar = findViewById(R.id.pb);
        tvProgress = findViewById(R.id.tv_progress);
    }

    /**
     * 属性动画
     * 1. ValueAnimator
     * 2. ObjectAnimator
     * 3. ViewPropertyAnimator
     */
    private void executeAnimWithPropertyAnim(int method) {
        switch (method) {
            case 1:
                // 1.ValueAnimator
                ValueAnimator valueAnimator = ValueAnimator.ofInt(0, 100)
                        .setDuration(2000);
                valueAnimator.setInterpolator(new LinearInterpolator());
                valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        int animatedValue = (int) animation.getAnimatedValue();
                        progressBar.setProgress(animatedValue);
                    }
                });
                this.valueAnimator = valueAnimator;
                valueAnimator.start();
                break;
            case 2:
                // 2.ObjectAnimator
                PropertyValuesHolder propertyValuesHolder = PropertyValuesHolder.ofInt("progress", 0, 100);
                ObjectAnimator objectAnimator = ObjectAnimator.ofPropertyValuesHolder(progressBar, propertyValuesHolder)
                        .setDuration(2000);
                objectAnimator.setInterpolator(new LinearInterpolator());
                this.objectAnimator = objectAnimator;
                objectAnimator.start();
                break;
            case 3:
                // 3.ViewPropertyAnimator
                progressBar.post(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.animate()
                                .setDuration(2000)
                                .setInterpolator(new LinearInterpolator())
                                .setUpdateListener(animation -> {
                                    // 默认情况下，Animator的动画值区间是[0f,1f]
                                    float animatedValue = (float) animation.getAnimatedValue();
                                    int max = progressBar.getMax();
                                    int progress = (int) (animatedValue * max);
                                    progressBar.setProgress(progress);
                                })
                                .start();
                    }
                });
                break;
        }
    }

    /**
     * Handler方式执行动画
     * 1. Handler延时消息
     * 2. Handler延时任务
     */
    private void executeAnimWithHandler(int method) {
        switch (method) {
            case 1:
                // 1.Handler延时消息
                Message obtain = Message.obtain(progressHandler, ProgressHandler.MSG_UPDATE, progress);
                progressHandler.sendMessageDelayed(obtain, 20);
//                progressHandler.sendEmptyMessageDelayed(ProgressHandler.MSG_UPDATE, 20);
                break;
            case 2:
                // 2.Handler延时任务
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        int progress = MainActivity.this.progress + 10;
                        boolean finish = MainActivity.this.handleProgressUpdate(progress);
                        if (!finish) {
                            handler.postDelayed(this, 20);
                        }
                    }
                }, 20);
                break;
        }
    }

    /**
     * ProgressAnimWrapper执行动画
     */
    public void executeAnimWithWrapper() {
        progressAnimWrapper = new ProgressAnimWrapper(progressBar, 1000, new LinearInterpolator(), 200);
        postDelayed(() -> progressAnimWrapper.animateTo(100), 200);
        postDelayed(() -> progressAnimWrapper.animateTo(200), 400);
    }

    private static class ProgressHandler extends Handler {
        @NonNull
        private final WeakReference<Activity> activityWeakReference;

        // 更新进度值
        public static final int MSG_UPDATE = 0x01;

        public ProgressHandler(@NonNull WeakReference<Activity> activityWeakReference) {
            super(Looper.getMainLooper());
            this.activityWeakReference = activityWeakReference;
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            Activity activity = activityWeakReference.get();
            if (activity instanceof MainActivity) {
                switch (msg.what) {
                    case MSG_UPDATE:
                        // 获取进度值
                        int progress = ((MainActivity) activity).progress + 10;
                        boolean finish = ((MainActivity) activity).handleProgressUpdate(progress);
                        if (!finish) {
                            sendEmptyMessageDelayed(MSG_UPDATE, 20);
                        }
                }
            }
        }
    }

    private int progress;

    /**
     * 处理进度值更新
     *
     * @return 完成进度值动画或progressBar为null
     */
    private boolean handleProgressUpdate(int progress) {
        if (progressBar != null) {
            progressBar.setProgress(progress);
            this.progress = progressBar.getProgress();
            return progressBar.getMax() == progressBar.getProgress();
        } else {
            return true;
        }
    }

    /**
     * 延时任务
     */
    private void postDelayed(@NonNull Runnable r, long timeMillis) {
        handler.postDelayed(r, timeMillis);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (objectAnimator != null) {
            objectAnimator.removeAllListeners();
            objectAnimator.removeAllUpdateListeners();
            objectAnimator.cancel();
        }
        if (valueAnimator != null) {
            valueAnimator.removeAllListeners();
            valueAnimator.removeAllUpdateListeners();
            valueAnimator.cancel();
        }
        if (progressHandler != null) {
            progressHandler.removeCallbacksAndMessages(null);
        }
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
}