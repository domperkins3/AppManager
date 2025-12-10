package io.github.muntashirakon.AppManager.graphene;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Experimental helper for GrapheneOS-aware tuning.
 *
 * For now this class only understands how to:
 *  - Look at logcat lines (one or many)
 *  - Detect obvious permission / AppOp / component issues
 *  - Represent them as Issue objects
 *
 * Later, this will be used together with ADB logcat streaming and
 * App Manager's log viewer to build a "GrapheneOS smart tuning"
 * report for an app.
 */
public class GrapheneOsTuner {

    /**
     * Broad categories of issues we care about.
     */
    public enum IssueType {
        PERMISSION_DENIED,
        APP_OP_BLOCKED,
        COMPONENT_DISABLED_OR_BLOCKED,
        UNKNOWN
    }

    /**
     * One problem detected in log output.
     */
    public static class Issue {
        public final IssueType type;
        public final String rawLine;
        public final String shortMessage;
        public final String detail;

        public Issue(IssueType type, String rawLine, String shortMessage, String detail) {
            this.type = type;
            this.rawLine = rawLine;
            this.shortMessage = shortMessage;
            this.detail = detail;
        }
    }

    // Very simple patterns to spot interesting log entries.
    // These are intentionally broad; we'll refine them over time.

    // Anything that looks like a SecurityException / permission denial
    private final Pattern permissionDeniedPattern =
            Pattern.compile("Permission Denial:|Permission denial|java\\.lang\\.SecurityException",
                    Pattern.CASE_INSENSITIVE);

    // Anything mentioning AppOps / app-op
    private final Pattern appOpBlockedPattern =
            Pattern.compile("app-op|AppOp|AppOps", Pattern.CASE_INSENSITIVE);

    // Rough matches for disabled or blocked components/services
    private final Pattern componentDisabledPattern =
            Pattern.compile("not exported from uid|not allowed to start service|"
                            + "Unable to start service|Service not registered",
                    Pattern.CASE_INSENSITIVE);

    /**
     * Temporary method so we can test things without touching the UI.
     */
    public String getStatus() {
        return "GrapheneOS tuner: basic log-line analysis active";
    }

    /**
     * Look at a single logcat line and decide if it represents an issue we care about.
     *
     * @param line One line from logcat (already filtered to the target app, ideally).
     * @return Issue if recognized, or null if the line doesn't look interesting.
     */
    public Issue analyzeLogLine(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }

        // Check for permission-related problems first
        if (permissionDeniedPattern.matcher(line).find()) {
            String permission = extractPermissionName(line);
            String shortMsg;
            if (permission != null) {
                shortMsg = "Permission denied: " + permission;
            } else {
                shortMsg = "Permission denied (exact permission unknown)";
            }
            return new Issue(IssueType.PERMISSION_DENIED, line, shortMsg, line);
        }

        // Check for AppOp-related blocks
        if (appOpBlockedPattern.matcher(line).find()) {
            String shortMsg = "Operation blocked by AppOps / app-op policy";
            return new Issue(IssueType.APP_OP_BLOCKED, line, shortMsg, line);
        }

        // Check for disabled / blocked components
        if (componentDisabledPattern.matcher(line).find()) {
            String shortMsg = "Component (service/receiver) seems disabled or blocked";
            return new Issue(IssueType.COMPONENT_DISABLED_OR_BLOCKED, line, shortMsg, line);
        }

        // Most lines won't be interesting to us
        return null;
    }

    /**
     * Analyze many logcat lines at once and return all issues found.
     *
     * This is what we'll use later when we have a captured log buffer
     * from the app being tested.
     */
    public List<Issue> analyzeLogLines(Iterable<String> lines) {
        List<Issue> issues = new ArrayList<>();
        if (lines == null) {
            return issues;
        }

        for (String line : lines) {
            Issue issue = analyzeLogLine(line);
            if (issue != null) {
                issues.add(issue);
            }
        }
        return issues;
    }

    /**
     * Try to pull out something that looks like a permission name from a log line.
     *
     * Example patterns we might see:
     *  - "requires android.permission.CAMERA"
     *  - "requires com.android.voicemail.permission.ADD_VOICEMAIL"
     */
    private String extractPermissionName(String line) {
        if (line == null) {
            return null;
        }

        Pattern permPattern = Pattern.compile("requires ([\\w\\.]+(?:\\.[\\w]+)*)");
        Matcher m = permPattern.matcher(line);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
}
