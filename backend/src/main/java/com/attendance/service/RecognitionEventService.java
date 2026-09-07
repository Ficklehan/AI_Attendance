package com.attendance.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.attendance.dto.response.RecognitionEventDTO;
import com.attendance.dto.response.RecognitionEventPageDTO;
import com.attendance.dto.response.TaskProgressDTO;
import com.attendance.entity.TaskRecognitionEvent;
import com.attendance.mapper.TaskMapper;
import com.attendance.mapper.TaskRecognitionEventMapper;
import com.attendance.security.TaskAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class RecognitionEventService {

    private static final Logger log = LoggerFactory.getLogger(RecognitionEventService.class);

    public static final String TYPE_RUN_START = "run_start";
    public static final String TYPE_RECORD = "record";
    public static final String TYPE_PAGE_DONE = "page_done";
    public static final String TYPE_STATUS = "status";
    public static final String TYPE_ERROR = "error";
    public static final String TYPE_COMPLETE = "complete";

    public static final int DEFAULT_LIMIT = 100;
    public static final int MAX_LIMIT = 200;
    public static final long DEFAULT_WAIT_MS = 25000L;
    public static final long MAX_WAIT_MS = 28000L;
    public static final long SSE_TIMEOUT_MS = 1_800_000L;
    public static final long HEARTBEAT_MS = 15_000L;

    @Autowired
    private TaskRecognitionEventMapper eventMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TaskAccessService taskAccessService;

    @Autowired
    private RecognitionStreamHub streamHub;

    private final ConcurrentHashMap<String, Object> taskLocks = new ConcurrentHashMap<String, Object>();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "recognition-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public RecognitionEventDTO publishRecord(String taskId, JSONObject record, Integer imageIndex) {
        return publish(taskId, TYPE_RECORD, record, imageIndex);
    }

    public void beginRun(String taskId, List<JSONObject> baseline) {
        if (taskId == null || taskId.trim().isEmpty()) {
            return;
        }
        deleteByTaskId(taskId);
        JSONObject start = new JSONObject();
        start.put("status", "processing");
        if (baseline != null && !baseline.isEmpty()) {
            start.put("records", baseline);
        }
        publish(taskId, TYPE_RUN_START, start, null);
    }

    public RecognitionEventDTO publishPageDone(String taskId, Integer imageIndex, int recordCount) {
        return publishPageDone(taskId, imageIndex, recordCount, null);
    }

    public RecognitionEventDTO publishPageDone(String taskId, Integer imageIndex, int recordCount,
                                               List<JSONObject> records) {
        JSONObject payload = new JSONObject();
        payload.put("imageIndex", imageIndex);
        payload.put("recordCount", recordCount);
        if (records != null) {
            payload.put("records", records);
        }
        return publish(taskId, TYPE_PAGE_DONE, payload, imageIndex);
    }

    public RecognitionEventDTO publishStatus(String taskId, String status) {
        JSONObject payload = new JSONObject();
        payload.put("status", status);
        return publish(taskId, TYPE_STATUS, payload, null);
    }

    public RecognitionEventDTO publishError(String taskId, String message, Object errorArgs) {
        JSONObject payload = new JSONObject();
        payload.put("message", message);
        if (errorArgs != null) {
            payload.put("errorArgs", errorArgs);
        }
        return publish(taskId, TYPE_ERROR, payload, null);
    }

    public RecognitionEventDTO publishComplete(String taskId, int recordCount) {
        return publishComplete(taskId, recordCount, null);
    }

    public RecognitionEventDTO publishComplete(String taskId, int recordCount, Object records) {
        JSONObject payload = new JSONObject();
        payload.put("recordCount", recordCount);
        payload.put("status", "processed");
        if (records != null) {
            payload.put("records", records);
        }
        return publish(taskId, TYPE_COMPLETE, payload, null);
    }

    @Transactional
    public RecognitionEventDTO publish(String taskId, String eventType, Object payload, Integer imageIndex) {
        if (taskId == null || taskId.trim().isEmpty() || eventType == null) {
            return null;
        }
        RecognitionEventDTO dto;
        synchronized (lockFor(taskId)) {
            dto = insertEvent(taskId, eventType, payload, imageIndex);
        }
        if (dto == null) {
            return null;
        }
        notifyAfterCommit(taskId, dto);
        return dto;
    }

    @Transactional
    public void deleteByTaskId(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) {
            return;
        }
        synchronized (lockFor(taskId)) {
            eventMapper.deleteByTaskId(taskId);
        }
    }

    public RecognitionEventPageDTO listEventsForCurrentUser(String taskId, int afterSeq, int limit) {
        taskAccessService.requireTaskAccessForProgress(taskId);
        return listAfter(taskId, afterSeq, limit);
    }

    public RecognitionEventPageDTO waitEventsForCurrentUser(String taskId, int afterSeq, long timeoutMs) {
        taskAccessService.requireTaskAccessForProgress(taskId);
        RecognitionEventPageDTO first = listAfter(taskId, afterSeq, DEFAULT_LIMIT);
        if (!first.getEvents().isEmpty() || isTerminal(first.getStatus())) {
            return first;
        }
        long wait = timeoutMs;
        if (wait <= 0L) {
            wait = DEFAULT_WAIT_MS;
        }
        if (wait > MAX_WAIT_MS) {
            wait = MAX_WAIT_MS;
        }
        streamHub.await(taskId, afterSeq, wait);
        return listAfter(taskId, afterSeq, DEFAULT_LIMIT);
    }

    public SseEmitter streamEventsForCurrentUser(String taskId, int afterSeq) {
        taskAccessService.requireTaskAccessForProgress(taskId);
        SseEmitter emitter = new SseEmitter(Long.valueOf(SSE_TIMEOUT_MS));
        attachSse(emitter, taskId, afterSeq);
        return emitter;
    }

    public RecognitionEventPageDTO listAfter(String taskId, int afterSeq, int limit) {
        int pageLimit = limit;
        if (pageLimit <= 0) {
            pageLimit = DEFAULT_LIMIT;
        }
        if (pageLimit > MAX_LIMIT) {
            pageLimit = MAX_LIMIT;
        }
        int fetch = pageLimit + 1;
        List<TaskRecognitionEvent> rows = eventMapper.selectAfterSeq(taskId, Math.max(0, afterSeq), fetch);
        boolean hasMore = rows.size() > pageLimit;
        if (hasMore) {
            rows = new ArrayList<TaskRecognitionEvent>(rows.subList(0, pageLimit));
        }
        List<RecognitionEventDTO> events = new ArrayList<RecognitionEventDTO>(rows.size());
        for (TaskRecognitionEvent row : rows) {
            events.add(toDto(row));
        }
        Integer max = eventMapper.selectMaxSeq(taskId);
        RecognitionEventPageDTO page = new RecognitionEventPageDTO();
        page.setTaskId(taskId);
        page.setEvents(events);
        page.setHasMore(hasMore);
        page.setServerSeq(max == null ? 0 : max);
        TaskProgressDTO progress = taskMapper.selectTaskProgress(taskId);
        page.setStatus(progress != null ? progress.getStatus() : null);
        return page;
    }

    private void attachSse(final SseEmitter emitter, final String taskId, int afterSeq) {
        final int[] cursor = new int[] { Math.max(0, afterSeq) };
        sendCatchUp(emitter, taskId, cursor);
        RecognitionStreamHub.StreamListener listener = new RecognitionStreamHub.StreamListener() {
            @Override
            public void onEvent(RecognitionEventDTO event) {
                if (event == null || event.getSeq() <= cursor[0]) {
                    return;
                }
                cursor[0] = event.getSeq();
                sendSse(emitter, event);
                if (TYPE_COMPLETE.equals(event.getEventType()) || TYPE_ERROR.equals(event.getEventType())) {
                    safeComplete(emitter);
                }
            }
        };
        final RecognitionStreamHub.Subscription subscription = streamHub.subscribe(taskId, listener);
        sendCatchUp(emitter, taskId, cursor);
        final ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    emitter.send(SseEmitter.event().name("heartbeat").data("{}", MediaType.APPLICATION_JSON));
                } catch (Exception e) {
                    safeComplete(emitter);
                }
            }
        }, HEARTBEAT_MS, HEARTBEAT_MS, TimeUnit.MILLISECONDS);
        Runnable cleanup = new Runnable() {
            @Override
            public void run() {
                subscription.close();
                heartbeat.cancel(false);
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(new java.util.function.Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) {
                cleanup.run();
            }
        });
    }

    private void notifyAfterCommit(final String taskId, final RecognitionEventDTO dto) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    streamHub.notifyEvent(taskId, dto);
                }
            });
        } else {
            streamHub.notifyEvent(taskId, dto);
        }
    }

    private RecognitionEventDTO insertEvent(String taskId, String eventType, Object payload, Integer imageIndex) {
        int attempts = 0;
        while (attempts < 3) {
            attempts++;
            Integer max = eventMapper.selectMaxSeq(taskId);
            int nextSeq = max == null ? 1 : max + 1;
            TaskRecognitionEvent row = new TaskRecognitionEvent();
            row.setTaskId(taskId);
            row.setSeq(Integer.valueOf(nextSeq));
            row.setEventType(eventType);
            row.setPayload(payload == null ? null : JSON.toJSONString(payload));
            row.setImageIndex(imageIndex);
            row.setCreatedAt(java.time.LocalDateTime.now());
            try {
                eventMapper.insert(row);
                return toDto(row);
            } catch (Exception e) {
                log.warn("写入识别事件失败，重试: taskId={}, seq={}, type={}, attempt={}",
                        taskId, nextSeq, eventType, attempts, e);
            }
        }
        return null;
    }

    private Object lockFor(String taskId) {
        Object created = new Object();
        Object existing = taskLocks.putIfAbsent(taskId, created);
        return existing != null ? existing : created;
    }

    private static boolean isTerminal(String status) {
        return "processed".equals(status) || "failed".equals(status) || "cancelled".equals(status)
                || "confirmed".equals(status);
    }

    private RecognitionEventDTO toDto(TaskRecognitionEvent row) {
        RecognitionEventDTO dto = new RecognitionEventDTO();
        dto.setSeq(row.getSeq() != null ? row.getSeq().intValue() : 0);
        dto.setEventType(row.getEventType());
        dto.setImageIndex(row.getImageIndex());
        dto.setCreatedAt(row.getCreatedAt());
        if (row.getPayload() != null && !row.getPayload().trim().isEmpty()) {
            try {
                dto.setPayload(JSON.parse(row.getPayload()));
            } catch (Exception e) {
                dto.setPayload(row.getPayload());
            }
        }
        return dto;
    }

    private void sendCatchUp(SseEmitter emitter, String taskId, int[] cursor) {
        while (true) {
            RecognitionEventPageDTO page = listAfter(taskId, cursor[0], DEFAULT_LIMIT);
            if (page.getEvents().isEmpty()) {
                return;
            }
            for (RecognitionEventDTO event : page.getEvents()) {
                if (event.getSeq() <= cursor[0]) {
                    continue;
                }
                cursor[0] = event.getSeq();
                sendSse(emitter, event);
                if (TYPE_COMPLETE.equals(event.getEventType()) || TYPE_ERROR.equals(event.getEventType())) {
                    safeComplete(emitter);
                    return;
                }
            }
            if (!page.isHasMore()) {
                return;
            }
        }
    }

    private void sendSse(SseEmitter emitter, RecognitionEventDTO event) {
        try {
            String name = event.getEventType() != null ? event.getEventType() : "message";
            emitter.send(SseEmitter.event().name(name).data(event, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            safeComplete(emitter);
        }
    }

    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // already completed
        }
    }
}
