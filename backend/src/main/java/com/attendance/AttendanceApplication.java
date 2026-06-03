package com.attendance;

import io.github.cdimascio.dotenv.Dotenv;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

@SpringBootApplication
@EnableAsync
@MapperScan("com.attendance.mapper")
public class AttendanceApplication {

    public static void main(String[] args) {
        loadDotenv();
        SpringApplication.run(AttendanceApplication.class, args);
    }

    private static void loadDotenv() {
        String userDir = System.getProperty("user.dir");
        List<String> candidates = Arrays.asList(
            userDir,
            Paths.get(userDir, "backend").toString(),
            Paths.get(userDir, "..", "backend").toString()
        );
        for (String dir : candidates) {
            if (Files.exists(Paths.get(dir, ".env"))) {
                Dotenv dotenv = Dotenv.configure()
                    .directory(dir)
                    .load();
                dotenv.entries().forEach(e -> System.setProperty(e.getKey(), e.getValue()));
                return;
            }
        }
        // Fallback: let Spring resolve from OS env / YAML defaults
        Dotenv.configure().directory(".").ignoreIfMissing().load();
    }
}
