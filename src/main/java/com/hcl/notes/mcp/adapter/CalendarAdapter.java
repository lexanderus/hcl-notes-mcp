package com.hcl.notes.mcp.adapter;

import com.hcl.notes.mcp.connection.NotesSessionPool;
import com.hcl.notes.mcp.model.CalendarEvent;
import lotus.domino.*;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

import static com.hcl.notes.mcp.adapter.NotesUtils.recycle;

@Component
public class CalendarAdapter {

    private final NotesSessionPool pool;
    private final MailDatabaseLocator mailDb;

    public CalendarAdapter(NotesSessionPool pool, MailDatabaseLocator mailDb) {
        this.pool   = pool;
        this.mailDb = mailDb;
    }

    /**
     * Returns calendar events in the given date range by querying the ($Calendar) view.
     *
     * NOTE: We intentionally do NOT use session.getCalendar(db).getEntries() here.
     * The Notes Calendar API requires databases with a full mail/C&S design AND calendar
     * entries registered via the calendar protocol. On sandbox NSFs (created from blank
     * template with raw Appointment documents) getEntries() hangs indefinitely, blocking
     * the notes-jni executor thread and causing all subsequent IT tests to time out.
     *
     * View-based iteration is reliable for any database and works with raw Appointment
     * documents regardless of C&S registration status.
     */
    public List<CalendarEvent> getEvents(Instant startDate, Instant endDate) {
        return pool.withSession(session -> {
            Database db = mailDb.openMailDatabase(session, "calendar");
            try {
                View calView = db.getView("($Calendar)");
                if (calView == null) return List.of();
                List<CalendarEvent> events = new ArrayList<>();
                Document doc = calView.getFirstDocument();
                try {
                    while (doc != null) {
                        Document next = calView.getNextDocument(doc);
                        try {
                            CalendarEvent event = toCalendarEvent(doc, startDate, endDate);
                            if (event != null) events.add(event);
                        } finally {
                            recycle(doc);
                        }
                        doc = next;
                    }
                } finally {
                    if (doc != null) recycle(doc);
                    recycle(calView);
                }
                return events;
            } finally {
                recycle(db);
            }
        });
    }

    public String createEvent(String title, Instant start, Instant end,
                               String location, List<String> attendees) {
        return pool.withSession(session -> {
            Database db = mailDb.openMailDatabase(session, "calendar");
            try {
                Document doc = db.createDocument();
                try {
                    doc.replaceItemValue("Form", "Appointment");
                    doc.replaceItemValue("Subject", title);
                    DateTime startDt = session.createDateTime(Date.from(start));
                    DateTime endDt   = session.createDateTime(Date.from(end));
                    try {
                        // Use StartDate/StartTime/EndDate/EndTime — recognized by Notes Calendar API
                        doc.replaceItemValue("StartDate",     startDt);
                        doc.replaceItemValue("StartTime",     startDt);
                        doc.replaceItemValue("EndDate",       endDt);
                        doc.replaceItemValue("EndTime",       endDt);
                        // StartDateTime for back-compat with toCalendarEvent read path
                        doc.replaceItemValue("StartDateTime", startDt);
                        doc.replaceItemValue("EndDateTime",   endDt);
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
                recycle(db);
            }
        });
    }

    /**
     * Converts a raw Appointment document to CalendarEvent, filtering by date range.
     * Returns null if the event's start time falls outside [startFilter, endFilter].
     * Reads StartDateTime (preferred) or StartDate as the event start time.
     */
    private CalendarEvent toCalendarEvent(Document doc, Instant startFilter, Instant endFilter)
            throws NotesException {
        String title    = doc.getItemValueString("Subject");
        String location = doc.getItemValueString("Location");

        // Read start: prefer StartDateTime (set by createEvent), fall back to StartDate
        DateTime startDt = readFirstDateTime(doc, "StartDateTime", "StartDate");
        DateTime endDt   = readFirstDateTime(doc, "EndDateTime",   "EndDate");

        try {
            if (startDt == null) return null;
            Instant eventStart = Instant.ofEpochMilli(startDt.toJavaDate().getTime());

            // Filter: event start must be within [startFilter, endFilter]
            if (eventStart.isBefore(startFilter) || eventStart.isAfter(endFilter)) return null;

            Instant eventEnd = endDt != null ? Instant.ofEpochMilli(endDt.toJavaDate().getTime()) : null;
            return new CalendarEvent(
                    doc.getUniversalID(), title,
                    eventStart, eventEnd,
                    (location != null && !location.isEmpty()) ? location : null,
                    List.of()
            );
        } finally {
            recycle(startDt);
            recycle(endDt);
        }
    }

    /** Returns the first DateTime value from the first field name that has a value. */
    @SuppressWarnings("unchecked")
    private DateTime readFirstDateTime(Document doc, String... fieldNames) throws NotesException {
        for (String field : fieldNames) {
            Vector<?> vec = doc.getItemValue(field);
            if (vec != null && !vec.isEmpty() && vec.firstElement() instanceof DateTime) {
                return (DateTime) vec.firstElement();
            }
        }
        return null;
    }
}
