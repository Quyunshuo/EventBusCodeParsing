/*
 * Copyright (C) 2012-2016 Markus Junginger, greenrobot (http://greenrobot.org)
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
package org.greenrobot.eventbus.meta;

import org.greenrobot.eventbus.SubscriberMethod;

/**
 * 索引类的信息类
 */
public interface SubscriberInfo {
    /**
     * 获取索引类的 Class 对象
     */
    Class<?> getSubscriberClass();

    /**
     * 获取所有订阅者方法
     */
    SubscriberMethod[] getSubscriberMethods();

    /**
     * 获取当前索引类的父类订阅者信息
     */
    SubscriberInfo getSuperSubscriberInfo();

    /**
     * 是否检查超类
     */
    boolean shouldCheckSuperclass();
}
