package com.example.project.batch;

import com.example.project.batch.config.BatchConfig;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class BatchApplication {
    private BatchApplication() {
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(BatchConfig.class)) {
            context.registerShutdownHook();
        }
    }
}
