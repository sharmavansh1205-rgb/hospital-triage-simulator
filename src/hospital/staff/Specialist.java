package hospital.staff;

import hospital.model.Patient;
import hospital.service.EmergencyRoom;

/** Faster with critical cases, slower with routine ones. */
public class Specialist extends Doctor {

    public Specialist(String name, EmergencyRoom room) {
        super(name, room);
    }

    @Override public String getRole() { return "Specialist"; }

    @Override
    public double getTimeFactor(Patient patient) {
        return switch (patient.getPriority()) {
            case 1 -> 0.5;
            case 2 -> 0.9;
            default -> 1.4;
        };
    }
}
