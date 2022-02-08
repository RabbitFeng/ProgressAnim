package com.rabbit.anim;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Created by RabbitFeng on 2022/2/8
 */
public interface ProgressAnimInterface {
    /**
     * 执行动画到指定进度值
     *
     * @param animateTo 进度值
     */
    void animateTo(int animateTo);

    /**
     * 执行动画经过指定进度值。需要使用synchronized关键字修饰
     *
     * @param animateOver 进度值
     */
    void animateOver(int animateOver);

    /**
     * 释放资源
     */
    void release();

    /**
     * 进度监听器
     */
    interface OnProgressChangeListener {
        /**
         * 进度值变化
         *
         * @param progress 当前进度值
         */
        void onChange(int progress);

        /**
         * 进度值动画结束
         */
        void onFinish();
    }

    /**
     * 注册进度监听器
     *
     * @param key      键。不能为空
     * @param listener 监听器实例。不能为空
     */
    void registerOnProgressListener(@NonNull String key, @NonNull OnProgressChangeListener listener);

    /**
     * 取消注册进度监听器
     * 若key为null，则取消注册所有的进度监听器
     *
     * @param key 键。
     */
    void unregisterOnProgressListener(@Nullable String key);

    /**
     * 取消注册所有的进度监听器
     */
    void unregisterAllOnProgressListener();

}
