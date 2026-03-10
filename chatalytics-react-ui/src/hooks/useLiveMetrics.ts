import { useEffect, useRef, useState } from 'react';
import { LiveMetrics } from '../types/message';

export function useLiveMetrics(channelId: number | null) {
  const [metrics, setMetrics] = useState<LiveMetrics | null>(null);
  const [connected, setConnected] = useState(false);
  const sourceRef = useRef<EventSource | null>(null);

  useEffect(() => {
    if (!channelId) return;

    const source = new EventSource(`/public/live/${channelId}/stream`);
    sourceRef.current = source;

    source.addEventListener('metrics', (event) => {
      try {
        const data: LiveMetrics = JSON.parse(event.data);
        setMetrics(data);
      } catch {
        // ignore parse errors
      }
    });

    source.onopen = () => setConnected(true);

    source.onerror = () => {
      setConnected(false);
      // EventSource auto-reconnects
    };

    return () => {
      source.close();
      sourceRef.current = null;
      setMetrics(null);
      setConnected(false);
    };
  }, [channelId]);

  return { metrics, connected };
}
