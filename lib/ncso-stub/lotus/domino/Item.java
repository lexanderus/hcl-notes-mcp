package lotus.domino;

import java.util.Vector;

public interface Item {
    String getName() throws NotesException;
    Vector<?> getValues() throws NotesException;
}
