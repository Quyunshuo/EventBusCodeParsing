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
 * 简单订阅者信息类 用于注解处理器生成的索引类中
 * 使用 {@link SubscriberMethodInfo} 对象按需创建 {@link org.greenrobot.eventbus.SubscriberMethod} 对象。
 */
public class SimpleSubscriberInfo extends AbstractSubscriberInfo {
    /**
     * 所有订阅者方法信息
     */
    private final SubscriberMethodInfo[] methodInfos;

    public SimpleSubscriberInfo(Class subscriberClass, boolean shouldCheckSuperclass, SubscriberMethodInfo[] methodInfos) {
        super(subscriberClass, null, shouldCheckSuperclass);
        this.methodInfos = methodInfos;
    }

    /**
     * 获取当前类的所有订阅者方法，该方法是同步方法
     *
     * @return SubscriberMethod[] 订阅者方法
     */
    @Override
    public synchronized SubscriberMethod[] getSubscriberMethods() {
        // 遍历订阅者方法信息集合，创建 SubscriberMethod
        int length = methodInfos.length;
        SubscriberMethod[] methods = new SubscriberMethod[length];
        for (int i = 0; i < length; i++) {
            SubscriberMethodInfo info = methodInfos[i];
            methods[i] = createSubscriberMethod(info.methodName, info.eventType, info.threadMode,
                    info.priority, info.sticky);
        }
        return methods;
    }
}