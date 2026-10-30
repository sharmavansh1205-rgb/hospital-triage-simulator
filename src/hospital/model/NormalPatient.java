package hospital.model;

public class NormalPatient extends Patient {

    public NormalPatient(int id, String name, int age, String complaint) {
        super(id, name, age, complaint);
    }

    @Override public String getCategory() { return "NORMAL"; }

    @Override public String getCode() { return "N"; }

    @Override public int getPriority() { return 3; }

    @Override public long getTreatmentTimeMs() { return 600; }
}
