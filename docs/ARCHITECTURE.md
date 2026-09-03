# Berna Focus architecture

## Product flow
Intention → commitment → preparation → reminder → action → completion → reward → progress → consistency.

## Reminder contract
Each occurrence owns three scheduled alarms internally: preparation (T-10), exact time (T), and a silent expiry check (T+5). Only the first two can create notifications. The expiry alarm exists solely to close an unresolved occurrence and, for an explicit recurring commitment, create the next occurrence. There is never a T-5 notification.

## Offline-first
SharedPreferences/JSON is intentionally used for this version to keep the core small and fully offline. The core never depends on Firebase or network access.

## Premium
UI → MainActivity/View layer → HuaweiIapManager → HMS IAP. Product IDs are centralized. A non-consumable is unlocked locally only after signature verification succeeds. The Huawei IAP public key must be supplied by the app owner and is not invented or committed.
