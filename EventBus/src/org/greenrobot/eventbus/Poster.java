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
package org.greenrobot.eventbus;

/**
 * 发布事件程序抽象 可以理解为事件发布器抽象
 *
 * @author William Ferguson
 */
public interface Poster {

    /**
     * 将要为特定订阅发布的事件排入队列
     *
     * @param subscription Subscription 接收事件的订阅方法
     * @param event        Object 将发布给订阅者的事件
     */
    void enqueue(Subscription subscription, Object event);
}
