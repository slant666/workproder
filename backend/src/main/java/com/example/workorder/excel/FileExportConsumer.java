package com.example.workorder.excel;

import com.example.workorder.async.AsyncRabbitConfig;
import com.example.workorder.async.FileExportMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.async", name = "rabbit-enabled", havingValue = "true", matchIfMissing = true)
public class FileExportConsumer {

    private final ExcelService excelService;

    public FileExportConsumer(ExcelService excelService) {
        this.excelService = excelService;
    }

    @RabbitListener(queues = AsyncRabbitConfig.FILE_EXPORT_QUEUE)
    public void consume(FileExportMessage message) {
        if (message == null || message.jobId() == null) {
            return;
        }
        excelService.processWorkOrderExportJob(message.jobId());
    }
}
