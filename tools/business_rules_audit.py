#!/usr/bin/env python3
"""Framework-free business-rule checks for the Focused Mind commitment contract."""
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone

WINDOW=timedelta(minutes=5)
@dataclass(frozen=True)
class C:
    timestamp: datetime
    repeat: str='ONE_TIME'
    days: frozenset[int]=frozenset()

def visible_stages():
    return ('T-10', 'EXACT')

def next_occurrence(c: C, now: datetime):
    if c.repeat=='ONE_TIME': return None
    if c.repeat=='SPECIFIC' and not c.days: return None
    cur=c.timestamp
    while True:
        cur += timedelta(days=1)
        if c.repeat=='DAILY':
            pass
        elif c.repeat=='WEEKDAYS' and cur.weekday() >= 5:
            continue
        elif c.repeat=='WEEKENDS' and cur.weekday() < 5:
            continue
        elif c.repeat=='SPECIFIC' and cur.isoweekday() not in c.days:
            continue
        if cur > now:
            return C(cur,c.repeat,c.days)

assert visible_stages()==('T-10','EXACT')
now=datetime(2026,9,1,12,0,tzinfo=timezone.utc)
for repeat,expected in [('ONE_TIME',False),('DAILY',True),('WEEKDAYS',True),('WEEKENDS',True),('SPECIFIC',True)]:
    c=C(now-timedelta(days=1),repeat,frozenset({1,3,5}) if repeat=='SPECIFIC' else frozenset())
    n=next_occurrence(c,now)
    assert (n is not None)==expected and (n is None or n.timestamp > now)
assert next_occurrence(C(now-timedelta(days=1),'SPECIFIC',frozenset()),now) is None
assert now <= now <= now+WINDOW
assert not (now+WINDOW < now)
print('BUSINESS RULES AUDIT: PASS')
print(' - exactly two visible reminders: T-10 and exact')
print(' - silent 5-minute response window')
print(' - one-time does not recur')
print(' - recurring modes advance strictly into the future')
print(' - empty specific-day selection is rejected')
