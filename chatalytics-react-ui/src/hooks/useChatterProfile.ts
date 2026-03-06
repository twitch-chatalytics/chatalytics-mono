import { useEffect, useRef, useState } from 'react';
import { ChatterProfile, Message } from '../types/message';
import { fetchChatterProfile } from '../api/client';

export function useChatterProfile(author: string, messages: Message[]): ChatterProfile | null {
  const [profile, setProfile] = useState<ChatterProfile | null>(null);
  const messagesRef = useRef(messages);
  messagesRef.current = messages;

  useEffect(() => {
    if (!author) { setProfile(null); return; }

    fetchChatterProfile(author)
      .then((p) => {
        if (p) { setProfile(p); return; }
        // Fallback: compute from loaded messages
        const msgs = messagesRef.current;
        if (msgs.length === 0) { setProfile(null); return; }
        const timestamps = msgs.map((m) => new Date(m.timestamp).getTime());
        const sessions = new Set(msgs.map((m) => m.sessionId));
        const hours = msgs.map((m) => new Date(m.timestamp).getHours());
        const hourCounts = new Map<number, number>();
        hours.forEach((h) => hourCounts.set(h, (hourCounts.get(h) ?? 0) + 1));
        let peak = 0; let peakCount = 0;
        hourCounts.forEach((c, h) => { if (c > peakCount) { peak = h; peakCount = c; } });

        setProfile({
          author,
          totalMessages: msgs.length,
          firstSeen: new Date(Math.min(...timestamps)).toISOString(),
          lastSeen: new Date(Math.max(...timestamps)).toISOString(),
          distinctSessions: sessions.size,
          peakHour: peak,
          avgMessagesPerSession: sessions.size > 0 ? msgs.length / sessions.size : 0,
          repeatedMessages: [],
        });
      })
      .catch(() => setProfile(null));
  }, [author]);

  return profile;
}
