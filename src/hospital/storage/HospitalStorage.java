package hospital.storage;

import hospital.model.Patient;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * All file handling uses Java NIO: Path, Paths, Files, DirectoryStream and StandardOpenOption.
 */
public class HospitalStorage {

    private final Path dir;
    private final Path pendingFile;
    private final Path treatedFile;
    private final Path logFile;
    private final Path reportFile;
    private final Path backupDir;
    private final Path intakeDir;

    public HospitalStorage(String directory) {
        this.dir = Paths.get(directory);
        this.pendingFile = dir.resolve("waiting.csv");
        this.treatedFile = dir.resolve("treated.csv");
        this.logFile = dir.resolve("activity.log");
        this.reportFile = dir.resolve("report.txt");
        this.backupDir = dir.resolve("backup");
        this.intakeDir = dir.resolve("intake");
    }

    public synchronized void save(List<Patient> pending, List<Patient> treated) throws IOException {
        Files.createDirectories(dir);
        Files.write(pendingFile, toLines(pending), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.write(treatedFile, toLines(treated), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        log("Saved " + pending.size() + " pending and " + treated.size() + " treated patients");
    }

    private List<String> toLines(List<Patient> patients) {
        return patients.stream().map(Patient::toCsv).collect(Collectors.toList());
    }

    public synchronized List<String> readSavedPending() throws IOException {
        if (Files.notExists(pendingFile)) {
            return Collections.emptyList();
        }
        return Files.readAllLines(pendingFile, StandardCharsets.UTF_8);
    }

    /** Appends a timestamped line to the activity log. */
    public synchronized void log(String message) {
        try {
            Files.createDirectories(dir);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            Files.writeString(logFile, stamp + "  " + message + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Could not write log: " + e.getMessage());
        }
    }

    public synchronized List<String> readLog(int lastN) throws IOException {
        if (Files.notExists(logFile)) {
            return Collections.emptyList();
        }
        List<String> all = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        return all.subList(Math.max(0, all.size() - lastN), all.size());
    }

    public synchronized Path writeReport(List<String> lines) throws IOException {
        Files.createDirectories(dir);
        return Files.write(reportFile, lines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /** Copies every data file into a timestamped backup folder. */
    public synchronized Path backup() throws IOException {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path target = backupDir.resolve(stamp);
        Files.createDirectories(target);
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, p -> Files.isRegularFile(p))) {
            for (Path file : files) {
                Files.copy(file, target.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        log("Backup created: " + target);
        return target;
    }

    // ----- intake folder: CSV files dropped here can be imported in bulk -----

    /** On first run, creates the intake folder with a sample file (includes two bad rows on purpose). */
    public synchronized void createSampleIntake() throws IOException {
        if (Files.exists(intakeDir)) {
            return;
        }
        Files.createDirectories(intakeDir);
        List<String> lines = List.of(
                "name,age,category,complaint",
                "Arun,45,C,Chest pain",
                "Bhavna,30,N,Mild fever",
                "Charan,60,U,Fracture",
                "Deepa,25,N,Cold and cough",
                "Eswar,52,C,Stroke symptoms",
                "Farah,35,U,High fever",
                "Suresh,200,N,Invalid age example",
                "Gita,abc,N,Not a number example");
        Files.write(intakeDir.resolve("sample_patients.csv"), lines, StandardCharsets.UTF_8);
    }

    public synchronized List<Path> listIntakeFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        if (Files.notExists(intakeDir)) {
            return files;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(intakeDir, "*.csv")) {
            for (Path p : stream) {
                files.add(p);
            }
        }
        Collections.sort(files);
        return files;
    }

    public synchronized List<String> readLines(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return reader.lines().collect(Collectors.toList());
        }
    }

    /** Moves a processed intake file into intake/processed/. */
    public synchronized void moveToProcessed(Path file) throws IOException {
        Path processed = intakeDir.resolve("processed");
        Files.createDirectories(processed);
        Files.move(file, processed.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
    }
}
