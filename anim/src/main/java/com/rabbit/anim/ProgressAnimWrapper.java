package com.rabbit.anim;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;

/**
 * Created by RabbitFeng on 2022/1/10
 * 进度动画包装器
 */
public class ProgressAnimWrapper {
    // ProgressBar实例
    @NonNull
    private final ProgressBar progressBar;

    // Animator，为每个子任务执行动画
    private ValueAnimator valueAnimator;

    // 动画时间
    private long duration = 2000;

    // 时间插值器
    private TimeInterpolator interpolator = new LinearInterpolator();

    // 最大进度值
    private int maxProgress;

    // 动画开始进度值
    private int progressStart;

    // 动画结束进度值
    private int progressEnd;

    // 记录当前进度值
    private int progress;

    public ProgressAnimWrapper(@NonNull ProgressBar progressBar, long duration, TimeInterpolator interpolator) {
        this(progressBar, duration, interpolator, 100);
    }

    public ProgressAnimWrapper(@NonNull ProgressBar progressBar, long duration, TimeInterpolator interpolator, int maxProgress) {
        this.progressBar = progressBar;
        this.duration = duration;
        this.interpolator = interpolator;
        this.maxProgress = maxProgress;
        setupAnim();
    }

    /**
     * 装载动画
     * 调用该方法需要考虑调用时序，并且不希望在ValueAnimator运行过程中改变duration等属性，所以该方法仅在初始化时调用
     */
    private void setupAnim() {
        valueAnimator = ValueAnimator.ofFloat(0f, 1f);
        valueAnimator.setInterpolator(interpolator);
        valueAnimator.setDuration(duration);
        valueAnimator.addUpdateListener(animation -> {
            float runningPercent = (float) animation.getAnimatedValue();
            progress = (int) ((progressEnd - progressStart) * runningPercent + progressStart);
            progressBar.setProgress(progress);
        });
        progressBar.setMax(maxProgress);
    }

    /**
     * 过渡过指定进度值
     * 子任务并行
     *
     * @param over 进度值
     */
    public void animateOver(int over) {
        animateTo(progressEnd + over);
    }

    /**
     * 过渡过指定进度值，同步方法
     * 子任务并行
     *
     * @param over 进度值
     */
    public synchronized void animateOverSync(int over) {
        animateTo(progressEnd + over);
    }

    /**
     * 过渡到指定进度值
     * 暂停运行中的动画，取当前进度值作为下一次动画初始值。若to和maxProgress的较小值作为下一次动画结束值
     *
     * @param to 进度值
     */
    public void animateTo(int to) {
        if (valueAnimator.isRunning()) {
            valueAnimator.pause();
        }

        progressStart = progress;
        progressEnd = Math.min(to, maxProgress);

        valueAnimator.start();
    }

    /**
     * 释放资源
     */
    public void release() {
        if (valueAnimator != null) {
            valueAnimator.removeAllListeners();
            valueAnimator.removeAllUpdateListeners();
            valueAnimator.cancel();
        }
    }
}