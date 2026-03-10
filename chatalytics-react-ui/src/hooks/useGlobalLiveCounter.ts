import { useEffect, useRef, useState, useCallback } from 'react';

const INITIAL_RETRY_MS = 1000;
const MAX_RETRY_MS = 10000;

export function useGlobalLiveCounter() {
  const [totalMessages, setTotalMessages] = useState<number | null>(null);
  const [connected, setConnected] = useState(false);
  const sourceRef = useRef<EventSource | null>(null);
  const retryDelayRef = useRef(INITIAL_RETRY_MS);
  const retryTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const mountedRef = useRef(true);

  const connect = useCallback(() => {
    if (!mountedRef.current) return;

    // Clean up any existing connection
    if (sourceRef.current) {
      sourceRef.current.close();
      sourceRef.current = null;
    }

    const source = new EventSource('/public/live/global/stream');
    sourceRef.current = source;

    source.addEventListener('metrics', (event) => {
      try {
        const data = JSON.parse(event.data);
        setTotalMessages(data.totalMessages);
      } catch {
        // ignore parse errors
      }
    });

    source.onopen = () => {
      setConnected(true);
      retryDelayRef.current = INITIAL_RETRY_MS; // reset backoff on success
    };

    source.onerror = () => {
      setConnected(false);
      source.close();
      sourceRef.current = null;

      if (!mountedRef.current) return;

      // Reconnect with exponential backoff
      const delay = retryDelayRef.current;
      retryDelayRef.current = Math.min(delay * 2, MAX_RETRY_MS);
      retryTimerRef.current = setTimeout(connect, delay);
    };
  }, []);

  useEffect(() => {
    mountedRef.current = true;
    connect();

    return () => {
      mountedRef.current = false;
      if (retryTimerRef.current) clearTimeout(retryTimerRef.current);
      if (sourceRef.current) {
        sourceRef.current.close();
        sourceRef.current = null;
      }
      setConnected(false);
    };
  }, [connect]);

  return { totalMessages, connected };
}
