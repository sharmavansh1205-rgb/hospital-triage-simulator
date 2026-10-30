package hospital.model;

/** Contract for anything that can receive treatment. */
public interface Treatable {

    /** Treats the patient; takes 'millis' milliseconds (simulated by sleeping). */
    void treat(String doctorName, long millis) throws InterruptedException;

    PatientStatus getStatus();

    /** Default method shared by all implementers. */
    default boolean isDischarged() {
        return getStatus() == PatientStatus.DISCHARGED;
    }
}
