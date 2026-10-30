package hospital.model;

public class CriticalPatient extends Patient {

    public CriticalPatient(int id, String name, int age, String complaint) {
        super(id, name, age, complaint);
    }

    @Override public String getCategory() { return "CRITICAL"; }

    @Override public String getCode() { return "C"; }

    @Override public int getPriority() { return 1; }

    @Override public long getTreatmentTimeMs() { return 1500; }

    /** Overrides the parent to add an alert marker. */
    @Override
    public String describe() {
        return super.describe() + "  !!";
    }
}
