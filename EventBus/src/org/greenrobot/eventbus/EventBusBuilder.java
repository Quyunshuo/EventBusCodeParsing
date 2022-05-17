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

import org.greenrobot.eventbus.android.AndroidComponents;
import org.greenrobot.eventbus.meta.SubscriberInfoIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 使用自定义参数创建 EventBus 实例，还允许安装自定义的默认 EventBus 实例。
 * 使用 {@link EventBus#builder()} 创建一个新的构建器。
 */
@SuppressWarnings("unused")
public class EventBusBuilder {
    // 私有线程池
    private final static ExecutorService DEFAULT_EXECUTOR_SERVICE = Executors.newCachedThreadPool();

    // 订阅函数执行有异常时，打印异常信息
    boolean logSubscriberExceptions = true;
    // 事件无匹配订阅函数时，打印信息
    boolean logNoSubscriberMessages = true;
    // 订阅函数执行有异常时，发布 SubscriberExceptionEvent 事件
    boolean sendSubscriberExceptionEvent = true;
    // 事件无匹配订阅函数时，发布NoSubscriberEvent
    boolean sendNoSubscriberEvent = true;
    // 如果订阅者方法执行有异常时，抛出 SubscriberException
    boolean throwSubscriberException;
    // 事件继承
    boolean eventInheritance = true;
    // 是否忽略生成的索引 默认值为 false
    boolean ignoreGeneratedIndex;
    // 是否进行严格的方法验证 默认值为 false
    boolean strictMethodVerification;
    // 公开线程池
    ExecutorService executorService = DEFAULT_EXECUTOR_SERVICE;
    // 跳过类的方法验证
    List<Class<?>> skipMethodVerificationForClasses;

    // 订阅者索引集合 SubscriberInfoIndex 是注解处理器生成的索引类的抽象接口
    // 该属性只有在调用 addIndex(SubscriberInfoIndex index) 添加索引类的时候才会被初始化赋值
    List<SubscriberInfoIndex> subscriberInfoIndexes;
    // 日志处理程序
    Logger logger;
    // 主线程支持
    MainThreadSupport mainThreadSupport;

    EventBusBuilder() {
    }

    /**
     * 配置订阅函数执行有异常时，是否打印异常信息
     *
     * @param logSubscriberExceptions boolean 默认：true
     * @return EventBusBuilder
     */
    public EventBusBuilder logSubscriberExceptions(boolean logSubscriberExceptions) {
        this.logSubscriberExceptions = logSubscriberExceptions;
        return this;
    }

    /**
     * 配置事件无匹配订阅函数时，是否打印信息
     *
     * @param logNoSubscriberMessages boolean 默认：true
     * @return EventBusBuilder
     */
    public EventBusBuilder logNoSubscriberMessages(boolean logNoSubscriberMessages) {
        this.logNoSubscriberMessages = logNoSubscriberMessages;
        return this;
    }

    /**
     * 配置订阅函数执行有异常时，是否发布 SubscriberExceptionEvent 事件
     *
     * @param sendSubscriberExceptionEvent boolean 默认：true
     * @return EventBusBuilder
     */
    public EventBusBuilder sendSubscriberExceptionEvent(boolean sendSubscriberExceptionEvent) {
        this.sendSubscriberExceptionEvent = sendSubscriberExceptionEvent;
        return this;
    }

    /**
     * 配置事件无匹配订阅函数时，是否发布 NoSubscriberEvent
     *
     * @param sendNoSubscriberEvent boolean 默认：true
     * @return EventBusBuilder
     */
    public EventBusBuilder sendNoSubscriberEvent(boolean sendNoSubscriberEvent) {
        this.sendNoSubscriberEvent = sendNoSubscriberEvent;
        return this;
    }

    /**
     * 如果订阅者方法执行有异常时，是否抛出 SubscriberException
     *
     * @param throwSubscriberException boolean 默认：false
     * @return EventBusBuilder
     */
    public EventBusBuilder throwSubscriberException(boolean throwSubscriberException) {
        this.throwSubscriberException = throwSubscriberException;
        return this;
    }

    /**
     * 配置是否事件继承
     * 默认情况下，EventBus 考虑事件类层次结构（将通知超类的订阅者）。
     * 关闭此功能将改善事件的发布。对于直接扩展 Object 的简单事件类，我们测得事件发布的速度提高了 20%。
     * 对于更复杂的事件层次结构，加速应该大于 20%。
     * 但是，请记住，事件发布通常只消耗应用程序内的一小部分 CPU 时间，除非它以高速率发布，例如每秒数十万个事件
     *
     * @param eventInheritance boolean 默认：true
     * @return EventBusBuilder
     */
    public EventBusBuilder eventInheritance(boolean eventInheritance) {
        this.eventInheritance = eventInheritance;
        return this;
    }


    /**
     * 为 EventBus 提供一个自定义线程池，用于异步和后台事件传递。
     * 这是一个可以破坏事情的高级设置：确保给定的 ExecutorService 不会卡住以避免未定义的行为。
     */
    public EventBusBuilder executorService(ExecutorService executorService) {
        this.executorService = executorService;
        return this;
    }

    /**
     * 跳过类的方法验证
     */
    public EventBusBuilder skipMethodVerificationFor(Class<?> clazz) {
        if (skipMethodVerificationForClasses == null) {
            skipMethodVerificationForClasses = new ArrayList<>();
        }
        skipMethodVerificationForClasses.add(clazz);
        return this;
    }

    /**
     * 配置是否即使有生成的索引也强制使用反射
     *
     * @param ignoreGeneratedIndex boolean 默认：false
     * @return EventBusBuilder
     */
    public EventBusBuilder ignoreGeneratedIndex(boolean ignoreGeneratedIndex) {
        this.ignoreGeneratedIndex = ignoreGeneratedIndex;
        return this;
    }

    /**
     * 是否启用严格的方法验证
     *
     * @param strictMethodVerification boolean 默认：false
     * @return EventBusBuilder
     */
    public EventBusBuilder strictMethodVerification(boolean strictMethodVerification) {
        this.strictMethodVerification = strictMethodVerification;
        return this;
    }

    /**
     * 添加索引类
     *
     * @param index SubscriberInfoIndex APT 生成的订阅者索引类
     * @return EventBusBuilder
     */
    public EventBusBuilder addIndex(SubscriberInfoIndex index) {
        if (subscriberInfoIndexes == null) {
            subscriberInfoIndexes = new ArrayList<>();
        }
        subscriberInfoIndexes.add(index);
        return this;
    }

    /**
     * 为所有 EventBus 日志设置特定的日志处理程序
     * 默认情况下，所有日志记录都是通过 Android 上的 {@code android.util.Log} 或 JVM 上的 System.out。
     */
    public EventBusBuilder logger(Logger logger) {
        this.logger = logger;
        return this;
    }

    /**
     * 获取 Logger
     */
    Logger getLogger() {
        if (logger != null) {
            return logger;
        } else {
            return Logger.Default.get();
        }
    }

    /**
     * 获取主线程支持
     */
    MainThreadSupport getMainThreadSupport() {
        if (mainThreadSupport != null) {
            return mainThreadSupport;
        } else if (AndroidComponents.areAvailable()) {
            // 如果有 Android 组件就返回默认的 Android 主线程支持
            return AndroidComponents.get().defaultMainThreadSupport;
        } else {
            return null;
        }
    }

    /**
     * 使用此构建器的值安装由 {@link EventBus#getDefault()} 返回的默认 EventBus
     * 在第一次使用默认 EventBus 之前必须只执行一次
     * 此方法调用后，再通过 {@link EventBus#getDefault()} 获取到的对象就是该构建器配置的实例
     * 所以该方法应该在最后调用
     *
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