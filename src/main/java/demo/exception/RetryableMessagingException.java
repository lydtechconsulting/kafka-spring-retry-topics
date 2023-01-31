package demo.exception;

public class RetryableMessagingException extends RuntimeException {

    public RetryableMessagingException(final String message) {
        super(message);
    }
}
