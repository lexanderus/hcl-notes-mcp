package com.hcl.notes.mcp.adapter;

import lotus.domino.Base;

/** Shared utilities for HCL Notes Domino object lifecycle. */
public final class NotesUtils {

    private NotesUtils() {}

    /** Recycles a Domino object, swallowing exceptions (object may already be recycled). */
    public static void recycle(Base obj) {
        if (obj == null) return;
        try { obj.recycle(); } catch (Exception ignored) {}
    }
}
