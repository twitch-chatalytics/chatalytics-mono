import { useEffect, useState } from 'react';
import {
  ChannelAuthenticityReport,
  AuthenticityTrendPoint,
  ChannelProfile,
  SocialBladeChannel,
} from '../types/message';
import {
  fetchChannelAuthenticity,
  fetchAuthenticityTrend,
  fetchChannelByLogin,
  fetchSocialBladeChannel,
} from '../api/client';
import { formatCompact, getScoreColor } from './advertiserUtils';
import './ChannelReport.css';

interface ChannelReportProps {
  channelLogin: string;
}

function getVerdict(score: number): { label: string; className: string } {
  if (score >= 70) return { label: 'Recommended', className: 'verdict-recommended' };
  if (score >= 40) return { label: 'Proceed with Caution', className: 'verdict-caution' };
  return { label: 'Avoid', className: 'verdict-avoid' };
}

function getRiskLabel(level: string): string {
  switch (level?.toLowerCase()) {
    case 'low': return 'Low';
    case 'medium': return 'Medium';
    case 'high': return 'High';
    default: return level || 'N/A';
  }
}

function getRiskClassName(level: string): string {
  switch (level?.toLowerCase()) {
    case 'low': return 'report-risk-low';
    case 'medium': return 'report-risk-medium';
    case 'high': return 'report-risk-high';
    default: return '';
  }
}

function getSbGradeColor(grade: string | null): string {
  if (!grade) return '#666';
  const g = grade.toUpperCase().charAt(0);
  switch (g) {
    case 'A': return '#22c55e';
    case 'B': return '#4ade80';
    case 'C': return '#eab308';
    case 'D': return '#f97316';
    default: return '#ef4444';
  }
}

/** Tiny inline sparkline SVG */
function Sparkline({ data }: { data: AuthenticityTrendPoint[] }) {
  if (data.length < 2) return null;

  const w = 320;
  const h = 64;
  const pad = { top: 6, right: 6, bottom: 6, left: 6 };
  const cw = w - pad.left - pad.right;
  const ch = h - pad.top - pad.bottom;

  const scores = data.map(d => d.score);
  const min = Math.max(0, Math.min(...scores) - 5);
  const max = Math.min(100, Math.max(...scores) + 5);
  const range = max - min || 1;

  const x = (i: number) => pad.left + (i / (data.length - 1)) * cw;
  const y = (v: number) => pad.top + ch - ((v - min) / range) * ch;

  const line = data.map((d, i) => `${i === 0 ? 'M' : 'L'} ${x(i).toFixed(1)} ${y(d.score).toFixed(1)}`).join(' ');
  const area = `${line} L ${x(data.length - 1).toFixed(1)} ${(pad.top + ch).toFixed(1)} L ${pad.left.toFixed(1)} ${(pad.top + ch).toFixed(1)} Z`;

  const lastScore = scores[scores.length - 1];
  const color = getScoreColor(lastScore);

  return (
    <svg className="report-sparkline" viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="xMidYMid meet">
      <defs>
        <linearGradient id="sparkFill" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity={0.15} />
          <stop offset="100%" stopColor={color} stopOpacity={0} />
        </linearGradient>
      </defs>
      <path d={area} fill="url(#sparkFill)" />
      <path d={line} fill="none" stroke={color} strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />
      {/* Last point indicator */}
      <circle cx={x(data.length - 1)} cy={y(lastScore)} r={3} fill={color} />
    </svg>
  );
}

export default function ChannelReport({ channelLogin }: ChannelReportProps) {
  const [channel, setChannel] = useState<ChannelProfile | null>(null);
  const [report, setReport] = useState<ChannelAuthenticityReport | null>(null);
  const [trend, setTrend] = useState<AuthenticityTrendPoint[]>([]);
  const [sbChannel, setSbChannel] = useState<SocialBladeChannel | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    setError(null);

    fetchChannelByLogin(channelLogin).then(ch => {
      if (!ch) {
        setError('Channel not found');
        setLoading(false);
        return;
      }
      setChannel(ch);

      Promise.all([
        fetchChannelAuthenticity(ch.id),
        fetchAuthenticityTrend(ch.id),
        fetchSocialBladeChannel(ch.id),
      ]).then(([channelReport, trendData, sbData]) => {
        setReport(channelReport);
        setTrend(trendData);
        setSbChannel(sbData);
        setLoading(false);
      }).catch(() => {
        setError('Failed to load report data');
        setLoading(false);
      });
    });
  }, [channelLogin]);

  if (loading) {
    return (
      <div className="report-loading">
        <div className="recap-spinner" />
        Generating report...
      </div>
    );
  }

  if (error || !report || !channel) {
    return (
      <div className="report-error">
        {error || 'Unable to generate report. No authenticity data available.'}
      </div>
    );
  }

  const avg = Math.round(report.avgAuthenticityScore ?? 0);
  const verdict = getVerdict(avg);
  const generatedDate = new Date().toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });

  // Compute average viewers from trend data
  const avgViewers = (() => {
    const viewerPoints = trend.filter(t => t.viewerCount != null);
    if (viewerPoints.length === 0) return null;
    return Math.round(viewerPoints.reduce((s, t) => s + (t.viewerCount ?? 0), 0) / viewerPoints.length);
  })();

  // Estimated real viewers (authenticity % of avg viewers)
  const estRealViewers = avgViewers != null ? Math.round(avgViewers * (avg / 100)) : null;

  const handlePrint = () => window.print();

  return (
    <div className="report-page">
      <div className="report-container">
        {/* Print button (screen only) */}
        <button className="report-print-btn" onClick={handlePrint}>
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="4,6 4,1 12,1 12,6" />
            <rect x="1" y="6" width="14" height="7" rx="1" />
            <rect x="4" y="10" width="8" height="5" rx="0.5" />
          </svg>
          Print / Save PDF
        </button>

        {/* ── Header ── */}
        <header className="report-header">
          <div className="report-header-left">
            {channel.profileImageUrl && (
              <img src={channel.profileImageUrl} alt="" className="report-avatar" />
            )}
            <div>
              <h1 className="report-channel-name">{channel.displayName}</h1>
              <p className="report-subtitle">Channel Authenticity Report</p>
            </div>
          </div>
          <div className="report-header-right">
            <span className="report-date">{generatedDate}</span>
          </div>
        </header>

        {/* ── Verdict ── */}
        <div className={`report-verdict ${verdict.className}`}>
          <span className="report-verdict-label">{verdict.label}</span>
          <span className="report-verdict-score">Score: {avg}/100</span>
        </div>

        {/* ── Key Metrics ── */}
        <section className="report-section">
          <h2 className="report-section-title">Key Metrics</h2>
          <div className="report-metrics-row">
            <div className="report-metric">
              <span className="report-metric-value" style={{ color: getScoreColor(avg) }}>{avg}</span>
              <span className="report-metric-label">Authenticity Score</span>
            </div>
            <div className="report-metric">
              <span className={`report-metric-value report-metric-risk ${getRiskClassName(report.riskLevel)}`}>
                {getRiskLabel(report.riskLevel)}
              </span>
              <span className="report-metric-label">Risk Level</span>
            </div>
            <div className="report-metric">
              <span className="report-metric-value">
                {avgViewers != null ? formatCompact(avgViewers) : 'N/A'}
              </span>
              <span className="report-metric-label">Avg Viewers</span>
            </div>
            <div className="report-metric">
              <span className="report-metric-value" style={{ color: estRealViewers != null ? getScoreColor(avg) : undefined }}>
                {estRealViewers != null ? formatCompact(estRealViewers) : 'N/A'}
              </span>
              <span className="report-metric-label">Est. Real Viewers</span>
            </div>
            <div className="report-metric">
              <span className="report-metric-value">{report.sessionsAnalyzed}</span>
              <span className="report-metric-label">Sessions Analyzed</span>
            </div>
          </div>
        </section>

        {/* ── Score Trend ── */}
        {trend.length >= 2 && (
          <section className="report-section">
            <h2 className="report-section-title">
              Score Trend
              <span className="report-trend-direction">
                {report.trendDirection === 'improving' && 'Improving'}
                {report.trendDirection === 'declining' && 'Declining'}
                {report.trendDirection === 'stable' && 'Stable'}
              </span>
            </h2>
            <div className="report-sparkline-wrap">
              <Sparkline data={trend} />
              <div className="report-sparkline-labels">
                <span>{new Date(trend[0].date).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}</span>
                <span>{new Date(trend[trend.length - 1].date).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}</span>
              </div>
            </div>
          </section>
        )}

        {/* ── Growth Summary ── */}
        {sbChannel && (
          <section className="report-section">
            <h2 className="report-section-title">Growth Summary</h2>
            <div className="report-growth-grid">
              {sbChannel.followers != null && (
                <div className="report-growth-item">
                  <span className="report-growth-value">{formatCompact(sbChannel.followers)}</span>
                  <span className="report-growth-label">Followers</span>
                </div>
              )}
              {sbChannel.grade && (
                <div className="report-growth-item">
                  <span className="report-growth-value" style={{ color: getSbGradeColor(sbChannel.grade) }}>
                    {sbChannel.grade}
                  </span>
                  <span className="report-growth-label">SocialBlade Grade</span>
                </div>
              )}
              {sbChannel.followersGained30d != null && (
                <div className="report-growth-item">
                  <span className={`report-growth-value ${sbChannel.followersGained30d >= 0 ? 'report-gain-positive' : 'report-gain-negative'}`}>
                    {sbChannel.followersGained30d >= 0 ? '+' : ''}{formatCompact(sbChannel.followersGained30d)}
                  </span>
                  <span className="report-growth-label">30-Day Gain</span>
                </div>
              )}
              {sbChannel.followersGained90d != null && (
                <div className="report-growth-item">
                  <span className={`report-growth-value ${sbChannel.followersGained90d >= 0 ? 'report-gain-positive' : 'report-gain-negative'}`}>
                    {sbChannel.followersGained90d >= 0 ? '+' : ''}{formatCompact(sbChannel.followersGained90d)}
                  </span>
                  <span className="report-growth-label">90-Day Gain</span>
                </div>
              )}
            </div>
          </section>
        )}

        {/* ── Risk Factors ── */}
        <section className="report-section">
          <h2 className="report-section-title">Risk Factors</h2>
          {report.riskFactors.length > 0 ? (
            <ul className="report-risk-list">
              {report.riskFactors.map((factor, i) => (
                <li key={i} className="report-risk-item">
                  <span className="report-risk-icon">!</span>
                  <span>{factor}</span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="report-no-risks">No risk factors identified. All signals within expected ranges.</p>
          )}
        </section>

        {/* ── Score Range Context ── */}
        {report.minAuthenticityScore != null && report.maxAuthenticityScore != null && (
          <section className="report-section report-score-range-section">
            <div className="report-score-range-bar">
              <div className="report-range-zone report-range-avoid" />
              <div className="report-range-zone report-range-caution" />
              <div className="report-range-zone report-range-good" />
              <div
                className="report-range-marker"
                style={{ left: `${avg}%` }}
              >
                <span className="report-range-marker-label">{avg}</span>
              </div>
            </div>
            <div className="report-range-labels">
              <span>0 - Avoid</span>
              <span>40 - Caution</span>
              <span>70 - Recommended</span>
              <span>100</span>
            </div>
          </section>
        )}

        {/* ── Footer ── */}
        <footer className="report-footer">
          <div className="report-footer-brand">
            <span className="report-footer-logo">chatalytics</span>
            <span className="report-footer-tagline">Audience Authenticity Intelligence</span>
          </div>
          <span className="report-footer-date">Generated {generatedDate}</span>
        </footer>
      </div>
    </div>
  );
}
