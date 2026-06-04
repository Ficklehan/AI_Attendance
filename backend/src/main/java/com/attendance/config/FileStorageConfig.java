package com.attendance.config;

import com.attendance.storage.FileStorage;
import com.attendance.storage.LocalFileStorage;
import com.attendance.storage.OssFileStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FileStorageConfig {

    @Bean
    public FileStorage fileStorage(StorageProperties properties) {
        if (properties.isOss()) {
            return new OssFileStorage(properties);
        }
        return new LocalFileStorage(properties);
    }
}
