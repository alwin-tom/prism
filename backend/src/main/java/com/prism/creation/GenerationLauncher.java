package com.prism.creation;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.Executor;

@Component
public class GenerationLauncher {

    private final GenerationPipeline pipeline;
    private final Executor executor;

    public GenerationLauncher(GenerationPipeline pipeline, @Qualifier("creationExecutor") Executor executor) {
        this.pipeline = pipeline;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void launch(GenerationRequested event) {
        executor.execute(() -> pipeline.run(event.jobId()));
    }
}
