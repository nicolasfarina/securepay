package com.securepay.logging;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import io.opentelemetry.context.Context;

import java.util.Map;

/** Copies submitting-thread MDC and OpenTelemetry context, then restores the worker afterward. */
public class MdcTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable task) {
        Map<String, String> submittedContext = MDC.getCopyOfContextMap();
        Context submittedTraceContext = Context.current();
        return () -> {
            Map<String, String> workerContext = MDC.getCopyOfContextMap();
            restore(submittedContext);
            try (var ignored = submittedTraceContext.makeCurrent()) {
                task.run();
            } finally {
                restore(workerContext);
            }
        };
    }

    private static void restore(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
