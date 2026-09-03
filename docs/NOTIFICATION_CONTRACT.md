# Notification contract

Exactly two user-visible commitment notifications:
- T−10 minutes: preparation.
- Exact scheduled time: action.

The T+5 minute point is a **silent internal expiry timer** only. It must never create a notification.

A deleted/expired occurrence has no future reminder. One-time commitments never recur. On boot/package replacement, only valid future occurrences are rescheduled. A recurring occurrence advances only after completion, explicit not-completed, or silent expiry of the current occurrence.
