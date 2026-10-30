package hospital.staff;

import hospital.model.Patient;
import hospital.service.EmergencyRoom;

public class GeneralPhysician extends Doctor {

    public GeneralPhysician(String name, EmergencyRoom room) {
        super(name, room);
    }

    @Override public String getRole() { return "General Physician"; }

    @Override public double getTimeFactor(Patient patient) { return 1.0; }
}
