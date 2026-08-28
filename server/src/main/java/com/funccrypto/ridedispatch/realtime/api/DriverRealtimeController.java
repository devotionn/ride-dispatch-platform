package com.funccrypto.ridedispatch.realtime.api;

import com.funccrypto.ridedispatch.auth.AuthenticatedPrincipal;
import com.funccrypto.ridedispatch.realtime.RealtimeEventHub;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/driver")
public class DriverRealtimeController {

    private final RealtimeEventHub eventHub;

    public DriverRealtimeController(RealtimeEventHub eventHub) {
        this.eventHub = eventHub;
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(Authentication authentication) {
        Long driverId = ((AuthenticatedPrincipal) authentication.getPrincipal()).principalId();
        return eventHub.subscribe(driverId);
    }
}
