import { useMemo } from 'react';
import { ActivityBucket, Message } from '../types/message';

export function useActivityBuckets(messages: Message[]): ActivityBucket[] {
  return useMemo(() => {
    if (messages.length === 0) return [];

    const counts = new Map<string, number>();
    messages.forEach((m) => {
      const day = m.timestamp.slice(0, 10);
      counts.set(day, (counts.get(day) ?? 0) + 1);
    });

    const days = [...counts.keys()].sort();
    if (days.length < 2) return days.map((d) => ({ date: d, count: counts.get(d)! }));

    const start = new Date(days[0]);
    const end = new Date(days[days.length - 1]);
    const diffDays = Math.round((end.getTime() - start.getTime()) / 86400000);

    if (diffDays > 90) {
      // Weekly buckets
      const weekly = new Map<string, number>();
      messages.forEach((m) => {
        const d = new Date(m.timestamp);
        const weekStart = new Date(d);
        weekStart.setDate(d.getDate() - d.getDay());
        const key = weekStart.toISOString().slice(0, 10);
        weekly.set(key, (weekly.get(key) ?? 0) + 1);
      });
      return [...weekly.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([date, count]) => ({ date, count }));
    }

    // Fill gaps
    const buckets: ActivityBucket[] = [];
    const cursor = new Date(start);
    while (cursor <= end) {
      const key = cursor.toISOString().slice(0, 10);
      buckets.push({ date: key, count: counts.get(key) ?? 0 });
      cursor.setDate(cursor.getDate() + 1);
    }
    return buckets;
  }, [messages]);
}
