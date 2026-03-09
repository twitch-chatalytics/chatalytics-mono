import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  ChannelAuthenticityReport,
  AuthenticityTrendPoint,
  ChannelProfile,
  SocialBladeChannel,
} from '../types/message';
import {
  fetchChannels,
  fetchChannelAuthenticity,
  fetchAuthenticityTrend,
  fetchSocialBladeChannel,
} from '../api/client';
import { formatCompact, getScoreColor } from './advertiserUtils';

interface ChannelCompareData {
  channel: ChannelProfile;
  report: ChannelAuthenticityReport | null;
  trend: AuthenticityTrendPoint[];
  socialBlade: SocialBladeChannel | null;
  loading: boolean;
  error: string | null;
}

const MAX_CHANNELS = 5;

export default function AdvCompareView() {
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<ChannelProfile[]>([]);
  const [allChannels, setAllChannels] = useState<ChannelProfile[]>([]);
  const [channelsLoaded, setChannelsLoaded] = useState(false);
  const [compareData, setCompareData] = useState<ChannelCompareData[]>([]);
  const [showDropdown, setShowDropdown] = useState(false);
  const searchRef = useRef<HTMLDivElement>(null);

  // Load all tracked channels once
  useEffect(() => {
    fetchChannels().then(channels => {
      setAllChannels(channels);
      setChannelsLoaded(true);
    });
  }, []);

  // Close dropdown on outside click
  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (searchRef.current && !searchRef.current.contains(e.target as Node)) {
        setShowDropdown(false);
      }
    }
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  // Filter channels based on search query
  useEffect(() => {
    if (!searchQuery.trim()) {
      setSearchResults([]);
      return;
    }
    const q = searchQuery.toLowerCase();
    const addedIds = new Set(compareData.map(d => d.channel.id));
    const filtered = allChannels
      .filter(c => !addedIds.has(c.id))
      .filter(c =>
        c.displayName.toLowerCase().includes(q) ||
        c.login.toLowerCase().includes(q)
      )
      .slice(0, 8);
    setSearchResults(filtered);
  }, [searchQuery, allChannels, compareData]);

  const addChannel = useCallback((channel: ChannelProfile) => {
    if (compareData.length >= MAX_CHANNELS) return;
    if (compareData.some(d => d.channel.id === channel.id)) return;

    const entry: ChannelCompareData = {
      channel,
      report: null,
      trend: [],
      socialBlade: null,
      loading: true,
      error: null,
    };

    setCompareData(prev => [...prev, entry]);
    setSearchQuery('');
    setShowDropdown(false);

    // Fetch data in parallel
    Promise.all([
      fetchChannelAuthenticity(channel.id),
      fetchAuthenticityTrend(channel.id),
      fetchSocialBladeChannel(channel.id),
    ])
      .then(([report, trend, sb]) => {
        setCompareData(prev =>
          prev.map(d =>
            d.channel.id === channel.id
              ? { ...d, report, trend, socialBlade: sb, loading: false }
              : d
          )
        );
      })
      .catch(() => {
        setCompareData(prev =>
          prev.map(d =>
            d.channel.id === channel.id
              ? { ...d, loading: false, error: 'Failed to load data' }
              : d
          )
        );
      });
  }, [compareData]);

  const removeChannel = useCallback((channelId: number) => {
    setCompareData(prev => prev.filter(d => d.channel.id !== channelId));
  }, []);

  // Computed helpers
  const readyData = compareData.filter(d => !d.loading && d.report != null);

  const avgViewersFor = (d: ChannelCompareData): number | null => {
    const withViewers = d.trend.filter(t => t.viewerCount != null);
    if (withViewers.length === 0) return null;
    return Math.round(withViewers.reduce((sum, t) => sum + t.viewerCount!, 0) / withViewers.length);
  };

  const estRealViewersFor = (d: ChannelCompareData): number | null => {
    const avg = avgViewersFor(d);
    if (avg == null || d.report == null) return null;
    const score = d.report.avgAuthenticityScore ?? 0;
    return Math.round(avg * (score / 100));
  };

  const discountFor = (d: ChannelCompareData): number | null => {
    if (d.report == null || d.report.avgAuthenticityScore == null) return null;
    return 100 - Math.round(d.report.avgAuthenticityScore);
  };

  // Campaign summary
  const campaignSummary = useMemo(() => {
    if (readyData.length === 0) return null;

    let totalReach = 0;
    let totalReal = 0;
    let weightedScore = 0;
    let totalWeight = 0;
    let riskCount = 0;

    for (const d of readyData) {
      const avgV = avgViewersFor(d);
      const realV = estRealViewersFor(d);
      const score = d.report?.avgAuthenticityScore ?? 0;

      if (avgV != null) {
        totalReach += avgV;
        weightedScore += score * avgV;
        totalWeight += avgV;
      }
      if (realV != null) {
        totalReal += realV;
      }
      if (d.report && d.report.riskFactors.length > 0) {
        riskCount++;
      }
    }

    return {
      totalReach,
      totalReal,
      blendedScore: totalWeight > 0 ? Math.round(weightedScore / totalWeight) : 0,
      riskCount,
      channelCount: readyData.length,
    };
  }, [readyData]);

  // Determine best value per row for highlighting
  const bestValues = useMemo(() => {
    if (readyData.length < 2) return null;

    const scores = readyData.map(d => d.report?.avgAuthenticityScore ?? 0);
    const discounts = readyData.map(d => discountFor(d) ?? 100);
    const followers = readyData.map(d => d.socialBlade?.followers ?? 0);
    const avgViewersList = readyData.map(d => avgViewersFor(d) ?? 0);
    const realViewersList = readyData.map(d => estRealViewersFor(d) ?? 0);
    const sessionsList = readyData.map(d => d.report?.sessionsAnalyzed ?? 0);

    return {
      bestScoreIdx: scores.indexOf(Math.max(...scores)),
      bestDiscountIdx: discounts.indexOf(Math.min(...discounts)),
      bestFollowersIdx: followers.indexOf(Math.max(...followers)),
      bestAvgViewersIdx: avgViewersList.indexOf(Math.max(...avgViewersList)),
      bestRealViewersIdx: realViewersList.indexOf(Math.max(...realViewersList)),
      bestSessionsIdx: sessionsList.indexOf(Math.max(...sessionsList)),
    };
  }, [readyData]);

  const getRiskBadgeClass = (riskLevel: string) => {
    switch (riskLevel) {
      case 'low': return 'cmp-risk-low';
      case 'medium': return 'cmp-risk-medium';
      case 'high': return 'cmp-risk-high';
      default: return 'cmp-risk-low';
    }
  };

  const getGradeClass = (grade: string | null) => {
    if (!grade) return '';
    const g = grade.charAt(0).toUpperCase();
    if (g === 'A') return 'sb-grade-a';
    if (g === 'B') return 'sb-grade-b';
    if (g === 'C') return 'sb-grade-c';
    if (g === 'D') return 'sb-grade-d';
    return 'sb-grade-f';
  };

  const getTrendIcon = (dir: string) => {
    if (dir === 'improving') return '\u2191';
    if (dir === 'declining') return '\u2193';
    return '\u2192';
  };

  const getTrendColor = (dir: string) => {
    if (dir === 'improving') return '#22c55e';
    if (dir === 'declining') return '#ef4444';
    return '#888';
  };

  return (
    <div className="view-container">
      <h1 className="view-title">Compare Channels</h1>

      {/* Search / Add Bar */}
      <motion.div
        className="adv-card"
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
      >
        <h3 className="card-label">Add Channels to Compare</h3>
        <p className="card-desc">
          Search tracked channels to build a side-by-side comparison. Up to {MAX_CHANNELS} channels.
        </p>

        {/* Added channel pills */}
        {compareData.length > 0 && (
          <div className="cmp-pills">
            <AnimatePresence>
              {compareData.map(d => (
                <motion.div
                  key={d.channel.id}
                  className="cmp-pill"
                  initial={{ opacity: 0, scale: 0.8 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.8 }}
                  transition={{ duration: 0.15 }}
                >
                  {d.channel.profileImageUrl && (
                    <img src={d.channel.profileImageUrl} alt="" className="cmp-pill-avatar" />
                  )}
                  <span className="cmp-pill-name">{d.channel.displayName}</span>
                  {d.loading && <span className="cmp-pill-loading" />}
                  <button
                    className="cmp-pill-remove"
                    onClick={() => removeChannel(d.channel.id)}
                    aria-label={`Remove ${d.channel.displayName}`}
                  >
                    <svg width="12" height="12" viewBox="0 0 12 12" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
                      <line x1="3" y1="3" x2="9" y2="9" />
                      <line x1="9" y1="3" x2="3" y2="9" />
                    </svg>
                  </button>
                </motion.div>
              ))}
            </AnimatePresence>
          </div>
        )}

        {/* Search input */}
        {compareData.length < MAX_CHANNELS && (
          <div className="cmp-search-wrapper" ref={searchRef}>
            <div className="cmp-search-input-row">
              <svg className="cmp-search-icon" width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
                <circle cx="7" cy="7" r="4.5" />
                <line x1="10.5" y1="10.5" x2="14" y2="14" />
              </svg>
              <input
                className="cmp-search-input"
                type="text"
                placeholder={channelsLoaded ? 'Search tracked channels...' : 'Loading channels...'}
                value={searchQuery}
                onChange={e => {
                  setSearchQuery(e.target.value);
                  setShowDropdown(true);
                }}
                onFocus={() => {
                  if (searchQuery.trim()) setShowDropdown(true);
                }}
                disabled={!channelsLoaded}
              />
            </div>

            {/* Search dropdown */}
            {showDropdown && searchResults.length > 0 && (
              <div className="cmp-search-dropdown">
                {searchResults.map(ch => (
                  <button
                    key={ch.id}
                    className="cmp-search-result"
                    onClick={() => addChannel(ch)}
                  >
                    {ch.profileImageUrl && (
                      <img src={ch.profileImageUrl} alt="" className="cmp-search-result-avatar" />
                    )}
                    <div className="cmp-search-result-info">
                      <span className="cmp-search-result-name">{ch.displayName}</span>
                      <span className="cmp-search-result-login">{ch.login}</span>
                    </div>
                  </button>
                ))}
              </div>
            )}

            {showDropdown && searchQuery.trim() && searchResults.length === 0 && channelsLoaded && (
              <div className="cmp-search-dropdown">
                <div className="cmp-search-empty">No tracked channels match "{searchQuery}"</div>
              </div>
            )}
          </div>
        )}

        {compareData.length >= MAX_CHANNELS && (
          <p className="cmp-limit-msg">Maximum of {MAX_CHANNELS} channels reached. Remove one to add another.</p>
        )}
      </motion.div>

      {/* Comparison Table */}
      {readyData.length > 0 && (
        <motion.div
          className="adv-card cmp-table-card"
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: 0.05 }}
        >
          <h3 className="card-label">Side-by-Side Comparison</h3>
          <div className="cmp-table-scroll">
            <table className="cmp-table">
              <thead>
                <tr>
                  <th className="cmp-th-label" />
                  {readyData.map(d => (
                    <th key={d.channel.id} className="cmp-th-channel">
                      <div className="cmp-th-channel-inner">
                        {d.channel.profileImageUrl && (
                          <img src={d.channel.profileImageUrl} alt="" className="cmp-th-avatar" />
                        )}
                        <span className="cmp-th-name">{d.channel.displayName}</span>
                      </div>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {/* Authenticity Score */}
                <tr>
                  <td className="cmp-row-label">Authenticity Score</td>
                  {readyData.map((d, i) => {
                    const score = Math.round(d.report?.avgAuthenticityScore ?? 0);
                    const isBest = bestValues?.bestScoreIdx === i;
                    return (
                      <td key={d.channel.id} className={`cmp-cell ${isBest ? 'cmp-cell-best' : ''}`}>
                        <span className="cmp-score-value" style={{ color: getScoreColor(score) }}>
                          {score}
                        </span>
                      </td>
                    );
                  })}
                </tr>

                {/* Risk Level */}
                <tr>
                  <td className="cmp-row-label">Risk Level</td>
                  {readyData.map(d => (
                    <td key={d.channel.id} className="cmp-cell">
                      <span className={`cmp-risk-badge ${getRiskBadgeClass(d.report!.riskLevel)}`}>
                        {d.report!.riskLevel}
                      </span>
                    </td>
                  ))}
                </tr>

                {/* Avg Viewers */}
                <tr>
                  <td className="cmp-row-label">Avg Viewers</td>
                  {readyData.map((d, i) => {
                    const v = avgViewersFor(d);
                    const isBest = bestValues?.bestAvgViewersIdx === i;
                    return (
                      <td key={d.channel.id} className={`cmp-cell ${isBest ? 'cmp-cell-best' : ''}`}>
                        <span className="cmp-num-value">
                          {v != null ? formatCompact(v) : '\u2014'}
                        </span>
                      </td>
                    );
                  })}
                </tr>

                {/* Est. Real Viewers */}
                <tr>
                  <td className="cmp-row-label">Est. Real Viewers</td>
                  {readyData.map((d, i) => {
                    const rv = estRealViewersFor(d);
                    const score = d.report?.avgAuthenticityScore ?? 0;
                    const isBest = bestValues?.bestRealViewersIdx === i;
                    return (
                      <td key={d.channel.id} className={`cmp-cell ${isBest ? 'cmp-cell-best' : ''}`}>
                        <span className="cmp-num-value" style={{ color: getScoreColor(score) }}>
                          {rv != null ? formatCompact(rv) : '\u2014'}
                        </span>
                      </td>
                    );
                  })}
                </tr>

                {/* Audience Discount */}
                <tr>
                  <td className="cmp-row-label">Audience Discount</td>
                  {readyData.map((d, i) => {
                    const disc = discountFor(d);
                    const isBest = bestValues?.bestDiscountIdx === i;
                    return (
                      <td key={d.channel.id} className={`cmp-cell ${isBest ? 'cmp-cell-best' : ''}`}>
                        <span className={`cmp-discount-badge ${disc != null && disc > 30 ? 'av-discount-high' : disc != null && disc > 10 ? 'av-discount-med' : 'av-discount-low'}`}>
                          {disc != null ? `-${disc}%` : '\u2014'}
                        </span>
                      </td>
                    );
                  })}
                </tr>

                {/* Followers */}
                <tr>
                  <td className="cmp-row-label">Followers</td>
                  {readyData.map((d, i) => {
                    const f = d.socialBlade?.followers;
                    const isBest = bestValues?.bestFollowersIdx === i;
                    return (
                      <td key={d.channel.id} className={`cmp-cell ${isBest ? 'cmp-cell-best' : ''}`}>
                        <span className="cmp-num-value">
                          {f != null ? formatCompact(f) : '\u2014'}
                        </span>
                      </td>
                    );
                  })}
                </tr>

                {/* SocialBlade Grade */}
                <tr>
                  <td className="cmp-row-label">SocialBlade Grade</td>
                  {readyData.map(d => (
                    <td key={d.channel.id} className="cmp-cell">
                      <span className={`cmp-grade ${getGradeClass(d.socialBlade?.grade ?? null)}`}>
                        {d.socialBlade?.grade ?? '\u2014'}
                      </span>
                    </td>
                  ))}
                </tr>

                {/* Trend Direction */}
                <tr>
                  <td className="cmp-row-label">Trend Direction</td>
                  {readyData.map(d => (
                    <td key={d.channel.id} className="cmp-cell">
                      <span className="cmp-trend" style={{ color: getTrendColor(d.report!.trendDirection) }}>
                        {getTrendIcon(d.report!.trendDirection)} {d.report!.trendDirection}
                      </span>
                    </td>
                  ))}
                </tr>

                {/* Sessions Analyzed */}
                <tr>
                  <td className="cmp-row-label">Sessions Analyzed</td>
                  {readyData.map((d, i) => {
                    const isBest = bestValues?.bestSessionsIdx === i;
                    return (
                      <td key={d.channel.id} className={`cmp-cell ${isBest ? 'cmp-cell-best' : ''}`}>
                        <span className="cmp-num-value">{d.report!.sessionsAnalyzed}</span>
                      </td>
                    );
                  })}
                </tr>

                {/* Risk Factor Count */}
                <tr>
                  <td className="cmp-row-label">Risk Factors</td>
                  {readyData.map(d => {
                    const count = d.report!.riskFactors.length;
                    return (
                      <td key={d.channel.id} className="cmp-cell">
                        <span className="cmp-num-value" style={{ color: count === 0 ? '#22c55e' : count <= 2 ? '#eab308' : '#ef4444' }}>
                          {count}
                        </span>
                      </td>
                    );
                  })}
                </tr>
              </tbody>
            </table>
          </div>
        </motion.div>
      )}

      {/* Loading indicator for channels still fetching */}
      {compareData.some(d => d.loading) && (
        <div className="cmp-loading-row">
          <div className="recap-spinner" />
          <span>Loading channel data...</span>
        </div>
      )}

      {/* Campaign Summary */}
      {campaignSummary && campaignSummary.channelCount >= 2 && (
        <motion.div
          className="adv-card"
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: 0.1 }}
        >
          <h3 className="card-label">Campaign Summary</h3>
          <p className="card-desc">
            Aggregate metrics across {campaignSummary.channelCount} selected channels.
          </p>

          <div className="cmp-summary-grid">
            <div className="cmp-summary-item">
              <span className="cmp-summary-value">{formatCompact(campaignSummary.totalReach)}</span>
              <span className="cmp-summary-label">Total Combined Reach</span>
            </div>
            <div className="cmp-summary-item cmp-summary-item-highlight">
              <span className="cmp-summary-value" style={{ color: getScoreColor(campaignSummary.blendedScore) }}>
                {formatCompact(campaignSummary.totalReal)}
              </span>
              <span className="cmp-summary-label">Total Real Reach</span>
            </div>
            <div className="cmp-summary-item">
              <span className="cmp-summary-value" style={{ color: getScoreColor(campaignSummary.blendedScore) }}>
                {campaignSummary.blendedScore}
              </span>
              <span className="cmp-summary-label">Blended Authenticity</span>
            </div>
            <div className="cmp-summary-item">
              <span className="cmp-summary-value" style={{ color: campaignSummary.riskCount === 0 ? '#22c55e' : campaignSummary.riskCount <= 1 ? '#eab308' : '#ef4444' }}>
                {campaignSummary.riskCount}
              </span>
              <span className="cmp-summary-label">Channels w/ Risk Flags</span>
            </div>
          </div>
        </motion.div>
      )}

      {/* Empty state */}
      {compareData.length === 0 && (
        <motion.div
          className="adv-card"
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: 0.05 }}
        >
          <div className="cmp-empty-state">
            <svg width="48" height="48" viewBox="0 0 48 48" fill="none" stroke="#333" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
              <rect x="4" y="8" width="16" height="32" rx="3" />
              <rect x="28" y="8" width="16" height="32" rx="3" />
              <line x1="22" y1="18" x2="26" y2="18" />
              <line x1="22" y1="24" x2="26" y2="24" />
              <line x1="22" y1="30" x2="26" y2="30" />
            </svg>
            <p className="cmp-empty-title">No channels selected</p>
            <p className="cmp-empty-desc">Search and add tracked channels above to compare their authenticity metrics side-by-side.</p>
          </div>
        </motion.div>
      )}
    </div>
  );
}
