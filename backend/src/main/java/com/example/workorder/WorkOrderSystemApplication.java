package com.example.workorder;

import com.example.workorder.async.AsyncMessagingProperties;
import com.example.workorder.workorder.WorkOrderAttachmentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({WorkOrderAttachmentProperties.class, AsyncMessagingProperties.class})
public class WorkOrderSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkOrderSystemApplication.class, args);
    }
}
