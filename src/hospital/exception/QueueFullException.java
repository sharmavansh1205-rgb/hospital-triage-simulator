package hospital.exception;

public class QueueFullException extends HospitalException {
    public QueueFullException(String message) {
        super(message);
    }
}
