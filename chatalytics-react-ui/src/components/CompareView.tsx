import { useEffect, useMemo, useRef, useState, useCallback } from 'react';
import { motion } from 'framer-motion';
import { StreamRecap, GameSegment } from '../types/message';
import { fetchSessionRecap } from '../api/client';
import './CompareView.css';

const STREAM_COLORS = ['#6366f1', '#14b8a6', '#f59e0b'];
const STREAM_BG = ['rgba(99,102,241,0.15)', 'rgba(20,184,166,0.15)', 'rgba(245,158,11,0.15)'];

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-US', {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
  });
}

function formatFullDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-US', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric',
  });
}

function formatDuration(start: string, end: string | null): string {
  if (!end) return 'Live';
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

const sectionVariants = {
  hidden: { opacity: 0, y: 20 },
  visible: { opacity: 1, y: 0 },
};

interface Props {
  sessionIds: number[];
  channelLogin: string;
  onBack: () => void;
  onChatterClick: (author: string) => void;
  labels?: string[];
}

// ── SVG path builders (from StreamTimeline) ──

function buildLinePath(points: { x: number; y: number }[], width: number, height: number): string {
  if (points.length === 0) return '';
  const scaled = points.map(p => ({ x: p.x * width, y: height - p.y * (height - 4) }));
  let path = `M ${scaled[0].x},${scaled[0].y}`;
  for (let i = 1; i < scaled.length; i++) {
    const prev = scaled[i - 1];
    const curr = scaled[i];
    const cpx = (prev.x + curr.x) / 2;
    path += ` C ${cpx},${prev.y} ${cpx},${curr.y} ${curr.x},${curr.y}`;
  }
  return path;
}

function buildAreaPath(points: { x: number; y: number }[], width: number, height: number): string {
  if (points.length === 0) return '';
  const line = buildLinePath(points, width, height);
  const scaled = points.map(p => ({ x: p.x * width }));
  return line + ` L ${scaled[scaled.length - 1].x},${height} L ${scaled[0].x},${height} Z`;
}

/** Interpolate value at a given progress from sorted data points. */
function interpolate(points: { x: number; value: number }[], progress: number): number {
  if (points.length === 0) return 0;
  if (progress <= points[0].x) return points[0].value;
  if (progress >= points[points.length - 1].x) return points[points.length - 1].value;
  for (let i = 1; i < points.length; i++) {
    if (progress <= points[i].x) {
      const t = (progress - points[i - 1].x) / (points[i].x - points[i - 1].x);
      return points[i - 1].value + t * (points[i].value - points[i - 1].value);
    }
  }
  return points[points.length - 1].value;
}

// ── Derive game segments from snapshots if none exist ──

function deriveSegments(recap: StreamRecap): GameSegment[] {
  if (recap.snapshots.length === 0) return [];
  const segments: GameSegment[] = [];
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

const CHART_HEIGHT = 160;

export default function CompareView({ sessionIds, onBack, onChatterClick, labels }: Props) {
  const [recaps, setRecaps] = useState<(StreamRecap | null)[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    Promise.all(sessionIds.map(id => fetchSessionRecap(id))).then(results => {
      setRecaps(results);
      setLoading(false);
    });
  }, [sessionIds]);

  const validRecaps = recaps.filter((r): r is StreamRecap => r !== null);

  if (loading) {
    return (
      <div className="compare-view" style={{ padding: '0 clamp(20px, 5vw, 64px)' }}>
        <button className="recap-back-btn" onClick={onBack}>
          <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
            <path d="M10 4L6 8l4 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          Streams
        </button>
        <section className="recap-hero" style={{ textAlign: 'center', maxWidth: 1120, margin: '0 auto 56px', padding: '40px 0 0' }}>
          <div className="cmp-skel" style={{ width: 160, height: 28, borderRadius: 20, margin: '0 auto 16px' }} />
          <div className="cmp-skel" style={{ width: 280, height: 40, margin: '0 auto 24px' }} />
          <div style={{ display: 'flex', justifyContent: 'center', gap: 16 }}>
            {sessionIds.map((_, i) => (
              <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <div className="cmp-skel" style={{ width: 10, height: 10, borderRadius: '50%' }} />
                <div className="cmp-skel" style={{ width: 80, height: 14 }} />
              </div>
            ))}
          </div>
        </section>
        <section className="recap-section" style={{ maxWidth: 1120, margin: '0 auto 48px' }}>
          <div className="cmp-skel" style={{ width: 120, height: 14, marginBottom: 20 }} />
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12 }}>
            {Array.from({ length: 6 }, (_, i) => (
              <div key={i} className="cmp-skel" style={{ height: 80, borderRadius: 14 }} />
            ))}
          </div>
        </section>
        <section className="recap-section" style={{ maxWidth: 1120, margin: '0 auto 48px' }}>
          <div className="cmp-skel" style={{ width: '100%', height: 200, borderRadius: 14 }} />
        </section>
      </div>
    );
  }

  if (validRecaps.length < 2) {
    return (
      <div className="recap-error">
        <button className="recap-back-btn" onClick={onBack}>Back to streams</button>
        <p>Could not load enough sessions for comparison.</p>
      </div>
    );
  }

  return (
    <motion.div
      className="compare-view"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.3 }}
    >
      <button className="recap-back-btn" onClick={onBack}>
        <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
          <path d="M10 4L6 8l4 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        Streams
      </button>

      <HeroSection recaps={validRecaps} labels={labels} />
      <MetricsGrid recaps={validRecaps} />
      <TimelineOverlay recaps={validRecaps} labels={labels} />
      <MessageAnalysisComparison recaps={validRecaps} />
      <CommunityComparison recaps={validRecaps} onChatterClick={onChatterClick} labels={labels} />
      <GameSegmentsComparison recaps={validRecaps} labels={labels} />
    </motion.div>
  );
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Hero Section
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

function HeroSection({ recaps, labels }: { recaps: StreamRecap[]; labels?: string[] }) {
  return (
    <motion.section
      className="compare-hero"
      variants={sectionVariants}
      initial="hidden"
      animate="visible"
      transition={{ duration: 0.4, delay: 0.05 }}
    >
      <span className="recap-duration-badge">
        Comparing {recaps.length} streams
      </span>
      <h1 className="recap-date">Stream Comparison</h1>
      <div className="compare-legend">
        {recaps.map((r, i) => (
          <div key={r.sessionId} className="compare-legend-item">
            <span className="compare-legend-dot" style={{ background: STREAM_COLORS[i] }} />
            <span className="compare-legend-label">
              {labels?.[i] ? `${labels[i]} — ` : ''}{formatFullDate(r.startTime)}
            </span>
            <span className="compare-legend-duration">{formatDuration(r.startTime, r.endTime)}</span>
          </div>
        ))}
      </div>
    </motion.section>
  );
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Metrics Grid
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

interface MetricDef {
  label: string;
  getValue: (r: StreamRecap) => number | null;
  format: (n: number) => string;
  higher?: 'better' | 'neutral';
}

const METRICS: MetricDef[] = [
  { label: 'Messages', getValue: r => r.totalMessages, format: n => formatNumber(n), higher: 'better' },
  { label: 'Chatters', getValue: r => r.totalChatters, format: n => formatNumber(n), higher: 'better' },
  { label: 'Msgs / min', getValue: r => r.messagesPerMinute, format: n => n.toFixed(1), higher: 'better' },
  { label: 'Peak Viewers', getValue: r => r.peakViewerCount, format: n => formatNumber(n), higher: 'better' },
  { label: 'Avg Viewers', getValue: r => r.avgViewerCount, format: n => formatNumber(Math.round(n)), higher: 'better' },
  { label: 'Chat Rate', getValue: r => r.chatParticipationRate, format: n => formatPercent(n), higher: 'better' },
  { label: 'New Chatters', getValue: r => r.newChatterCount, format: n => formatNumber(n), higher: 'neutral' },
  { label: 'Returning', getValue: r => r.returningChatterCount, format: n => formatNumber(n), higher: 'neutral' },
];

function MetricsGrid({ recaps }: { recaps: StreamRecap[] }) {
  return (
    <motion.section
      className="compare-section"
      variants={sectionVariants}
      initial="hidden"
      animate="visible"
      transition={{ duration: 0.4, delay: 0.1 }}
    >
      <h2 className="recap-section-title">Key Metrics</h2>
      <div className="compare-metrics-grid">
        {METRICS.map(metric => {
          const values = recaps.map(r => metric.getValue(r));
          const validValues = values.filter((v): v is number => v !== null);
          const maxVal = validValues.length > 0 ? Math.max(...validValues) : null;

          return (
            <div key={metric.label} className="compare-metric-card">
              <span className="compare-metric-label">{metric.label}</span>
              <div className="compare-metric-values">
                {recaps.map((r, i) => {
                  const val = metric.getValue(r);
                  if (val === null) return null;
                  const isHighest = metric.higher === 'better' && validValues.length > 1 && val === maxVal;
                  return (
                    <div key={r.sessionId} className={`compare-metric-value${isHighest ? ' highest' : ''}`}>
                      <span className="compare-metric-dot" style={{ background: STREAM_COLORS[i] }} />
                      <span className="compare-metric-number">{metric.format(val)}</span>
                      {isHighest && <span className="compare-metric-crown">&#9650;</span>}
                    </div>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>
    </motion.section>
  );
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Timeline Overlay
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

function TimelineOverlay({ recaps, labels }: { recaps: StreamRecap[]; labels?: string[] }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [containerWidth, setContainerWidth] = useState(600);
  const [scrubProgress, setScrubProgress] = useState<number | null>(null);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const observer = new ResizeObserver(entries => {
      for (const entry of entries) setContainerWidth(entry.contentRect.width);
    });
    observer.observe(el);
    setContainerWidth(el.clientWidth);
    return () => observer.disconnect();
  }, []);

  // Normalize each stream's viewer data to 0-1 progress (% of duration)
  const streamData = useMemo(() => {
    // Find global max viewers across all streams for shared Y-axis
    const globalMaxViewers = Math.max(1, ...recaps.flatMap(r => r.snapshots.map(s => s.viewerCount)));
    const globalMaxChat = Math.max(1, ...recaps.flatMap(r => r.chatActivity.map(b => b.messageCount)));

    return recaps.map(recap => {
      const start = new Date(recap.startTime).getTime();
      const end = recap.endTime
        ? new Date(recap.endTime).getTime()
        : recap.snapshots.length > 0
          ? new Date(recap.snapshots[recap.snapshots.length - 1].timestamp).getTime()
          : start + 1;
      const duration = Math.max(end - start, 1);

      const viewerPoints = recap.snapshots.map(s => ({
        x: (new Date(s.timestamp).getTime() - start) / duration,
        y: s.viewerCount / globalMaxViewers,
        value: s.viewerCount,
      }));

      const chatPoints = recap.chatActivity.map(b => ({
        x: (new Date(b.bucketStart).getTime() - start) / duration,
        y: b.messageCount / globalMaxChat,
        value: b.messageCount,
      }));

      return { viewerPoints, chatPoints, duration };
    });
  }, [recaps]);

  const updateScrub = useCallback((clientX: number) => {
    const el = containerRef.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    setScrubProgress(Math.max(0, Math.min(1, (clientX - rect.left) / rect.width)));
  }, []);

  // Scrub detail values
  const scrubDetail = useMemo(() => {
    if (scrubProgress === null) return null;
    return streamData.map((sd, i) => {
      const viewers = interpolate(sd.viewerPoints, scrubProgress);
      const msgs = interpolate(sd.chatPoints, scrubProgress);
      const label = labels?.[i];
      return {
        date: label ? `${label} — ${formatDate(recaps[i].startTime)}` : formatDate(recaps[i].startTime),
        viewers: Math.round(viewers),
        msgs: Math.round(msgs),
        color: STREAM_COLORS[i],
      };
    });
  }, [scrubProgress, streamData, recaps]);

  const hasViewers = streamData.some(sd => sd.viewerPoints.length > 0);
  const hasChat = streamData.some(sd => sd.chatPoints.length > 0);

  if (!hasViewers && !hasChat) return null;

  return (
    <motion.section
      className="compare-section"
      variants={sectionVariants}
      initial="hidden"
      animate="visible"
      transition={{ duration: 0.4, delay: 0.15 }}
    >
      <h2 className="recap-section-title">Timeline Overlay</h2>
      <div className="compare-timeline-hint">Normalized to % of stream duration. Hover to compare.</div>

      <div
        ref={containerRef}
        className="compare-timeline-container"
        onPointerMove={(e) => updateScrub(e.clientX)}
        onPointerLeave={() => setScrubProgress(null)}
        style={{ touchAction: 'none' }}
      >
        <svg
          width={containerWidth}
          height={CHART_HEIGHT}
          className="compare-timeline-svg"
          viewBox={`0 0 ${containerWidth} ${CHART_HEIGHT}`}
          preserveAspectRatio="none"
        >
          {/* Viewer area fills */}
          {streamData.map((sd, i) => {
            if (sd.viewerPoints.length === 0) return null;
            const areaPath = buildAreaPath(
              sd.viewerPoints.map(p => ({ x: p.x, y: p.y })),
              containerWidth,
              CHART_HEIGHT,
            );
            return (
              <path key={`area-${i}`} d={areaPath} fill={STREAM_BG[i]} />
            );
          })}

          {/* Viewer lines */}
          {streamData.map((sd, i) => {
            if (sd.viewerPoints.length === 0) return null;
            const linePath = buildLinePath(
              sd.viewerPoints.map(p => ({ x: p.x, y: p.y })),
              containerWidth,
              CHART_HEIGHT,
            );
            return (
              <path
                key={`viewer-line-${i}`}
                d={linePath}
                fill="none"
                stroke={STREAM_COLORS[i]}
                strokeWidth="2"
                strokeLinejoin="round"
                opacity="0.8"
              />
            );
          })}

          {/* Chat lines (dashed) */}
          {streamData.map((sd, i) => {
            if (sd.chatPoints.length === 0) return null;
            const linePath = buildLinePath(
              sd.chatPoints.map(p => ({ x: p.x, y: p.y })),
              containerWidth,
              CHART_HEIGHT,
            );
            return (
              <path
                key={`chat-line-${i}`}
                d={linePath}
                fill="none"
                stroke={STREAM_COLORS[i]}
                strokeWidth="1.5"
                strokeDasharray="4 3"
                strokeLinejoin="round"
                opacity="0.5"
              />
            );
          })}

          {/* Scrubber line */}
          {scrubProgress !== null && (
            <line
              x1={scrubProgress * containerWidth}
              y1={0}
              x2={scrubProgress * containerWidth}
              y2={CHART_HEIGHT}
              stroke="#f3f3f3"
              strokeWidth="1"
              opacity="0.4"
            />
          )}
        </svg>

        {/* X-axis labels */}
        <div className="compare-timeline-x-labels">
          {['0%', '25%', '50%', '75%', '100%'].map((label, i) => (
            <span key={label} style={{ left: `${i * 25}%` }}>{label}</span>
          ))}
        </div>
      </div>

      {/* Scrub detail */}
      {scrubDetail && (
        <div className="compare-timeline-detail">
          {scrubDetail.map((d, i) => (
            <div key={i} className="compare-timeline-detail-row">
              <span className="compare-metric-dot" style={{ background: d.color }} />
              <span className="compare-timeline-detail-date">{d.date}</span>
              {d.viewers > 0 && <span className="compare-timeline-detail-stat">{formatNumber(d.viewers)} viewers</span>}
              <span className="compare-timeline-detail-stat">{formatNumber(d.msgs)} msgs/5min</span>
            </div>
          ))}
        </div>
      )}

      {/* Legend */}
      <div className="compare-timeline-legend">
        <span className="legend-item">
          <span className="legend-swatch" style={{ background: '#666' }} /> Viewers (solid)
        </span>
        <span className="legend-item">
          <span className="legend-swatch legend-line" style={{ background: '#666', borderStyle: 'dashed' }} /> Chat (dashed)
        </span>
      </div>
    </motion.section>
  );
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Message Analysis Comparison
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

interface AnalysisMetric {
  label: string;
  getValue: (r: StreamRecap) => number | null;
  isPercent: boolean;
}

const ANALYSIS_METRICS: AnalysisMetric[] = [
  { label: 'CAPS Ratio', getValue: r => r.messageAnalysis?.capsRatio ?? null, isPercent: true },
  { label: 'Questions', getValue: r => r.messageAnalysis?.questionRatio ?? null, isPercent: true },
  { label: 'Exclamation', getValue: r => r.messageAnalysis?.exclamationRatio ?? null, isPercent: true },
  { label: 'Short / Emotes', getValue: r => r.messageAnalysis?.shortMessageRatio ?? null, isPercent: true },
  { label: 'Avg Length', getValue: r => r.messageAnalysis?.avgMessageLength ?? null, isPercent: false },
  { label: 'Median Length', getValue: r => r.messageAnalysis?.medianMessageLength ?? null, isPercent: false },
];

function MessageAnalysisComparison({ recaps }: { recaps: StreamRecap[] }) {
  const hasAnalysis = recaps.some(r => r.messageAnalysis != null);
  if (!hasAnalysis) return null;

  return (
    <motion.section
      className="compare-section"
      variants={sectionVariants}
      initial="hidden"
      animate="visible"
      transition={{ duration: 0.4, delay: 0.25 }}
    >
      <h2 className="recap-section-title">Message Analysis</h2>
      <div className="compare-analysis-bars">
        {ANALYSIS_METRICS.map(metric => {
          const values = recaps.map(r => metric.getValue(r));
          const validValues = values.filter((v): v is number => v != null);
          if (validValues.length === 0) return null;
          const maxVal = Math.max(...validValues, 0.001);

          return (
            <div key={metric.label} className="compare-bar-row">
              <span className="compare-bar-label">{metric.label}</span>
              <div className="compare-bar-tracks">
                {recaps.map((r, i) => {
                  const val = metric.getValue(r);
                  if (val === null) return null;
                  const pct = (val / maxVal) * 100;
                  return (
                    <div key={r.sessionId} className="compare-bar-track">
                      <div
                        className="compare-bar-fill"
                        style={{ width: `${Math.max(pct, 2)}%`, background: STREAM_COLORS[i] }}
                      />
                      <span className="compare-bar-value">
                        {metric.isPercent ? formatPercent(val) : val.toFixed(0)}
                      </span>
                    </div>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>
    </motion.section>
  );
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Community Comparison
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

function CommunityComparison({ recaps, onChatterClick, labels }: { recaps: StreamRecap[]; onChatterClick: (author: string) => void; labels?: string[] }) {
  const hasChatters = recaps.some(r => r.topChatters.length > 0);
  const hasWords = recaps.some(r => r.topWords.length > 0);

  // Find chatters that appear in multiple streams
  const sharedChatters = useMemo(() => {
    const counts = new Map<string, number>();
    recaps.forEach(r => {
      const seen = new Set<string>();
      r.topChatters.forEach(c => {
        if (!seen.has(c.author)) {
          seen.add(c.author);
          counts.set(c.author, (counts.get(c.author) || 0) + 1);
        }
      });
    });
    return new Set([...counts.entries()].filter(([, c]) => c > 1).map(([name]) => name));
  }, [recaps]);

  if (!hasChatters && !hasWords) return null;

  return (
    <motion.section
      className="compare-section"
      variants={sectionVariants}
      initial="hidden"
      animate="visible"
      transition={{ duration: 0.4, delay: 0.3 }}
    >
      <h2 className="recap-section-title">Community</h2>

      {hasChatters && (
        <>
          <h3 className="compare-sub-title">Top Chatters</h3>
          {sharedChatters.size > 0 && (
            <div className="compare-shared-badge">
              {sharedChatters.size} shared chatter{sharedChatters.size !== 1 ? 's' : ''} across streams
            </div>
          )}
          <div className="compare-chatters-grid">
            {recaps.map((r, i) => (
              <div key={r.sessionId} className="compare-chatters-column">
                <div className="compare-column-header">
                  <span className="compare-metric-dot" style={{ background: STREAM_COLORS[i] }} />
                  {labels?.[i] ? `${labels[i]} — ` : ''}{formatDate(r.startTime)}
                </div>
                <div className="recap-chatters">
                  {r.topChatters.slice(0, 10).map((chatter, j) => (
                    <div key={chatter.author} className={`recap-chatter${sharedChatters.has(chatter.author) ? ' shared' : ''}`}>
                      <span className="recap-chatter-rank">#{j + 1}</span>
                      <button
                        className="recap-chatter-name recap-chatter-link"
                        onClick={() => onChatterClick(chatter.author)}
                      >
                        {chatter.author}
                      </button>
                      <span className="recap-chatter-count">{chatter.messageCount.toLocaleString()}</span>
                      {sharedChatters.has(chatter.author) && (
                        <span className="compare-shared-icon" title="Appears in multiple streams">&#9679;</span>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      {hasWords && (
        <>
          <h3 className="compare-sub-title" style={{ marginTop: 32 }}>Top Words</h3>
          <div className="compare-chatters-grid">
            {recaps.map((r, i) => (
              <div key={r.sessionId} className="compare-chatters-column">
                <div className="compare-column-header">
                  <span className="compare-metric-dot" style={{ background: STREAM_COLORS[i] }} />
                  {labels?.[i] ? `${labels[i]} — ` : ''}{formatDate(r.startTime)}
                </div>
                <div className="recap-word-cloud">
                  {r.topWords.slice(0, 15).map(tw => {
                    const maxCount = r.topWords[0].count;
                    const scale = 0.8 + (tw.count / maxCount) * 0.6;
                    const opacity = 0.4 + (tw.count / maxCount) * 0.6;
                    return (
                      <span
                        key={tw.word}
                        className="recap-word"
                        style={{ fontSize: `${scale}em`, opacity, color: STREAM_COLORS[i] }}
                        title={`${tw.word}: ${tw.count}`}
                      >
                        {tw.word}
                      </span>
                    );
                  })}
                </div>
              </div>
            ))}
          </div>
        </>
      )}
    </motion.section>
  );
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Game Segments Comparison
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

const GAME_COLORS = ['#6366f1', '#8b5cf6', '#06b6d4', '#f59e0b', '#e11d48', '#10b981', '#ec4899', '#3b82f6', '#14b8a6', '#f97316'];

function GameSegmentsComparison({ recaps, labels }: { recaps: StreamRecap[]; labels?: string[] }) {
  const streamSegments = recaps.map(r => {
    const segments = r.gameSegments.length > 0 ? r.gameSegments : deriveSegments(r);
    return segments;
  });

  const hasSegments = streamSegments.some(s => s.length > 0);
  if (!hasSegments) return null;

  // Collect all unique game names across all streams
  const allGames = [...new Set(streamSegments.flatMap(segs => segs.map(s => s.gameName)))];
  const gameColorMap = new Map(allGames.map((g, i) => [g, GAME_COLORS[i % GAME_COLORS.length]]));

  return (
    <motion.section
      className="compare-section"
      variants={sectionVariants}
      initial="hidden"
      animate="visible"
      transition={{ duration: 0.4, delay: 0.35 }}
    >
      <h2 className="recap-section-title">Games Played</h2>
      <div className="compare-game-strips">
        {recaps.map((r, i) => {
          const segments = streamSegments[i];
          if (segments.length === 0) return null;

          const start = new Date(r.startTime).getTime();
          const end = r.endTime
            ? new Date(r.endTime).getTime()
            : new Date(segments[segments.length - 1].endTime).getTime();
          const duration = Math.max(end - start, 1);

          return (
            <div key={r.sessionId} className="compare-game-strip-row">
              <div className="compare-game-strip-label">
                <span className="compare-metric-dot" style={{ background: STREAM_COLORS[i] }} />
                {labels?.[i] ? `${labels[i]} — ` : ''}{formatDate(r.startTime)}
              </div>
              <div className="compare-game-strip">
                {segments.map((seg, j) => {
                  const left = ((new Date(seg.startTime).getTime() - start) / duration) * 100;
                  const right = ((new Date(seg.endTime).getTime() - start) / duration) * 100;
                  const width = Math.max(right - left, 0.5);
                  const color = gameColorMap.get(seg.gameName) || GAME_COLORS[0];
                  return (
                    <div
                      key={j}
                      className="segment-band"
                      style={{
                        left: `${left}%`,
                        width: `${width}%`,
                        backgroundColor: color,
                      }}
                      title={seg.gameName}
                    >
                      {width > 15 && <span className="segment-label">{seg.gameName}</span>}
                    </div>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>

      {/* Game legend */}
      <div className="compare-game-legend">
        {allGames.map(game => (
          <span key={game} className="legend-item">
            <span className="legend-swatch" style={{ background: gameColorMap.get(game) }} />
            {game}
          </span>
        ))}
      </div>
    </motion.section>
  );
}
