package lotus.domino;

public class NotesException extends Exception {
    public int id;
    public NotesException(int id, String text) { super(text); this.id = id; }
    public NotesException(int id, String text, Throwable cause) { super(text, cause); this.id = id; }
}
