package exception;

/**
 * Created by machpro on 5/9/17.
 */
public class IllegalArgumentException extends BaseException {

    public IllegalArgumentException() {
        super();
    }

    public IllegalArgumentException(String message) {
        super(message);
    }

    public IllegalArgumentException(String message, Throwable cause) {
        super(message, cause);
    }
}
