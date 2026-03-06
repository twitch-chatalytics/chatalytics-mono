import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { SessionSummaryView } from '../types/message';
import { fetchSessions } from '../api/client';
import './StreamList.css';

function formatDuration(start: string, end: string | null): string {
  if (!end) return 'Live';
  const ms = new Date(end).getTime() - new Date(start).getTime();
  const hours = Math.floor(ms / 3600000);
  const minutes = Math.floor((ms % 3600000) / 60000);
  return hours > 0 ? `${hours}h ${minutes}m` : `${minutes}m`;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

interface Props {
  onSelectSession: (sessionId: number) => void;
}

export default function StreamList({ onSelectSession }: Props) {
  const [sessions, setSessions] = useState<SessionSummaryView[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;

    const load = () => {
      fetchSessions().then((data) => {
        if (active) {
          setSessions(data);
          setLoading(false);
        }
      });
    };

    load();
    const interval = setInterval(load, 30_000);
    return () => { active = false; clearInterval(interval); };
  }, []);

  if (loading) {
    return <div className="stream-list-loading">Loading streams...</div>;
  }

  if (sessions.length === 0) {
    return <div className="stream-list-empty">No streams found yet.</div>;
  }

  return (
    <div className="stream-list">
      {sessions.map((session, i) => (
        <motion.button
          key={session.sessionId}
          className="stream-card"
          onClick={() => onSelectSession(session.sessionId)}
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: i * 0.03 }}
        >
          <div className="stream-card-top">
            <span className="stream-card-date">{formatDate(session.startTime)}</span>
            {!session.endTime && <span className="stream-card-live">LIVE</span>}
          </div>
          <div className="stream-card-game">
            {session.lastGameName || 'Unknown Category'}
          </div>
          <div className="stream-card-stats">
            <span>{session.totalMessages.toLocaleString()} msgs</span>
            <span>{session.totalChatters.toLocaleString()} chatters</span>
            <span>{formatDuration(session.startTime, session.endTime)}</span>
          </div>
        </motion.button>
      ))}
    </div>
  );
}
