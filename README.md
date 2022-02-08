# `Android`进度条动画

## 1.为进度条添加动画效果

### 配合`属性动画`实现进度条动画

`Android`原生控件`ProgressBar`提供`setProgress()`设置进度条的进度值。可以借助属性动画来实现进度条动画，大概有三种写法：

- `ValueAnimator`

  值动画，支持`int`、`float`、`rgb`、`object`等，通过`ValueAnimator`工厂方法`ofInt()`、`ofFloat()`、`ofArgb()`、`ofObject()`等方法获取(还有一个不太常用的`ofPropertyValuesHolder`)。通过添加`AnimatorUpdateListener`来获取和使用动画的值。

- `ObjectAnimator`

  `ValueAnimator`的子类，融合了`ValueAnimator`的计时引擎和值计算以及为目标的命名属性添加动画效果的功能。

  添加动画效果的对象属性必须具有`set<PropertyName>`形式的`setter`函数（因为是使用反射获取了方法）如果不存在`setter`函数，有三种做法：

  -  添加`setter`方法到类中
  -  封装或更改容器类
  -  改用`ValueAnimator`

  如果只设定一个值，会认定为动画的结束值，动画的起始值`getter`方法中获取，所以这时需要有`get<Property>`形式的`getter`方法。

  `getter(如果需要的话)`和`setter`方法操作的对象必须和为`ObjectAnimator`指定的`起始值`和`结束值`的类型相同。

- `ViewPropertyAnimator`

  适用于`View`基础动画效果，如平移、旋转、不透明度等，多个属性的组合动画。提供了链式调用，也具有更好的性能。只能通过`View.animate`方法获取实例。当然这里设置的是进度值，其实是不太合适的。

```java
/**
 * 简单的进度条动画
 * 1. ValueAnimator。值动画。
 * 2. ObjectAnimator。ValueAnimator子类，通过反射获取setProperty方法注入值。
 *    必须保证类型正确以及property提供了公开的set方法
 * 3. ViewPropertyAnimator。适合于View基础属性的组合动画
 */
private void executeSimpleProgressAnim(int method) {
    switch (method) {
        case 1:
            // 1. ValueAnimator
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
            // 2. ObjectAnimator
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
```

### 配合`Handler`实现进度条动画

`Handler`实现进度条动画的关键在于`延时消息`或`延时任务`。

**继承Handler**

如果继承`Handler`类，可以调用`Handler.sendEmptyMessageDelayed()`相关的方法执行延时操作，如下：

```java
public static class ProgressHandler extends Handler {
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

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    // ...
  
    // 初始化Handler
    progressHandler = new ProgressHandler(new WeakReference<>(this));
    
    executeSimpleProgressAnim(4);
}

private void bindView() {
    progressBar = findViewById(R.id.pb);
    tvProgress = findViewById(R.id.tv_progress);
}

private void executeSimpleProgressAnim(int method) {
    switch (method) {
        // ...
        case 4:
            // 4.Handler
            progressHandler.sendEmptyMessageDelayed(ProgressHandler.MSG_UPDATE, 20);
    }
}

// 进度值
private int progress;

/**
 * 处理进度值更新
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

 @Override
    protected void onDestroy() {
        super.onDestroy();
        // ...
        if (progressHandler != null) {
            progressHandler.removeCallbacksAndMessages(null);
        }
    }
```

除了继承`Handler`类发送延时消息还可以发送延时事件:

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    // ... 
    
    // 初始化Handler
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        handler = Handler.createAsync(getMainLooper());
    } else {
        handler = new Handler(getMainLooper());
    }

    executeSimpleProgressAnim(5);
}

private void executeSimpleProgressAnim(int method) {
    switch (method) {
    	// ...
        case 5:
            // 5.Handler发送延时任务
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

 @Override
protected void onDestroy() {
    super.onDestroy();
    // ...
    if (handler != null) {
        handler.removeCallbacksAndMessages(null);
    }
}
```

## 2.描述任务进度的进度条动画

使用属性动画可以实现流畅的进度条动画。但是在一般的情境下我们会使用进度条的进度值映射任务完成度。

**任务进度度量方式**

`任务A`的进度值必须是可以度量的：

- 任务进度可以获取到
- 任务进度不容易获取，需要通过在任务中设置回调、事件等方式通知

**任务进度关键属性**

描述任务`A`进度的两个关键属性：`任务执行时间`和`任务完成进度`。

可以考虑使用控制变量的方式

- 控制`任务执行时间`，适合于对于时间敏感的业务逻辑，如`每500ms获取任务完成进度`
- 控制`任务完成进度`，适合于对任务进度敏感的业务逻辑，如`每完成一个子任务通知一次`

**串行和并行**

- 子任务串行执行。即执行`子任务ai`时前`i`个子任务已经执行完成，之后的`n-i`个子任务尚未执行，由当前子任务就可以直接获取到任务进度。
- 子任务并行执行。不能由当前子任务直接获取到任务进度，记录已完成子任务状态，增量地表示任务执行进度。

**动画同步**

- 同步执行动画

  任务开始时就通知动画播放

- 滞后执行动画

  任务结束后再执行该段动画

我遇到的业务场景简单描述为：`删除选中图片，使用进度条描述删除过程`。

`被动通知`+`控制任务完成进度`+`串行/并行`+`滞后执行动画`

### 包装`ProgressBar`实现动画

***`ProgressAnimWrapper`***

```java
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
     */
    public synchronized void animateOver(int over) {
        animateTo(progressEnd + over);
    }

    /**
     * 过渡到指定进度值
     * 暂停运行中的动画，取当前进度值作为下一次动画初始值。若to和maxProgress的较小值作为下一次动画结束值
     */
    public void animateTo(int to) {
        if (valueAnimator.isRunning()) {
            valueAnimator.pause();
        }

        progressStart = progress;
        progressEnd = Math.min(to, maxProgress);

        valueAnimator.start();
    }
}
```

封装了`ProgressBar`的实例，在创建该类的实例时做参数传入；

子任务串行可以调用`animateTo`指定动画过渡到指定进度值，或者`animateOver`方法增量地计算需要过渡到的指定进度值。子任务并行调用`animateOver`方法，对于子任务并行的情景，只采用增量地计算进度值的方式。（`Java SE 1.6+`对`synchronized`做了优化，所以`animateOver()`默认用`synchronized`关键字修饰，无线程竞争时获取偏向锁，有线程竞争时锁升级）

在使用时：

```java
/**
 * ProgressAnimWrapper执行动画
 */
public void executeAnimWithWrapper() {
    progressAnimWrapper = new ProgressAnimWrapper(progressBar, 1000, new LinearInterpolator(), 200);
    postDelayed(() -> progressAnimWrapper.animateTo(100), 200);
    postDelayed(() -> progressAnimWrapper.animateTo(200), 400);
}

/**
 * 延时任务
 */
private void postDelayed(@NonNull Runnable r, long timeMillis) {
    handler.postDelayed(r, timeMillis);
}
```



### 优化

**提取接口**

移除对于`ProgressBar`实例，注册进度监听器`OnProgressListener`，在`onChange`回调中获取进度值并更新。

`ProgressAnimWrapper`的职责更改，修改类名为`ProgressAnim`

提取`ProgressAnimInterface`接口，`ProgressAnim`实现`ProgressAnimInterface`接口

***`ProgressAnimInterface`***

```java
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
     * @param key 键。
     */
    void unregisterOnProgressListener(@Nullable String key);

    /**
     * 取消注册所有的进度监听器
     */
    void unregisterAllOnProgressListener();
}
```

**生成器模式优化构建过程**

`ProgressAnim`构造过程比较繁琐，为了保证构建顺序，尽量在构造器中传递参数，但是会导致参数列表过长，并且有些参数或许不希望进行配置而使用默认值即可，就需要提供多个构造方法；使用`setter`注入能够避免参数列表过长的问题，但是调用公开的`API`方法就不能保证构造顺序，也不容易实现某些参数仅在构造过程传入。

考虑使用`生成器模式`优化构建过程，能够很好地解决上述问题：

- 构造过程返回`Buidler`实例以链式调用，调用`create()`方法返回创建的实例对象，结束构建过程
- 屏蔽实例对象的组装细节，能够支持构造过程中可以不需要考虑调用方法的顺序。

`Android`中对于`设计模式`的实践和`Java设计模式`有些差异，可以参考`AlertDialog`中的`生成器模式`。

使用`BuildParams`封装构造参数，可以看作缓存，在调用`Builder.create()`方法时按照顺序从`BuildParams`中获取相应的参数值。

***`BuildParams`***

```java
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
```

***`Builder`***

```java
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
```

***`ProgressAnim`***

```java
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
```
