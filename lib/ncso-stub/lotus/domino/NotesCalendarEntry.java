package lotus.domino;

public interface NotesCalendarEntry {
    Document getAsDocument() throws NotesException;
    String getUID() throws NotesException;
}
