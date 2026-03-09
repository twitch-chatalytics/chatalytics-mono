import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { StreamSnapshot, ChatActivityBucket, GameSegment, HypeMoment, StreamClip } from '../types/message';
import './StreamTimeline.css';

interface Props {
  snapshots: StreamSnapshot[];
  chatActivity: ChatActivityBucket[];
  gameSegments: GameSegment[];
  startTime: string;
  endTime: string | null;
  hypeMoments?: HypeMoment[];
  clips?: StreamClip[];
}

// Refined palette for game segments
const SEGMENT_COLORS = [
  '#6366f1', '#8b5cf6', '#06b6d4', '#f59e0b', '#e11d48',
  '#10b981', '#ec4899', '#3b82f6', '#14b8a6', '#f97316',
];

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
}

function formatNumber(n: number): string {
  if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k';
  return String(n);
}

/** Map a timestamp to a 0..1 position within the stream range. */
function timeToProgress(ts: string, rangeStart: number, rangeDuration: number): number {
  return Math.max(0, Math.min(1, (new Date(ts).getTime() - rangeStart) / rangeDuration));
}

/** Build a smooth SVG path from normalized (x, y) points using monotone cubic interpolation. */
function buildAreaPath(points: { x: number; y: number }[], width: number, height: number, baseline: number): string {
  if (points.length === 0) return '';
  const scaled = points.map(p => ({ x: p.x * width, y: baseline - p.y * (baseline - 4) }));

  // Build the top curve
  let path = `M ${scaled[0].x},${scaled[0].y}`;
  for (let i = 1; i < scaled.length; i++) {
    const prev = scaled[i - 1];
    const curr = scaled[i];
    const cpx = (prev.x + curr.x) / 2;
    path += ` C ${cpx},${prev.y} ${cpx},${curr.y} ${curr.x},${curr.y}`;
  }

  // Close along the bottom
  path += ` L ${scaled[scaled.length - 1].x},${height} L ${scaled[0].x},${height} Z`;
  return path;
}

/** Build a line-only SVG path. */
function buildLinePath(points: { x: number; y: number }[], width: number, _height: number, baseline: number): string {
  if (points.length === 0) return '';
  const scaled = points.map(p => ({ x: p.x * width, y: baseline - p.y * (baseline - 4) }));

  let path = `M ${scaled[0].x},${scaled[0].y}`;
  for (let i = 1; i < scaled.length; i++) {
    const prev = scaled[i - 1];
    const curr = scaled[i];
    const cpx = (prev.x + curr.x) / 2;
    path += ` C ${cpx},${prev.y} ${cpx},${curr.y} ${curr.x},${curr.y}`;
  }
  return path;
}

/** Interpolate value at a given progress from sorted data points. */
function interpolate(points: { progress: number; value: number }[], progress: number): number {
  if (points.length === 0) return 0;
  if (progress <= points[0].progress) return points[0].value;
  if (progress >= points[points.length - 1].progress) return points[points.length - 1].value;

  for (let i = 1; i < points.length; i++) {
    if (progress <= points[i].progress) {
      const t = (progress - points[i - 1].progress) / (points[i].progress - points[i - 1].progress);
      return points[i - 1].value + t * (points[i].value - points[i - 1].value);
    }
  }
  return points[points.length - 1].value;
}

const CHART_HEIGHT = 140;
const SEGMENT_STRIP_HEIGHT = 20;
export default function StreamTimeline({ snapshots, chatActivity, gameSegments, startTime, endTime, hypeMoments = [], clips = [] }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [scrubProgress, setScrubProgress] = useState<number | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [containerWidth, setContainerWidth] = useState(600);

  // Observe container width
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const observer = new ResizeObserver(entries => {
      for (const entry of entries) {
        setContainerWidth(entry.contentRect.width);
      }
    });
    observer.observe(el);
    setContainerWidth(el.clientWidth);
    return () => observer.disconnect();
  }, []);

  // Compute time range
  const rangeStart = new Date(startTime).getTime();
  const rangeEnd = endTime
    ? new Date(endTime).getTime()
    : snapshots.length > 0
      ? new Date(snapshots[snapshots.length - 1].timestamp).getTime()
      : new Date().getTime();
  const rangeDuration = Math.max(rangeEnd - rangeStart, 1);

  // Normalize snapshot data
  const viewerPoints = useMemo(() => {
    const maxViewers = Math.max(1, ...snapshots.map(s => s.viewerCount));
    return snapshots.map(s => ({
      progress: timeToProgress(s.timestamp, rangeStart, rangeDuration),
      value: s.viewerCount / maxViewers,
      raw: s.viewerCount,
    }));
  }, [snapshots, rangeStart, rangeDuration]);

  // Normalize chat activity data
  const chatPoints = useMemo(() => {
    const maxMsgs = Math.max(1, ...chatActivity.map(b => b.messageCount));
    return chatActivity.map(b => ({
      progress: timeToProgress(b.bucketStart, rangeStart, rangeDuration),
      value: b.messageCount / maxMsgs,
      rawMsgs: b.messageCount,
      rawChatters: b.uniqueChatters,
    }));
  }, [chatActivity, rangeStart, rangeDuration]);

  // SVG paths
  const viewerAreaPath = useMemo(
    () => buildAreaPath(
      viewerPoints.map(p => ({ x: p.progress, y: p.value })),
      containerWidth, CHART_HEIGHT, CHART_HEIGHT
    ),
    [viewerPoints, containerWidth]
  );

  const chatLinePath = useMemo(
    () => buildLinePath(
      chatPoints.map(p => ({ x: p.progress, y: p.value })),
      containerWidth, CHART_HEIGHT, CHART_HEIGHT
    ),
    [chatPoints, containerWidth]
  );

  // Game segment colors
  const gameColorMap = useMemo(() => {
    const map = new Map<string, string>();
    const games = [...new Set(gameSegments.map(s => s.gameName))];
    games.forEach((g, i) => map.set(g, SEGMENT_COLORS[i % SEGMENT_COLORS.length]));
    return map;
  }, [gameSegments]);

  // Hype moment positions
  const hypePositions = useMemo(() =>
    hypeMoments.map(hm => ({
      progress: timeToProgress(hm.timestamp, rangeStart, rangeDuration),
      multiplier: hm.multiplier,
      messageCount: hm.messageCount,
      uniqueChatters: hm.uniqueChatters,
      time: formatTime(hm.timestamp),
    })),
    [hypeMoments, rangeStart, rangeDuration]
  );

  // Clip positions
  const clipPositions = useMemo(() =>
    clips.map(clip => ({
      progress: timeToProgress(clip.createdAt, rangeStart, rangeDuration),
      title: clip.title,
      time: formatTime(clip.createdAt),
    })),
    [clips, rangeStart, rangeDuration]
  );

  // Scrub handler
  const updateScrub = useCallback((clientX: number) => {
    const el = containerRef.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    const progress = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width));
    setScrubProgress(progress);
  }, []);

  const handlePointerDown = useCallback((e: React.PointerEvent) => {
    setIsDragging(true);
    updateScrub(e.clientX);
    (e.target as HTMLElement).setPointerCapture(e.pointerId);
  }, [updateScrub]);

  const handlePointerMove = useCallback((e: React.PointerEvent) => {
    if (isDragging) {
      updateScrub(e.clientX);
    }
  }, [isDragging, updateScrub]);

  const handlePointerUp = useCallback(() => {
    setIsDragging(false);
  }, []);

  const handlePointerLeave = useCallback(() => {
    if (!isDragging) {
      setScrubProgress(null);
    }
  }, [isDragging]);

  const handleHover = useCallback((e: React.PointerEvent) => {
    if (!isDragging) {
      updateScrub(e.clientX);
    }
  }, [isDragging, updateScrub]);

  // Compute detail at current scrub position
  const detail = useMemo(() => {
    if (scrubProgress === null) return null;

    const timestamp = new Date(rangeStart + scrubProgress * rangeDuration);
    const viewers = interpolate(
      viewerPoints.map(p => ({ progress: p.progress, value: p.raw })),
      scrubProgress
    );
    const msgs = interpolate(
      chatPoints.map(p => ({ progress: p.progress, value: p.rawMsgs })),
      scrubProgress
    );
    const chatters = interpolate(
      chatPoints.map(p => ({ progress: p.progress, value: p.rawChatters })),
      scrubProgress
    );

    // Find active game segment
    let game = '';
    for (const seg of gameSegments) {
      const segStart = timeToProgress(seg.startTime, rangeStart, rangeDuration);
      const segEnd = timeToProgress(seg.endTime, rangeStart, rangeDuration);
      if (scrubProgress >= segStart && scrubProgress <= segEnd + 0.01) {
        game = seg.gameName;
        break;
      }
    }

    // Check for nearby hype moment (within 2% of timeline width)
    const nearbyHype = hypePositions.find(h => Math.abs(h.progress - scrubProgress) < 0.02);
    // Check for nearby clip
    const nearbyClip = clipPositions.find(c => Math.abs(c.progress - scrubProgress) < 0.02);

    return {
      time: formatTime(timestamp.toISOString()),
      viewers: Math.round(viewers),
      msgs: Math.round(msgs),
      chatters: Math.round(chatters),
      game,
      hypeMultiplier: nearbyHype ? nearbyHype.multiplier : null,
      clipTitle: nearbyClip ? nearbyClip.title : null,
    };
  }, [scrubProgress, rangeStart, rangeDuration, viewerPoints, chatPoints, gameSegments, hypePositions, clipPositions]);

  // Time labels along the x-axis
  const timeLabels = useMemo(() => {
    const count = Math.min(6, Math.floor(containerWidth / 80));
    if (count < 2) return [];
    return Array.from({ length: count }, (_, i) => {
      const p = i / (count - 1);
      const t = new Date(rangeStart + p * rangeDuration);
      return { progress: p, label: formatTime(t.toISOString()) };
    });
  }, [rangeStart, rangeDuration, containerWidth]);

  if (snapshots.length === 0 && chatActivity.length === 0) return null;

  return (
    <div className="recap-section timeline-section">
      <h3 className="recap-section-title">Stream Timeline</h3>
      <div className="timeline-hint">Click or drag to explore</div>

      <div
        ref={containerRef}
        className="timeline-container"
        onPointerDown={handlePointerDown}
        onPointerMove={isDragging ? handlePointerMove : handleHover}
        onPointerUp={handlePointerUp}
        onPointerLeave={handlePointerLeave}
        style={{ touchAction: 'none' }}
      >
        {/* Chart area */}
        <svg
          width={containerWidth}
          height={CHART_HEIGHT}
          className="timeline-svg"
          viewBox={`0 0 ${containerWidth} ${CHART_HEIGHT}`}
          preserveAspectRatio="none"
        >
          <defs>
            <linearGradient id="viewerGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#6366f1" stopOpacity="0.25" />
              <stop offset="100%" stopColor="#6366f1" stopOpacity="0.02" />
            </linearGradient>
          </defs>

          {/* Viewer area */}
          {viewerAreaPath && (
            <path d={viewerAreaPath} fill="url(#viewerGrad)" />
          )}

          {/* Chat line */}
          {chatLinePath && (
            <path
              d={chatLinePath}
              fill="none"
              stroke="#14b8a6"
              strokeWidth="2"
              strokeLinejoin="round"
              opacity="0.7"
            />
          )}

          {/* Hype markers */}
          {hypePositions.map((hp, i) => (
            <circle
              key={`hype-${i}`}
              cx={hp.progress * containerWidth}
              cy={CHART_HEIGHT - 12}
              r={4}
              fill="#e11d48"
              stroke="#141414"
              strokeWidth="1.5"
              opacity="0.85"
            />
          ))}

          {/* Clip markers */}
          {clipPositions.map((cp, i) => (
            <g key={`clip-${i}`} transform={`translate(${cp.progress * containerWidth}, ${CHART_HEIGHT - 12})`}>
              <rect x={-4} y={-4} width={8} height={8} rx={2} fill="#7c3aed" stroke="#141414" strokeWidth="1.5" opacity="0.85" />
            </g>
          ))}

          {/* Scrubber line */}
          {scrubProgress !== null && (
            <line
              x1={scrubProgress * containerWidth}
              y1={0}
              x2={scrubProgress * containerWidth}
              y2={CHART_HEIGHT}
              stroke="#f3f3f3"
              strokeWidth="1"
              opacity="0.5"
            />
          )}
        </svg>

        {/* Y-axis labels */}
        {viewerPoints.length > 0 && (
          <div className="timeline-y-labels">
            <span>{formatNumber(Math.max(...snapshots.map(s => s.viewerCount)))}</span>
            <span>0</span>
          </div>
        )}

        {/* Game segment strip */}
        {gameSegments.length > 0 && (
          <div className="timeline-segments" style={{ height: SEGMENT_STRIP_HEIGHT }}>
            {gameSegments.map((seg, i) => {
              const left = timeToProgress(seg.startTime, rangeStart, rangeDuration) * 100;
              const right = timeToProgress(seg.endTime, rangeStart, rangeDuration) * 100;
              const width = Math.max(right - left, 0.5);
              const color = gameColorMap.get(seg.gameName) || SEGMENT_COLORS[0];
              return (
                <div
                  key={i}
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
        )}

        {/* Scrubber handle */}
        <AnimatePresence>
          {scrubProgress !== null && (
            <motion.div
              className="timeline-scrubber"
              style={{ left: `${scrubProgress * 100}%` }}
              initial={{ opacity: 0, scale: 0 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0 }}
              transition={{ duration: 0.15 }}
            />
          )}
        </AnimatePresence>

        {/* Time labels */}
        <div className="timeline-x-labels">
          {timeLabels.map((tl, i) => (
            <span key={i} style={{ left: `${tl.progress * 100}%` }}>{tl.label}</span>
          ))}
        </div>
      </div>

      {/* Detail panel — wrapper reserves space to prevent layout shift */}
      <div className="timeline-detail-wrapper">
        <AnimatePresence>
          {detail && (
            <motion.div
              className="timeline-detail"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.15 }}
            >
              <span className="detail-time">{detail.time}</span>
              {detail.game && <span className="detail-game">{detail.game}</span>}
              <div className="detail-metrics">
                {detail.viewers > 0 && (
                  <span className="detail-metric">
                    <span className="detail-dot" style={{ background: '#6366f1' }} />
                    {formatNumber(detail.viewers)} viewers
                  </span>
                )}
                <span className="detail-metric">
                  <span className="detail-dot" style={{ background: '#14b8a6' }} />
                  {formatNumber(detail.msgs)} msgs/5min
                </span>
                <span className="detail-metric detail-chatters">
                  {formatNumber(detail.chatters)} chatters
                </span>
                {detail.hypeMultiplier && (
                  <span className="detail-metric detail-hype">
                    <span className="detail-dot" style={{ background: '#e11d48' }} />
                    {detail.hypeMultiplier.toFixed(1)}x avg
                  </span>
                )}
              </div>
              {detail.clipTitle && (
                <div className="detail-clip-title">
                  <span className="detail-dot" style={{ background: '#7c3aed' }} />
                  {detail.clipTitle}
                </div>
              )}
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {/* Legend */}
      <div className="timeline-legend">
        {viewerPoints.length > 0 && (
          <span className="legend-item">
            <span className="legend-swatch" style={{ background: '#6366f1' }} /> Viewers
          </span>
        )}
        {chatPoints.length > 0 && (
          <span className="legend-item">
            <span className="legend-swatch legend-line" style={{ background: '#14b8a6' }} /> Chat
          </span>
        )}
        {hypePositions.length > 0 && (
          <span className="legend-item">
            <span className="legend-swatch legend-dot" style={{ background: '#e11d48' }} /> Hype
          </span>
        )}
        {clipPositions.length > 0 && (
          <span className="legend-item">
            <span className="legend-swatch legend-square" style={{ background: '#7c3aed' }} /> Clips
          </span>
        )}
      </div>
    </div>
  );
}
