package io.github.tachikomako.xhsknowledge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
public class XhsKnowledgeApplication {

    public static void main(String[] args) {
        prepareDataDirectory();
        SpringApplication.run(XhsKnowledgeApplication.class, args);
    }

    private static void prepareDataDirectory() {
        String configuredPath = System.getenv().getOrDefault("XHS_DB_PATH", "./data/xhs-knowledge.db");
        Path parent = Path.of(configuredPath).toAbsolutePath().normalize().getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create SQLite data directory: " + parent, exception);
        }
    }
}
