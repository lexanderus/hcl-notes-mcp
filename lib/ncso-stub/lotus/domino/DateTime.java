package lotus.domino;

import java.util.Date;

public interface DateTime {
    Date toJavaDate() throws NotesException;
}
