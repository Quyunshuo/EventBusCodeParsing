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

import java.util.logging.Level;

/**
 * 后台线程事件发布器，基于线程池实现
 *
 * @author Markus
 */
final class BackgroundPoster implements Runnable, Poster {

    // 待发布事件队列
    private final PendingPostQueue queue;
    private final EventBus eventBus;

    // 执行器运行状态 volatile 可见性保障
    private volatile boolean executorRunning;

    BackgroundPoster(EventBus eventBus) {
        this.eventBus = eventBus;
        queue = new PendingPostQueue();
    }

    /**
     * 入队
     *
     * @param subscription Subscription 接收事件的订阅者方法包装类
     * @param event        Object 将发布给订阅者的事件
     */
    public void enqueue(Subscription subscription, Object event) {
        // 获得一个 PendingPost
        PendingPost pendingPost = PendingPost.obtainPendingPost(subscription, event);
        // 加锁 监视器为当前对象
        synchronized (this) {
            // 入队
            queue.enqueue(pendingPost);
            // 判断执行器是否在执行
            if (!executorRunning) {
                // 设置状态为正在执行
                executorRunning = true;
                // 向线程池提交任务
                eventBus.getExecutorService().execute(this);
            }
        }
    }

    @Override
    public void run() {
        try {
            try {
                // 循环处理队列中的所有待发布事件
                while (true) {
                    // 取出队头的元素
                    PendingPost pendingPost = queue.poll(1000);
                    // 二次校验
                    if (pendingPost == null) {
                        // 第二级校验是同步的
                        synchronized (this) {
                            pendingPost = queue.poll();
                            if (pendingPost == null) {
                                // 最终没有元素，将执行器置为空闲
                                executorRunning = false;
                                return;
                            }
                        }
                    }
                    // 调用订阅者方法
                    eventBus.invokeSubscriber(pendingPost);
                }
            } catch (InterruptedException e) {
                eventBus.getLogger().log(Level.WARNING, Thread.currentThread().getName() + " was interrupted", e);
            }
        } finally {
            // 执行完后将运行状态置为空闲
            executorRunning = false;
        }
    }

}
