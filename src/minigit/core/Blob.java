package minigit.core;

public class Blob {
    public final String id;
    public final byte[] data;

    public Blob(String id, byte[] data) {
        this.id = id;
        this.data = data;
    }
}
