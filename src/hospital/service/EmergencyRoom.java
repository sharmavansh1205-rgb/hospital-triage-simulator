package hospital.service;

import hospital.exception.*;
import hospital.model.*;
import hospital.staff.Doctor;
import hospital.staff.GeneralPhysician;
import hospital.staff.Specialist;
import hospital.storage.HospitalStorage;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Core of the system. Thread-safe collections:
 *  - PriorityBlockingQueue : waiting patients, most urgent first
 *  - ConcurrentHashMap     : registry of every patient (lookup by id)
 *  - CopyOnWriteArrayList  : discharged patients in completion order
 */
public class EmergencyRoom {

    private static final int CAPACITY = 50;
    private static final String[] DOCTOR_NAMES = {"Dr. Rao", "Dr. Mehta", "Dr. Iyer", "Dr. Khan", "Dr. Sen"};
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final PriorityBlockingQueue<Patient> waiting = new PriorityBlockingQueue<>();
    private final Map<Integer, Patient> registry = new ConcurrentHashMap<>();
    private final List<Patient> treated = new CopyOnWriteArrayList<>();
    private final List<Doctor> doctors = new ArrayList<>();

    private final AtomicInteger idGenerator = new AtomicInteger(1000);
    private final AtomicInteger pending = new AtomicInteger();   // admitted but not yet discharged
    private final HospitalStorage storage;

    private ExecutorService pool;
    private volatile boolean open;

    public EmergencyRoom(HospitalStorage storage) {
        this.storage = storage;
    }

    // ================= admitting patients =================

    public synchronized Patient admit(String name, int age, String code, String complaint)
            throws HospitalException {
        validate(name, age, code, complaint);
        if (waiting.size() >= CAPACITY) {
            throw new QueueFullException("Waiting room is full (" + CAPACITY + " patients)");
        }
        Patient patient = create(idGenerator.getAndIncrement(), name.trim(), age, code, complaint.trim());
        register(patient);
        log("Admitted #" + patient.getId() + " " + patient.getName() + " [" + patient.getCategory() + "]");
        return patient;
    }

    private void validate(String name, int age, String code, String complaint)
            throws InvalidPatientDataException {
        if (name == null || name.isBlank()) {
            throw new InvalidPatientDataException("Name cannot be empty");
        }
        if (age < 0 || age > 120) {
            throw new InvalidPatientDataException("Age must be between 0 and 120 (got " + age + ")");
        }
        if (code == null || !code.matches("(?i)[CUN]")) {
            throw new InvalidPatientDataException("Category must be C, U or N (got '" + code + "')");
        }
        if (complaint == null || complaint.isBlank()) {
            throw new InvalidPatientDataException("Complaint cannot be empty");
        }
    }

    private Patient create(int id, String name, int age, String code, String complaint) {
        return switch (code.toUpperCase()) {
            case "C" -> new CriticalPatient(id, name, age, complaint);
            case "U" -> new UrgentPatient(id, name, age, complaint);
            default -> new NormalPatient(id, name, age, complaint);
        };
    }

    private void register(Patient patient) {
        registry.put(patient.getId(), patient);
        waiting.put(patient);
        pending.incrementAndGet();
    }

    // ================= doctors (threads) =================

    public synchronized void start(int doctorCount) throws ErStateException {
        if (open) {
            throw new ErStateException("The ER is already open");
        }
        if (doctorCount < 1 || doctorCount > DOCTOR_NAMES.length) {
            throw new ErStateException("Number of doctors must be between 1 and " + DOCTOR_NAMES.length);
        }
        doctors.clear();
        pool = Executors.newFixedThreadPool(doctorCount);
        open = true;
        for (int i = 0; i < doctorCount; i++) {
            Doctor doctor = (i % 2 == 0)
                    ? new GeneralPhysician(DOCTOR_NAMES[i], this)
                    : new Specialist(DOCTOR_NAMES[i], this);
            doctors.add(doctor);
            pool.execute(doctor);
        }
        log("ER opened with " + doctorCount + " doctor(s)");
    }

    public synchronized void stop() throws ErStateException, InterruptedException {
        if (!open) {
            throw new ErStateException("The ER is not open");
        }
        open = false;
        pool.shutdown();
        if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
            pool.shutdownNow();
        }
        log("ER closed. " + pending.get() + " patient(s) still waiting");
    }

    public boolean isOpen() { return open; }

    /** Called by doctor threads: blocks up to 300 ms waiting for the next most urgent patient. */
    public Patient nextPatient() throws InterruptedException {
        return waiting.poll(300, TimeUnit.MILLISECONDS);
    }

    public void recordTreated(Patient patient) {
        treated.add(patient);
        pending.decrementAndGet();
    }

    public void requeue(Patient patient) {
        waiting.put(patient);
    }

    /** Waits until every admitted patient has been discharged (or the timeout passes). */
    public boolean awaitAllTreated(long timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (pending.get() > 0 && System.currentTimeMillis() < end) {
            Thread.sleep(100);
        }
        return pending.get() == 0;
    }

    public void log(String message) {
        System.out.println("   [" + LocalTime.now().format(TIME) + "] " + message);
        storage.log(message);
    }

    // ================= queries =================

    /** Snapshot of the waiting queue, sorted most urgent first. */
    public List<Patient> getWaitingSnapshot() {
        List<Patient> list = new ArrayList<>(waiting);
        Collections.sort(list);
        return list;
    }

    public List<Patient> getTreated() { return new ArrayList<>(treated); }

    public synchronized List<Doctor> getDoctors() { return new ArrayList<>(doctors); }

    public int getPendingCount() { return pending.get(); }

    public List<Patient> search(String keyword) {
        String k = keyword.trim().toLowerCase();
        return registry.values().stream()
                .filter(p -> String.valueOf(p.getId()).equals(k) || p.getName().toLowerCase().contains(k))
                .sorted(Comparator.comparingInt(Patient::getId))
                .collect(Collectors.toList());
    }

    // ================= persistence =================

    public void save() throws IOException {
        List<Patient> unfinished = registry.values().stream()
                .filter(p -> !p.isDischarged())
                .sorted(Comparator.comparingLong(Patient::getTicket))
                .collect(Collectors.toList());
        storage.save(unfinished, getTreated());
    }

    /** Reloads patients that were still waiting when the program was last closed. */
    public int loadSaved() throws IOException {
        int restored = 0;
        for (String line : storage.readSavedPending()) {
            String[] f = line.split(",", 7);
            if (f.length < 5) continue;
            try {
                int id = Integer.parseInt(f[1]);
                validate(f[2], Integer.parseInt(f[3]), f[0], f[4]);
                register(create(id, f[2], Integer.parseInt(f[3]), f[0], f[4]));
                idGenerator.accumulateAndGet(id + 1, Math::max);
                restored++;
            } catch (NumberFormatException | InvalidPatientDataException e) {
                System.out.println("Skipped corrupt saved record: " + line);
            }
        }
        return restored;
    }

    /** Imports every CSV file in the intake folder; bad rows are reported, not fatal. */
    public List<String> importFromIntake() throws IOException {
        List<String> report = new ArrayList<>();
        List<Path> files = storage.listIntakeFiles();
        if (files.isEmpty()) {
            report.add("No CSV files found in the intake folder.");
        }
        for (Path file : files) {
            int ok = 0;
            int bad = 0;
            for (String line : storage.readLines(file)) {
                if (line.isBlank() || line.toLowerCase().startsWith("name")) continue;
                String[] f = line.split(",", 4);
                try {
                    if (f.length < 4) {
                        throw new InvalidPatientDataException("Expected 4 columns in: " + line);
                    }
                    admit(f[0].trim(), Integer.parseInt(f[1].trim()), f[2].trim(), f[3].trim());
                    ok++;
                } catch (NumberFormatException e) {
                    bad++;
                    report.add("  Skipped (age is not a number): " + line);
                } catch (HospitalException e) {
                    bad++;
                    report.add("  Skipped (" + e.getMessage() + ")");
                }
            }
            storage.moveToProcessed(file);
            report.add(file.getFileName() + ": " + ok + " admitted, " + bad + " skipped (file moved to intake/processed)");
        }
        return report;
    }

    // ================= statistics (streams + collectors) =================

    public List<String> buildReport() {
        List<String> r = new ArrayList<>();
        List<Patient> done = getTreated();
        r.add("===== EMERGENCY ROOM REPORT =====");
        r.add("Patients registered this session : " + registry.size());
        r.add("Treated                          : " + done.size());
        r.add("Still waiting                    : " + pending.get());

        Map<String, Long> countByCategory = done.stream().collect(
                Collectors.groupingBy(Patient::getCategory, TreeMap::new, Collectors.counting()));
        r.add("Treated by category              : " + countByCategory);

        Map<String, Double> avgWait = done.stream().collect(
                Collectors.groupingBy(Patient::getCategory, TreeMap::new,
                        Collectors.averagingLong(Patient::getWaitedMs)));
        avgWait.forEach((cat, ms) -> r.add(String.format("Average wait (%-8s)          : %.1f s", cat, ms / 1000.0)));

        done.stream().max(Comparator.comparingLong(Patient::getWaitedMs)).ifPresent(p ->
                r.add(String.format("Longest wait                    : %s (%.1f s)", p.getName(), p.getWaitedMs() / 1000.0)));

        r.add("Doctors:");
        for (Doctor d : getDoctors()) {
            r.add("  " + d.describe() + " treated " + d.getTreatedCount() + " patient(s)");
        }
        return r;
    }
}
