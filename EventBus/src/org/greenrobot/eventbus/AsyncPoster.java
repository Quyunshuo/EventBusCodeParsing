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
 * 异步事件发布器，基于线程池实现
 * 
 * @author Markus
 */
class AsyncPoster implements Runnable, Poster {

    // 待发布事件队列
    private final PendingPostQueue queue;
    private final EventBus eventBus;

    AsyncPoster(EventBus eventBus) {
        this.eventBus = eventBus;
        queue = new PendingPostQueue();
    }

    /**
     * 入队
     * @param subscription Subscription 接收事件的订阅方法
     * @param event        Object 将发布给订阅者的事件
     */
    public void enqueue(Subscription subscription, Object event) {
        // 获取一个 PendingPost
        PendingPost pendingPost = PendingPost.obtainPendingPost(subscription, event);
        // 入队
        queue.enqueue(pendingPost);
        // 将任务提交到线程池处理，每个事件都会单独提交，不同于 BackgroundPoster
        eventBus.getExecutorService().execute(this);
    }

    @Override
    public void run() {
        // 获取队头的元素 一一对应，一次任务执行消费一个事件元素
        PendingPost pendingPost = queue.poll();
        if(pendingPost == null) {
            throw new IllegalStateException("No pending post available");
        }
        // 调用订阅者方法发布事件
        eventBus.invokeSubscriber(pendingPost);
    }

}
