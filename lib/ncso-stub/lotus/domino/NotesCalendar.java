package lotus.domino;

import java.util.Vector;

public interface NotesCalendar {
    Vector<?> getEntries(DateTime start, DateTime end) throws NotesException;
}
