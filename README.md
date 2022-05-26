# EventBus 源码解析

# 一、开篇

&emsp;&emsp;一直以来就想写一篇关于 **EventBus** 源码解析的文章，因为这是我从业以来读的第一个深度剖析源码的框架，第一次接触这个框架还是我在赤子城科技实习的时候，第一次使用就带给当时没有多少经验的我十分震撼的感觉，可能这就是技术的力量吧。在我实习的时候第一次读了 **EventBus** 的源码，可以说给我带来了非常多的知识。如今时隔两年我又重新读了最新版的代码，虽然没有什么大的改动，但是收获比以前更多了。所以这次我将以文章及其他形式将 **EventBus** 的源码解读带给大家。

&emsp;&emsp;我始终认为，写一篇技术性的文章需要准备很长时间和耗费很大精力，并且为了能够让文章有一个很好的理解效果也需要在阐述中下功夫。阅读源码带给我们的不仅仅是更熟悉框架，而是我们能够发现一些设计思路和技巧，培养自身的封装设计能力。

&emsp;&emsp;结尾处会有我做了全面注释的源码解析仓库地址及其他资料

# 二、EventBus 全面介绍

![EventBus-Publish-Subscribe.png](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/c39e4f7e971d4d34bf825c7775bdf5ef~tplv-k3u1fbpfcp-watermark.image?)

- **EventBus 项目地址:** [EventBus](https://github.com/greenrobot/EventBus)
- **Version:** 3.3.1

&emsp;&emsp;**EventBus** 是一个适用于 **Android/Java** 的**发布/订阅事件总线**，整个框架最突出的核心思想就是**观察者模式**，其中被观察者是事件，观察者是需要处理事件的类或者说是类具体的方法。

&emsp;&emsp;整个框架的内部实现是基于**反射**和 **APT（Annotation Processing Tool）**，在早期的版本是基于反射实现，这会使效率比较低下和发生反射异常，所以在后续推出了基于 **APT** 编译时生成索引类来取代大部分的反射实现，这让运行时的效率大幅提升，还有就是避免了发生反射异常的严重问题。

&emsp;&emsp;然而然而然而！**EventBus** 的 **APT**，需要手动开启和配置，并且似乎这个开关隐藏的足够深，以至于有很多的开发者并不知道有这个东西！真的是有点可惜，在下文中会提到如何配置。

&emsp;&emsp;下面是我整理的关于 **EventBus** 相关的比较全面的知识，在解读源码之前，建议先对 **EventBus** 有一个全面的回顾：

![EventBus.png](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/5a095bf3e68040a687e2fdc596111ccc~tplv-k3u1fbpfcp-watermark.image?)

# 三、源码/设计解析

&emsp;&emsp;这里我将分几部分对整个框架的核心进行解析，为了使讲解的效果达到一个容易理解的程度，我也会在中间插入一些图示

1. **项目结构**
2. **实例构建**
3. **注册订阅/取消注册订阅**
4. **发布普通事件**
5. **发布黏性事件**
6. **事件发布器**

## 1.项目结构

&emsp;&emsp;**EventBus** 是适用于 **Java/Android** 两个平台的，从 **3.3.0** 版本开始 **greenrobot** 将 **EventBus** 分离为 **Java** 和 **Android** 两部分，并且默认情况下引入的依赖为 **Android** 版本，**Java** 版本需要调整依赖地址。**Android** 部分是我们需要关注的。

对于 **Android** 部分，我们需要关心的有三个模块：

- **EventBus:** 该模块是 EventBus 的核心内容

- **eventbus-android:** 该模块是针对 Android 平台特性的支持

- **EventBusAnnotationProcessor:** 针对 Subscribe 注解的注解处理器

## 2.实例构建

&emsp;&emsp;**EventBus** 的实例获取可以使用默认的全局单例模式，也可以使用 **Builder** 模式进行配置特定行为

### 2-1.通过 `getDefault()` 获取默认实例

我们常使用的是 `EventBus.getDefault()` 该方法获取一个实例来进行发布事件，我们看下源码部分：

```java
public class EventBus {

    // 默认实例变量
    static volatile EventBus defaultInstance;
    
    // 默认的 EventBus 构建器
    private static final EventBusBuilder DEFAULT_BUILDER = new EventBusBuilder();
    
    // 获取默认实例
    public static EventBus getDefault() {
        EventBus instance = defaultInstance;
        if (instance == null) {
            synchronized (EventBus.class) {
                instance = EventBus.defaultInstance;
                if (instance == null) {
                    instance = EventBus.defaultInstance = new EventBus();
                }
            }
        }
        return instance;
    }

    // 无参构造
    public EventBus() {
        this(DEFAULT_BUILDER);
    }
    
    // 包可见的一个形参的构造
    EventBus(EventBusBuilder builder) {
       // ...
    }   
}
```
我将代码精简了一下，只留了我们需要关注的地方，整体的 `getDefault()` 方法其实就是一个双重校验锁式单例的写法，其中创建实例的代码是调用了公开的无参构造，而这个无参构造最后调用了一个参数的构造 `EventBus(EventBusBuilder builder)` 传入了一个默认构建器，具体构建器的逻辑我们暂时不需要关注。

所以到现在我们可以看出来，默认实例其实就是一个双重校验锁式的单例，他们的调用顺序是这样的：

```java
getDefault() --> EventBus() --> EventBus(EventBusBuilder builder) ==> defaultInstance
```

最终调用了 `EventBus(EventBusBuilder builder)` 创建出了实例，默认的实例都是默认行为，如果我们需要进一步的配置行为，就需要用到第二种方式来构建实例。

### 2-2.通过 Builder 模式构建实例

&emsp;&emsp;我们对 **Builder** 模式也不陌生了，常用于一些框架之类的配置行为，通过 **Builder** 模式我们可以生产出不同行为的实例，并且代码还是链式调用，比较美观简洁。我们具体看一下 **EventBus** 的 **Builder** 模式。

```java
public class EventBus {
    // 创建 EventBusBuilder 实例
    public static EventBusBuilder builder() {
        return new EventBusBuilder();
    }
}

// 建造者
public class EventBusBuilder {

    EventBusBuilder() {}

    /**
     * 配置订阅函数执行有异常时，是否打印异常信息
     * @param logSubscriberExceptions boolean 默认：true
     * @return EventBusBuilder
     */
    public EventBusBuilder logSubscriberExceptions(boolean logSubscriberExceptions) {...}

    /**
     * 配置事件无匹配订阅函数时，是否打印信息
     * @param logNoSubscriberMessages boolean 默认：true
     * @return EventBusBuilder
     */
    public EventBusBuilder logNoSubscriberMessages(boolean logNoSubscriberMessages) {...}

    /**
     * 配置订阅函数执行有异常时，是否发布 SubscriberExceptionEvent 事件
     * @param sendSubscriberExceptionEvent boolean 默认：true
     * @return EventBusBuilder
     */
    public EventBusBuilder sendSubscriberExceptionEvent(boolean sendSubscriberExceptionEvent) {...}

    /**
     * 配置事件无匹配订阅函数时，是否发布 NoSubscriberEvent
     * @param sendNoSubscriberEvent boolean 默认：true
     * @return EventBusBuilder
     */
    public EventBusBuilder sendNoSubscriberEvent(boolean sendNoSubscriberEvent) {...}

    /**
     * 如果订阅者方法执行有异常时，是否抛出 SubscriberException
     * @param throwSubscriberException boolean 默认：false
     * @return EventBusBuilder
     */
    public EventBusBuilder throwSubscriberException(boolean throwSubscriberException) {...}

    /**
     * 配置是否事件继承
     * 默认情况下，EventBus 考虑事件类层次结构（将通知超类的订阅者）。
     * 关闭此功能将改善事件的发布。对于直接扩展 Object 的简单事件类，我们测得事件发布的速度提高了 20%。
     * 对于更复杂的事件层次结构，加速应该大于 20%。
     * 但是，请记住，事件发布通常只消耗应用程序内的一小部分 CPU 时间，除非它以高速率发布，例如每秒数十万个事件
     * @param eventInheritance boolean 默认：true
     * @return EventBusBuilder
     */
    public EventBusBuilder eventInheritance(boolean eventInheritance) {...}

    /**
     * 为 EventBus 提供一个自定义线程池，用于异步和后台事件传递。
     * 这是一个可以破坏事情的高级设置：确保给定的 ExecutorService 不会卡住以避免未定义的行为。
     */
    public EventBusBuilder executorService(ExecutorService executorService) {...}

    /**
     * 跳过类的方法验证
     */
    public EventBusBuilder skipMethodVerificationFor(Class<?> clazz) {...}

    /**
     * 配置是否即使有生成的索引也强制使用反射
     * @param ignoreGeneratedIndex boolean 默认：false
     * @return EventBusBuilder
     */
    public EventBusBuilder ignoreGeneratedIndex(boolean ignoreGeneratedIndex) {...}

    /**
     * 是否启用严格的方法验证
     * @param strictMethodVerification boolean 默认：false
     * @return EventBusBuilder
     */
    public EventBusBuilder strictMethodVerification(boolean strictMethodVerification) {...}

    /**
     * 添加索引类
     * @param index SubscriberInfoIndex APT 生成的订阅者索引类
     * @return EventBusBuilder
     */
    public EventBusBuilder addIndex(SubscriberInfoIndex index) {...}

    /**
     * 为所有 EventBus 日志设置特定的日志处理程序
     * 默认情况下，所有日志记录都是通过 Android 上的 {@code android.util.Log} 或 JVM 上的 System.out。
     */
    public EventBusBuilder logger(Logger logger) {...}

    /**
     * 获取 Logger
     */
    Logger getLogger() {...}

    /**
     * 获取主线程支持
     */
    MainThreadSupport getMainThreadSupport() {...}

    /**
     * 使用此构建器的值安装由 {@link EventBus#getDefault()} 返回的默认 EventBus
     * 在第一次使用默认 EventBus 之前必须只执行一次
     * 此方法调用后，再通过 {@link EventBus#getDefault()} 获取到的对象就是该构建器配置的实例
     * 所以该方法应该在最后调用
     * @throws EventBusException 如果已经有一个默认的 EventBus 实例
     */
    public EventBus installDefaultEventBus() {
        synchronized (EventBus.class) {
            if (EventBus.defaultInstance != null) {
                throw new EventBusException("Default instance already exists." +
                        " It may be only set once before it's used the first time to ensure consistent behavior.");
            }
            EventBus.defaultInstance = build();
            return EventBus.defaultInstance;
        }
    }

    /**
     * 根据当前配置构建 EventBus
     */
    public EventBus build() {
        return new EventBus(this);
    }
}
```

&emsp;&emsp;我们调用 `EventBus.builder()` 方法创建一个建造者实例（`EventBusBuilder`）对实例进行配置，最后通过 `EventBusBuilder.build()` 方法结尾，该方法返回一个配置好行为的 **EventBus** 实例，我们可以使用该实例进行发布事件等操作。

&emsp;&emsp;至此为止似乎有一个问题，我们不能使用 `EventBus.getDefault()` 来方便的获取实例了，其实是可以的，在 `EventBusBuilder` 中提供了一个方法 `installDefaultEventBus()`,该方法的源码我没有简化，可以回顾下上面的源码，该方法将配置好的 `EventBusBuilder` 去创建出一个 EventBus 实例，并且将这个实例赋值给了 `EventBus.defaultInstance`，我们就可以使用 `EventBus.getDefault()` 来获得我们配置好的对象了，需要注意的是该方法需要在最后替代 `EventBusBuilder.build()` 调用。

`EventBusBuilder` 提供了很多对于 **EventBus** 行为配置的方法：

- **logSubscriberExceptions(boolean)**: 订阅函数执行有异常时，是否打印异常信息

- **logNoSubscriberMessages(boolean)**: 事件无匹配订阅函数时，是否打印信息

- **sendSubscriberExceptionEvent(boolean)**: 订阅函数执行有异常时，是否发布 `SubscriberExceptionEvent` 事件

- **sendNoSubscriberEvent(boolean)**: 事件无匹配订阅函数时，是否发布 `NoSubscriberEvent`

- **throwSubscriberException(boolean)**: 如果订阅者方法执行有异常时，是否抛出 `SubscriberException`

- **eventInheritance(boolean)**: 是否处理事件继承

- **executorService(ExecutorService)**: 为 **EventBus** 提供一个自定义线程池，用于异步和后台事件传递

- **skipMethodVerificationFor(Class<?>)**: 跳过类的方法验证

- **ignoreGeneratedIndex(boolean)**: 是否即使有生成的索引也强制使用反射

- **strictMethodVerification(boolean)**: 是否启用严格的方法验证

- **addIndex(SubscriberInfoIndex)**: 添加索引类

- **logger(Logger)**: 为所有 **EventBus** 日志设置特定的日志处理程序

- **installDefaultEventBus()**: 创建基于配置行为的实例赋值给 `EventBus.defaultInstance`

- **build()**: 创建实例

&emsp;&emsp;如果我们需要对 **EventBus** 进行一些自定义的行为配置，我们就使用 **Builder** 模式构建实例，如果没有自定义行为配置的需要并且也不使用索引的情况下，我们只需要调用 `EventBus.getDefault()` 获取默认实例就足够了。

## 3.注册订阅/取消注册订阅

对于注册的流程是较为复杂的，为了能够很好的去阅读源码解读部分，先看下图熟悉注册的整个流程：

![EventBus注册.png](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/2acc99a4b108437ebab23a24b87484f8~tplv-k3u1fbpfcp-watermark.image?)

在讲解源码之前有几个重要的属性需要特别说一下：

- **Map<Class<?>, CopyOnWriteArrayList<Subscription>> subscriptionsByEventType**
    
    此 `Map` 为 `HashMap`，用于存储所有的订阅者方法，以订阅者方法接收的事件类型进行分类。
    **key:** 事件类的 `Class` 对象，**value:** 订阅者方法包装类集合。

- **Map<Object, List<Class<?>>> typesBySubscriber**

    此 `Map` 为 `HashMap`，用于存储订阅者接收的事件类型。  
    **key:** 订阅者实例，**value:** 所接收的事件类型的 `Class` 对象 `List`
    
- **Map<Class<?>, List<Class<?>>> eventTypesCache = new HashMap<>()**

    此 `Map` 为 `HashMap`，用于存储事件类型的所有父级类型，包括超类和接口。  
    **key:** 事件 Class 对象，**value:** 事件 `Class` 对象的所有父级 `Class` 对象，包括超类和接口

- **Map<Class<?>, Object> stickyEvents**

    此 `Map` 为 `ConcurrentHashMap`，用于存储当前存在的最新黏性事件。  
    **key:** 事件类的 `Class` 对象，**value:** 当前最新的黏性事件

### 3.1 注册（register）
首先我们调用 `register(Object subscriber)` 传入当前类的实例进行注册，该方法的大致代码如下：

```java
/**
 * 注册给定的订阅者以接收事件。一旦订阅者不再对接收事件感兴趣，就必须调用 {@link #unregister(Object)}。
 * 订阅者具有必须由 {@link Subscribe} 注释的事件处理方法。
 * {@link Subscribe} 注解还允许像 {@link ThreadMode} 和优先级这样的配置。
 * 订阅者可以是任何对象
 */
public void register(Object subscriber) {
    // 判断是否是 Android 平台，是否引用了 EventBus 的 Android 兼容库
    if (AndroidDependenciesDetector.isAndroidSDKAvailable() && !AndroidDependenciesDetector.areAndroidComponentsAvailable()) {
        // 满足条件进入此分支后，表示是 Android 平台，但是没有依赖 EventBus 的 Android 兼容库
        // 如果用户（开发人员）没有导入 Android 兼容库，则会崩溃。
        throw new RuntimeException("It looks like you are using EventBus on Android, " +
                "make sure to add the "eventbus" Android library to your dependencies.");
    }

    // 反射获取订阅者的 Class 对象
    Class<?> subscriberClass = subscriber.getClass();
    // 通过 subscriberMethodFinder 订阅方法查找器去查找订阅者的订阅方法，得到一个订阅方法List List<SubscriberMethod>
    List<SubscriberMethod> subscriberMethods = subscriberMethodFinder.findSubscriberMethods(subscriberClass);
    // 加同步锁，监视器为当前 EventBus 对象
    synchronized (this) {
        // 对订阅方法 List 进行遍历
        for (SubscriberMethod subscriberMethod : subscriberMethods) {
            // 遍历到的每一个方法对其产生订阅关系，就是正式存放在订阅者的大集合中
            subscribe(subscriber, subscriberMethod);
        }
    }
}
```

进入方法后，首先对平台进行判断，如果是 **Android** 平台还要检查是否依赖了 **EventBus Android** 部分的代码，也就是 **eventbus-android** 模块，后面就是获取到订阅者实例 `subscriber` 的 **Class** 对象，通过 `SubscriberMethodFinder`（订阅方法查找器）调用 `findSubscriberMethods(Class<?> subscriberClass)` 方法来对当前订阅者实例进行订阅方法的查找，拿到所有的订阅方法后对结果遍历调用 `subscribe(Object subscriber, SubscriberMethod subscriberMethod)` 将方法存放于总的事件集合中。

该方法主要就是对平台判断、查找订阅方法、将订阅方法存放于集合中为后续发布事件使用，在这段源码中有几个需要进一步探究的地方：`SubscriberMethodFinder`（订阅方法查找器）、`findSubscriberMethods()`（查找方法）、`subscribe`（产生订阅关系，存放到集合），我们进一步看源码。

### 3.2 订阅方法查找器（SubscriberMethodFinder）

该类是单独将订阅方法查找这一行为进行抽象得到的，主要用途就是查找订阅方法，其源码大概如下：

``` java
/**
 * 订阅者方法查找器
 */
class SubscriberMethodFinder {
    private static final int BRIDGE = 0x40;
    private static final int SYNTHETIC = 0x1000;
    private static final int MODIFIERS_IGNORE = Modifier.ABSTRACT | Modifier.STATIC | BRIDGE | SYNTHETIC;
    /**
     * 订阅者方法缓存 ConcurrentHashMap，为了避免重复查找订阅者的订阅方法，维护了此缓存
     * key:   Class<?>                 订阅者 Class 对象
     * value: List<SubscriberMethod>>  订阅者方法 List
     */
    private static final Map<Class<?>, List<SubscriberMethod>> METHOD_CACHE = new ConcurrentHashMap<>();
    // 订阅者索引类集合
    private List<SubscriberInfoIndex> subscriberInfoIndexes;
    // 是否进行严格的方法验证 默认值为 false
    private final boolean strictMethodVerification;
    // 是否忽略生成的索引 默认值为 false
    private final boolean ignoreGeneratedIndex;
    // FIND_STATE_POOL 长度
    private static final int POOL_SIZE = 4;
    // FindState 池，默认4个位置 POOL_SIZE = 4
    private static final FindState[] FIND_STATE_POOL = new FindState[POOL_SIZE];
    
    SubscriberMethodFinder(List<SubscriberInfoIndex> subscriberInfoIndexes, boolean strictMethodVerification, boolean ignoreGeneratedIndex) {...}

    /**
     * 查找订阅者方法
     * @param subscriberClass Class<?> 订阅者 Class 对象
     * @return List<SubscriberMethod> 订阅者方法List
     */
    List<SubscriberMethod> findSubscriberMethods(Class<?> subscriberClass) {...}

    /**
     * 查找订阅者方法
     * @param subscriberClass Class<?> 订阅者 Class 对象
     * @return List<SubscriberMethod> 订阅者方法 List
     */
    private List<SubscriberMethod> findUsingInfo(Class<?> subscriberClass) {...}

    /**
     * 从 FindState 中获取订阅者方法并正式发布
     * 该方法其实只是将形参 findState 中的 subscriberMethods 以新的 List 返回出来
     * 并且还对 findState 做了资源释放及回收的处理
     * @param findState FindState
     * @return List<SubscriberMethod> 订阅者方法
     */
    private List<SubscriberMethod> getMethodsAndRelease(FindState findState) {...}

    /**
     * 准备 FindState，会从对象池中去获取，没有缓存的情况下会去 new 一个新对象
     * 此处运用了对象复用及池化技术
     * @return FindState
     */
    private FindState prepareFindState() {...}

    /**
     * 获取索引类
     * @param findState FindState 查找状态类
     * @return SubscriberInfo 索引类
     */
    private SubscriberInfo getSubscriberInfo(FindState findState) {...}

    /**
     * 通过反射查找订阅者方法
     * @param subscriberClass Class<?> 订阅者 Class 对象
     * @return List<SubscriberMethod> 订阅者方法 List
     */
    private List<SubscriberMethod> findUsingReflection(Class<?> subscriberClass) {...}

    /**
     * 通过反射查找 {@link FindState#clazz} 对应类的订阅者方法
     * @param findState FindState
     */
    private void findUsingReflectionInSingleClass(FindState findState) {...}

    /**
     * 用于测试
     */
    static void clearCaches() {...}

    /**
     * 查找状态
     * 主要就是在查找订阅者方法的过程中记录一些状态信息
     * FindState 类是 SubscriberMethodFinder 的内部类，这个方法主要做一个初始化的工作。
     * 由于该类中字段多，为了内存做了对象缓存池处理，见{@link #FIND_STATE_POOL}
     */
    static class FindState {
        // 订阅者方法 List
        final List<SubscriberMethod> subscriberMethods = new ArrayList<>();
        // 按事件类型区分的方法 HashMap
        final Map<Class, Object> anyMethodByEventType = new HashMap<>();
        // 按方法签名存储
        final Map<String, Class> subscriberClassByMethodKey = new HashMap<>();
        // StringBuilder
        final StringBuilder methodKeyBuilder = new StringBuilder(128);
        // 订阅者的 Class 对象
        Class<?> subscriberClass;
        // 当前类的 Class 对象，该字段会随着从子类到父类查找的过程而进行赋值当前类的 Class 对象
        Class<?> clazz;
        // 是否跳过父类的方法查找
        boolean skipSuperClasses;
        // 索引类 初始值为 null
        SubscriberInfo subscriberInfo;

        /**
         * 为订阅者进行初始化
         * @param subscriberClass Class<?> 订阅者 Class 对象
         */
        void initForSubscriber(Class<?> subscriberClass) {...}

        /**
         * 释放资源，准备下一次复用
         */
        void recycle() {...}

        /**
         * 检查并将方法添加
         * @param method    Method 订阅方法
         * @param eventType Class<?> 事件 Class 对象，也就是该方法的形参
         * @return 校验结果
         */
        boolean checkAdd(Method method, Class<?> eventType) {...}

        /**
         * 检查并将方法添加，对方法签名校验
         * @param method    Method 订阅方法
         * @param eventType Class<?> 事件 Class 对象，也就是该方法的形参
         * @return 校验结果
         */
        private boolean checkAddWithMethodSignature(Method method, Class<?> eventType) {...}

        /**
         * 移动到父类
         */
        void moveToSuperclass() {...}
}
```

**FindState**

在该类中有一个静态内部类 `FindState`，此内部类主要用于在查找方法时记录一些状态和数据，其中需要提到的是里面的一些设计。

`FindState` 类中有一些集合用于存储查找过程中的数据，在 `SubscriberMethodFinder` 中有一个属性：

```java
// FIND_STATE_POOL 长度
private static final int POOL_SIZE = 4;
// FindState 池，默认4个位置 POOL_SIZE = 4
private static final FindState[] FIND_STATE_POOL = new FindState[POOL_SIZE];
```

该属性是一个对象缓存池，而缓存的对象就是 `FindState`，这样做是为了能够去复用被创建出来的 `FindState` 实例，每次使用完都会调用 `FindState.recycle()` 方法释放实例中的资源，然后将该实例放入缓存池中以便下次使用。对象缓存池的长度设置为 `POOL_SIZE = 4`，而对于 `FindState` 的获取，并非是直接 `new `,而是 `SubscriberMethodFinder` 向外暴露了一个方法来获取 `FindState` 的实例，该方法就是 `prepareFindState()`：

``` java
private FindState prepareFindState() {
    // 加锁，监视器为 FindState 池
    synchronized (FIND_STATE_POOL) {
        // 对 FindState 池进行遍历
        for (int i = 0; i < POOL_SIZE; i++) {
            FindState state = FIND_STATE_POOL[i];
            // 对池中指定索引位置获取的值进行判空，如果不为 null 将此对象返回，并且将该索引位置置空
            if (state != null) {
                FIND_STATE_POOL[i] = null;
                return state;
            }
        }
    }
    // 如果缓存池中没有缓存的对象，就去 new 一个
    return new FindState();
}
```

该方法会返回一个 `FindState` 实例，方法中首先对对象池进行遍历取值，如果取到了非空对象  `FindState`，就将其返回，如果池中没有缓存的对象，这时会去 `new` 一个新的对象并返回给调用者。

该池的运作机制就是通过 `SubscriberMethodFinder.prepareFindState()` 获取实例（该实例先从缓存中取，没有缓存就去创建新实例），在查找订阅方法执行结束后会调用 `FindState.recycle()` 方法释放实例中的资源，然后将实例放入缓存池中。

### 3.3 查找订阅方法（findSubscriberMethods）

在注册方法中调用了 `findSubscriberMethods(Class<?> subscriberClass)` 方法来获取订阅者中的所有订阅方法，下面我们就深入的看下整个的查找过程。

```java
/**
 * 查找订阅者方法
 *
 * @param subscriberClass Class<?> 订阅者 Class 对象
 * @return List<SubscriberMethod> 订阅者方法List
 */
List<SubscriberMethod> findSubscriberMethods(Class<?> subscriberClass) {
    // 首先尝试从缓存中获取订阅方法 List
    List<SubscriberMethod> subscriberMethods = METHOD_CACHE.get(subscriberClass);
    // 判断从缓存中获取订阅方法 List 是否为null
    if (subscriberMethods != null) {
        // 如果不为 null，就返回缓存中的订阅方法 List
        return subscriberMethods;
    }

    // 是否忽略生成的索引
    if (ignoreGeneratedIndex) {
        // 忽略索引的情况下，通过反射进行查找订阅者方法
        subscriberMethods = findUsingReflection(subscriberClass);
    } else {
        // 通过索引的方式进行查找
        subscriberMethods = findUsingInfo(subscriberClass);
    }
    // 如果没有订阅者方法，就抛出 EventBusException 异常
    if (subscriberMethods.isEmpty()) {
        throw new EventBusException("Subscriber " + subscriberClass
                + " and its super classes have no public methods with the @Subscribe annotation");
    } else {
        // 将此订阅者和其订阅者方法添加进缓存中
        METHOD_CACHE.put(subscriberClass, subscriberMethods);
        // 返回查找的订阅者方法
        return subscriberMethods;
    }
}
```

进入方法体后，首先是先从缓存中尝试获取该订阅者的订阅方法，该属性的代码如下：

```java
/**
 * 订阅者方法缓存 ConcurrentHashMap，为了避免重复查找订阅者的订阅方法，维护了此缓存
 * key:   Class<?>                 订阅者 Class 对象
 * value: List<SubscriberMethod>>  订阅者方法 List
 */
private static final Map<Class<?>, List<SubscriberMethod>> METHOD_CACHE = new ConcurrentHashMap<>();
```
拿到结果后进行了判断，如果有缓存就会返回此缓存，如果没有缓存，就会进行查找。后续代码对 `ignoreGeneratedIndex` 进行了判断，该属性可以通过 `EventBusBuilder.ignoreGeneratedIndex()` 进行配置，根据属性的是否忽略索引结果，来选择使用哪种方法进行具体的查找。当忽略索引类时，通过反射 `findUsingReflection(Class<?> subscriberClass)` 进行查找，如果不忽略索引，使用 `findUsingInfo(Class<?> subscriberClass)` 方法进行查找。查找完成后，将结果存入 `METHOD_CACHE` 缓存中以便下次再对此订阅者进行查找时直接从缓存读取数据，随后返回查找结果。

### 3.4 反射查找（findUsingReflection）

反射查找核心在 `findUsingReflection(Class<?> subscriberClass)` 和 `findUsingReflectionInSingleClass(FindState findState)` 这两个方法，源码如下:

```java
/**
 * 通过反射查找订阅者方法
 *
 * @param subscriberClass Class<?> 订阅者 Class 对象
 * @return List<SubscriberMethod> 订阅者方法 List
 */
private List<SubscriberMethod> findUsingReflection(Class<?> subscriberClass) {
    // 获取一个 FindState 对象
    FindState findState = prepareFindState();
    // 为当前订阅者初始化 FindState
    findState.initForSubscriber(subscriberClass);
    // 循环 从子类到父类查找订阅方法
    while (findState.clazz != null) {
        // 从当前 findState.clazz 指向的类中使用反射查找订阅方法
        findUsingReflectionInSingleClass(findState);
        // 查找阶段结束，移动到当前类的父类
        findState.moveToSuperclass();
    }
    // 返回查找到的订阅者方法并释放资源
    return getMethodsAndRelease(findState);
}

/**
 * 通过反射查找 {@link FindState#clazz} 对应类的订阅者方法
 *
 * @param findState FindState
 */
private void findUsingReflectionInSingleClass(FindState findState) {
    // 声明一个 方法数组
    Method[] methods;
    try {
        // 通过反射获取当前 Class 的方法，某些 Android 版本在调用 getDeclaredMethods 或 getMethods 时似乎存在反射错误
        // getMethods(): 该方法是获取本类以及父类或者父接口中所有的公共方法(public修饰符修饰的)。
        // getDeclaredMethods(): 该方法是获取本类中的所有方法，包括私有的(private、protected、默认以及public)的方法。
        // getDeclaredMethods() 这比 getMethods() 快，尤其是当订阅者是像活动这样的巨大的类时
        methods = findState.clazz.getDeclaredMethods();
    } catch (Throwable th) {
        // Workaround for java.lang.NoClassDefFoundError, see https://github.com/greenrobot/EventBus/issues/149
        try {
            // 当 getDeclaredMethods() 发生异常时，尝试使用 getMethods()
            methods = findState.clazz.getMethods();
        } catch (LinkageError error) { // super class of NoClassDefFoundError to be a bit more broad...
            // 当 getMethods() 也产生了异常时，会抛出 EventBusException 异常
            String msg = "Could not inspect methods of " + findState.clazz.getName();
            if (ignoreGeneratedIndex) {
                msg += ". Please consider using EventBus annotation processor to avoid reflection.";
            } else {
                msg += ". Please make this class visible to EventBus annotation processor to avoid reflection.";
            }
            throw new EventBusException(msg, error);
        }
        // 跳过父类的方法查找，因为在此 catch 中已经获取了所有父类方法，所以要跳过父类
        findState.skipSuperClasses = true;
    }

    // 代码执行到此处表示反射获取方法没有产生异常
    // 对获取到的类中的所有方法进行遍历操作
    for (Method method : methods) {
        // 获取方法的修饰符
        int modifiers = method.getModifiers();
        // 订阅者方法有一定的限制，必须为 public，不能是static volatile abstract strict
        if ((modifiers & Modifier.PUBLIC) != 0 && (modifiers & MODIFIERS_IGNORE) == 0) {
            // 获取该方法的参数列表
            Class<?>[] parameterTypes = method.getParameterTypes();

            // 对参数个数进行判断
            if (parameterTypes.length == 1) {
                // 参数个事为1的情况下，符合订阅者方法对于参数个数的限制
                // 获取方法的注解，判断是否存在 Subscribe
                Subscribe subscribeAnnotation = method.getAnnotation(Subscribe.class);
                if (subscribeAnnotation != null) {
                    // 如果方法存在 Subscribe 注解
                    // 获取方法参数的 Class 对象
                    Class<?> eventType = parameterTypes[0];
                    // 对订阅者方法进行二级校验
                    if (findState.checkAdd(method, eventType)) {
                        // 获取订阅者方法的线程模型
                        ThreadMode threadMode = subscribeAnnotation.threadMode();
                        // 将此订阅者方法 添加进 subscriberMethods
                        findState.subscriberMethods.add(new SubscriberMethod(method, eventType, threadMode,
                                subscribeAnnotation.priority(), subscribeAnnotation.sticky()));
                    }
                }
            } else
                // 继续严格的方法验证判断
                if (strictMethodVerification && method.isAnnotationPresent(Subscribe.class)) {
                    String methodName = method.getDeclaringClass().getName() + "." + method.getName();
                    throw new EventBusException("@Subscribe method " + methodName +
                            "must have exactly 1 parameter but has " + parameterTypes.length);
                }

        } else
            // 方法不是 public，进一步判断
            // 判断是否进行严格的方法验证，如果是，再判断当前方法是否有 Subscribe 注解
            // 两个条件都成立的情况下，抛出 EventBusException 异常，该异常来自于严格的方法验证
            if (strictMethodVerification && method.isAnnotationPresent(Subscribe.class)) {
                String methodName = method.getDeclaringClass().getName() + "." + method.getName();
                throw new EventBusException(methodName +
                        " is a illegal @Subscribe method: must be public, non-static, and non-abstract");
            }
    }
}
```

首先是 `findUsingReflection(Class<?> subscriberClass)` 方法，进入方法体后获取一个 FindState 对象然后进行初始化，通过 `while` 循环进行从子类到父类的查找，循环条件为 `findState.clazz != null`，这个 `findState.clazz` 属性总是会指向当前需要查找的 class 对象，一开始初始值就是当前订阅者的 class 对象，当查询完该类后，会调用 `findState.moveToSuperclass()` 将父类的 class 对象赋值给 `findState.clazz`，然后继续循环。进入循环体后就调用了第二个核心方法 `findUsingReflectionInSingleClass(FindState findState)`。

进入方法体后先是声明了一个 `Method[]` 局部变量，紧接着就是一个大大的 `try/catch`,通过调用 class 的 `getDeclaredMethods()` 方法继续反射操作获取当前 Class 的方法，因为某些 Android 版本在调用 getDeclaredMethods 或 getMethods 时似乎存在反射错误，这点在官网中也有提到，所以有了 `try/catch`，在 `catch` 块中，针对调用 `getDeclaredMethods()` 产生的异常做了进一步的处理，就是调用另一个反射方法来获取方法 `getMethods()`。此处要讲一下两个方法的区别：

- **getMethods():** 该方法是获取本类以及父类或者父接口中所有的公共方法( `public` 修饰符修饰的)

- **getDeclaredMethods():** 该方法是获取本类中的所有方法，包括私有的( `private`、`protected`、默认以及 `public` )的方法。这比 `getMethods()` 快，尤其是当订阅者是像 `Activity` 这样的巨大的类时

所以当使用 `getMethods()` 进行查找时已经获得了父类的公共方法，因此需要跳过父类的查找 `findState.skipSuperClasses = true`。

下一步就是对通过反射获取到的所有方法进行遍历操作，对每一个方法进行筛选，其中包括方法修饰符、参数个数、是否有 `Subscribe` 注解，在这些条件都成立的情况下，调用 `findState.checkAdd()` 进行二级校验，具体的校验规则在后文中解释，在校验完成后拿到订阅方法的信息创建 `SubscriberMethod` 实例添加进 `findState.subscriberMethods` 中。

此时，查找结束，返回到 `findUsingReflection()` 方法，在最后调用了 `getMethodsAndRelease(FindState findState)` 方法：

```java
/**
 * 从 FindState 中获取订阅者方法并正式发布
 * 该方法其实只是将形参 findState 中的 subscriberMethods 以新的 List 返回出来
 * 并且还对 findState 做了资源释放及回收的处理
 *
 * @param findState FindState
 * @return List<SubscriberMethod> 订阅者方法
 */
private List<SubscriberMethod> getMethodsAndRelease(FindState findState) {
    // 创建订阅者方法 List，传入 findState.subscriberMethods
    List<SubscriberMethod> subscriberMethods = new ArrayList<>(findState.subscriberMethods);
    // 释放 findState 资源，为下一次复用该类做准备
    findState.recycle();
    // 加锁，监视器为 FindState 对象池，将该 FindState 放入池中
    synchronized (FIND_STATE_POOL) {
        for (int i = 0; i < POOL_SIZE; i++) {
            if (FIND_STATE_POOL[i] == null) {
                FIND_STATE_POOL[i] = findState;
                break;
            }
        }
    }
    // 返回订阅者方法
    return subscriberMethods;
}
```

该方法将查找到的合规的订阅者方法返回，释放本次查找使用的 `FindState` 资源，将实例放入缓存池中。到此整个查找过程结束，方法回到 `register()`。

### 3.5 索引查找（findUsingInfo）

我们继续看使用索引查询的方法 `findUsingInfo(Class<?> subscriberClass)`:

```java
/**
 * 查找订阅者方法
 *
 * @param subscriberClass Class<?> 订阅者 Class 对象
 * @return List<SubscriberMethod> 订阅者方法 List
 */
private List<SubscriberMethod> findUsingInfo(Class<?> subscriberClass) {
    // 获取一个 FindState 对象
    FindState findState = prepareFindState();
    // 为当前订阅者初始化 FindState
    findState.initForSubscriber(subscriberClass);
    // 循环 从子类到父类查找订阅方法
    while (findState.clazz != null) {
        // 获取索引类并赋值给 findState.subscriberInfo
        findState.subscriberInfo = getSubscriberInfo(findState);
        // 当索引类的信息类不为 null 时，进一步操作
        if (findState.subscriberInfo != null) {
            // 获取索引类的所有订阅者方法
            SubscriberMethod[] array = findState.subscriberInfo.getSubscriberMethods();
            // 对订阅者方法数组遍历
            for (SubscriberMethod subscriberMethod : array) {
                // 检查并将方法添加
                if (findState.checkAdd(subscriberMethod.method, subscriberMethod.eventType)) {
                    // 校验通过，将该订阅方法添加至 subscriberMethods，
                    findState.subscriberMethods.add(subscriberMethod);
                }
            }
        } else {
            // 如果没有索引类，就使用反射的方式继续查找
            findUsingReflectionInSingleClass(findState);
        }
        // 将 findState 的 Class 对象移至父类型
        findState.moveToSuperclass();
    }
    // 返回查找到的订阅者方法并释放资源
    return getMethodsAndRelease(findState);
}
```
该方法前部分和 `findUsingReflection(Class<?> subscriberClass)` 一致，不一样的地方就是循环体内的逻辑。

循环体中，首先调用了 `getSubscriberInfo(FindState findState)` 方法来获取索引类，具体细节就不展开讲，代码注释如下：

```java
/**
 * 获取索引类
 *
 * @param findState FindState 查找状态类
 * @return SubscriberInfo 索引类
 */
private SubscriberInfo getSubscriberInfo(FindState findState) {
    // findState.subscriberInfo 初始状态是为 null 的，唯一赋值途径也是本方法的返回值
    // 当 findState.subscriberInfo 不为 null 时，就表示已经进行了一轮查找了，一轮查找后，理论上已经转向查找其父类了
    // 所以此 if 的操作，其实就是转向父类型
    // 如果在 FindState 中存在索引类，并且索引类的父类不为null，进行下一步操作
    if (findState.subscriberInfo != null && findState.subscriberInfo.getSuperSubscriberInfo() != null) {
        // 获取当前索引类的父类
        SubscriberInfo superclassInfo = findState.subscriberInfo.getSuperSubscriberInfo();
        // 如果 findState 对应的 clazz 是当前获取到的父类，就返回此父类的 SubscriberInfo
        if (findState.clazz == superclassInfo.getSubscriberClass()) {
            return superclassInfo;
        }
    }

    // 判断订阅者索引类集合是否为 null，不为 null 时进行遍历获取
    if (subscriberInfoIndexes != null) {
        for (SubscriberInfoIndex index : subscriberInfoIndexes) {
            // 获取当前 findState.clazz 的订阅者信息类
            SubscriberInfo info = index.getSubscriberInfo(findState.clazz);
            if (info != null) {
                // 不为 null 时表示已找到该 findState.clazz 对应的索引类的订阅者信息类
                return info;
            }
        }
    }

    // 找不到索引类，返回null
    return null;
}
```

拿到索引类的信息后进行空判断，不为空时，通过索引类 `SubscriberInfo.getSubscriberMethods()` 拿到所有的订阅者方法，对该方法数组进行校验，校验在后文中解释，校验通过还是老样子将方法放入 `findState.subscriberMethods`。当没有拿到索引类时，继续使用 `findUsingReflectionInSingleClass(FindState findState)` 方法反射式查找，后面逻辑大同小异。

此时我们可以总结出来，对于订阅方法的查找，优先使用索引查找，当找不到索引类时，继续使用反射的方式查找。

### 3.6 订阅方法二级校验（checkAdd）

在对方法查找时，需要对初步合适的方法进行二级校验，通过 `FindState.checkAdd(Method method, Class<?> eventType)` 来进行校验：

```java
/**
 * 检查并将方法添加
 *
 * @param method    Method 订阅方法
 * @param eventType Class<?> 事件 Class 对象，也就是该方法的形参
 * @return 校验结果
 */
boolean checkAdd(Method method, Class<?> eventType) {
    // 2 级检查：第1级仅具有事件类型（快速），第2级在需要具有完整签名
    // 通常订阅者没有监听相同事件类型的方法
    // 将方法先放进 anyMethodByEventType 中
    Object existing = anyMethodByEventType.put(eventType, method);
    // 对 put 返回结果进行判断
    if (existing == null) {
        // null 的情况，表示该 map 中没有相同方法，校验成功
        return true;
    } else {
        // 判断是否是 Method 类型
        if (existing instanceof Method) {
            // 对相同事件接收方法进行方法签名校验
            if (!checkAddWithMethodSignature((Method) existing, eventType)) {
                // 抛出异常
                throw new IllegalStateException();
            }
            // 放置任何非 Method 对象以“使用”现有 Method
            anyMethodByEventType.put(eventType, this);
        }
        return checkAddWithMethodSignature(method, eventType);
    }
}

/**
 * 检查并将方法添加，对方法签名校验
 *
 * @param method    Method 订阅方法
 * @param eventType Class<?> 事件 Class 对象，也就是该方法的形参
 * @return 校验结果
 */
private boolean checkAddWithMethodSignature(Method method, Class<?> eventType) {
    methodKeyBuilder.setLength(0);
    methodKeyBuilder.append(method.getName());
    methodKeyBuilder.append('>').append(eventType.getName());
    // 方法键：方法名>Class name ，例如 onEventTest1>java.lang.String
    String methodKey = methodKeyBuilder.toString();
    // 获取该方法的所在类的 Class 对象
    Class<?> methodClass = method.getDeclaringClass();
    // 将该方法签名作为 key，方法 Class 对象作为 value put 进 subscriberClassByMethodKey
    Class<?> methodClassOld = subscriberClassByMethodKey.put(methodKey, methodClass);

    // 场景：查找方法并添加是从子类向父类的顺序进行的
    // methodClassOld 不为 null 的情况可以是这样的：
    // 方法签名相同，但是 Class 对象不同，新的 Class 对象正常情况下是其父类
    // 不会存在方法签名相同而 Class 对象又是同一个的情况，这属于一个类中有一个以上相同签名的方法，这是不允许的

    // isAssignableFromL: 判断当前的 Class 对象所表示的类，是不是参数中传递的 Class 对象所表示类的父类、超接口、或者是相同的类型。
    // 如果没有旧值返回，或者 methodClassOld 是 methodClass 自己或父类型时，返回 true
    if (methodClassOld == null || methodClassOld.isAssignableFrom(methodClass)) {
        // 仅在未在子类中找到时添加
        return true;
    } else {
        // Revert the put, old class is further down the class hierarchy
        subscriberClassByMethodKey.put(methodKey, methodClassOld);
        return false;
    }
}
```

第一级校验是将方法放入 `anyMethodByEventType` 属性中，如果不存在同事件类型的方法，表示校验通过，如果存在相同事件类型的方法就会进行第二级的校验，通过 `checkAddWithMethodSignature(Method method, Class<?> eventType)` 方法进行。该方法是对方法签名进行校验，如果出现了当前 Class 对象有两个相同签名方法时（并未继承过来的方法），校验失败。当然这种情况在正常的情况下是不会发生的。

校验结束后就会决定着方法是否被加入到订阅方法集合中。

### 3.7 拿到查找结果添加到总集合

在经过查找、校验等操作后，在 `EventBus.register(Object subscriber)` 方法最后，对方法集合进行遍历调用 `subscribe(Object subscriber, SubscriberMethod subscriberMethod)` 方法对其产生订阅关系。

```java
/**
 * 产生订阅关系，实际上该方法主要就是将订阅方法放进那个大集合中，之前做的事情是将这些方法找出来
 * 而此方法是正式将方法放入那些正式的大集合中
 * 必须在同步块中调用
 *
 * @param subscriber       Object 订阅者对象
 * @param subscriberMethod SubscriberMethod 订阅者方法
 */
private void subscribe(Object subscriber, SubscriberMethod subscriberMethod) {
    // 获取订阅者方法接收的事件类型 Class 对象
    Class<?> eventType = subscriberMethod.eventType;
    // 创建 Subscription
    Subscription newSubscription = new Subscription(subscriber, subscriberMethod);
    // 从 subscriptionsByEventType 中 尝试获取当前订阅方法接收的事件类型的值
    CopyOnWriteArrayList<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
    if (subscriptions == null) {
        // 如果为 null，表示该方法是第一个，创建空的CopyOnWriteArrayList put 进 subscriptionsByEventType
        subscriptions = new CopyOnWriteArrayList<>();
        subscriptionsByEventType.put(eventType, subscriptions);
    } else {
        // 如果不为 null，判断现有数据中是否存在该方法，如果存在抛出 EventBusException 异常
        if (subscriptions.contains(newSubscription)) {
            throw new EventBusException("Subscriber " + subscriber.getClass() + " already registered to event "
                    + eventType);
        }
    }

    // 遍历当前事件类型的所有接收方法
    int size = subscriptions.size();
    for (int i = 0; i <= size; i++) {
        // 这一步主要是将订阅者方法添加进 subscriptionsByEventType 数据中，并且会按照优先级进行插入
        if (i == size || subscriberMethod.priority > subscriptions.get(i).subscriberMethod.priority) {
            subscriptions.add(i, newSubscription);
            break;
        }
    }
    // 获取当前订阅者接收的事件类型的数据
    List<Class<?>> subscribedEvents = typesBySubscriber.get(subscriber);
    if (subscribedEvents == null) {
        subscribedEvents = new ArrayList<>();
        typesBySubscriber.put(subscriber, subscribedEvents);
    }
    // 将当前的事件类型添加进订阅者的接收范围内
    subscribedEvents.add(eventType);

    // 对黏性事件进行处理
    if (subscriberMethod.sticky) {
        // 是否事件继承
        if (eventInheritance) {
            // 必须考虑所有 eventType 子类的现有粘性事件。
            // Note: 迭代所有事件可能会因大量粘性事件而效率低下，因此应更改数据结构以允许更有效的查找
            // (e.g. 存储超类的子类的附加映射: Class -> List<Class>).
            Set<Map.Entry<Class<?>, Object>> entries = stickyEvents.entrySet();
            for (Map.Entry<Class<?>, Object> entry : entries) {
                Class<?> candidateEventType = entry.getKey();
                // 判断 eventType 是否是 candidateEventType 的父类或父接口
                if (eventType.isAssignableFrom(candidateEventType)) {
                    Object stickyEvent = entry.getValue();
                    // 如果是父子关系  进行事件检查和发布
                    checkPostStickyEventToSubscription(newSubscription, stickyEvent);
                }
            }
        } else {
            // 从黏性事件 Map 中获取当前事件类型的最新事件
            Object stickyEvent = stickyEvents.get(eventType);
            // 校验事件并发布事件
            checkPostStickyEventToSubscription(newSubscription, stickyEvent);
        }
    }
}
```

`subscribe(Object subscriber, SubscriberMethod subscriberMethod)` 方法中将 **订阅者** 和单个的 **订阅方法** 包装为一个 `Subscription` 对象。先从 `subscriptionsByEventType` 这个按照事件类型区分的集合中获取当前订阅方法接收的事件类型的值，如果为空，表示该方法是第一个，然后创建一个 `CopyOnWriteArrayList` 将该订阅方法的包装类存入集合中再存入到 `subscriptionsByEventType` 中；如果不为空，将进行优先级的排列。

按照订阅方法的优先级按顺序的插入到原有数据集合中，下一步再将该订阅方法的接收类型放入到该订阅者接收的事件类型集合中 `typesBySubscriber`,再往下的代码就是对黏性事件的处理，这部分解析我们放到后面。

至此整个注册过程已经结束了。

## 4.发布普通事件

普通事件的发布过程如下：

![EventBus 事件发布.png](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/d917401ecdf545e79f6b122cde681e53~tplv-k3u1fbpfcp-watermark.image?)

### 4.1 事件入队

普通事件发布使用 `post(Object event)` 方法，源码如下：

```java
public void post(Object event) {
    // 从当前线程中取得线程专属变量 PostingThreadState 实例
    PostingThreadState postingState = currentPostingThreadState.get();
    // 拿到事件队列
    List<Object> eventQueue = postingState.eventQueue;
    // 将事件入队
    eventQueue.add(event);
    // 判断当前线程是否在发布事件中
    if (!postingState.isPosting) {
        // 设置当前线程是否是主线程
        postingState.isMainThread = isMainThread();
        // 将当前线程标记为正在发布
        postingState.isPosting = true;
        // 如果 canceled 为 true，则是内部错误，中止状态未重置
        if (postingState.canceled) {
            throw new EventBusException("Internal error. Abort state was not reset");
        }
        try {
            // 队列不为空时，循环发布单个事件
            while (!eventQueue.isEmpty()) {
                // 从事件队列中取出一个事件进行发布
                postSingleEvent(eventQueue.remove(0), postingState);
            }
        } finally {
            // 发布完成后 重置状态
            postingState.isPosting = false;
            postingState.isMainThread = false;
        }
    }
}
```

方法一开始通过 `currentPostingThreadState` 拿到一个 `PostingThreadState` 实例，`currentPostingThreadState` 是一个 `ThreadLocal<PostingThreadState>` 类型的变量，其中 `PostingThreadState` 主要作用是记录当前线程的事件发布状态、待发布事件等。是 `EventBus` 类的静态内部类，其源码如下：

```java
final static class PostingThreadState {
    // 事件队列
    final List<Object> eventQueue = new ArrayList<>();
    // 是否在发布
    boolean isPosting;
    // 是否是主线程
    boolean isMainThread;
    // 订阅者方法包装类
    Subscription subscription;
    // 正在发送的事件
    Object event;
    // 是否已经取消
    boolean canceled;
}
```

拿到线程独有变量 `PostingThreadState` 实例后，将需要发布的事件入队，然后判断了当前线程是否在发布事件中，如果不在活跃状态，就进行事件的发布流程，如果在活跃状态，就跳出方法，结束发布过程，当然一个线程正常的情况下有事件进行发布的时候，一定是非发布活跃状态，毕竟一个线程不能有两条路并行。

代码进入 `if` 后进行了主线程判断、标记当前线程为发布活跃状态，判断是否取消（如果是，表示内部状态异常），随后代码进入一个循环，循环条件就是 `PostingThreadState.eventQueue` 不为空，循环体内从 `eventQueue` 取出要进行发布的事件通过 `postSingleEvent(Object event, PostingThreadState postingState)` 方法进行发布操作，在循环执行结束后，会在 `finally` 块中进行重置当前线程的状态，然后结束方法。

`postSingleEvent(Object event, PostingThreadState postingState)`方法源码如下：

``` java
/**
 * 发布单个事件
 *
 * @param event        Object 需要发布的事件
 * @param postingState PostingThreadState 当前线程的发布状态
 * @throws Error
 */
private void postSingleEvent(Object event, PostingThreadState postingState) throws Error {
    // 获取事件的Class对象
    Class<?> eventClass = event.getClass();
    // 订阅者是否找到
    boolean subscriptionFound = false;
    // 判断是否处理事件继承
    if (eventInheritance) {
        // 查找所有事件类型
        List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
        // 循环遍历 所有类型
        int countTypes = eventTypes.size();
        for (int h = 0; h < countTypes; h++) {
            Class<?> clazz = eventTypes.get(h);
            // 按类型发布事件
            subscriptionFound |= postSingleEventForEventType(event, postingState, clazz);
        }
    } else {
        // 按类型发布事件
        subscriptionFound = postSingleEventForEventType(event, postingState, eventClass);
    }
    // 如果没有找到对应的订阅关系
    if (!subscriptionFound) {
        // 判断事件无匹配订阅函数时，是否打印信息
        if (logNoSubscriberMessages) {
            logger.log(Level.FINE, "No subscribers registered for event " + eventClass);
        }
        // 判断事件无匹配订阅函数时，是否发布 NoSubscriberEvent
        if (sendNoSubscriberEvent
                && eventClass != NoSubscriberEvent.class
                && eventClass != SubscriberExceptionEvent.class) {
            // 发布 NoSubscriberEvent
            post(new NoSubscriberEvent(this, event));
        }
    }
}
```

这个方法主要做了两件事，第一件事是处理事件的继承关系，找出事件类的所有父类型，第二件事就是按照事件类型进一步执行发布事件的操作。方法第三行对是否处理事件继承关系进行判断，该状态由 `eventInheritance` 提供，此状态可以通过 `EventBusBuilder.eventInheritance(boolean eventInheritance)` 方法进行配置。当不需要处理事件继承关系时，直接以事件的真实类型发布事件；如果需要处理事件的继承关系，就会走到查找事件所有父级类型方法 `lookupAllEventTypes(Class<?> eventClass)`，拿到所有的父级类型进行遍历发布。

### 4.2 查找事件的所有父类型

查找事件的所有父类型是通过 `List<Class<?>> lookupAllEventTypes(Class<?> eventClass)` 方法完成的，源码如下：

```java
/**
 * 查找所有事件类型
 * 查找给定 Class 对象的所有 Class 对象，包括超类和接口，也应该适用于接口
 * 该方法用于事件继承处理
 */
private static List<Class<?>> lookupAllEventTypes(Class<?> eventClass) {
    // 加锁 监视器为 eventTypesCache
    synchronized (eventTypesCache) {
        // 尝试从事件类型缓存中获取该事件 Class 类型的缓存
        List<Class<?>> eventTypes = eventTypesCache.get(eventClass);
        // 如果为 null 表示没有缓存
        if (eventTypes == null) {
            eventTypes = new ArrayList<>();
            Class<?> clazz = eventClass;
            // 循环查找父级 Class 对象
            while (clazz != null) {
                // 如果当前 clazz 不为 null，将此 Class 对象添加进 eventTypes
                eventTypes.add(clazz);
                // 对当前 Class 实现的接口进行递归添加进 eventTypes
                // 因为类和接口属于两条线，所以在处理每个类的时候都要在此递归处理接口类型
                addInterfaces(eventTypes, clazz.getInterfaces());
                // 将下一个需要处理的 Class 对象移至当前 Class 对象的父类
                clazz = clazz.getSuperclass();
            }
            // 查找结束，将结果 put 进缓存中，以便下次复用结果
            eventTypesCache.put(eventClass, eventTypes);
        }
        // 返回事件所有 Class 对象
        return eventTypes;
    }
}
```




### 4.3 按类型发布事件

### 4.4 处理线程模式（事件发布器）

## 5.发布黏性事件

## 6.事件发布器

# 四、结尾

