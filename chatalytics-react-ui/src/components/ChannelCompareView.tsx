import { useEffect, useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import { ChannelProfile, ChannelStats } from '../types/message';
import { fetchChannelByLogin, fetchStats } from '../api/client';
import './ChannelCompareView.css';

const STREAM_COLORS = ['#6366f1', '#14b8a6', '#f59e0b'];

function formatNumber(n: number): string {
  if (n >= 1000000) return (n / 1000000).toFixed(1).replace(/\.0$/, '') + 'M';
  if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k';
  return n.toLocaleString();
}

function formatHour(hour: number): string {
  const suffix = hour >= 12 ? 'PM' : 'AM';
  const h = hour % 12 || 12;
  return `${h} ${suffix}`;
}

function formatDuration(mins: number): string {
  const hours = Math.floor(mins / 60);
  const m = Math.round(mins % 60);
  return hours > 0 ? `${hours}h ${m}m` : `${m}m`;
}

const sectionVariants = {
  hidden: { opacity: 0, y: 20 },
  visible: { opacity: 1, y: 0 },
};

interface ChannelData {
  profile: ChannelProfile;
  stats: ChannelStats;
}

interface Props {
  onBack: () => void;
  onChatterClick: (author: string) => void;
}

export default function ChannelCompareView({ onBack, onChatterClick }: Props) {
  const [channelData, setChannelData] = useState<(ChannelData | null)[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const logins = (params.get('ids') || '').split(',').filter(Boolean).slice(0, 3);

    if (logins.length < 2) {
      setLoading(false);
      return;
    }

    Promise.all(
      logins.map(async login => {
        const profile = await fetchChannelByLogin(login);
        if (!profile) return null;
        const stats = await fetchStats(profile.id);
        return { profile, stats };
      })
    ).then(results => {
      setChannelData(results);
      setLoading(false);
    });
  }, []);

  const validData = channelData.filter((d): d is ChannelData => d !== null);

  if (loading) {
    return (
      <div className="channel-compare-view">
        <button className="recap-back-btn" onClick={onBack}>
          <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
            <path d="M10 4L6 8l4 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          Channels
        </button>
        <section className="compare-hero" style={{ textAlign: 'center', maxWidth: 1120, margin: '0 auto 56px', padding: '40px 0 0' }}>
          <div className="cmp-skel" style={{ width: 160, height: 28, borderRadius: 20, margin: '0 auto 16px' }} />
          <div className="cmp-skel" style={{ width: 280, height: 40, margin: '0 auto 24px' }} />
          <div style={{ display: 'flex', justifyContent: 'center', gap: 24 }}>
            {Array.from({ length: 3 }, (_, i) => (
              <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <div className="cmp-skel" style={{ width: 24, height: 24, borderRadius: '50%' }} />
                <div className="cmp-skel" style={{ width: 80, height: 14 }} />
              </div>
            ))}
          </div>
        </section>
        <section className="recap-section">
          <div className="cmp-skel" style={{ width: 120, height: 14, marginBottom: 20 }} />
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12 }}>
            {Array.from({ length: 6 }, (_, i) => (
              <div key={i} className="cmp-skel" style={{ height: 80, borderRadius: 14 }} />
            ))}
          </div>
        </section>
      </div>
    );
  }

  if (validData.length < 2) {
    return (
      <div className="recap-error">
        <button className="recap-back-btn" onClick={onBack}>Back to directory</button>
        <p>Could not load enough channels for comparison.</p>
      </div>
    );
  }

  return (
    <motion.div
      className="channel-compare-view"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.3 }}
    >
      <button className="recap-back-btn" onClick={onBack}>
        <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
          <path d="M10 4L6 8l4 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        Channels
      </button>

      <HeroSection data={validData} />
      <MetricsGrid data={validData} />
      <TopChattersComparison data={validData} onChatterClick={onChatterClick} />
      <TopGamesComparison data={validData} />
      <PeakHourComparison data={validData} />
    </motion.div>
  );
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Hero
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

function HeroSection({ data }: { data: ChannelData[] }) {
  return (
    <motion.section
      className="compare-hero"
      variants={sectionVariants}
      initial="hidden"
      animate="visible"
      transition={{ duration: 0.4, delay: 0.05 }}
    >
      <span className="recap-duration-badge">
        Comparing {data.length} channels
      </span>
      <h1 className="recap-date">Channel Comparison</h1>
      <div className="compare-legend">
        {data.map((d, i) => (
          <div key={d.profile.id} className="channel-compare-legend-item">
            <span className="compare-legend-dot" style={{ background: STREAM_COLORS[i] }} />
            {d.profile.profileImageUrl && (
              <img src={d.profile.profileImageUrl} alt="" className="channel-compare-legend-avatar" />
            )}
            <span className="compare-legend-label">{d.profile.displayName}</span>
            {d.profile.broadcasterType && (
              <span className="channel-compare-legend-badge">
                {d.profile.broadcasterType.charAt(0).toUpperCase() + d.profile.broadcasterType.slice(1)}
              </span>
            )}
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
  getValue: (s: ChannelStats) => number | null;
  format: (n: number) => string;
  higher: 'better' | 'neutral';
}

const METRICS: MetricDef[] = [
  { label: 'Total Messages', getValue: s => s.totalMessages, format: formatNumber, higher: 'better' },
  { label: 'Unique Chatters', getValue: s => s.uniqueChatters, format: formatNumber, higher: 'better' },
  { label: 'Total Streams', getValue: s => s.totalSessions, format: formatNumber, higher: 'neutral' },
  { label: 'Avg Msgs / Stream', getValue: s => s.avgMessagesPerSession, format: n => formatNumber(Math.round(n)), higher: 'better' },
  { label: 'Avg Chatters / Stream', getValue: s => s.avgChattersPerSession, format: n => formatNumber(Math.round(n)), higher: 'better' },
  { label: 'Avg Duration', getValue: s => s.avgStreamDurationMinutes, format: n => formatDuration(n), higher: 'neutral' },
];

function MetricsGrid({ data }: { data: ChannelData[] }) {
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
          const values = data.map(d => metric.getValue(d.stats));
          const validValues = values.filter((v): v is number => v !== null);
          const maxVal = validValues.length > 0 ? Math.max(...validValues) : null;

          return (
            <div key={metric.label} className="compare-metric-card">
              <span className="compare-metric-label">{metric.label}</span>
              <div className="compare-metric-values">
                {data.map((d, i) => {
                  const val = metric.getValue(d.stats);
                  if (val === null) return null;
                  const isHighest = metric.higher === 'better' && validValues.length > 1 && val === maxVal;
                  return (
                    <div key={d.profile.id} className={`compare-metric-value${isHighest ? ' highest' : ''}`}>
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
// Top Chatters
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

function TopChattersComparison({ data, onChatterClick }: { data: ChannelData[]; onChatterClick: (author: string) => void }) {
  const hasChatters = data.some(d => d.stats.topChatters.length > 0);

  const sharedChatters = useMemo(() => {
    const counts = new Map<string, number>();
    data.forEach(d => {
      const seen = new Set<string>();
      d.stats.topChatters.forEach(c => {
        if (!seen.has(c.author)) {
          seen.add(c.author);
          counts.set(c.author, (counts.get(c.author) || 0) + 1);
        }
      });
    });
    return new Set([...counts.entries()].filter(([, c]) => c > 1).map(([name]) => name));
  }, [data]);

  if (!hasChatters) return null;

  return (
    <motion.section
      className="compare-section"
      variants={sectionVariants}
      initial="hidden"
      animate="visible"
      transition={{ duration: 0.4, delay: 0.2 }}
    >
      <h2 className="recap-section-title">Top Chatters</h2>
      {sharedChatters.size > 0 && (
        <div className="compare-shared-badge">
          {sharedChatters.size} shared chatter{sharedChatters.size !== 1 ? 's' : ''} across channels
        </div>
      )}
      <div className="compare-chatters-grid">
        {data.map((d, i) => (
          <div key={d.profile.id} className="compare-chatters-column">
            <div className="compare-column-header">
              <span className="compare-metric-dot" style={{ background: STREAM_COLORS[i] }} />
              {d.profile.displayName}
            </div>
            <div className="recap-chatters">
              {d.stats.topChatters.slice(0, 10).map((chatter, j) => (
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
                    <span className="compare-shared-icon" title="Appears in multiple channels">&#9679;</span>
                  )}
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    </motion.section>
  );
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Top Games
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

const GAME_COLORS = ['#6366f1', '#8b5cf6', '#06b6d4', '#f59e0b', '#e11d48', '#10b981', '#ec4899', '#3b82f6', '#14b8a6', '#f97316'];

function TopGamesComparison({ data }: { data: ChannelData[] }) {
  const hasGames = data.some(d => d.stats.topGames.length > 0);
  if (!hasGames) return null;

  // Collect all unique games across channels for consistent coloring
  const allGames = [...new Set(data.flatMap(d => d.stats.topGames.map(g => g.gameName)))];
  const gameColorMap = new Map(allGames.map((g, i) => [g, GAME_COLORS[i % GAME_COLORS.length]]));

  // Find max session count across all channels for normalization
  const maxCount = Math.max(1, ...data.flatMap(d => d.stats.topGames.map(g => g.sessionCount)));

  return (
    <motion.section
      className="compare-section"
      variants={sectionVariants}
      initial="hidden"
      animate="visible"
      transition={{ duration: 0.4, delay: 0.25 }}
    >
      <h2 className="recap-section-title">Top Games</h2>
      <div className="channel-compare-games-container">
        {data.map((d, i) => (
          <div key={d.profile.id} className="channel-compare-games-column">
            <div className="compare-column-header">
              <span className="compare-metric-dot" style={{ background: STREAM_COLORS[i] }} />
              {d.profile.displayName}
            </div>
            <div className="channel-compare-game-bars">
              {d.stats.topGames.slice(0, 8).map(game => {
                const pct = (game.sessionCount / maxCount) * 100;
                const color = gameColorMap.get(game.gameName) || GAME_COLORS[0];
                return (
                  <div key={game.gameName} className="channel-compare-game-row">
                    <span className="channel-compare-game-name" title={game.gameName}>{game.gameName}</span>
                    <div className="channel-compare-game-track">
                      <div
                        className="channel-compare-game-fill"
                        style={{ width: `${Math.max(pct, 3)}%`, background: color }}
                      />
                    </div>
                    <span className="channel-compare-game-count">{game.sessionCount}</span>
                  </div>
                );
              })}
            </div>
          </div>
        ))}
      </div>
    </motion.section>
  );
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Peak Hour
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

function PeakHourComparison({ data }: { data: ChannelData[] }) {
  const hasPeakHours = data.some(d => d.stats.peakHour !== null);
  if (!hasPeakHours) return null;

  return (
    <motion.section
      className="compare-section"
      variants={sectionVariants}
      initial="hidden"
      animate="visible"
      transition={{ duration: 0.4, delay: 0.3 }}
    >
      <h2 className="recap-section-title">Peak Chat Hours</h2>
      <div className="channel-compare-peak-hours">
        {/* 24-hour clock visualization */}
        <div className="channel-compare-clock">
          {Array.from({ length: 24 }, (_, hour) => {
            const activeChannels = data
              .map((d, i) => ({ peakHour: d.stats.peakHour, color: STREAM_COLORS[i], name: d.profile.displayName }))
              .filter(d => d.peakHour === hour);

            return (
              <div
                key={hour}
                className={`channel-compare-hour-slot${activeChannels.length > 0 ? ' active' : ''}`}
              >
                <span className="channel-compare-hour-label">{formatHour(hour)}</span>
                <div className="channel-compare-hour-dots">
                  {activeChannels.map((ch, j) => (
                    <span
                      key={j}
                      className="channel-compare-hour-dot"
                      style={{ background: ch.color }}
                      title={`${ch.name} peaks at ${formatHour(hour)}`}
                    />
                  ))}
                </div>
              </div>
            );
          })}
        </div>

        {/* Legend */}
        <div className="channel-compare-peak-legend">
          {data.map((d, i) => (
            d.stats.peakHour !== null && (
              <div key={d.profile.id} className="channel-compare-peak-item">
                <span className="compare-metric-dot" style={{ background: STREAM_COLORS[i] }} />
                <span className="channel-compare-peak-name">{d.profile.displayName}</span>
                <span className="channel-compare-peak-time">{formatHour(d.stats.peakHour)}</span>
              </div>
            )
          ))}
        </div>
      </div>
    </motion.section>
  );
}
