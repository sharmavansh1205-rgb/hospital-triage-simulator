package hospital.service;

import hospital.exception.HospitalException;

import java.util.Random;

/** A second kind of thread: simulates patients arriving at random while doctors are working. */
public class PatientGenerator implements Runnable {

    private static final String[] NAMES = {"Arjun", "Priya", "Kiran", "Sneha", "Ravi", "Anita",
            "Vikram", "Divya", "Rohit", "Lakshmi", "Imran", "Pooja"};
    private static final String[] CRITICAL = {"Chest pain", "Severe bleeding", "Stroke symptoms", "Breathing failure"};
    private static final String[] URGENT = {"Fracture", "High fever", "Deep cut", "Asthma attack"};
    private static final String[] NORMAL = {"Cold and cough", "Mild headache", "Sprained ankle", "Stomach ache", "Skin rash"};

    private final EmergencyRoom room;
    private final int count;
    private final long delayMs;
    private final Random random = new Random();

    public PatientGenerator(EmergencyRoom room, int count, long delayMs) {
        this.room = room;
        this.count = count;
        this.delayMs = delayMs;
    }

    @Override
    public void run() {
        for (int i = 0; i < count; i++) {
            int roll = random.nextInt(10);          // 20% critical, 30% urgent, 50% normal
            String code;
            String[] complaints;
            if (roll < 2) {
                code = "C";
                complaints = CRITICAL;
            } else if (roll < 5) {
                code = "U";
                complaints = URGENT;
            } else {
                code = "N";
                complaints = NORMAL;
            }
            try {
                room.admit(NAMES[i % NAMES.length], 18 + random.nextInt(60), code,
                        complaints[random.nextInt(complaints.length)]);
                Thread.sleep(delayMs);
            } catch (HospitalException e) {
                System.out.println("Arrival failed: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
