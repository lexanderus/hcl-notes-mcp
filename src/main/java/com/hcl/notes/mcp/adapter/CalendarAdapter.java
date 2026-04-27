package com.hcl.notes.mcp.adapter;

import com.hcl.notes.mcp.connection.NotesOperationException;
import com.hcl.notes.mcp.connection.NotesSessionPool;
import com.hcl.notes.mcp.model.CalendarEvent;
import lotus.domino.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

import static com.hcl.notes.mcp.adapter.DatabaseAdapter.recycle;

@Component
public class CalendarAdapter {

    private static final Logger log = LoggerFactory.getLogger(CalendarAdapter.class);

    private final NotesSessionPool pool;

    public CalendarAdapter(NotesSessionPool pool) {
        this.pool = pool;
    }

    public List<CalendarEvent> getEvents(Instant startDate, Instant endDate) {
        return pool.withSession(session -> {
            Database mailDb = getMailDatabase(session);
            try {
                NotesCalendar cal = session.getCalendar(mailDb);
                DateTime startDt = session.createDateTime(Date.from(startDate));
                DateTime endDt   = session.createDateTime(Date.from(endDate));
                Vector<?> entries;
                try {
                    entries = cal.getEntries(startDt, endDt);
                } finally {
                    recycle(startDt);
                    recycle(endDt);
                }
                List<CalendarEvent> events = new ArrayList<>();
                try {
                    for (Object e : entries) {
                        NotesCalendarEntry calEntry = (NotesCalendarEntry) e;
                        try {
                            CalendarEvent event = toCalendarEvent(calEntry);
                            if (event != null) events.add(event);
                        } finally {
                            recycle(calEntry);
                        }
                    }
                } finally {
                    recycle(cal);
                }
                return events;
            } finally {
                recycle(mailDb);
            }
        });
    }

    public String createEvent(String title, Instant start, Instant end,
                               String location, List<String> attendees) {
        return pool.withSession(session -> {
            Database mailDb = getMailDatabase(session);
            try {
                Document doc = mailDb.createDocument();
                try {
                    doc.replaceItemValue("Form", "Appointment");
                    doc.replaceItemValue("Subject", title);
                    DateTime startDt = session.createDateTime(Date.from(start));
                    DateTime endDt   = session.createDateTime(Date.from(end));
                    try {
                        doc.replaceItemValue("StartDateTime", startDt);
                        doc.replaceItemValue("EndDateTime", endDt);
                    } finally {
                        recycle(startDt);
                        recycle(endDt);
                    }
                    if (location != null) doc.replaceItemValue("Location", location);
                    if (attendees != null && !attendees.isEmpty()) {
                        doc.replaceItemValue("RequiredAttendees", new Vector<>(attendees));
                    }
                    doc.replaceItemValue("AppointmentType", "0");
                    doc.save();
                    return doc.getUniversalID();
                } finally {
                    recycle(doc);
                }
            } finally {
                recycle(mailDb);
            }
        });
    }

    private CalendarEvent toCalendarEvent(NotesCalendarEntry calEntry) throws NotesException {
        Document doc = calEntry.getAsDocument();
        if (doc == null) return null;
        try {
            String title    = doc.getItemValueString("Subject");
            String location = doc.getItemValueString("Location");
            Vector<?> startVec = doc.getItemValue("StartDateTime");
            Vector<?> endVec   = doc.getItemValue("EndDateTime");
            DateTime startDt = (startVec != null && !startVec.isEmpty()) ? (DateTime) startVec.firstElement() : null;
            DateTime endDt   = (endVec   != null && !endVec.isEmpty())   ? (DateTime) endVec.firstElement()   : null;
            try {
                return new CalendarEvent(
                        doc.getUniversalID(), title,
                        startDt != null ? Instant.ofEpochMilli(startDt.toJavaDate().getTime()) : null,
                        endDt   != null ? Instant.ofEpochMilli(endDt.toJavaDate().getTime())   : null,
                        (location != null && !location.isEmpty()) ? location : null,
                        List.of()
                );
            } finally {
                recycle(startDt);
                recycle(endDt);
            }
        } finally {
            recycle(doc);
        }
    }

    private Database getMailDatabase(Session session) throws NotesException {
        String mailFile   = session.getEnvironmentString("MailFile", true);
        String mailServer = session.getEnvironmentString("MailServer", true);
        Database db = session.getDatabase(mailServer, mailFile);
        if (db == null) {
            throw new NotesOperationException("Cannot get mail database for calendar", null);
        }
        if (!db.isOpen()) {
            db.open();
        }
        if (!db.isOpen()) {
            recycle(db);
            throw new NotesOperationException(
                    "Cannot open mail database for calendar: " + mailServer + "!!" + mailFile, null);
        }
        return db;
    }
}
