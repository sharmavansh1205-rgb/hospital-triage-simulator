package hospital.staff;

import hospital.model.Patient;
import hospital.service.EmergencyRoom;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A doctor is a Runnable: each doctor works on its own thread, repeatedly taking the
 * most urgent patient from the shared priority queue.
 */
public abstract class Doctor extends Staff implements Runnable {

    private final EmergencyRoom room;
    private final AtomicInteger treatedCount = new AtomicInteger();

    protected Doctor(String name, EmergencyRoom room) {
        super(name);
        this.room = room;
    }

    /** Multiplier applied to the base treatment time; overridden by each doctor type. */
    public abstract double getTimeFactor(Patient patient);

    @Override
    public void run() {
        Thread.currentThread().setName(getName());
        room.log(describe() + " started shift");

        while (room.isOpen()) {
            Patient patient;
            try {
                patient = room.nextPatient();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (patient == null) {
                continue; // nobody waiting, check again
            }

            long time = (long) (patient.getTreatmentTimeMs() * getTimeFactor(patient));
            room.log(getName() + " is treating #" + patient.getId() + " " + patient.getName()
                    + " [" + patient.getCategory() + "]");
            try {
                patient.treat(getName(), time);
                treatedCount.incrementAndGet();
                room.recordTreated(patient);
                room.log(getName() + " discharged #" + patient.getId() + " " + patient.getName());
            } catch (InterruptedException e) {
                patient.markWaiting();
                room.requeue(patient);
                Thread.currentThread().interrupt();
                break;
            }
        }
        room.log(getName() + " ended shift (" + treatedCount.get() + " patients treated)");
    }

    public int getTreatedCount() { return treatedCount.get(); }
}
