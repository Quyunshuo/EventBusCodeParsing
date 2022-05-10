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

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

/**
 * 主线程事件发布器，基于 Handler 是实现
 * 实现了 Poster 接口，这就是一个普通的 Handler，只是它的 Looper 使用的是主线程的 「Main Looper」，可以将消息分发到主线程中
 * enqueue() 方法并不会每次都发送 Message 激活 handleMessage()，这是通过 handlerActive 标志位进行控制的
 * 那么 enqueue() 中那些没有被消费的事件该怎么消费呢？答案是 handleMessage() 中的 while 死循环
 * 但是为了避免一直在死循环中处理事件影响主线程的性能，又设置了一个超时时间，一旦执行了超时了
 * 那么再发送一个 Message 并且退出，那么 Handler 的机制可以保证过会儿又能进入到 handleMessage() 方法中继续处理队列中的事件
 * 核心还是通过反射进行调用的，这儿也能看出订阅方法的执行是在主线程中的。但是由于 enqueue() 的存在，订阅与发布是异步的，订阅的消费不会阻塞发布
 */
public class HandlerPoster extends Handler implements Poster {

    // 事件队列
    private final PendingPostQueue queue;
    // 处理消息最大间隔时间 默认10ms，每次循环发布消息的时间超过该值时，就会让出主线程的使用权，等待下次调度再继续发布事件
    private final int maxMillisInsideHandleMessage;
    private final EventBus eventBus;
    // 此 Handle 是否活跃
    private boolean handlerActive;

    /**
     * 唯一构造
     *
     * @param eventBus                     EventBus
     * @param looper                       Looper 主线程的 Looper
     * @param maxMillisInsideHandleMessage int 超时时间
     */
    public HandlerPoster(EventBus eventBus, Looper looper, int maxMillisInsideHandleMessage) {
        super(looper);
        this.eventBus = eventBus;
        this.maxMillisInsideHandleMessage = maxMillisInsideHandleMessage;
        queue = new PendingPostQueue();
    }

    /**
     * 入队
     *
     * @param subscription Subscription 接收事件的订阅方法
     * @param event        Object 将发布给订阅者的事件
     */
    public void enqueue(Subscription subscription, Object event) {
        // 获取一个 PendingPost，实际上将 subscription、event 包装成为一个 PendingPost
        PendingPost pendingPost = PendingPost.obtainPendingPost(subscription, event);
        // 加锁 监视器为当前对象
        synchronized (this) {
            // 将获取到的 PendingPost 包装类入队
            queue.enqueue(pendingPost);
            // 判断此发布器是否活跃，如果活跃就不执行，等待 Looper 调度上一个消息，重新进入发布处理
            if (!handlerActive) {
                // 将发布器设置为活跃状态
                handlerActive = true;
                // sendMessage
                // 划重点!!!
                // 此处没有使用 new Message()，而是使用了 obtainMessage()，该方法将从全局的消息对象池中复用旧的对象，这比直接创建要更高效
                if (!sendMessage(obtainMessage())) {
                    throw new EventBusException("Could not send handler message");
                }
            }
        }
    }

    /**
     * 处理消息
     * 该 Handle 的工作方式为：
     *
     */
    @Override
    public void handleMessage(Message msg) {
        boolean rescheduled = false;
        try {
            // 获取一个开始时间
            long started = SystemClock.uptimeMillis();
            // 死循环
            while (true) {
                // 取出队列中最前面的元素
                PendingPost pendingPost = queue.poll();
                // 接下来会进行两次校验操作
                // 判空 有可能队列中已经没有元素
                if (pendingPost == null) {
                    // 再次检查，这次是同步的
                    synchronized (this) {
                        // 继续取队头的元素
                        pendingPost = queue.poll();
                        if (pendingPost == null) {
                            // 还是为 null，将处理状态设置为不活跃，跳出循环
                            handlerActive = false;
                            return;
                        }
                    }
                }
                // 调用订阅者方法
                eventBus.invokeSubscriber(pendingPost);
                // 获得方法耗时
                long timeInMethod = SystemClock.uptimeMillis() - started;
                // 判断本次调用的方法耗时是否超过预设值
                if (timeInMethod >= maxMillisInsideHandleMessage) {
                    // 发送进行下一次处理的消息，为了不阻塞主线程，暂时交出主线程使用权，并且发布消息到Looper，等待下一次调度再次进行消息的发布操作
                    if (!sendMessage(obtainMessage())) {
                        throw new EventBusException("Could not send handler message");
                    }
                    // 设置为活跃状态
                    rescheduled = true;
                    return;
                }
            }
        } finally {
            // 更新 Handle 状态
            handlerActive = rescheduled;
        }
    }
}