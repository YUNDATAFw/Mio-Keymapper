package io.takamiya.mio.injector;

public class InjectionException extends Exception {

    private final int keyCode;

    public InjectionException(String message) {
        super(message);
        this.keyCode = -1;
    }

    public InjectionException(String message, Throwable cause) {
        super(message, cause);
        this.keyCode = -1;
    }

    public InjectionException(int keyCode, String message) {
        super(message);
        this.keyCode = keyCode;
    }

    public InjectionException(int keyCode, String message, Throwable cause) {
        super(message, cause);
        this.keyCode = keyCode;
    }

    public int getKeyCode() {
        return keyCode;
    }
}
