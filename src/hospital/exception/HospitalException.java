package hospital.exception;

/** Base checked exception for every problem the hospital system can report. */
public class HospitalException extends Exception {
    public HospitalException(String message) {
        super(message);
    }
}
