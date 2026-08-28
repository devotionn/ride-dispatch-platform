package com.funccrypto.ridedispatch.realtime;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class RealtimeEventHub {

    private final Map<Long, Set<SseEmitter>> driverEmitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long driverId) {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
        driverEmitters.computeIfAbsent(driverId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        Runnable cleanup = () -> remove(driverId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());
        try {
            emitter.send(SseEmitter.event()
                    .id(UUID.randomUUID().toString())
                    .name("CONNECTED")
                    .data(Map.of("driverId", driverId)));
        } catch (IOException exception) {
            cleanup.run();
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    public void publishAfterCommit(DriverRealtimeEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishNow(event);
                }
            });
        } else {
            publishNow(event);
        }
    }

    private void publishNow(DriverRealtimeEvent event) {
        Set<SseEmitter> emitters = driverEmitters.get(event.driverId());
        if (emitters == null || emitters.isEmpty()) return;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .id(UUID.randomUUID().toString())
                        .name(event.eventType())
                        .data(event));
            } catch (IOException exception) {
                remove(event.driverId(), emitter);
                emitter.completeWithError(exception);
            }
        }
    }

    private void remove(Long driverId, SseEmitter emitter) {
        Set<SseEmitter> emitters = driverEmitters.get(driverId);
        if (emitters == null) return;
        emitters.remove(emitter);
        if (emitters.isEmpty()) driverEmitters.remove(driverId, emitters);
    }
}
