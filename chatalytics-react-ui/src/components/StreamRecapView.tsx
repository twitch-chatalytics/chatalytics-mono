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
  if (!end) return 'Live now';
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

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
}

function formatClipDuration(seconds: number): string {
  const s = Math.round(seconds);
  return s >= 60 ? `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}` : `0:${String(s).padStart(2, '0')}`;
}

const sectionVariants = {
  hidden: { opacity: 0, y: 20 },
  visible: { opacity: 1, y: 0 },
};

interface Props {
  sessionId: number;
  onBack: () => void;
  onChatterClick?: (author: string) => void;
}

export default function StreamRecapView({ sessionId, onBack, onChatterClick }: Props) {
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

  const segments = recap.gameSegments.length > 0
    ? recap.gameSegments
    : deriveSegments(recap);

  const hasTimeline = recap.snapshots.length > 0 || recap.chatActivity.length > 0;
  const hasClips = recap.topClips && recap.topClips.length > 0;
  const hasChatters = recap.topChatters.length > 0;
  const hasWords = recap.topWords.length > 0;
  const hasAnalysis = recap.messageAnalysis != null;
  const hasHype = recap.hypeMoments && recap.hypeMoments.length > 0;

  return (
    <motion.div
      className="recap-view"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.3 }}
    >
      {/* Nav */}
      <button className="recap-back-btn" onClick={onBack}>
        <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
          <path d="M10 4L6 8l4 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        Streams
      </button>

      {/* ── Hero ── */}
      <motion.section
        className="recap-hero"
        variants={sectionVariants}
        initial="hidden"
        animate="visible"
        transition={{ duration: 0.4, delay: 0.05 }}
      >
        <span className="recap-duration-badge">{formatDuration(recap.startTime, recap.endTime)}</span>
        <h1 className="recap-date">{formatDate(recap.startTime)}</h1>
        {recap.aiSummary && (
          <p className="recap-blurb">{recap.aiSummary}</p>
        )}
      </motion.section>

      {/* ── Key metrics ── */}
      <motion.section
        className="recap-section"
        variants={sectionVariants}
        initial="hidden"
        animate="visible"
        transition={{ duration: 0.4, delay: 0.1 }}
      >
        <div className="recap-metrics-grid">
          <MetricBlock value={recap.totalMessages.toLocaleString()} label="Messages" />
          <MetricBlock value={recap.totalChatters.toLocaleString()} label="Chatters" />
          <MetricBlock value={recap.messagesPerMinute.toFixed(1)} label="Msgs / min" />
          <MetricBlock value={recap.chattersPerMinute.toFixed(1)} label="Chatters / min" />
          {recap.peakViewerCount != null && (
            <MetricBlock value={formatNumber(recap.peakViewerCount)} label="Peak viewers" />
          )}
          {recap.avgViewerCount != null && (
            <MetricBlock value={formatNumber(Math.round(recap.avgViewerCount))} label="Avg viewers" />
          )}
          {recap.minViewerCount != null && (
            <MetricBlock value={formatNumber(recap.minViewerCount)} label="Min viewers" />
          )}
          {recap.chatParticipationRate != null && (
            <MetricBlock value={formatPercent(recap.chatParticipationRate)} label="Chat rate" />
          )}
          <MetricBlock value={formatNumber(recap.newChatterCount)} label="New chatters" />
          <MetricBlock value={formatNumber(recap.returningChatterCount)} label="Returning" />
        </div>
      </motion.section>

      {/* ── Peak moment ── */}
      {recap.peakMoment && (
        <motion.section
          className="recap-section"
          variants={sectionVariants}
          initial="hidden"
          animate="visible"
          transition={{ duration: 0.4, delay: 0.15 }}
        >
          <div className="recap-peak-callout">
            <span className="peak-callout-label">Peak at {formatTime(recap.peakMoment.timestamp)}</span>
            <span className="peak-callout-stat">{recap.peakMoment.messageCount.toLocaleString()} messages</span>
            <span className="peak-callout-stat">{recap.peakMoment.uniqueChatters.toLocaleString()} chatters</span>
          </div>
        </motion.section>
      )}

      {/* ── Timeline ── */}
      {hasTimeline && (
        <motion.section
          className="recap-section"
          variants={sectionVariants}
          initial="hidden"
          animate="visible"
          transition={{ duration: 0.4, delay: 0.2 }}
        >
          <StreamTimeline
            snapshots={recap.snapshots}
            chatActivity={recap.chatActivity}
            gameSegments={segments}
            startTime={recap.startTime}
            endTime={recap.endTime}
            hypeMoments={recap.hypeMoments || []}
            clips={recap.topClips || []}
          />
        </motion.section>
      )}

      {/* ── Hype Moments ── */}
      {hasHype && (
        <motion.section
          className="recap-section"
          variants={sectionVariants}
          initial="hidden"
          animate="visible"
          transition={{ duration: 0.4, delay: 0.25 }}
        >
          <h2 className="recap-section-title">Hype Moments</h2>
          <div className="recap-hype-grid">
            {recap.hypeMoments.map((hm, i) => (
              <div key={i} className="recap-hype-card">
                <span className="hype-multiplier">{hm.multiplier.toFixed(1)}x</span>
                <span className="hype-time">{formatTime(hm.timestamp)}</span>
                <div className="hype-detail">
                  <span>{hm.messageCount.toLocaleString()} msgs</span>
                  <span>{hm.uniqueChatters.toLocaleString()} chatters</span>
                </div>
              </div>
            ))}
          </div>
        </motion.section>
      )}

      {/* ── Clips ── */}
      {hasClips && (
        <motion.section
          className="recap-section"
          variants={sectionVariants}
          initial="hidden"
          animate="visible"
          transition={{ duration: 0.4, delay: 0.3 }}
        >
          <h2 className="recap-section-title">Top Clips</h2>
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
                  <span className="clip-views">{formatNumber(clip.viewCount)} views</span>
                </div>
                <div className="clip-info">
                  <span className="clip-title">{clip.title}</span>
                  <span className="clip-creator">clipped by {clip.creatorName}</span>
                </div>
              </a>
            ))}
          </div>
        </motion.section>
      )}

      {/* ── Community: Chatters + Words ── */}
      {(hasChatters || hasWords) && (
        <motion.section
          className="recap-section"
          variants={sectionVariants}
          initial="hidden"
          animate="visible"
          transition={{ duration: 0.4, delay: 0.35 }}
        >
          <h2 className="recap-section-title">Community</h2>
          <div className="recap-community-grid">
            {hasChatters && (
              <div className="recap-community-panel">
                <h3 className="recap-panel-label">Top Chatters</h3>
                <div className="recap-chatters">
                  {recap.topChatters.map((chatter, i) => (
                    <div key={chatter.author} className="recap-chatter">
                      <span className="recap-chatter-rank">#{i + 1}</span>
                      <button
                        className="recap-chatter-name recap-chatter-link"
                        onClick={() => onChatterClick?.(chatter.author)}
                      >
                        {chatter.author}
                      </button>
                      <span className="recap-chatter-count">{chatter.messageCount.toLocaleString()}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
            {hasWords && (
              <div className="recap-community-panel">
                <h3 className="recap-panel-label">Top Words</h3>
                <div className="recap-word-cloud">
                  {recap.topWords.map((tw) => {
                    const maxCount = recap.topWords[0].count;
                    const scale = 0.8 + (tw.count / maxCount) * 0.6;
                    const opacity = 0.4 + (tw.count / maxCount) * 0.6;
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
          </div>
        </motion.section>
      )}

      {/* ── Message Analysis ── */}
      {hasAnalysis && (
        <motion.section
          className="recap-section"
          variants={sectionVariants}
          initial="hidden"
          animate="visible"
          transition={{ duration: 0.4, delay: 0.4 }}
        >
          <h2 className="recap-section-title">Message Analysis</h2>
          <div className="recap-analysis-grid">
            <AnalysisStat value={recap.messageAnalysis!.avgMessageLength.toFixed(0)} label="Avg length" />
            <AnalysisStat value={recap.messageAnalysis!.medianMessageLength.toFixed(0)} label="Median length" />
            <AnalysisStat value={formatPercent(recap.messageAnalysis!.shortMessageRatio)} label="Short / emotes" />
            <AnalysisStat value={formatPercent(recap.messageAnalysis!.capsRatio)} label="CAPS" />
            <AnalysisStat value={formatPercent(recap.messageAnalysis!.questionRatio)} label="Questions" />
            <AnalysisStat value={String(recap.messageAnalysis!.commandCount)} label="Commands" />
            <AnalysisStat value={String(recap.messageAnalysis!.linkCount)} label="Links" />
          </div>
        </motion.section>
      )}
    </motion.div>
  );
}

function MetricBlock({ value, label }: { value: string; label: string }) {
  return (
    <div className="metric-block">
      <span className="metric-block-value">{value}</span>
      <span className="metric-block-label">{label}</span>
    </div>
  );
}

function AnalysisStat({ value, label }: { value: string; label: string }) {
  return (
    <div className="analysis-stat">
      <span className="analysis-stat-value">{value}</span>
      <span className="analysis-stat-label">{label}</span>
    </div>
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
