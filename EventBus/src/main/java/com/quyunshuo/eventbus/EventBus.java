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
package com.quyunshuo.eventbus;

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
 * EventBus is a central publish/subscribe event system for Java and Android.
 * Events are posted ({@link #post(Object)}) to the bus, which delivers it to subscribers that have a matching handler
 * method for the event type.
 * To receive events, subscribers must register themselves to the bus using {@link #register(Object)}.
 * Once registered, subscribers receive events until {@link #unregister(Object)} is called.
 * Event handling methods must be annotated by {@link Subscribe}, must be public, return nothing (void),
 * and have exactly one parameter (the event).
 *
 * @author Markus Junginger, greenrobot
 */
public class EventBus {

    /**
     * Log tag, apps may override it.
     */
    public static String TAG = "EventBus";

    static volatile EventBus defaultInstance;

    private static final EventBusBuilder DEFAULT_BUILDER = new EventBusBuilder();
    // 事件类型缓存
    private static final Map<Class<?>, List<Class<?>>> eventTypesCache = new HashMap<>();

    // 按事件类型订阅
    private final Map<Class<?>, CopyOnWriteArrayList<Subscription>> subscriptionsByEventType;
    // key为订阅者 value为订阅者接收的事件类型 应该是用来判断这个订阅者是否已被注册过
    private final Map<Object, List<Class<?>>> typesBySubscriber;
    // 粘性事件 key为粘性事件类型的Class对象  value为粘性事件的事件类实例
    private final Map<Class<?>, Object> stickyEvents;

    // ThreadLocal实现了线程间的数据隔离
    private final ThreadLocal<PostingThreadState> currentPostingThreadState = new ThreadLocal<PostingThreadState>() {
        @Override
        protected PostingThreadState initialValue() {
            return new PostingThreadState();
        }
    };

    // @Nullable
    private final MainThreadSupport mainThreadSupport;
    // @Nullable
    // Main 主线程
    private final Poster mainThreadPoster;
    // 在子线程中
    private final BackgroundPoster backgroundPoster;
    // 总是开启一个子线程的
    private final AsyncPoster asyncPoster;
    // 订阅方法查找器
    private final SubscriberMethodFinder subscriberMethodFinder;
    private final ExecutorService executorService;
    // 抛出订阅异常
    private final boolean throwSubscriberException;
    // 订阅者异常日志 默认开启
    private final boolean logSubscriberExceptions;
    // 记录没有订阅者消息 默认开启
    private final boolean logNoSubscriberMessages;
    private final boolean sendSubscriberExceptionEvent;
    // 发送没有订阅者事件 默认开启
    private final boolean sendNoSubscriberEvent;
    // 事件继承 默认true
    // 如果允许事件继承 如果该事件的父类型有订阅方法  则父类型活跃状态的订阅者的订阅方法也会收到该事件
    private final boolean eventInheritance;

    private final int indexCount;
    private final Logger logger;

    /** Convenience singleton for apps using a process-wide EventBus instance. */
    /**
     * 双重校验锁单例模式
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
     * Creates a new EventBus instance; each instance is a separate scope in which events are delivered. To use a
     * central bus, consider {@link #getDefault()}.
     * 构造函数
     */
    public EventBus() {
        this(DEFAULT_BUILDER);
    }

    /**
     * 建造者模式
     */
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
     * 注册
     * 订阅者具有必须由{@link Subscribe}注释的事件处理方法
     * {@link Subscribe}注释还允许进行类似{@link * ThreadMode}和优先级的配置。
     */
    public void register(Object subscriber) {
        // 获取订阅者的Class对象
        Class<?> subscriberClass = subscriber.getClass();
        // 通过订阅方法查找器对订阅者的订阅方法进行查找
        List<SubscriberMethod> subscriberMethods = subscriberMethodFinder.findSubscriberMethods(subscriberClass);
        // 同步锁🔒
        synchronized (this) {
            for (SubscriberMethod subscriberMethod : subscriberMethods) {
                subscribe(subscriber, subscriberMethod);
            }
        }
    }

    /**
     * 订阅
     *
     * @param subscriber       订阅者
     * @param subscriberMethod 订阅方法
     *                         必须在synchronized代码块中执行
     */
    private void subscribe(Object subscriber, SubscriberMethod subscriberMethod) {
        // 订阅方法的参数类型
        Class<?> eventType = subscriberMethod.eventType;
        // 对订阅者&订阅方法进行包装
        Subscription newSubscription = new Subscription(subscriber, subscriberMethod);
        // 从subscriptionsByEventType中尝试get该参数类型的订阅方法list 该list会根据优先级进行排序 优先级高的订阅方法会在前面
        CopyOnWriteArrayList<Subscription> subscriptions = subscriptionsByEventType.get(eventType);

        if (subscriptions == null) {
            // 为null就创建该类型的list
            subscriptions = new CopyOnWriteArrayList<>();
            // 将该事件类型的订阅方法put进subscriptionsByEventType
            subscriptionsByEventType.put(eventType, subscriptions);
        } else {
            // 不为null
            // 是否重复订阅
            if (subscriptions.contains(newSubscription)) {
                throw new EventBusException("Subscriber " + subscriber.getClass() + " already registered to event "
                        + eventType);
            }
        }

        // 获取该事件类型的订阅方法列表长度
        int size = subscriptions.size();
        // 对该类型事件列表进行遍历
        for (int i = 0; i <= size; i++) {
            // 如过优先级不比该下标的订阅方法高 就继续遍历  如果比当前下标的订阅方法优先级高 就在目标事件前插入该数据  否则遍历到最后在最后添加该订阅事件
            if (i == size || subscriberMethod.priority > subscriptions.get(i).subscriberMethod.priority) {
                subscriptions.add(i, newSubscription);
                break;
            }
        }

        // 从typesBySubscriber中尝试get该订阅者
        List<Class<?>> subscribedEvents = typesBySubscriber.get(subscriber);
        if (subscribedEvents == null) {
            subscribedEvents = new ArrayList<>();
            // 将该订阅者 以及接收的事件类型列表put进typesBySubscriber
            typesBySubscriber.put(subscriber, subscribedEvents);
        }
        // 将接收的事件类型add进该订阅者的接收事件类型列表中
        subscribedEvents.add(eventType);

        // 判断是否是粘性事件
        if (subscriberMethod.sticky) {
            if (eventInheritance) {
                // Existing sticky events of all subclasses of eventType have to be considered.
                // Note: Iterating over all events may be inefficient with lots of sticky events,
                // thus data structure should be changed to allow a more efficient lookup
                // (e.g. an additional map storing sub classes of super classes: Class -> List<Class>).
                // 获取粘性事件Map里键值对的映射关系set
                Set<Map.Entry<Class<?>, Object>> entries = stickyEvents.entrySet();
                // 遍历
                for (Map.Entry<Class<?>, Object> entry : entries) {
                    // 遍历到的事件类型
                    Class<?> candidateEventType = entry.getKey();
                    // 判断订阅方法的参数类型是否匹配现有的粘性事件类型
                    if (eventType.isAssignableFrom(candidateEventType)) {
                        // 获取到粘性事件
                        Object stickyEvent = entry.getValue();
                        //
                        checkPostStickyEventToSubscription(newSubscription, stickyEvent);
                    }
                }
            } else {
                Object stickyEvent = stickyEvents.get(eventType);
                checkPostStickyEventToSubscription(newSubscription, stickyEvent);
            }
        }
    }

    /**
     * 将已有的粘性事件发布到订阅者
     *
     * @param newSubscription 订阅者+订阅方法 的包装类
     * @param stickyEvent     粘性事件
     */
    private void checkPostStickyEventToSubscription(Subscription newSubscription, Object stickyEvent) {
        if (stickyEvent != null) {
            // If the subscriber is trying to abort the event, it will fail (event is not tracked in posting state)
            // --> Strange corner case, which we don't take care of here.
            // 发布到订阅者
            postToSubscription(newSubscription, stickyEvent, isMainThread());
        }
    }

    /**
     * Checks if the current thread is running in the main thread.
     * If there is no main thread support (e.g. non-Android), "true" is always returned. In that case MAIN thread
     * subscribers are always called in posting thread, and BACKGROUND subscribers are always called from a background
     * poster.
     */
    /**
     * 检查当前线程是否是主线程
     *
     * @return
     */
    private boolean isMainThread() {
        return mainThreadSupport == null || mainThreadSupport.isMainThread();
    }

    public synchronized boolean isRegistered(Object subscriber) {
        return typesBySubscriber.containsKey(subscriber);
    }

    /**
     * Only updates subscriptionsByEventType, not typesBySubscriber! Caller must update typesBySubscriber.
     */
    private void unsubscribeByEventType(Object subscriber, Class<?> eventType) {
        List<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
        if (subscriptions != null) {
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
     * Unregisters the given subscriber from all event classes.
     */
    public synchronized void unregister(Object subscriber) {
        List<Class<?>> subscribedTypes = typesBySubscriber.get(subscriber);
        if (subscribedTypes != null) {
            for (Class<?> eventType : subscribedTypes) {
                unsubscribeByEventType(subscriber, eventType);
            }
            typesBySubscriber.remove(subscriber);
        } else {
            logger.log(Level.WARNING, "Subscriber to unregister was not registered before: " + subscriber.getClass());
        }
    }

    /**
     * Posts the given event to the event bus.
     */
    /**
     * 发送普通事件
     *
     * @param event
     */
    public void post(Object event) {
        // 从当前线程中取得线程专属变量PostingThreadState实例
        PostingThreadState postingState = currentPostingThreadState.get();
        // 取得事件队列
        List<Object> eventQueue = postingState.eventQueue;
        // 将该事件添加进队列当中
        eventQueue.add(event);
        // 判断当前事件是否是发送状态
        if (!postingState.isPosting) {
            // 如果不是发送状态
            // 设置当前线程是否是主线程
            postingState.isMainThread = isMainThread();
            // 标记为发送中
            postingState.isPosting = true;

            // 判断是否取消
            if (postingState.canceled) {
                throw new EventBusException("Internal error. Abort state was not reset");
            }
            try {
                // 事件队列是否为空
                while (!eventQueue.isEmpty()) {
                    // 发送单个事件 将事件从队列中移除
                    postSingleEvent(eventQueue.remove(0), postingState);
                }
            } finally {
                // 重置状态
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
     * 发送单个事件
     *
     * @param event        事件
     * @param postingState 发送状态
     * @throws Error
     */
    private void postSingleEvent(Object event, PostingThreadState postingState) throws Error {
        // 获取事件的Class对象
        Class<?> eventClass = event.getClass();
        // 订阅是否找到
        boolean subscriptionFound = false;
        // 是否事件继承
        if (eventInheritance) {
            // 获取到该事件类型的超类Class对象列表
            List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                // 如果取消了 subscriptionFound=false
                subscriptionFound |= postSingleEventForEventType(event, postingState, clazz);
            }
        } else {
            // 如果事件不继承
            // 直接对该事件进行发送
            subscriptionFound = postSingleEventForEventType(event, postingState, eventClass);
        }
        // 如果取消了或者是没有订阅方法
        if (!subscriptionFound) {

            if (logNoSubscriberMessages) {
                logger.log(Level.FINE, "No subscribers registered for event " + eventClass);
            }
            if (sendNoSubscriberEvent && eventClass != NoSubscriberEvent.class &&
                    eventClass != SubscriberExceptionEvent.class) {
                // 发送一个内置的空事件
                post(new NoSubscriberEvent(this, event));
            }
        }
    }

    /**
     * 针对事件类型发布单个事件
     *
     * @param event        该事件
     * @param postingState 发送状态信息
     * @param eventClass   父类型
     * @return 是否取消了
     */
    private boolean postSingleEventForEventType(Object event, PostingThreadState postingState, Class<?> eventClass) {
        //
        CopyOnWriteArrayList<Subscription> subscriptions;
        synchronized (this) {
            // 从subscriptionsByEventType中获取到该事件类型的所有订阅方法
            subscriptions = subscriptionsByEventType.get(eventClass);
        }
        if (subscriptions != null && !subscriptions.isEmpty()) {
            // 遍历该事件类型的所有订阅方法 将事件发送给订阅方法
            for (Subscription subscription : subscriptions) {
                postingState.event = event;
                postingState.subscription = subscription;
                boolean aborted;
                try {
                    // 将事件发布给订阅方法
                    postToSubscription(subscription, event, postingState.isMainThread);
                    // 取消？
                    aborted = postingState.canceled;
                } finally {
                    postingState.event = null;
                    postingState.subscription = null;
                    postingState.canceled = false;
                }
                // 如果该事件取消发送 则不再进行发送
                if (aborted) {
                    break;
                }
            }
            // 没取消
            return true;
        }
        // 取消了 或是没有订阅方法
        return false;
    }

    /**
     * 将事件发布给订阅方法 & 线程切换
     *
     * @param subscription 订阅者+订阅方法 的包装类
     * @param event        事件
     * @param isMainThread 是否是主线程
     */
    private void postToSubscription(Subscription subscription, Object event, boolean isMainThread) {
        // 匹配线程模型
        switch (subscription.subscriberMethod.threadMode) {
            case POSTING:
                invokeSubscriber(subscription, event);
                break;
            case MAIN:
                if (isMainThread) {
                    invokeSubscriber(subscription, event);
                } else {
                    mainThreadPoster.enqueue(subscription, event);
                }
                break;
            case MAIN_ORDERED:
                // 如果在Android平台上，那么事件将异步地在Android 主线程中执行；
                // 如果在非Android平台上，那么事件将同步地在发布线程中执行。
                if (mainThreadPoster != null) {
                    mainThreadPoster.enqueue(subscription, event);
                } else {
                    // temporary: technically not correct as poster not decoupled from subscriber
                    invokeSubscriber(subscription, event);
                }
                break;
            case BACKGROUND:
                if (isMainThread) {
                    backgroundPoster.enqueue(subscription, event);
                } else {
                    invokeSubscriber(subscription, event);
                }
                break;
            case ASYNC:
                asyncPoster.enqueue(subscription, event);
                break;
            default:
                throw new IllegalStateException("Unknown thread mode: " + subscription.subscriberMethod.threadMode);
        }
    }

    /**
     * Looks up all Class objects including super classes and interfaces. Should also work for interfaces.
     */
    /**
     * 查找所有的事件类型
     *
     * @param eventClass 事件Class对象
     * @return
     */
    private static List<Class<?>> lookupAllEventTypes(Class<?> eventClass) {
        synchronized (eventTypesCache) {
            // 从事件类型缓存中尝试获取该事件的超类Class对象列表
            List<Class<?>> eventTypes = eventTypesCache.get(eventClass);
            if (eventTypes == null) {
                eventTypes = new ArrayList<>();
                Class<?> clazz = eventClass;
                while (clazz != null) {
                    // 将该事件添加进eventTypes
                    eventTypes.add(clazz);
                    // 对事件实现的接口进行递归添加进eventTypes
                    addInterfaces(eventTypes, clazz.getInterfaces());
                    // 获取事件的父类 对其父类进行遍历
                    clazz = clazz.getSuperclass();
                }
                // 将该事件的超类进行递归 put进eventTypesCache
                eventTypesCache.put(eventClass, eventTypes);
            }
            // 返回该事件的超类Class对象列表
            return eventTypes;
        }
    }

    /**
     * Recurses through super interfaces.
     */
    static void addInterfaces(List<Class<?>> eventTypes, Class<?>[] interfaces) {
        // 遍历该事件类型的接口
        for (Class<?> interfaceClass : interfaces) {
            // 该事件类型集合里是否包含该接口
            if (!eventTypes.contains(interfaceClass)) {
                // 如果不包含就添加进事件类型集合
                eventTypes.add(interfaceClass);
                // 继续递归
                addInterfaces(eventTypes, interfaceClass.getInterfaces());
            }
        }
    }

    /**
     * Invokes the subscriber if the subscriptions is still active. Skipping subscriptions prevents race conditions
     * between {@link #unregister(Object)} and event delivery. Otherwise the event might be delivered after the
     * subscriber unregistered. This is particularly important for main thread delivery and registrations bound to the
     * live cycle of an Activity or Fragment.
     */
    /**
     * 发送事件
     *
     * @param pendingPost
     */
    void invokeSubscriber(PendingPost pendingPost) {
        // 获取事件
        Object event = pendingPost.event;
        // 获取订阅方法的包装类
        Subscription subscription = pendingPost.subscription;
        PendingPost.releasePendingPost(pendingPost);
        // 如果订阅者的状态为活跃状态 就发送事件
        if (subscription.active) {
            invokeSubscriber(subscription, event);
        }
    }

    /**
     * 发送事件
     *
     * @param subscription 订阅方法的包装类
     * @param event        事件
     */
    void invokeSubscriber(Subscription subscription, Object event) {
        try {
            // 通过反射调用订阅方法
            subscription.subscriberMethod.method.invoke(subscription.subscriber, event);
        } catch (InvocationTargetException e) {
            handleSubscriberException(subscription, event, e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
    }

    /**
     * 调用目标异常
     *
     * @param subscription 订阅者订阅方法的包装类
     * @param event        事件
     * @param cause        异常信息
     */
    private void handleSubscriberException(Subscription subscription, Object event, Throwable cause) {
        if (event instanceof SubscriberExceptionEvent) {
            if (logSubscriberExceptions) {
                // Don't send another SubscriberExceptionEvent to avoid infinite event recursion, just log
                logger.log(Level.SEVERE, "SubscriberExceptionEvent subscriber " + subscription.subscriber.getClass()
                        + " threw an exception", cause);
                SubscriberExceptionEvent exEvent = (SubscriberExceptionEvent) event;
                logger.log(Level.SEVERE, "Initial event " + exEvent.causingEvent + " caused exception in "
                        + exEvent.causingSubscriber, exEvent.throwable);
            }
        } else {
            if (throwSubscriberException) {
                throw new EventBusException("Invoking subscriber failed", cause);
            }
            if (logSubscriberExceptions) {
                logger.log(Level.SEVERE, "Could not dispatch event: " + event.getClass() + " to subscribing class "
                        + subscription.subscriber.getClass(), cause);
            }
            if (sendSubscriberExceptionEvent) {
                SubscriberExceptionEvent exEvent = new SubscriberExceptionEvent(this, cause, event,
                        subscription.subscriber);
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

        // 是否发送
        boolean isPosting;
        // 是否是主线程
        boolean isMainThread;

        // 订阅方法包装类
        Subscription subscription;

        // 事件
        Object event;

        // 取消
        boolean canceled;
    }

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