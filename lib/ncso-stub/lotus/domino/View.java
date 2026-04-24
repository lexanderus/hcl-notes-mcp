package lotus.domino;

public interface View {
    String getName() throws NotesException;
    int getEntryCount() throws NotesException;
    Document getFirstDocument() throws NotesException;
    Document getLastDocument() throws NotesException;
    Document getNextDocument(Document doc) throws NotesException;
    Document getPrevDocument(Document doc) throws NotesException;
    ViewEntryCollection getAllEntries() throws NotesException;
    ViewEntryCollection getAllEntriesByKey(Object key, boolean exactMatch) throws NotesException;
    DocumentCollection FTSearch(String query, int maxDocs) throws NotesException;
}
