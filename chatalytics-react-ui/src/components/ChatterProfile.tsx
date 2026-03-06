import { ReactNode, useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { ChatterProfile as ChatterProfileType } from '../types/message';
import { fetchChatterSummary } from '../api/client';
import { useAnimatedNumber } from '../hooks/useAnimatedNumber';
import './ChatterProfile.css';

function formatDate(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleDateString('en-US', { month: 'short', year: 'numeric' });
}

function formatHour(hour: number): string {
  const suffix = hour >= 12 ? 'PM' : 'AM';
  const h = hour % 12 || 12;
  return `${h}${suffix}`;
}

interface Props {
  profile: ChatterProfileType;
  children?: ReactNode;
}

export default function ChatterProfile({ profile, children }: Props) {
  const animatedTotal = useAnimatedNumber(profile.totalMessages);
  const animatedSessions = useAnimatedNumber(profile.distinctSessions);
  const [summary, setSummary] = useState<string | null>(null);
  const [summaryLoading, setSummaryLoading] = useState(false);
  const [summaryError, setSummaryError] = useState(false);

  useEffect(() => {
    setSummary(null);
    setSummaryLoading(false);
    setSummaryError(false);
  }, [profile.author]);

  const handleSummarize = async () => {
    if (summary || summaryLoading) return;
    setSummaryLoading(true);
    setSummaryError(false);
    const result = await fetchChatterSummary(profile.author);
    if (result) {
      setSummary(result);
    } else {
      setSummaryError(true);
    }
    setSummaryLoading(false);
  };

  return (
    <motion.div
      className="chatter-profile"
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4 }}
    >
      <div className="chatter-profile-header">
        <h2 className="chatter-profile-author">{profile.author}</h2>
        {!summary && (
          <button
            className="summarize-btn"
            onClick={handleSummarize}
            disabled={summaryLoading}
          >
            {summaryLoading ? (
              <>
                <span className="summarize-spinner" />
                Summarizing...
              </>
            ) : (
              <>
                <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
                  <path d="M4 5h8M4 8h6M4 11h4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
                </svg>
                Summarize
              </>
            )}
          </button>
        )}
      </div>

      <AnimatePresence>
        {summary && (
          <motion.p
            className="chatter-summary"
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            transition={{ duration: 0.3 }}
          >
            {summary}
          </motion.p>
        )}
        {summaryError && !summary && (
          <motion.p
            className="chatter-summary chatter-summary-error"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
          >
            Summary unavailable
          </motion.p>
        )}
      </AnimatePresence>

      <div className="chatter-profile-stats">
        <div className="chatter-stat">
          <span className="chatter-stat-value">{animatedTotal.toLocaleString()}</span>
          <span className="chatter-stat-label">MESSAGES</span>
        </div>
        <div className="chatter-stat">
          <span className="chatter-stat-value">{formatDate(profile.firstSeen)}</span>
          <span className="chatter-stat-label">FIRST SEEN</span>
        </div>
        <div className="chatter-stat">
          <span className="chatter-stat-value">{formatDate(profile.lastSeen)}</span>
          <span className="chatter-stat-label">LAST SEEN</span>
        </div>
        <div className="chatter-stat">
          <span className="chatter-stat-value">{animatedSessions.toLocaleString()}</span>
          <span className="chatter-stat-label">SESSIONS</span>
        </div>
        <div className="chatter-stat">
          <span className="chatter-stat-value">{profile.avgMessagesPerSession.toFixed(1)}</span>
          <span className="chatter-stat-label">AVG/SESSION</span>
        </div>
        {profile.peakHour !== null && (
          <div className="chatter-stat">
            <span className="chatter-stat-value">{formatHour(profile.peakHour)}</span>
            <span className="chatter-stat-label">PEAK HOUR</span>
          </div>
        )}
      </div>

      {children}
    </motion.div>
  );
}
