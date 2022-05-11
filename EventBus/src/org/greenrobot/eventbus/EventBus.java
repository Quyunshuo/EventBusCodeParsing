/*
 * Copyright (C) 2012-2020 Markus Junginger, greenrobot (http://greenrobot.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.greenrobot.eventbus;

import org.greenrobot.eventbus.android.AndroidDependenciesDetector;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

/**
 * EventBus 是一个用于 Java 和 Android 的中央发布订阅事件系统。
 * 事件被发布（{@link #post(Object)}）到总线，总线将其传递给具有事件类型匹配处理程序方法的订阅者。
 * 要接收事件，订阅者必须使用 {@link #register(Object)} 将自己注册到总线。
 * 注册后，订阅者会收到事件，直到调用 {@link #unregister(Object)}。
 * 事件处理方法必须由 {@link Subscribe} 注释，必须是公共的，不返回任何内容（void），并且只有一个参数（事件）。
 */
public class EventBus {

    public static String TAG = "EventBus";

    /**
     * 默认的 EventBus 实例，使用 volatile 修饰，保证其在并发环境下的可见行
     */
    static volatile EventBus defaultInstance;
    /**
     * EventBus 构建者实例
     */
    private static final EventBusBuilder DEFAULT_BUILDER = new EventBusBuilder();
    /**
     * 事件所有类型缓存
     * key: 事件 Class 对象
     * value: 事件 Class 对象的所有父级 Class 对象，包括超类和接口
     */
    private static final Map<Class<?>, List<Class<?>>> eventTypesCache = new HashMap<>();
    /**
     * 按照事件类型分类的订阅方法 HashMap
     * key:Class<?> 事件类的 Class 对象， value:CopyOnWriteArrayList<Subscription> 订阅者方法信息集合
     */
    private final Map<Class<?>, CopyOnWriteArrayList<Subscription>> subscriptionsByEventType;
    /**
     * 订阅者所接收的事件类型
     * key:Object 订阅者， value:List<Class<?>> 所接收的事件类型的 Class 对象 List
     * todo: 为什么不使用 Set？而是使用List?
     */
    private final Map<Object, List<Class<?>>> typesBySubscriber;
    /**
     * 黏性事件 ConcurrentHashMap
     * key:Class<?> 事件类的 Class 对象，value: 当前最新的黏性事件
     */
    private final Map<Class<?>, Object> stickyEvents;
    /**
     * ThreadLocal 线程间数据隔离，当前发布线程状态
     */
    private final ThreadLocal<PostingThreadState> currentPostingThreadState = new ThreadLocal<PostingThreadState>() {
        @Override
        protected PostingThreadState initialValue() {
            return new PostingThreadState();
        }
    };

    // @Nullable
    // 主线程支持
    private final MainThreadSupport mainThreadSupport;
    // @Nullable
    // 主线程事件发布器
    private final Poster mainThreadPoster;
    // 后台线程事件发布器
    private final BackgroundPoster backgroundPoster;
    // 异步事件发布器
    private final AsyncPoster asyncPoster;
    // 订阅者方法查找器
    private final SubscriberMethodFinder subscriberMethodFinder;
    // 用于异步和后台处理的线程池 默认为 Executors.newCachedThreadPool()
    private final ExecutorService executorService;

    // 如果订阅者方法执行有异常时，抛出 SubscriberException
    private final boolean throwSubscriberException;
    // 订阅函数执行有异常时，打印异常信息
    private final boolean logSubscriberExceptions;
    // 事件无匹配订阅函数时，打印信息
    private final boolean logNoSubscriberMessages;
    // 订阅函数执行有异常时，发布 SubscriberExceptionEvent 事件
    private final boolean sendSubscriberExceptionEvent;
    // 事件无匹配订阅函数时，发布NoSubscriberEvent
    private final boolean sendNoSubscriberEvent;
    // 事件继承
    private final boolean eventInheritance;
    // 索引类数量
    private final int indexCount;
    // 日志处理程序
    private final Logger logger;

    /**
     * 使用进程范围的 EventBus 实例为应用程序提供便利的单例。
     * 双重校验锁式单例
     */
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

    public static EventBusBuilder builder() {
        return new EventBusBuilder();
    }

    /**
     * For unit test primarily.
     */
    public static void clearCaches() {
        SubscriberMethodFinder.clearCaches();
        eventTypesCache.clear();
    }

    /**
     * 创建一个新的 EventBus 实例；每个实例都是一个单独的范围，在其中传递事件。
     * 要使用中央总线，请考虑 {@link #getDefault()}。
     */
    public EventBus() {
        this(DEFAULT_BUILDER);
    }

    EventBus(EventBusBuilder builder) {
        logger = builder.getLogger();
        subscriptionsByEventType = new HashMap<>();
        typesBySubscriber = new HashMap<>();
        stickyEvents = new ConcurrentHashMap<>();
        mainThreadSupport = builder.getMainThreadSupport();
        mainThreadPoster = mainThreadSupport != null ? mainThreadSupport.createPoster(this) : null;
        backgroundPoster = new BackgroundPoster(this);
        asyncPoster = new AsyncPoster(this);
        indexCount = builder.subscriberInfoIndexes != null ? builder.subscriberInfoIndexes.size() : 0;
        subscriberMethodFinder = new SubscriberMethodFinder(builder.subscriberInfoIndexes,
                builder.strictMethodVerification, builder.ignoreGeneratedIndex);
        logSubscriberExceptions = builder.logSubscriberExceptions;
        logNoSubscriberMessages = builder.logNoSubscriberMessages;
        sendSubscriberExceptionEvent = builder.sendSubscriberExceptionEvent;
        sendNoSubscriberEvent = builder.sendNoSubscriberEvent;
        throwSubscriberException = builder.throwSubscriberException;
        eventInheritance = builder.eventInheritance;
        executorService = builder.executorService;
    }

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
                    "make sure to add the \"eventbus\" Android library to your dependencies.");
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

    /**
     * 检查黏性事件并发布到订阅者
     *
     * @param newSubscription Subscription 订阅关系
     * @param stickyEvent     Object 黏性事件
     */
    private void checkPostStickyEventToSubscription(Subscription newSubscription, Object stickyEvent) {
        if (stickyEvent != null) {
            // 如果订阅者试图中止事件，它将失败（在发布状态下不跟踪事件）
            // --> Strange corner case, which we don't take care of here.
            // 将事件发布到订阅者
            postToSubscription(newSubscription, stickyEvent, isMainThread());
        }
    }

    /**
     * 检查当前线程是否在主线程中运行。如果没有主线程支持（例如非 Android），则始终返回“true”。
     * 在这种情况下，主线程订阅者总是在发布线程中被调用，而后台订阅者总是从后台发布者中调用。
     */
    private boolean isMainThread() {
        return mainThreadSupport == null || mainThreadSupport.isMainThread();
    }

    /**
     * 判断给定订阅者是否已经进行注册
     *
     * @param subscriber Object 订阅者
     * @return 是否已经进行注册
     */
    public synchronized boolean isRegistered(Object subscriber) {
        return typesBySubscriber.containsKey(subscriber);
    }

    /**
     * 按事件类型退订
     * 只更新subscriptionsByEventType，不更新 typeBySubscriber！调用者必须更新 typesBySubscriber。
     *
     * @param subscriber Object 订阅者
     * @param eventType  Class<?> 事件类型 Class 对象
     */
    private void unsubscribeByEventType(Object subscriber, Class<?> eventType) {
        // 获取需要退订的事件类型的订阅者方法
        List<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
        if (subscriptions != null) {
            // 循环遍历移除当前订阅者的订阅者方法
            int size = subscriptions.size();
            for (int i = 0; i < size; i++) {
                Subscription subscription = subscriptions.get(i);
                if (subscription.subscriber == subscriber) {
                    subscription.active = false;
                    subscriptions.remove(i);
                    i--;
                    size--;
                }
            }
        }
    }

    /**
     * 从所有事件类中注销给定的订阅者
     */
    public synchronized void unregister(Object subscriber) {
        // 获取订阅者接收的事件类型
        List<Class<?>> subscribedTypes = typesBySubscriber.get(subscriber);
        if (subscribedTypes != null) {
            // 按事件类型遍历退订相关订阅方法
            for (Class<?> eventType : subscribedTypes) {
                unsubscribeByEventType(subscriber, eventType);
            }
            // 从订阅者所接收的事件类型Map中移除该订阅者
            typesBySubscriber.remove(subscriber);
        } else {
            logger.log(Level.WARNING, "Subscriber to unregister was not registered before: " + subscriber.getClass());
        }
    }

    /**
     * 将给定事件发布到事件总线
     */
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

    /**
     * Called from a subscriber's event handling method, further event delivery will be canceled. Subsequent
     * subscribers
     * won't receive the event. Events are usually canceled by higher priority subscribers (see
     * {@link Subscribe#priority()}). Canceling is restricted to event handling methods running in posting thread
     * {@link ThreadMode#POSTING}.
     */
    public void cancelEventDelivery(Object event) {
        PostingThreadState postingState = currentPostingThreadState.get();
        if (!postingState.isPosting) {
            throw new EventBusException(
                    "This method may only be called from inside event handling methods on the posting thread");
        } else if (event == null) {
            throw new EventBusException("Event may not be null");
        } else if (postingState.event != event) {
            throw new EventBusException("Only the currently handled event may be aborted");
        } else if (postingState.subscription.subscriberMethod.threadMode != ThreadMode.POSTING) {
            throw new EventBusException(" event handlers may only abort the incoming event");
        }

        postingState.canceled = true;
    }

    /**
     * Posts the given event to the event bus and holds on to the event (because it is sticky). The most recent sticky
     * event of an event's type is kept in memory for future access by subscribers using {@link Subscribe#sticky()}.
     */
    public void postSticky(Object event) {
        synchronized (stickyEvents) {
            stickyEvents.put(event.getClass(), event);
        }
        // Should be posted after it is putted, in case the subscriber wants to remove immediately
        post(event);
    }

    /**
     * Gets the most recent sticky event for the given type.
     *
     * @see #postSticky(Object)
     */
    public <T> T getStickyEvent(Class<T> eventType) {
        synchronized (stickyEvents) {
            return eventType.cast(stickyEvents.get(eventType));
        }
    }

    /**
     * Remove and gets the recent sticky event for the given event type.
     *
     * @see #postSticky(Object)
     */
    public <T> T removeStickyEvent(Class<T> eventType) {
        synchronized (stickyEvents) {
            return eventType.cast(stickyEvents.remove(eventType));
        }
    }

    /**
     * Removes the sticky event if it equals to the given event.
     *
     * @return true if the events matched and the sticky event was removed.
     */
    public boolean removeStickyEvent(Object event) {
        synchronized (stickyEvents) {
            Class<?> eventType = event.getClass();
            Object existingEvent = stickyEvents.get(eventType);
            if (event.equals(existingEvent)) {
                stickyEvents.remove(eventType);
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Removes all sticky events.
     */
    public void removeAllStickyEvents() {
        synchronized (stickyEvents) {
            stickyEvents.clear();
        }
    }

    public boolean hasSubscriberForEvent(Class<?> eventClass) {
        List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
        if (eventTypes != null) {
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                CopyOnWriteArrayList<Subscription> subscriptions;
                synchronized (this) {
                    subscriptions = subscriptionsByEventType.get(clazz);
                }
                if (subscriptions != null && !subscriptions.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

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

    /**
     * 通过事件类型发布单个事件
     *
     * @param event        Object 事件
     * @param postingState PostingThreadState 当前线程的发布状态
     * @param eventClass   eventClass 事件 Class 对象
     * @return 是否找到订阅关系
     */
    private boolean postSingleEventForEventType(Object event, PostingThreadState postingState, Class<?> eventClass) {
        CopyOnWriteArrayList<Subscription> subscriptions;
        // 加锁 监视器为当前对象
        synchronized (this) {
            // 获取该 Class 对象的订阅方法 List
            subscriptions = subscriptionsByEventType.get(eventClass);
        }
        if (subscriptions != null && !subscriptions.isEmpty()) {
            // 如果存在订阅方法，就进行遍历操作
            for (Subscription subscription : subscriptions) {
                // 将事件和订阅方法赋值给 postingState
                postingState.event = event;
                postingState.subscription = subscription;
                // 是否中止
                boolean aborted;
                try {
                    // 将事件发布到订阅者
                    postToSubscription(subscription, event, postingState.isMainThread);
                    // 是否已经取消发布
                    aborted = postingState.canceled;
                } finally {
                    // 重置 postingState 状态
                    postingState.event = null;
                    postingState.subscription = null;
                    postingState.canceled = false;
                }
                // 如果已经中止，就跳出循环
                if (aborted) {
                    break;
                }
            }
            // 方法体结束，返回找到订阅关系
            return true;
        }
        // 到此步骤表示没有订阅方法，返回 false
        return false;
    }

    /**
     * 将事件发布到订阅者
     *
     * @param subscription Subscription 订阅关系
     * @param event        Object 事件
     * @param isMainThread boolean 是否是主线程
     */
    private void postToSubscription(Subscription subscription, Object event, boolean isMainThread) {
        // 按照订阅者方法指定的线程模式进行针对性处理
        switch (subscription.subscriberMethod.threadMode) {
            // 发布线程
            case POSTING:
                invokeSubscriber(subscription, event);
                break;
            // Android 上为主线程，非 Android 与 POSTING 一致
            case MAIN:
                // 判断是否是主线程，如果是主线程，直接调用 void invokeSubscriber(Subscription subscription, Object event) 方法进行在当前线程中发布事件
                if (isMainThread) {
                    invokeSubscriber(subscription, event);
                } else {
                    // 不是主线程，将该事件入队到主线程事件发布器处理
                    mainThreadPoster.enqueue(subscription, event);
                }
                break;
            // Android 上为主线程，并且按顺序发布，非 Android 与 POSTING 一致
            case MAIN_ORDERED:
                if (mainThreadPoster != null) {
                    // 不考虑当前的线程环境，直接入队，保证顺序
                    mainThreadPoster.enqueue(subscription, event);
                } else {
                    // 不是主线程，将该事件入队到主线程事件发布器处理
                    // temporary: technically not correct as poster not decoupled from subscriber
                    invokeSubscriber(subscription, event);
                }
                break;
            // Android 上为后台线程调用，非 Android 与 POSTING 一致
            case BACKGROUND:
                // 主线程发布的事件才会被入队到 backgroundPoster，非主线程发布的事件会被直接调用订阅者方法发布事件
                if (isMainThread) {
                    backgroundPoster.enqueue(subscription, event);
                } else {
                    invokeSubscriber(subscription, event);
                }
                break;
            // 使用单独的线程处理，基于线程池
            case ASYNC:
                // 入队 asyncPoster，该线程模式总是在非发布线程处理订阅者方法的调用
                asyncPoster.enqueue(subscription, event);
                break;
            default:
                throw new IllegalStateException("Unknown thread mode: " + subscription.subscriberMethod.threadMode);
        }
    }

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

    /**
     * 将给定 interfaces 添加进全部事件类型中
     * 该方法会对每一个接口进行深入查找父类，直到全部类型查找结束，通过递归的方式
     */
    static void addInterfaces(List<Class<?>> eventTypes, Class<?>[] interfaces) {
        // 循环遍历当前的接口数组
        for (Class<?> interfaceClass : interfaces) {
            // 判断所有事件类型中是否已经包含此类型，不包含的情况下将其添加进所有的事件类型
            if (!eventTypes.contains(interfaceClass)) {
                eventTypes.add(interfaceClass);
                // 对刚刚添加完的接口类型进一步深入查找父接口，通过递归的方式
                addInterfaces(eventTypes, interfaceClass.getInterfaces());
            }
        }
    }

    /**
     * 调用订阅者方法 该方法主要做了一些取值、释放、判断的操作，具体执行步骤在重载方法 {@link #invokeSubscriber(Subscription, Object)}中
     * 如果订阅仍处于活动状态，则调用订阅者；跳过订阅可防止 {@link #unregister(Object)} 和事件传递之间的竞争条件，否则，事件可能会在订阅者注销后传递。
     * 这对于绑定到 Activity 或 Fragment 的生命周期的主线程交付和注册尤其重要。
     */
    void invokeSubscriber(PendingPost pendingPost) {
        Object event = pendingPost.event;
        Subscription subscription = pendingPost.subscription;
        // 释放 pendingPost，准备下次复用
        PendingPost.releasePendingPost(pendingPost);
        // 判断订阅关系是否活跃
        if (subscription.active) {
            // 调用订阅方法进行发布事件
            invokeSubscriber(subscription, event);
        }
    }

    /**
     * 在当前线程直接调用订阅者方法
     *
     * @param subscription Subscription 订阅者及方法
     * @param event        Object 事件
     */
    void invokeSubscriber(Subscription subscription, Object event) {
        try {
            // 调用订阅者的订阅方法，将事件作为参数传递（反射调用）
            subscription.subscriberMethod.method.invoke(subscription.subscriber, event);
        } catch (InvocationTargetException e) {
            // 如果产生调用目标异常，就处理该异常
            handleSubscriberException(subscription, event, e.getCause());
        } catch (IllegalAccessException e) {
            // 如果是非法访问 直接抛出异常
            throw new IllegalStateException("Unexpected exception", e);
        }
    }

    /**
     * 处理订阅者方法异常
     *
     * @param subscription Subscription 订阅者及方法
     * @param event        Object 事件
     * @param cause        Throwable 异常
     */
    private void handleSubscriberException(Subscription subscription, Object event, Throwable cause) {
        // 判断是否是 EventBus 内部发布的 SubscriberExceptionEvent
        if (event instanceof SubscriberExceptionEvent) {
            // 判断订阅函数执行有异常时，是否打印异常信息
            if (logSubscriberExceptions) {
                // 不要发送另一个 SubscriberExceptionEvent 以避免无限事件递归，只需记录
                logger.log(Level.SEVERE, "SubscriberExceptionEvent subscriber " + subscription.subscriber.getClass()
                        + " threw an exception", cause);
                SubscriberExceptionEvent exEvent = (SubscriberExceptionEvent) event;
                logger.log(Level.SEVERE, "Initial event " + exEvent.causingEvent + " caused exception in "
                        + exEvent.causingSubscriber, exEvent.throwable);
            }
        } else {
            // 如果订阅者方法执行有异常时，是否抛出 SubscriberException
            if (throwSubscriberException) {
                throw new EventBusException("Invoking subscriber failed", cause);
            }
            // 判断订阅函数执行有异常时，是否打印异常信息
            if (logSubscriberExceptions) {
                logger.log(Level.SEVERE, "Could not dispatch event: " + event.getClass() + " to subscribing class "
                        + subscription.subscriber.getClass(), cause);
            }
            // 订阅函数执行有异常时，发布 SubscriberExceptionEvent 事件，该事件还会到该方法进行处理
            if (sendSubscriberExceptionEvent) {
                SubscriberExceptionEvent exEvent = new SubscriberExceptionEvent(this, cause, event,
                        subscription.subscriber);
                // 发布事件
                post(exEvent);
            }
        }
    }

    /**
     * For ThreadLocal, much faster to set (and get multiple values).
     */
    final static class PostingThreadState {
        // 事件队列
        final List<Object> eventQueue = new ArrayList<>();
        // 是否在发布
        boolean isPosting;
        // 是否是主线程
        boolean isMainThread;
        // 订阅关系
        Subscription subscription;
        // 正在发送的事件
        Object event;
        // 是否已经取消
        boolean canceled;
    }

    /**
     * 获取当前的线程池
     *
     * @return ExecutorService
     */
    ExecutorService getExecutorService() {
        return executorService;
    }

    /**
     * For internal use only.
     */
    public Logger getLogger() {
        return logger;
    }

    // Just an idea: we could provide a callback to post() to be notified, an alternative would be events, of course...
    /* public */interface PostCallback {
        void onPostCompleted(List<SubscriberExceptionEvent> exceptionEvents);
    }

    @Override
    public String toString() {
        return "EventBus[indexCount=" + indexCount + ", eventInheritance=" + eventInheritance + "]";
    }
}
