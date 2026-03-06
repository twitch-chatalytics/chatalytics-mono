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
      </div>

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

      <div className="ai-summary-section">
        <div className="ai-summary-header">
          <svg className="ai-summary-icon" width="16" height="16" viewBox="0 0 16 16" fill="none">
            <path d="M8 1l1.5 3.5L13 6l-3.5 1.5L8 11 6.5 7.5 3 6l3.5-1.5L8 1z" stroke="currentColor" strokeWidth="1.2" strokeLinejoin="round" />
            <path d="M12 10l.75 1.75L14.5 12.5l-1.75.75L12 15l-.75-1.75-1.75-.75 1.75-.75L12 10z" stroke="currentColor" strokeWidth="1" strokeLinejoin="round" />
          </svg>
          <span className="ai-summary-title">AI Summary</span>
        </div>
        <AnimatePresence mode="wait">
          {summary ? (
            <motion.p
              key="summary"
              className="ai-summary-text"
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              transition={{ duration: 0.3 }}
            >
              {summary}
            </motion.p>
          ) : summaryError ? (
            <motion.p
              key="error"
              className="ai-summary-text ai-summary-error"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
            >
              Summary unavailable
            </motion.p>
          ) : (
            <button
              className="ai-summary-btn"
              onClick={handleSummarize}
              disabled={summaryLoading}
            >
              {summaryLoading ? (
                <>
                  <span className="summarize-spinner" />
                  Generating summary...
                </>
              ) : (
                'Generate summary'
              )}
            </button>
          )}
        </AnimatePresence>
      </div>

      {children}
    </motion.div>
  );
}
