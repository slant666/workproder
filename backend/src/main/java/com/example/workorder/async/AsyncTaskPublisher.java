package com.example.workorder.async;

public interface AsyncTaskPublisher {

    boolean publishFileExport(Long jobId);

    boolean publishEmailSend(Long emailOutboxId);
}
