package lotus.domino;

import java.util.Date;

public interface Session {
    String getEnvironmentString(String vari, boolean isSystem) throws NotesException;
    Database getDatabase(String server, String dbFile) throws NotesException;
    DateTime createDateTime(Date date) throws NotesException;
    NotesCalendar getCalendar(Database db) throws NotesException;
    String getUserName() throws NotesException;
    void recycle() throws NotesException;
}
