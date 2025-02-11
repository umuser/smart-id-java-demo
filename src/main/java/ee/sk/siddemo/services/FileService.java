package ee.sk.siddemo.services;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FileService {

    @Value("${app.signed-files-directory}")
    private String signedFilesDirectory;

    public String createPath() {
        File containerFile = null;
        try {
            containerFile = File.createTempFile("sid-demo-container-", ".asice");
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temporary file", e);
        }
        Path targetPath = createSavePath(containerFile);
        return targetPath.toString();
    }

    private Path createSavePath(File containerFile) {
        Path targetDir = Paths.get(signedFilesDirectory);
        File directory = targetDir.toFile();
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return targetDir.resolve(containerFile.getName());
    }
}
