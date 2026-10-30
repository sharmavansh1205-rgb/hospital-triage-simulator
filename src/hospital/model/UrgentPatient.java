package hospital.model;

public class UrgentPatient extends Patient {

    public UrgentPatient(int id, String name, int age, String complaint) {
        super(id, name, age, complaint);
    }

    @Override public String getCategory() { return "URGENT"; }

    @Override public String getCode() { return "U"; }

    @Override public int getPriority() { return 2; }

    @Override public long getTreatmentTimeMs() { return 1000; }
}
