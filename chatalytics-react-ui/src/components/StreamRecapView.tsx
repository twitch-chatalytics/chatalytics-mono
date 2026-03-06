import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { StreamRecap } from '../types/message';
import { fetchSessionRecap } from '../api/client';
import './StreamRecapView.css';

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-US', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric',
  });
}

function formatDuration(start: string, end: string | null): string {
  if (!end) return 'Ongoing';
  const ms = new Date(end).getTime() - new Date(start).getTime();
  const hours = Math.floor(ms / 3600000);
  const minutes = Math.floor((ms % 3600000) / 60000);
  return hours > 0 ? `${hours}h ${minutes}m` : `${minutes}m`;
}

interface Props {
  sessionId: number;
  onBack: () => void;
}

export default function StreamRecapView({ sessionId, onBack }: Props) {
  const [recap, setRecap] = useState<StreamRecap | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    setLoading(true);
    setError(false);
    fetchSessionRecap(sessionId).then((data) => {
      if (data) {
        setRecap(data);
      } else {
        setError(true);
      }
      setLoading(false);
    });
  }, [sessionId]);

  if (loading) {
    return (
      <div className="recap-loading">
        <div className="recap-spinner" />
        Generating recap...
      </div>
    );
  }

  if (error || !recap) {
    return (
      <div className="recap-error">
        <button className="recap-back-btn" onClick={onBack}>Back to streams</button>
        <p>Failed to load recap for this session.</p>
      </div>
    );
  }

  // Derive game segments from snapshots
  const segments: { game: string; startTime: string; endTime: string; peakViewers: number }[] = [];
  if (recap.snapshots.length > 0) {
    let current = { game: recap.snapshots[0].gameName || 'Unknown', startTime: recap.snapshots[0].timestamp, peakViewers: recap.snapshots[0].viewerCount };
    for (let i = 1; i < recap.snapshots.length; i++) {
      const snap = recap.snapshots[i];
      const game = snap.gameName || 'Unknown';
      if (game !== current.game) {
        segments.push({ ...current, endTime: snap.timestamp });
        current = { game, startTime: snap.timestamp, peakViewers: snap.viewerCount };
      } else {
        current.peakViewers = Math.max(current.peakViewers, snap.viewerCount);
      }
    }
    segments.push({ ...current, endTime: recap.snapshots[recap.snapshots.length - 1].timestamp });
  }

  // Find peak chat activity bucket
  const peakBucket = recap.chatActivity.length > 0
    ? recap.chatActivity.reduce((a, b) => b.messageCount > a.messageCount ? b : a)
    : null;

  return (
    <motion.div
      className="recap-view"
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4 }}
    >
      <button className="recap-back-btn" onClick={onBack}>
        <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
          <path d="M10 4L6 8l4 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        Streams
      </button>

      <div className="recap-header">
        <h2 className="recap-date">{formatDate(recap.startTime)}</h2>
        <div className="recap-meta">
          <span>{formatDuration(recap.startTime, recap.endTime)}</span>
          <span>{recap.totalMessages.toLocaleString()} messages</span>
          <span>{recap.totalChatters.toLocaleString()} chatters</span>
        </div>
      </div>

      {recap.aiSummary && (
        <div className="recap-summary">
          {recap.aiSummary.split('\n\n').map((para, i) => (
            <p key={i}>{para}</p>
          ))}
        </div>
      )}

      {segments.length > 0 && (
        <div className="recap-section">
          <h3 className="recap-section-title">Stream Timeline</h3>
          <div className="recap-timeline">
            {segments.map((seg, i) => (
              <div key={i} className="timeline-segment">
                <span className="timeline-time">{formatTime(seg.startTime)}</span>
                <div className="timeline-bar" />
                <div className="timeline-content">
                  <span className="timeline-game">{seg.game}</span>
                  <span className="timeline-viewers">{seg.peakViewers.toLocaleString()} peak viewers</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {recap.chatActivity.length > 0 && (
        <div className="recap-section">
          <h3 className="recap-section-title">Chat Activity</h3>
          <div className="recap-activity-chart">
            {(() => {
              const maxCount = Math.max(...recap.chatActivity.map(b => b.messageCount));
              return recap.chatActivity.map((bucket, i) => (
                <div
                  key={i}
                  className="activity-bar-wrapper"
                  title={`${formatTime(bucket.bucketStart)}: ${bucket.messageCount} msgs, ${bucket.uniqueChatters} chatters`}
                >
                  <div
                    className="activity-bar"
                    style={{
                      height: `${(bucket.messageCount / maxCount) * 100}%`,
                      opacity: 0.4 + (bucket.messageCount / maxCount) * 0.6,
                    }}
                  />
                </div>
              ));
            })()}
          </div>
          {peakBucket && (
            <div className="recap-activity-peak">
              Peak: {peakBucket.messageCount} msgs/5min at {formatTime(peakBucket.bucketStart)}
            </div>
          )}
        </div>
      )}

      {recap.topChatters.length > 0 && (
        <div className="recap-section">
          <h3 className="recap-section-title">Top Chatters</h3>
          <div className="recap-chatters">
            {recap.topChatters.map((chatter, i) => (
              <div key={chatter.author} className="recap-chatter">
                <span className="recap-chatter-rank">#{i + 1}</span>
                <span className="recap-chatter-name">{chatter.author}</span>
                <span className="recap-chatter-count">{chatter.messageCount}</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </motion.div>
  );
}
