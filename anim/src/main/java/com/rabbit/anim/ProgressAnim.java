package com.rabbit.anim;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.LinearInterpolator;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by RabbitFeng on 2022/2/8
 */
public class ProgressAnim implements ProgressAnimInterface {
    /**
     * 进度监听器
     */
    protected final Map<String, OnProgressChangeListener> onProgressChangeListenerMap = new ConcurrentHashMap<>();

    protected ValueAnimator progressAnimator = ValueAnimator.ofFloat(0f, 1f);

    /**
     * 动画持续时间
     */
    protected long duration;

    /**
     * 插值器
     */
    protected TimeInterpolator interpolator = new LinearInterpolator();

    /**
     * 最大进度
     */
    private int max;

    /**
     * 当前进度值
     */
    protected int progress;

    /**
     * 动画开始进度值
     */
    protected int progressStart;

    /**
     * 动画结束进度值
     */
    protected int progressEnd;

    /**
     * 主线程Handler
     */
    protected final Handler mainHandler;

    /**
     * 私有化构造器
     */
    private ProgressAnim() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            mainHandler = Handler.createAsync(Looper.getMainLooper());
        } else {
            mainHandler = new Handler(Looper.getMainLooper());
        }
    }

    /**
     * 装载动画
     */
    protected void setupAnim() {
        progressAnimator.setDuration(duration);
        progressAnimator.setInterpolator(interpolator);
        progressAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float runningPer = (float) animation.getAnimatedValue();
                progress = (int) (progressStart + runningPer * (progressEnd - progressStart));
                // 通知进度变化
                notifyOnProgressChange();
            }
        });
    }

    /**
     * 通知进度变化
     */
    protected void notifyOnProgressChange() {
        boolean isFinish = max == progress;
        for (String key : onProgressChangeListenerMap.keySet()) {
            // 遍历监听器Map通知进度更新
            ProgressAnim.OnProgressChangeListener onProgressChangedListener = onProgressChangeListenerMap.get(key);
            if (onProgressChangedListener != null) {
                onProgressChangedListener.onChange(progress);
                // 若当前进度值=总进度值，则通知动画结束
                if (isFinish) {
                    onProgressChangedListener.onFinish();
                }
            }
        }
    }

    @Override
    public void animateTo(int animateTo) {
        if (progressAnimator.isRunning()) {
            progressAnimator.pause();
        }

        progressStart = progress;
        progressEnd = Math.min(animateTo, max);

        runOnUIThread(() -> {
            progressAnimator.start();
        });
    }

    @Override
    public synchronized void animateOver(int animateOver) {
        animateTo(progressEnd + animateOver);
    }

    @Override
    public void registerOnProgressListener(@NonNull String key, @NonNull OnProgressChangeListener listener) {
        onProgressChangeListenerMap.put(key, listener);
    }

    @Override
    public void unregisterOnProgressListener(@Nullable String key) {
        if (key == null) {
            unregisterAllOnProgressListener();
        } else {
            onProgressChangeListenerMap.remove(key);
        }
    }

    @Override
    public void unregisterAllOnProgressListener() {
        onProgressChangeListenerMap.clear();
    }

    /**
     * Runnable主线程执行
     *
     * @param runnable 延时任务
     */
    protected void runOnUIThread(@NonNull Runnable runnable) {
        runOnUIThreadDelay(runnable, 0);
    }

    /**
     * Runnable主线程延时执行
     *
     * @param runnable    延时任务
     * @param delayMillis 延迟时间
     */
    protected void runOnUIThreadDelay(@NonNull Runnable runnable, long delayMillis) {
        mainHandler.postDelayed(runnable, delayMillis);
    }

    /**
     * 设置动画时长
     *
     * @param timeMillis 动画时长(毫秒)
     */
    protected void setDuration(long timeMillis) {
        this.duration = timeMillis;
    }

    /**
     * 设置动画插值器
     *
     * @param interpolator 动画插值器
     */
    protected void setInterpolator(TimeInterpolator interpolator) {
        this.interpolator = interpolator;
    }

    /**
     * 设置最大进度
     *
     * @param max 最大进度
     */
    protected void setMax(int max) {
        this.max = max;
    }

    @Override
    public void release() {
        progressAnimator.removeAllUpdateListeners();
        progressAnimator.removeAllListeners();
        progressAnimator.cancel();

        unregisterAllOnProgressListener();
    }

    /**
     * 封装构造参数
     */
    protected static class BuildParams {
        @IntRange(from = 0, to = 100)
        int max = 100;

        /**
         * 动画持续时间（每段）
         */
        long duration = 300;

        /**
         * 动画插值器类型
         */
        private TimeInterpolator interpolator = new LinearInterpolator();

        /**
         * 进度监听器Map
         */
        private final Map<String, OnProgressChangeListener> onProgressChangeListenerMap = new ConcurrentHashMap<>();
    }

    /**
     * ProgressAnim生成器
     */
    public static class Builder {
        @NonNull
        private final BuildParams P;

        public Builder() {
            this(new BuildParams());
        }

        public Builder(@NonNull BuildParams p) {
            P = p;
        }

        /**
         * 设置最大进度值
         *
         * @param max 最大进度值
         * @return Builder实例以链式调用
         */
        public Builder setMax(@IntRange(from = 0, to = Integer.MAX_VALUE) int max) {
            P.max = max;
            return this;
        }

        /**
         * 设置动画时长
         *
         * @param duration 动画时长。数值需要大于0
         * @return Builder实例以链式调用
         */
        public Builder setDuration(long duration) {
            if (duration > 0) {
                P.duration = duration;
            }
            return this;
        }

        /**
         * 设置动画插值器类型
         *
         * @param interpolator 动画插值器
         * @return Builder实例以链式调用
         */
        public Builder setInterpolator(@NonNull TimeInterpolator interpolator) {
            P.interpolator = interpolator;
            return this;
        }

        /**
         * 注册进度监听器
         *
         * @param key                      监听器Key
         * @param onProgressChangeListener 进度监听器
         * @return Builder实例以链式调用
         */
        public Builder registerOnProgressListener(@NonNull String key, @NonNull OnProgressChangeListener onProgressChangeListener) {
            P.onProgressChangeListenerMap.put(key, onProgressChangeListener);
            return this;
        }

        /**
         * 创建ProgressAnim实例对象
         *
         * @return ProgressAnim实例
         */
        public ProgressAnim create() {
            ProgressAnim progressAnim = new ProgressAnim();
            progressAnim.max = P.max;
            progressAnim.interpolator = P.interpolator;
            progressAnim.duration = P.duration;
            progressAnim.onProgressChangeListenerMap.putAll(P.onProgressChangeListenerMap);
            progressAnim.setupAnim();
            return progressAnim;
        }
    }
}