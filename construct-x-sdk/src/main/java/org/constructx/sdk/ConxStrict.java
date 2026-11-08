package org.constructx.sdk;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shared classification of converter warnings for the strict mode of the
 * reverse converters (RDF → GAEB / RDF → MS Project).
 *
 * <p>By default a conversion runs to completion (pragmatic mode) and reports
 * every lossy step as a warning. In strict mode the same warning becomes a
 * conversion error and the conversion is aborted. The strictness of a warning
 * depends on its content, not on its position in the pipeline:
 * <ul>
 *   <li>Warnings that <em>skip or drop data</em> (e.g. "skipped", "does not
 *       extend") are <b>strict</b> — part of the input never reached the
 *       output.</li>
 *   <li>Warnings that only report a <em>lossy approximation</em>
 *       (e.g. percentage splits written back as absolute values, an
 *       {@code InProgress} status mapped to 50%, a synthesized header) are
 *       <b>tolerated</b> — nothing is lost, the loss is documented.</li>
 * </ul>
 *
 * <p>The classification is keyword-based and deliberately conservative:
 * when in doubt, a warning is treated as strict.
 */
public final class ConxStrict {

    /**
     * Phrases that mark a warning as data loss. Lossy approximations that
     * preserve the data at a coarser granularity (e.g. an {@code InProgress}
     * status mapped to 50% complete, a synthesized GAEB header) deliberately
     * carry no marker and stay tolerated in strict mode.
     */
    private static final String[] STRICT_MARKERS = {
            "skipped",
            "could not be mapped", "could not be normalized",
            "does not extend", "no ordinal number",
            "is not a valid/unique",
            "unparseable", "unreadable",
            "missing predecessor", "unknown successor",
            "no t<n> identifier",
            "unknown status",
            "using placeholder", "using finishtostart", "assuming 0%",
            "was dropped", "are dropped"
    };

    private ConxStrict() {
    }

    /** Whether the given warning represents data loss (strict mode aborts on it). */
    public static boolean isStrict(String warning) {
        if (warning == null) {
            return false;
        }
        String w = warning.toLowerCase(Locale.ROOT);
        for (String marker : STRICT_MARKERS) {
            if (w.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /** The strict subset of the given warnings, in their original order. */
    public static List<String> strictWarnings(List<String> warnings) {
        List<String> strict = new ArrayList<>();
        if (warnings != null) {
            for (String warning : warnings) {
                if (isStrict(warning)) {
                    strict.add(warning);
                }
            }
        }
        return strict;
    }

    /** Human-readable message for the strict-mode failure. */
    public static String failureMessage(List<String> warnings) {
        List<String> strict = strictWarnings(warnings);
        return "Strict mode: conversion aborted with " + strict.size()
                + " lossy step(s) (data loss): " + String.join(" | ", strict);
    }
}
