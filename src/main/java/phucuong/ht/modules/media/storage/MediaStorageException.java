package phucuong.ht.modules.media.storage;

public class MediaStorageException extends RuntimeException {

    private final boolean notFound;

    public MediaStorageException(String message, Throwable cause) {
        super(message, cause);
        this.notFound = false;
    }

    public MediaStorageException(String message, boolean notFound) {
        super(message);
        this.notFound = notFound;
    }

    public boolean isNotFound() {
        return notFound;
    }
}
