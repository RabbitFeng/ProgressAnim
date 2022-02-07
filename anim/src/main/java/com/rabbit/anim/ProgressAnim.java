package com.rabbit.anim;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by RabbitFeng on 2022/1/29
 * 进度条动画
 */
public class ProgressAnim {
    private static final String TAG = ProgressAnim.class.getSimpleName();

    /**
     * 进度监听器
     */
    public interface OnProgressChangeListener {
        /**
         * 进度值变化回调
         *
         * @param progress    当前进度值
         * @param maxProgress 总进度值
         */
        void onProgressChange(@IntRange(from = 0, to = Integer.MAX_VALUE) int progress,
                              @IntRange(from = 0, to = Integer.MAX_VALUE) int maxProgress);

        /**
         * 进度值动画结束回调
         */
        void onFinish();
    }

    /**
     * 快速结束监听器
     */
    public interface OnFastFinishListener {
        /**
         * 完成快速结束
         */
        void onFinish();
    }

    /**
     * 重置状态监听器
     */
    public interface OnResetListener {
        void onSuccess(int code);
    }

    /**
     * 当前进度值
     */
    @IntRange(from = 0, to = Integer.MAX_VALUE)
    private int progress;

    /**
     * 总进度值
     */
    @IntRange(from = 0, to = Integer.MAX_VALUE)
    private int maxProgress;

    /**
     * 动画开始时进度值
     */
    private int progressStart;

    /**
     * 动画结束时进度值
     */
    private int progressEnd;

    /**
     * 动画时间
     */
    private long duration;

    /**
     * 超时时间
     */
    private long timeout;

    /**
     * 超时后进度条动画时间
     */
    private long timeoutDuration;

    /**
     * 时间插值器
     */
    private TimeInterpolator interpolator = new LinearInterpolator();

    /**
     * 快速结束时间插值器
     */
    private TimeInterpolator fastFinishInterpolator = new AccelerateInterpolator();

    /**
     * 动画进度值
     */
    private final ValueAnimator progressAnim = ValueAnimator.ofFloat(0f, 1f);

    /**
     * 是否阻塞动画标识
     * 值为true, 则阻塞动画，调用`animateTo()`或`animateOver()`不会触发动画，
     * 但是记录该段动画的`progressEnd`值，以保证在解除阻塞时能够从`progressStart`过渡到`progressEnd`
     * 值为false, 则不阻塞动画，调用`animateTo()`或`animateOver()`即可触发动画，
     * <p>
     * 动画过程中尽量不要更改
     */
    private final AtomicBoolean blockAnimFlag = new AtomicBoolean(false);

    /**
     * 是否释放资源标识
     */
    private final AtomicBoolean releaseFlag = new AtomicBoolean(false);

    /**
     * 是否在快速结束过程中标识
     */
    private final AtomicBoolean onFastFinishFlag = new AtomicBoolean(false);

    /**
     * 缓存设置超时任务标志
     * 因为阻塞过程中不能设置超时任务，在解除阻塞或移除超时任务时置为false
     */
    private final AtomicBoolean cacheTimeoutTaskFlag = new AtomicBoolean(false);

    /**
     * 记录版本号
     */
    private final AtomicInteger versionCode = new AtomicInteger(0);

    /**
     * 主线程Handler
     */
    private static final Handler mainThreadHandler;

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            mainThreadHandler = android.os.Handler.createAsync(Looper.getMainLooper());
        } else {
            mainThreadHandler = new android.os.Handler(Looper.getMainLooper());
        }
    }

    /**
     * 进度值监听器
     */
    private final Map<String, OnProgressChangeListener> onProgressChangeListenerMap
            = new ConcurrentHashMap<>();

    /**
     * 私有化构造器，仅允许使用Builder创建实例
     */
    private ProgressAnim() {
    }

    /**
     * 装载动画
     */
    private void setupAnim() {
        progressAnim.setDuration(duration);
        progressAnim.setInterpolator(interpolator);
        progressAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if (!blockAnimFlag.get()) {
                    float runningPer = (float) animation.getAnimatedValue();
                    progress = (int) (progressStart + runningPer * (progressEnd - progressStart));
                    // 通知进度变化
                    notifyOnProgressChange();
                }
            }
        });
    }

    /**
     * 装载超时任务逻辑
     * 若timeout<=0，则立即执行
     */
    public void setupTimeoutTask() {
        Log.d(TAG, "setupTimeoutTask: called");
        if (onFastFinishFlag.get()) {
            // 在执行超时逻辑，快速结束
            Log.d(TAG, "setupTimeoutTask: onFastFinish");
            return;
        }
        if (blockAnimFlag.get()) {
            Log.d(TAG, "setupTimeoutTask: onBlockAnim");
            // 动画阻塞
            cacheTimeoutTaskFlag.set(true);
            return;
        }
        onFastFinishFlag.set(false);
        mainThreadHandler.postDelayed(() -> handleFastFinish(null), timeout);
    }

    /**
     * 尽快结束动画
     */
    private void handleFastFinish(@Nullable OnFastFinishListener onFastFinishListener) {
        Log.d(TAG, "handleFastFinish: called");
        if (blockAnimFlag.get()) {
            Log.d(TAG, "handleFastFinish: block and return");
            return;
        }
        onFastFinishFlag.set(true);
        if (progressAnim.isRunning()) {
            progressAnim.pause();
        }
        if (progress != maxProgress) {
            progressStart = progress;
            progressEnd = maxProgress;
            progressAnim.setDuration(timeoutDuration);
            progressAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    onFastFinishFlag.set(false);
                    if (onFastFinishListener != null) {
                        onFastFinishListener.onFinish();
                    }
                    progressAnim.removeListener(this);
                }
            });
            progressAnim.start();
        } else {
            if (onFastFinishListener != null) {
                onFastFinishListener.onFinish();
            }
        }
    }

    /**
     * 通知进度变化
     */
    public void notifyOnProgressChange() {
        boolean isFinish = maxProgress == progress;
        if (isFinish) {
            // 结束
            removeTimeoutTask();
        }
        for (String key : onProgressChangeListenerMap.keySet()) {
            // 遍历监听器Map通知进度更新
            OnProgressChangeListener onProgressChangedListener = onProgressChangeListenerMap.get(key);
            if (onProgressChangedListener != null) {
                onProgressChangedListener.onProgressChange(progress, maxProgress);
                // 若当前进度值=总进度值，则通知动画结束
                if (isFinish) {
                    onProgressChangedListener.onFinish();
                }
            }
        }
//        if (isFinish && timeoutFlag.get()) {
//            // 设置超时完成
//            timeoutFlag.set(false);
//        }

        // 重置状态以便开启下一次动画
//        if (isFinish) {
//            resetStatus();
//        }
    }

    /**
     * 移除超时任务
     */
    public void removeTimeoutTask() {
        cacheTimeoutTaskFlag.set(false);
        // 有可能手动调用
        mainThreadHandler.removeCallbacksAndMessages(null);
    }

    /**
     * 重置状态
     * 若动画不阻塞，则尽快结束动画
     */
    public void resetStatus(@Nullable OnResetListener onResetListener, boolean fastFinishIfNotBlock) {
        if (fastFinishIfNotBlock) {
            if (!blockAnimFlag.get()) {
                Log.d(TAG, "resetStatus: not block and wait");
                handleFastFinish(() -> resetStatusNow(onResetListener));
            } else {
                resetStatusNow(onResetListener);
            }
        } else {
            resetStatusNow(onResetListener);
        }
    }

    /**
     * 立即重置状态
     */
    public void resetStatusNow(@Nullable OnResetListener onResetListener) {
        progress = 0;
        progressStart = 0;
        progressEnd = 0;
        // 移除Task
        removeTimeoutTask();

        if (!blockAnimFlag.get()) {
            Log.d(TAG, "resetStatusNow: blockAnim");
            // 未阻塞则装载超时任务
            setupTimeoutTask();
        } else {
            Log.d(TAG, "resetStatusNow: unblockAnim");
        }
        notifyOnProgressChange();

        if (onResetListener != null) {
            onResetListener.onSuccess(versionCode.addAndGet(1));
        }
    }

    /**
     * 执行动画到执行进度值
     *
     * @param toProgress 指定进度值
     */
    public void animateTo(@IntRange(from = 0, to = Integer.MAX_VALUE) int toProgress) {
        animateTo(toProgress, versionCode.get());
    }

    /**
     * 执行动画到指定进度值
     *
     * @param toProgress 指定进度值
     * @param version    版本号
     */
    public void animateTo(@IntRange(from = 0, to = Integer.MAX_VALUE) int toProgress, int version) {
        Log.d(TAG, "animateTo: toProgress= " + toProgress + " version= " + version);
        if (version != this.versionCode.get() || onFastFinishFlag.get()) {
            // 若版本号等于当前版本号或者在执行超时操作
            return;
        }
        if (progressAnim.isRunning()) {
            progressAnim.pause();
        }

        progressStart = progress;
        progressEnd = Math.min(maxProgress, toProgress);

        if (!blockAnimFlag.get()) {
            // 不阻塞动画 且 未处理超时
            runOnUIThread(() -> {
                if (!onFastFinishFlag.get()) {
                    progressAnim.setDuration(duration);
                    progressAnim.start();
                }
            });
        }
    }

    /**
     * 执行动画过渡过指定进度值
     *
     * @param animateOver 过度过进度
     */
    public void animateOver(@IntRange(from = 0, to = Integer.MAX_VALUE) int animateOver) {
        animateOver(animateOver, versionCode.get());
    }

    /**
     * 执行动画过渡过指定进度值
     *
     * @param animateOver 过渡过进度
     * @param version     版本号
     */
    public void animateOver(@IntRange(from = 0, to = Integer.MAX_VALUE) int animateOver, int version) {
        animateTo(progressEnd + animateOver, version);
    }

    /**
     * 执行动画过指定进度值，同步方法。多线程调用
     */
    public synchronized void animateOverSyn(@IntRange(from = 0, to = Integer.MAX_VALUE) int animateOver, int version) {
        animateOver(animateOver, version);
    }

    /**
     * 执行动画过渡过指定进度值，同步方法。多线程调用
     */
    public synchronized void animateOverSyn(@IntRange(from = 0, to = Integer.MAX_VALUE) int animateOver) {
        animateOver(animateOver, versionCode.get());
    }

    /**
     * 阻塞动画
     */
    public void blockAnim() {
        blockAnimFlag.set(true);
        // 移除超时
        removeTimeoutTask();
        if (progressAnim.isRunning()) {
            progressAnim.pause();
        }
    }

    /**
     * 释放动画阻塞，并开启动画
     */
    public void unblockAnim() {
        blockAnimFlag.set(false);
        // 不能判定是否移除了TimeoutTask
        if (cacheTimeoutTaskFlag.getAndSet(false)) {
            setupTimeoutTask();
        }
        startAnim();
    }

    /**
     * 开启动画
     */
    public void startAnim() {
        mainThreadHandler.post(progressAnim::start);
    }

    /**
     * Runnable主线程执行
     */
    protected void runOnUIThread(@NonNull Runnable runnable) {
        runOnUIThreadDelay(runnable, 0);
    }

    /**
     * Runnable主线程延时执行
     *
     * @param runnable    任务
     * @param delayMillis 若小于0，则转为0
     */
    protected void runOnUIThreadDelay(@NonNull Runnable runnable, long delayMillis) {
        mainThreadHandler.postDelayed(runnable, delayMillis);
    }

    /**
     * 设置进度条动画超时
     *
     * @param timeout 超时时间
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    /**
     * @param duration 超时动画持续时间
     */
    public void setTimeoutDuration(long duration) {
        this.timeoutDuration = duration;
    }

    /**
     * 注册进度监听器
     */
    public void registerOnProgressChangedListener(@NonNull String key, @NonNull OnProgressChangeListener listener) {
        onProgressChangeListenerMap.put(key, listener);
    }

    /**
     * 取消注册进度监听器
     */
    public void unregisterOnProgressChangedListener(@NonNull String key) {
        onProgressChangeListenerMap.remove(key);
    }

    /**
     * 取消注册所有的进度监听器
     */
    public void unregisterAllOnProgressChangedListenerListener() {
        onProgressChangeListenerMap.clear();
    }

    /**
     * 释放动画资源
     */
    public void releaseAnim() {
        releaseFlag.set(true);
        mainThreadHandler.removeCallbacksAndMessages(null);
        progressAnim.removeAllListeners();
        progressAnim.removeAllUpdateListeners();
        progressAnim.cancel();
    }

    /**
     * 封装构建参数
     */
    public static class BuildParams {
        @IntRange(from = 0, to = Integer.MAX_VALUE)
        private int maxProgress;
        /**
         * 每段动画的持续时间
         */
        private long duration = 100;
        /**
         * 动画插值器类型
         */
        @NonNull
        private TimeInterpolator interpolator = new LinearInterpolator();
        /**
         * 进度条动画超时时间
         */
        private long timeout = -1;
        /**
         * 到达超时时间后的进度条结束时间
         */
        private long timeoutDuration = 300;
        /**
         * 阻塞动画
         */
        private boolean blockAnim = false;
        /**
         * 监听器列表
         */
        private final Map<String, OnProgressChangeListener> onProgressChangeListenerMap = new ConcurrentHashMap<>();
    }

    /**
     * ProgressAnim生成器
     */
    public static class Builder {
        private final BuildParams buildParams;

        public Builder() {
            buildParams = new BuildParams();
        }

        /**
         * 设置最大进度值
         *
         * @param maxProgress 最大进度值
         * @return Builder实例以链式调用
         */
        public Builder setMaxProgress(@IntRange(from = 0, to = Integer.MAX_VALUE) int maxProgress) {
            buildParams.maxProgress = maxProgress;
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
                buildParams.duration = duration;
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
            buildParams.interpolator = interpolator;
            return this;
        }

        /**
         * 设置超时
         *
         * @param timeout 超时时间
         * @return Builder实例以链式调用
         */
        public Builder setTimeout(long timeout) {
            if (timeout > 0) {
                buildParams.timeout = timeout;
            }
            return this;
        }

        /**
         * 设置超时后进度条动画时间
         *
         * @param timeoutDuration 超时后进度条动画时间
         * @return Builder实例以链式调用
         */
        public Builder setTimeoutDuration(long timeoutDuration) {
            buildParams.timeoutDuration = timeoutDuration;
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
            buildParams.onProgressChangeListenerMap.put(key, onProgressChangeListener);
            return this;
        }

        /**
         * 阻塞动画
         *
         * @return Builder实例以链式调用
         */
        public Builder blockAnim() {
            buildParams.blockAnim = true;
            return this;
        }

        /**
         * 创建ProgressAnim实例对象
         *
         * @return ProgressAnim实例
         */
        public ProgressAnim create() {
            ProgressAnim progressAnim = new ProgressAnim();
            progressAnim.maxProgress = buildParams.maxProgress;
            progressAnim.duration = buildParams.duration;
            progressAnim.timeout = buildParams.timeout;
            progressAnim.timeoutDuration = buildParams.timeoutDuration;
            progressAnim.onProgressChangeListenerMap.putAll(buildParams.onProgressChangeListenerMap);
            progressAnim.blockAnimFlag.set(buildParams.blockAnim);
            progressAnim.setupAnim();
            // 一定要在设置阻塞后设置
            progressAnim.setupTimeoutTask();
            return progressAnim;
        }
    }

}
