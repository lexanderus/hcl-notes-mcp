package lotus.domino;

import java.util.Vector;

public interface Database {
    boolean isOpen() throws NotesException;
    String getTitle() throws NotesException;
    Vector<?> getViews() throws NotesException;
    View getView(String name) throws NotesException;
    Document createDocument() throws NotesException;
    Document getDocumentByUNID(String unid) throws NotesException;
    DocumentCollection search(String formula, DateTime startDate, int maxDocs) throws NotesException;
}
