package com.hcl.notes.mcp.adapter;

import com.hcl.notes.mcp.connection.NotesOperationException;
import com.hcl.notes.mcp.connection.NotesSessionPool;
import com.hcl.notes.mcp.model.CalendarEvent;
import lotus.domino.*;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

@Component
public class CalendarAdapter {

    private final NotesSessionPool pool;

    public CalendarAdapter(NotesSessionPool pool) {
        this.pool = pool;
    }

    public List<CalendarEvent> getEvents(Instant startDate, Instant endDate) {
        return pool.withSession(session -> {
            Database mailDb = getMailDatabase(session);
            NotesCalendar cal = session.getCalendar(mailDb);
            DateTime start = session.createDateTime(Date.from(startDate));
            DateTime end = session.createDateTime(Date.from(endDate));
            Vector<?> entries = cal.getEntries(start, end);
            List<CalendarEvent> events = new ArrayList<>();
            for (Object e : entries) {
                NotesCalendarEntry entry = (NotesCalendarEntry) e;
                events.add(toCalendarEvent(entry));
            }
            return events;
        });
    }

    public String createEvent(String title, Instant start, Instant end,
                               String location, List<String> attendees) {
        return pool.withSession(session -> {
            Database mailDb = getMailDatabase(session);
            Document doc = mailDb.createDocument();
            doc.replaceItemValue("Form", "Appointment");
            doc.replaceItemValue("Subject", title);
            doc.replaceItemValue("StartDateTime", session.createDateTime(Date.from(start)));
            doc.replaceItemValue("EndDateTime", session.createDateTime(Date.from(end)));
            if (location != null) doc.replaceItemValue("Location", location);
            if (attendees != null && !attendees.isEmpty()) {
                doc.replaceItemValue("RequiredAttendees", new Vector<>(attendees));
            }
            doc.replaceItemValue("AppointmentType", "0");
            doc.save();
            return doc.getUniversalID();
        });
    }

    private CalendarEvent toCalendarEvent(NotesCalendarEntry entry) throws NotesException {
        Document doc = entry.getAsDocument();
        String title = doc.getItemValueString("Subject");
        String location = doc.getItemValueString("Location");
        Vector<?> startVec = doc.getItemValue("StartDateTime");
        Vector<?> endVec = doc.getItemValue("EndDateTime");
        DateTime startDt = startVec != null && !startVec.isEmpty() ? (DateTime) startVec.firstElement() : null;
        DateTime endDt = endVec != null && !endVec.isEmpty() ? (DateTime) endVec.firstElement() : null;
        return new CalendarEvent(
                doc.getUniversalID(), title,
                startDt != null ? Instant.ofEpochMilli(startDt.toJavaDate().getTime()) : null,
                endDt != null ? Instant.ofEpochMilli(endDt.toJavaDate().getTime()) : null,
                location.isEmpty() ? null : location,
                List.of()
        );
    }

    private Database getMailDatabase(Session session) throws NotesException {
        String mailFile = session.getEnvironmentString("MailFile", true);
        String mailServer = session.getEnvironmentString("MailServer", true);
        Database db = session.getDatabase(mailServer, mailFile);
        if (db == null || !db.isOpen()) {
            throw new NotesOperationException("Cannot open mail database for calendar", null);
        }
        return db;
    }
}
