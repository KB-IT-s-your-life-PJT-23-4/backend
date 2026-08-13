package com.example.project.common.logging;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

public class DeferredAccessLogContext {

    public static final String REQUEST_ATTRIBUTE =
            DeferredAccessLogContext.class.getName();

    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final BiConsumer<String, String> completionHandler;

    public DeferredAccessLogContext(
            BiConsumer<String, String> completionHandler
    ) {
        this.completionHandler = completionHandler;
    }

    public void complete(String result, String exceptionName) {
        if (completed.compareAndSet(false, true)) {
            completionHandler.accept(result, exceptionName);
        }
    }
}
