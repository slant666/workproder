package com.example.workorder.async;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(AsyncTaskPublisher.class)
public class NoopAsyncTaskPublisher implements AsyncTaskPublisher {

    @Override
    public boolean publishFileExport(Long jobId) {
        return false;
    }

    @Override
    public boolean publishEmailSend(Long emailOutboxId) {
        return false;
    }
}
