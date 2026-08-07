package com.example.workorder;

import com.example.workorder.workorder.WorkOrderAttachmentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(WorkOrderAttachmentProperties.class)
public class WorkOrderSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkOrderSystemApplication.class, args);
    }
}
