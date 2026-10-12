# Student study activity

No new migration. Requires the existing lesson_study_days deployment used by Professor engagement.

The existing Student dashboard response (normal, Preview, Support) adds studyActivity: weeklyStudyDays, studyStreak, timezone, weekStartsOn. The authenticated Student or validated acting/preview session supplies the target; there is no client Student-ID parameter.

Both metrics share one read-only SQL activity-day union: lesson study dates, assessment starts and submissions, completed module timestamps, and non-empty STUDENT Mentor messages. Login, profile activity and temporary Preview data are not sources. All sources filter by the same Student ID. Multiple events on the same date count once. Historical real study remains meaningful even if the Student later changes year level. Professor analytics are unchanged.

Weekly frequency counts dates from Monday through today (0-7), using the same server-local calendar as HALO activity timestamps. Future dates are excluded. Streak counts the latest consecutive run only if its last day is today or yesterday, otherwise zero. Weeks, months and years do not interrupt a run. The UI explains the yesterday rule and timezone.

One SQL aggregate returns one row using a window over distinct activity dates. Full history is not transferred to or iterated in application memory; the database examines the Student's historical dates to avoid arbitrarily truncating long streaks. Both metrics use the same day definition. No extra frontend requests are added. Existing statistics loading/error/retry behavior remains in place.

Preview reads the selected Student's real metrics without writes. Real Support activity can contribute through existing durable records. Existing Preview history and Support transaction tests verify this behavior.

## Study Calendar and Mentor Sessions

Dashboard studyActivity also includes mentorSessionsWeek and mentorSessionsMonth. Each counts DISTINCT real session IDs with at least one non-empty STUDENT message during that reporting period. Reused sessions may count once in each period. Empty sessions, assistant-only messages and temporary Preview exchanges do not count.

GET /api/student/study-calendar?month=YYYY-MM returns { month, today, timezone, activeDates }. Omit month for the server's current month. Equivalent read-only routes exist under /api/admin/acting/student and /api/admin/preview/student with existing X-Acting-Session validation. No client Student ID selects the target. Invalid acting-session codes retain their existing status/code behavior.

Calendar and weekly/streak calculations share one SQL activity-day source definition. Calendar filters each source to the requested month before UNION deduplication, caps at tomorrow's start, and returns at most 31 dates. Future months return an empty list. Month navigation supports years 1000-9998. Mentor counting is a separate bounded aggregate from the earlier of current week/month through today. Professor analytics are unchanged.

The Dashboard loads the calendar only on expansion. Month changes abort/ignore stale responses via useRemoteData, and acting identity changes remount it. Today is outlined and marked aria-current=date; active days have a check mark, color and an accessible label. Date rendering uses UTC solely for calendar arithmetic so browser timezone does not shift dates received from the server. No migration or new tracking writes.
