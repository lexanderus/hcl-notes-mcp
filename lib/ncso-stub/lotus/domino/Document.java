package lotus.domino;

import java.util.Vector;

public interface Document {
    String getUniversalID() throws NotesException;
    DateTime getCreated() throws NotesException;
    DateTime getLastModified() throws NotesException;
    @SuppressWarnings("rawtypes")
    java.util.Vector<Item> getItems() throws NotesException;
    @SuppressWarnings("rawtypes")
    java.util.Vector getItemValue(String itemName) throws NotesException;
    String getItemValueString(String itemName) throws NotesException;
    Item replaceItemValue(String itemName, Object value) throws NotesException;
    boolean save() throws NotesException;
    void send(boolean attachForm) throws NotesException;
    boolean remove(boolean force) throws NotesException;
    void putInFolder(String folderName) throws NotesException;
    void removeFromFolder(String folderName) throws NotesException;
}
