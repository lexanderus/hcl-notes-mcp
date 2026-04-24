package lotus.domino;

public interface ViewEntryCollection {
    ViewEntry getNthEntry(int n) throws NotesException;
    ViewEntry getNextEntry(ViewEntry entry) throws NotesException;
    int getCount() throws NotesException;
}
