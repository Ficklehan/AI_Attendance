package com.attendance.service;

import com.attendance.dto.response.RecognitionEventDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 进程内识别事件扇出：长轮询等待 + SSE 订阅。
 */
@Component
public class RecognitionStreamHub {

    public interface StreamListener {
        void onEvent(RecognitionEventDTO event);
    }

    public static final class Subscription {
        private final RecognitionStreamHub hub;
        private final String taskId;
        private final StreamListener listener;

        Subscription(RecognitionStreamHub hub, String taskId, StreamListener listener) {
            this.hub = hub;
            this.taskId = taskId;
            this.listener = listener;
        }

        public void close() {
            hub.unsubscribe(taskId, listener);
        }
    }

    private static final class Waiter {
        private final int afterSeq;
        private final CountDownLatch latch;

        private Waiter(int afterSeq, CountDownLatch latch) {
            this.afterSeq = afterSeq;
            this.latch = latch;
        }
    }

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<StreamListener>> listeners =
            new ConcurrentHashMap<String, CopyOnWriteArrayList<StreamListener>>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Waiter>> waiters =
            new ConcurrentHashMap<String, CopyOnWriteArrayList<Waiter>>();

    public Subscription subscribe(String taskId, StreamListener listener) {
        listeners.putIfAbsent(taskId, new CopyOnWriteArrayList<StreamListener>());
        listeners.get(taskId).add(listener);
        return new Subscription(this, taskId, listener);
    }

    public void unsubscribe(String taskId, StreamListener listener) {
        CopyOnWriteArrayList<StreamListener> list = listeners.get(taskId);
        if (list != null) {
            list.remove(listener);
            if (list.isEmpty()) {
                listeners.remove(taskId, list);
            }
        }
    }

    public boolean await(String taskId, int afterSeq, long timeoutMs) {
        Waiter waiter = new Waiter(afterSeq, new CountDownLatch(1));
        waiters.putIfAbsent(taskId, new CopyOnWriteArrayList<Waiter>());
        waiters.get(taskId).add(waiter);
        try {
            return waiter.latch.await(Math.max(0L, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            CopyOnWriteArrayList<Waiter> list = waiters.get(taskId);
            if (list != null) {
                list.remove(waiter);
                if (list.isEmpty()) {
                    waiters.remove(taskId, list);
                }
            }
        }
    }

    public void notifyEvent(String taskId, RecognitionEventDTO event) {
        if (taskId == null || event == null) {
            return;
        }
        CopyOnWriteArrayList<Waiter> waiterList = waiters.get(taskId);
        if (waiterList != null) {
            for (Waiter waiter : waiterList) {
                if (event.getSeq() > waiter.afterSeq) {
                    waiter.latch.countDown();
                }
            }
        }
        CopyOnWriteArrayList<StreamListener> listenerList = listeners.get(taskId);
        if (listenerList != null) {
            for (StreamListener listener : listenerList) {
                try {
                    listener.onEvent(event);
                } catch (Exception ignored) {
                    // 单个订阅者失败不影响其他端
                }
            }
        }
    }
}
