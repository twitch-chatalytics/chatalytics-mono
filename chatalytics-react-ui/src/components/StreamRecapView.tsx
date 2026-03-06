import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { StreamRecap } from '../types/message';
import { fetchSessionRecap } from '../api/client';
import StreamTimeline from './StreamTimeline';
import './StreamRecapView.css';

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

function formatNumber(n: number): string {
  if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k';
  return n.toLocaleString();
}

function formatPercent(n: number): string {
  return (n * 100).toFixed(1) + '%';
}

function formatClipDuration(seconds: number): string {
  const s = Math.round(seconds);
  return s >= 60 ? `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}` : `0:${String(s).padStart(2, '0')}`;
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

  // Use backend game segments, fallback to client-side derivation
  const segments = recap.gameSegments.length > 0
    ? recap.gameSegments
    : deriveSegments(recap);

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

      {/* Key metrics cards */}
      <div className="recap-stats-grid">
        <div className="recap-stat-card">
          <span className="recap-stat-value">{recap.messagesPerMinute.toFixed(1)}</span>
          <span className="recap-stat-label">msgs/min</span>
        </div>
        {recap.peakViewerCount != null && (
          <div className="recap-stat-card">
            <span className="recap-stat-value">{formatNumber(recap.peakViewerCount)}</span>
            <span className="recap-stat-label">peak viewers</span>
          </div>
        )}
        {recap.avgViewerCount != null && (
          <div className="recap-stat-card">
            <span className="recap-stat-value">{formatNumber(Math.round(recap.avgViewerCount))}</span>
            <span className="recap-stat-label">avg viewers</span>
          </div>
        )}
        {recap.chatParticipationRate != null && (
          <div className="recap-stat-card">
            <span className="recap-stat-value">{formatPercent(recap.chatParticipationRate)}</span>
            <span className="recap-stat-label">chat rate</span>
          </div>
        )}
        <div className="recap-stat-card">
          <span className="recap-stat-value">{formatNumber(recap.newChatterCount)}</span>
          <span className="recap-stat-label">new chatters</span>
        </div>
        <div className="recap-stat-card">
          <span className="recap-stat-value">{formatNumber(recap.returningChatterCount)}</span>
          <span className="recap-stat-label">returning</span>
        </div>
      </div>

      {recap.aiSummary && (
        <div className="recap-summary">
          {recap.aiSummary.split('\n\n').map((para, i) => (
            <p key={i}>{para}</p>
          ))}
        </div>
      )}

      {/* Scrubbable timeline */}
      {(recap.snapshots.length > 0 || recap.chatActivity.length > 0) && (
        <StreamTimeline
          snapshots={recap.snapshots}
          chatActivity={recap.chatActivity}
          gameSegments={segments}
          startTime={recap.startTime}
          endTime={recap.endTime}
        />
      )}

      {/* Top Clips */}
      {recap.topClips && recap.topClips.length > 0 && (
        <div className="recap-section">
          <h3 className="recap-section-title">Top Clips</h3>
          <div className="recap-clips-grid">
            {recap.topClips.map((clip) => (
              <a
                key={clip.id}
                href={clip.url}
                target="_blank"
                rel="noopener noreferrer"
                className="recap-clip-card"
              >
                <div className="clip-thumb-wrapper">
                  <img src={clip.thumbnailUrl} alt={clip.title} className="clip-thumb" loading="lazy" />
                  <span className="clip-duration">{formatClipDuration(clip.duration)}</span>
                </div>
                <div className="clip-info">
                  <span className="clip-title">{clip.title}</span>
                  <div className="clip-meta">
                    <span>{formatNumber(clip.viewCount)} views</span>
                    <span>by {clip.creatorName}</span>
                  </div>
                </div>
              </a>
            ))}
          </div>
        </div>
      )}

      {/* Message Analysis */}
      {recap.messageAnalysis && (
        <div className="recap-section">
          <h3 className="recap-section-title">Message Analysis</h3>
          <div className="recap-analysis-grid">
            <div className="analysis-item">
              <span className="analysis-value">{recap.messageAnalysis.avgMessageLength.toFixed(0)}</span>
              <span className="analysis-label">avg length</span>
            </div>
            <div className="analysis-item">
              <span className="analysis-value">{formatPercent(recap.messageAnalysis.shortMessageRatio)}</span>
              <span className="analysis-label">short/emotes</span>
            </div>
            <div className="analysis-item">
              <span className="analysis-value">{formatPercent(recap.messageAnalysis.capsRatio)}</span>
              <span className="analysis-label">CAPS</span>
            </div>
            <div className="analysis-item">
              <span className="analysis-value">{formatPercent(recap.messageAnalysis.questionRatio)}</span>
              <span className="analysis-label">questions</span>
            </div>
            <div className="analysis-item">
              <span className="analysis-value">{recap.messageAnalysis.commandCount}</span>
              <span className="analysis-label">commands</span>
            </div>
            <div className="analysis-item">
              <span className="analysis-value">{recap.messageAnalysis.linkCount}</span>
              <span className="analysis-label">links</span>
            </div>
          </div>
        </div>
      )}

      {/* Top Words */}
      {recap.topWords.length > 0 && (
        <div className="recap-section">
          <h3 className="recap-section-title">Top Words</h3>
          <div className="recap-word-cloud">
            {recap.topWords.map((tw) => {
              const maxCount = recap.topWords[0].count;
              const scale = 0.7 + (tw.count / maxCount) * 0.6;
              const opacity = 0.5 + (tw.count / maxCount) * 0.5;
              return (
                <span
                  key={tw.word}
                  className="recap-word"
                  style={{ fontSize: `${scale}em`, opacity }}
                  title={`${tw.word}: ${tw.count}`}
                >
                  {tw.word}
                </span>
              );
            })}
          </div>
        </div>
      )}

      {/* Top Chatters */}
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

function deriveSegments(recap: StreamRecap) {
  if (recap.snapshots.length === 0) return [];
  const segments: { gameName: string; startTime: string; endTime: string; durationMinutes: number; messageCount: number; avgViewers: number; peakViewers: number }[] = [];
  let current = { gameName: recap.snapshots[0].gameName || 'Unknown', startTime: recap.snapshots[0].timestamp, peakViewers: recap.snapshots[0].viewerCount };
  for (let i = 1; i < recap.snapshots.length; i++) {
    const snap = recap.snapshots[i];
    const game = snap.gameName || 'Unknown';
    if (game !== current.gameName) {
      segments.push({ ...current, endTime: snap.timestamp, durationMinutes: 0, messageCount: 0, avgViewers: 0 });
      current = { gameName: game, startTime: snap.timestamp, peakViewers: snap.viewerCount };
    } else {
      current.peakViewers = Math.max(current.peakViewers, snap.viewerCount);
    }
  }
  segments.push({ ...current, endTime: recap.snapshots[recap.snapshots.length - 1].timestamp, durationMinutes: 0, messageCount: 0, avgViewers: 0 });
  return segments;
}
