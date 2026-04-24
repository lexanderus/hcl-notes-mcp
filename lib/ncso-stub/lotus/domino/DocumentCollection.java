package lotus.domino;

public interface DocumentCollection {
    Document getFirstDocument() throws NotesException;
    Document getNextDocument(Document doc) throws NotesException;
    Document getNthDocument(int n) throws NotesException;
    int getCount() throws NotesException;
}
