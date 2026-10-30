package hospital.model;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Abstract parent of CriticalPatient, UrgentPatient and NormalPatient.
 * Implements Treatable and Comparable so patients sort by priority, then by arrival.
 */
public abstract class Patient implements Treatable, Comparable<Patient> {

    private static final AtomicLong TICKETS = new AtomicLong();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final int id;
    private final String name;
    private final int age;
    private final String complaint;
    private final long ticket = TICKETS.incrementAndGet();      // arrival order
    private final long arrivalMillis = System.currentTimeMillis();
    private final LocalTime arrivalTime = LocalTime.now();

    private PatientStatus status = PatientStatus.WAITING;
    private String treatedBy = "-";
    private long waitedMs;

    protected Patient(int id, String name, int age, String complaint) {
        this.id = id;
        this.name = name.replace(",", " ");
        this.age = age;
        this.complaint = complaint.replace(",", " ");
    }

    // ----- methods that subclasses OVERRIDE -----
    public abstract String getCategory();

    public abstract String getCode();

    /** Lower number = more urgent. */
    public abstract int getPriority();

    public abstract long getTreatmentTimeMs();

    public String describe() {
        return String.format("#%d [%-8s] %-12s age %3d  %-20s arrived %s  %s",
                id, getCategory(), name, age, complaint, arrivalTime.format(TIME), getStatus());
    }

    // ----- Treatable implementation -----
    @Override
    public void treat(String doctorName, long millis) throws InterruptedException {
        markInTreatment(doctorName);
        Thread.sleep(millis);            // the actual "treatment"
        markDischarged();
    }

    @Override
    public synchronized PatientStatus getStatus() {
        return status;
    }

    private synchronized void markInTreatment(String doctorName) {
        status = PatientStatus.IN_TREATMENT;
        treatedBy = doctorName;
        waitedMs = System.currentTimeMillis() - arrivalMillis;
    }

    private synchronized void markDischarged() {
        status = PatientStatus.DISCHARGED;
    }

    /** Used when a doctor is interrupted mid-treatment. */
    public synchronized void markWaiting() {
        status = PatientStatus.WAITING;
        treatedBy = "-";
    }

    // ----- Comparable: priority first, then first-come-first-served -----
    @Override
    public int compareTo(Patient other) {
        int byPriority = Integer.compare(this.getPriority(), other.getPriority());
        return byPriority != 0 ? byPriority : Long.compare(this.ticket, other.ticket);
    }

    public int getId() { return id; }

    public String getName() { return name; }

    public int getAge() { return age; }

    public String getComplaint() { return complaint; }

    public long getTicket() { return ticket; }

    public synchronized String getTreatedBy() { return treatedBy; }

    public synchronized long getWaitedMs() { return waitedMs; }

    public String toCsv() {
        return String.join(",", getCode(), String.valueOf(id), name, String.valueOf(age),
                complaint, getStatus().name(), getTreatedBy());
    }
}
