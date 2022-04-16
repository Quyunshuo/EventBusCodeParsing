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
package com.quyunshuo.eventbus;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

/**
 * 负责切换主线程
 * 实现了 Poster 接口，这就是一个普通的 Handler，只是它的 Looper 使用的是主线程的 「Main Looper」，可以将消息分发到主线程中
 * enqueue()方法并不会每次都发送Message激活handleMessage()，这是通过handlerActive标志位进行控制的
 * 那么enqueue()中那些没有被消费的事件该怎么消费呢？答案是handleMessage()中的while死循环
 * 但是为了避免一直在死循环中处理事件影响主线程的性能，又设置了一个超时时间，一旦执行了超时了
 * 那么再发送一个Message并且退出，那么Handler的机制可以保证过会儿又能进入到handleMessage()方法中继续处理队列中的事件
 * 核心还是通过反射进行调用的，这儿也能看出订阅方法的执行是在主线程中的。但是由于enqueue()的存在，订阅与发布是异步的，订阅的消费不会阻塞发布
 */
public class HandlerPoster extends Handler implements Poster {

    // 是一个链表实现的队列
    private final PendingPostQueue queue;
    // 间隔10ms重新sendMessage
    private final int maxMillisInsideHandleMessage;
    private final EventBus eventBus;
    // 是否在处理中
    private boolean handlerActive;

    protected HandlerPoster(EventBus eventBus, Looper looper, int maxMillisInsideHandleMessage) {
        super(looper);
        this.eventBus = eventBus;
        this.maxMillisInsideHandleMessage = maxMillisInsideHandleMessage;
        queue = new PendingPostQueue();
    }

    /**
     * 将Subscription和Object组合成PendingPost对象，然后加入到队列中，然后发送的是一个Message对象
     * 发送完Message,Handler的接受逻辑是在handleMessage()方法中
     *
     * @param subscription Subscription which will receive the event.
     * @param event        Event that will be posted to subscribers.
     */
    public void enqueue(Subscription subscription, Object event) {
        PendingPost pendingPost = PendingPost.obtainPendingPost(subscription, event);
        synchronized (this) {
            // 加入队列
            queue.enqueue(pendingPost);
            // 如果不在处理中，那么发送一个Message激活激活处理流程
            if (!handlerActive) {
                handlerActive = true;
                if (!sendMessage(obtainMessage())) {
                    throw new EventBusException("Could not send handler message");
                }
            }
        }
    }

    /**
     * 为了避免频繁的向主线程 sendMessage()，EventBus 的做法是在一个消息里尽可能多的处理更多的消息事件，所以使用了 while 循环，持续从消息队列 queue 中获取消息。
     * 同时为了避免长期占有主线程，间隔 10ms （maxMillisInsideHandleMessage = 10ms）会重新发送 sendMessage()，用于让出主线程的执行权，避免造成 UI 卡顿和 ANR
     *
     * @param msg
     */
    @Override
    public void handleMessage(Message msg) {
        boolean rescheduled = false;
        try {
            // 获取自启动以来的毫秒数
            long started = SystemClock.uptimeMillis();
            // 循环处理消息事件  避免重复sendMessage()
            while (true) {
                // 从队列中取PendingPost
                PendingPost pendingPost = queue.poll();
                // //双重检查，由于enqueue存在并发问题
                if (pendingPost == null) {
                    synchronized (this) {
                        // Check again, this time in synchronized
                        pendingPost = queue.poll();
                        if (pendingPost == null) {
                            // 没有要处理的事件了，置标志位为false，表示不在处理中了
                            handlerActive = false;
                            return;
                        }
                    }
                }
                // 反射调用订阅方法
                eventBus.invokeSubscriber(pendingPost);
                // 记录进入while循环的时间
                long timeInMethod = SystemClock.uptimeMillis() - started;
                // 避免长期占用主线程 间隔10ms重新sendMessage() 但是处理中激活标志仍为true，因为又发了一条Message
                if (timeInMethod >= maxMillisInsideHandleMessage) {
                    if (!sendMessage(obtainMessage())) {
                        throw new EventBusException("Could not send handler message");
                    }
                    rescheduled = true;
                    return;
                }
            }
        } finally {
            handlerActive = rescheduled;
        }
    }
}