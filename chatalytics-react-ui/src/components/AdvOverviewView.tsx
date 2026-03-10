import { useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import { ChannelAuthenticityReport, AuthenticityTrendPoint, ChannelBenchmark } from '../types/message';
import { interpretChannelScore, formatCompact, getScoreColor } from './advertiserUtils';
import AuthenticityGauge from './AuthenticityGauge';
import AuthenticityTrendChart from './AuthenticityTrendChart';

interface AdvOverviewViewProps {
  report: ChannelAuthenticityReport;
  trend: AuthenticityTrendPoint[];
  avg: number;
  benchmark: ChannelBenchmark | null;
}

export default function AdvOverviewView({ report, trend, avg, benchmark }: AdvOverviewViewProps) {
  const [dealPrice, setDealPrice] = useState('');
  const [streamCount, setStreamCount] = useState('1');

  const avgViewers = useMemo(() => {
    const withViewers = trend.filter(t => t.viewerCount != null);
    if (withViewers.length === 0) return null;
    return Math.round(withViewers.reduce((sum, t) => sum + t.viewerCount!, 0) / withViewers.length);
  }, [trend]);

  const estimatedRealViewers = avgViewers != null ? Math.round(avgViewers * (avg / 100)) : null;
  const audienceDiscount = 100 - avg;

  const price = parseFloat(dealPrice);
  const streams = parseInt(streamCount, 10);
  const hasCpmInputs = !isNaN(price) && price > 0 && !isNaN(streams) && streams > 0 && avgViewers != null && avgViewers > 0;

  const reportedCpm = hasCpmInputs ? (price / (avgViewers! * streams)) * 1000 : null;
  const realCpm = hasCpmInputs && estimatedRealViewers && estimatedRealViewers > 0
    ? (price / (estimatedRealViewers * streams)) * 1000
    : null;
  const overpayPct = reportedCpm != null && realCpm != null && reportedCpm > 0
    ? Math.round(((realCpm - reportedCpm) / reportedCpm) * 100)
    : null;

  return (
    <div className="view-container">
      <h1 className="view-title">Overview</h1>

      {/* Score + Stats Card */}
      <motion.div
        className="adv-card"
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
      >
        <div className="overview-score-layout">
          <div className="overview-gauge">
            <AuthenticityGauge score={avg} size={200} />
            <div className="overview-score-label">Authenticity Score</div>
          </div>
          <div className="overview-stats">
            <div className="overview-stat">
              <span className="overview-stat-value">{report.minAuthenticityScore ?? '\u2014'}</span>
              <span className="overview-stat-label">Lowest</span>
            </div>
            <div className="overview-stat">
              <span className="overview-stat-value">{report.maxAuthenticityScore ?? '\u2014'}</span>
              <span className="overview-stat-label">Highest</span>
            </div>
            <div className="overview-stat">
              <span className={`overview-stat-value`} style={{ color: report.trendDirection === 'improving' ? '#22c55e' : report.trendDirection === 'declining' ? '#ef4444' : '#888' }}>
                {report.trendDirection === 'improving' ? '\u2191' : report.trendDirection === 'declining' ? '\u2193' : '\u2192'}
                {' '}{report.trendDirection}
              </span>
              <span className="overview-stat-label">Trend</span>
            </div>
            <div className="overview-stat">
              <span className="overview-stat-value">{report.sessionsAnalyzed}</span>
              <span className="overview-stat-label">Sessions</span>
            </div>
          </div>
        </div>
      </motion.div>

      {/* Assessment */}
      <motion.div
        className="adv-card overview-assessment"
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3, delay: 0.05 }}
      >
        <h3 className="card-label">Assessment</h3>
        <p className="assessment-text">{interpretChannelScore(avg, report)}</p>
        {report.sessionsAnalyzed < 5 && (
          <p className="assessment-caveat">
            Based on {report.sessionsAnalyzed} {report.sessionsAnalyzed === 1 ? 'session' : 'sessions'}.
            Scores become more reliable with at least 5 completed streams.
          </p>
        )}
      </motion.div>

      {/* Benchmark */}
      {benchmark && benchmark.channelsInTier > 0 && (
        <motion.div
          className="adv-card"
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: 0.07 }}
        >
          <h3 className="card-label">Peer Benchmark</h3>

          <div className="bench-top-row">
            <div className="bench-percentile-block">
              <span
                className="bench-percentile-value"
                style={{
                  color: benchmark.percentileRank >= 75 ? '#22c55e'
                    : benchmark.percentileRank >= 50 ? '#eab308'
                    : '#ef4444',
                }}
              >
                Top {100 - benchmark.percentileRank}%
              </span>
              <span className="bench-percentile-sub">of all tracked channels</span>
            </div>
            <div className="bench-tier-block">
              <span className="bench-tier-badge">{benchmark.viewerTier}</span>
              <span className="bench-tier-sub">
                {benchmark.channelsInTier} channel{benchmark.channelsInTier !== 1 ? 's' : ''} in tier
              </span>
            </div>
          </div>

          <div className="bench-bars">
            <BenchmarkBar
              label="This Channel"
              value={avg}
              maxValue={100}
              color={getScoreColor(avg)}
              highlight
            />
            <BenchmarkBar
              label={`${capitalize(benchmark.viewerTier)} Tier Avg`}
              value={Math.round(benchmark.tierAvgScore)}
              maxValue={100}
              color="#818cf8"
            />
            <BenchmarkBar
              label="Global Avg"
              value={Math.round(benchmark.globalAvgScore)}
              maxValue={100}
              color="#555"
            />
          </div>

          {benchmark.primaryCategory && benchmark.categoryAvgScore != null && benchmark.channelsInCategory > 1 && (
            <div className="bench-category-section">
              <div className="bench-category-header">
                <span className="bench-category-name">{benchmark.primaryCategory}</span>
                <span className="bench-category-count">{benchmark.channelsInCategory} channels</span>
              </div>
              <BenchmarkBar
                label="Category Avg"
                value={Math.round(benchmark.categoryAvgScore)}
                maxValue={100}
                color="#a78bfa"
              />
              <p className="bench-category-note">
                {avg >= benchmark.categoryAvgScore
                  ? `This channel scores ${Math.round(avg - benchmark.categoryAvgScore)} points above the average for ${benchmark.primaryCategory} streamers.`
                  : `This channel scores ${Math.round(benchmark.categoryAvgScore - avg)} points below the average for ${benchmark.primaryCategory} streamers.`
                }
              </p>
            </div>
          )}
        </motion.div>
      )}

      {/* Audience Value */}
      {avgViewers != null && (
        <motion.div
          className="adv-card"
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: 0.1 }}
        >
          <h3 className="card-label">Audience Value</h3>
          <p className="card-desc">
            Estimated real audience size based on authenticity analysis across {report.sessionsAnalyzed} sessions.
          </p>

          {/* Reported vs Real Viewers */}
          <div className="av-viewers-row">
            <div className="av-viewer-block">
              <span className="av-viewer-value">{formatCompact(avgViewers)}</span>
              <span className="av-viewer-label">Avg Reported Viewers</span>
            </div>
            <div className="av-arrow">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <line x1="5" y1="12" x2="19" y2="12" />
                <polyline points="14,7 19,12 14,17" />
              </svg>
            </div>
            <div className="av-viewer-block av-viewer-block-real">
              <span className="av-viewer-value" style={{ color: getScoreColor(avg) }}>
                {estimatedRealViewers != null ? formatCompact(estimatedRealViewers) : '\u2014'}
              </span>
              <span className="av-viewer-label">Est. Real Viewers</span>
            </div>
            <div className="av-discount-block">
              <span className={`av-discount-badge ${audienceDiscount > 30 ? 'av-discount-high' : audienceDiscount > 10 ? 'av-discount-med' : 'av-discount-low'}`}>
                {audienceDiscount > 0 ? `-${audienceDiscount}%` : 'No discount'}
              </span>
              <span className="av-viewer-label">Audience Discount</span>
            </div>
          </div>

          {/* CPM Calculator */}
          <div className="av-cpm-section">
            <h4 className="av-cpm-title">CPM Calculator</h4>
            <p className="av-cpm-desc">
              Enter your deal terms to see the true cost per 1,000 real viewers.
            </p>

            <div className="av-cpm-inputs">
              <div className="av-input-group">
                <label className="av-input-label">Deal Price ($)</label>
                <input
                  type="number"
                  className="av-input"
                  placeholder="5000"
                  value={dealPrice}
                  onChange={e => setDealPrice(e.target.value)}
                  min="0"
                  step="100"
                />
              </div>
              <div className="av-input-group">
                <label className="av-input-label">Streams in Deal</label>
                <input
                  type="number"
                  className="av-input"
                  placeholder="1"
                  value={streamCount}
                  onChange={e => setStreamCount(e.target.value)}
                  min="1"
                  step="1"
                />
              </div>
            </div>

            {hasCpmInputs && reportedCpm != null && realCpm != null && (
              <motion.div
                className="av-cpm-results"
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.2 }}
              >
                <div className="av-cpm-result">
                  <span className="av-cpm-value">${reportedCpm.toFixed(2)}</span>
                  <span className="av-cpm-label">Reported CPM</span>
                </div>
                <div className="av-cpm-result av-cpm-result-real">
                  <span className="av-cpm-value" style={{ color: getScoreColor(avg) }}>
                    ${realCpm.toFixed(2)}
                  </span>
                  <span className="av-cpm-label">Real CPM</span>
                </div>
                {overpayPct != null && overpayPct > 0 && (
                  <div className="av-cpm-result">
                    <span className={`av-overpay-badge ${overpayPct > 50 ? 'av-overpay-high' : overpayPct > 20 ? 'av-overpay-med' : 'av-overpay-low'}`}>
                      +{overpayPct}%
                    </span>
                    <span className="av-cpm-label">Overpay</span>
                  </div>
                )}
              </motion.div>
            )}
          </div>
        </motion.div>
      )}

      {/* Score History */}
      {trend.length >= 2 && (
        <motion.div
          className="adv-card"
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: 0.15 }}
        >
          <h3 className="card-label">Score History</h3>
          <AuthenticityTrendChart data={trend} />
        </motion.div>
      )}

      {/* Channel Risk Factors */}
      {report.riskFactors.length > 0 && (
        <motion.div
          className="adv-card"
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: 0.2 }}
        >
          <h3 className="card-label">Risk Factors</h3>
          <div className="flags-list">
            {report.riskFactors.map((f, i) => (
              <div key={i} className="flag-item">
                <span className="flag-icon">{'\u26A0'}</span>
                <div className="flag-content">
                  <span className="flag-detail">{f}</span>
                </div>
              </div>
            ))}
          </div>
        </motion.div>
      )}
    </div>
  );
}

/* ─── Benchmark Bar helper ─── */

function BenchmarkBar({
  label,
  value,
  maxValue,
  color,
  highlight,
}: {
  label: string;
  value: number;
  maxValue: number;
  color: string;
  highlight?: boolean;
}) {
  const pct = Math.max(0, Math.min(100, (value / maxValue) * 100));
  return (
    <div className={`bench-bar-row ${highlight ? 'bench-bar-highlight' : ''}`}>
      <span className="bench-bar-label">{label}</span>
      <div className="bench-bar-track">
        <motion.div
          className="bench-bar-fill"
          style={{ backgroundColor: color }}
          initial={{ width: 0 }}
          animate={{ width: `${pct}%` }}
          transition={{ duration: 0.8, ease: 'easeOut' }}
        />
      </div>
      <span className="bench-bar-value" style={{ color: highlight ? color : '#999' }}>
        {value}
      </span>
    </div>
  );
}

function capitalize(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}
