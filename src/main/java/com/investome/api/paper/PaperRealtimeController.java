package com.investome.api.paper;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/paper/realtime")
public class PaperRealtimeController {
    private final PaperRealtimeService realtime;
    @GetMapping(value = "/stream", produces = "text/event-stream")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter stream(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        return realtime.stream();
    }
    @PostMapping("/start")
    public PaperRealtimeService.Snapshot start() { return realtime.start(); }
    @GetMapping
    public PaperRealtimeService.Snapshot snapshot() { return realtime.snapshot(); }
}
