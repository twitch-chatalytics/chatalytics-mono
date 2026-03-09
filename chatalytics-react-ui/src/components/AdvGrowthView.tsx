import { motion } from 'framer-motion';
import { SocialBladeChannel, SocialBladeDailyPoint } from '../types/message';
import { formatCompact, hasSocialLinks } from './advertiserUtils';
import { GrowthAnomalyResult } from '../util/growthAnomalyDetector';
import SocialBladeDailyChart from './SocialBladeDailyChart';
import GradeAuthenticityCorrelation from './GradeAuthenticityCorrelation';

interface AdvGrowthViewProps {
  avg: number;
  sbChannel: SocialBladeChannel | null;
  sbDaily: SocialBladeDailyPoint[];
  growthAnalysis: GrowthAnomalyResult;
}

export default function AdvGrowthView({ avg, sbChannel, sbDaily, growthAnalysis }: AdvGrowthViewProps) {
  if (!sbChannel) {
    return (
      <div className="view-container">
        <h1 className="view-title">Growth Intel</h1>
        <div className="adv-card">
          <p className="empty-text">No external growth data available for this channel.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="view-container">
      <h1 className="view-title">Growth Intel</h1>

      {/* SB Overview Cards */}
      <motion.div
        className="adv-card"
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
      >
        <h3 className="card-label">External Metrics</h3>
        <p className="card-desc">Growth and ranking data from SocialBlade.</p>
        <div className="sb-overview-row">
          {sbChannel.grade && (
            <div className="sb-grade-card">
              <span className={`sb-grade sb-grade-${sbChannel.grade.charAt(0).toLowerCase()}`}>
                {sbChannel.grade}
              </span>
              <span className="sb-grade-label">SB Grade</span>
            </div>
          )}
          {sbChannel.rank != null && (
            <div className="sb-stat-card">
              <span className="sb-stat-value">#{sbChannel.rank.toLocaleString()}</span>
              <span className="sb-stat-label">Overall Rank</span>
            </div>
          )}
          {sbChannel.followerRank != null && (
            <div className="sb-stat-card">
              <span className="sb-stat-value">#{sbChannel.followerRank.toLocaleString()}</span>
              <span className="sb-stat-label">Follower Rank</span>
            </div>
          )}
          {sbChannel.followers != null && (
            <div className="sb-stat-card">
              <span className="sb-stat-value">{formatCompact(sbChannel.followers)}</span>
              <span className="sb-stat-label">Followers</span>
            </div>
          )}
          {sbChannel.views != null && (
            <div className="sb-stat-card">
              <span className="sb-stat-value">{formatCompact(sbChannel.views)}</span>
              <span className="sb-stat-label">Total Views</span>
            </div>
          )}
        </div>

        {sbChannel.grade && (
          <GradeAuthenticityCorrelation grade={sbChannel.grade} authenticityScore={avg} />
        )}
      </motion.div>

      {/* Follower Growth */}
      {(sbChannel.followersGained30d != null || sbChannel.followersGained90d != null) && (
        <motion.div
          className="adv-card"
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: 0.05 }}
        >
          <h3 className="card-label">Follower Growth</h3>
          <div className="sb-gains-row">
            {sbChannel.followersGained30d != null && (
              <div className="sb-gain-card">
                <span className={`sb-gain-value ${sbChannel.followersGained30d >= 0 ? 'sb-gain-positive' : 'sb-gain-negative'}`}>
                  {sbChannel.followersGained30d >= 0 ? '+' : ''}{sbChannel.followersGained30d.toLocaleString()}
                </span>
                <span className="sb-gain-label">Last 30 days</span>
              </div>
            )}
            {sbChannel.followersGained90d != null && (
              <div className="sb-gain-card">
                <span className={`sb-gain-value ${sbChannel.followersGained90d >= 0 ? 'sb-gain-positive' : 'sb-gain-negative'}`}>
                  {sbChannel.followersGained90d >= 0 ? '+' : ''}{sbChannel.followersGained90d.toLocaleString()}
                </span>
                <span className="sb-gain-label">Last 90 days</span>
              </div>
            )}
            {sbChannel.followersGained180d != null && (
              <div className="sb-gain-card">
                <span className={`sb-gain-value ${sbChannel.followersGained180d >= 0 ? 'sb-gain-positive' : 'sb-gain-negative'}`}>
                  {sbChannel.followersGained180d >= 0 ? '+' : ''}{sbChannel.followersGained180d.toLocaleString()}
                </span>
                <span className="sb-gain-label">Last 180 days</span>
              </div>
            )}
          </div>
        </motion.div>
      )}

      {/* Daily Chart */}
      {sbDaily.length >= 2 && (
        <motion.div
          className="adv-card"
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: 0.1 }}
        >
          <h3 className="card-label">Follower History</h3>
          <SocialBladeDailyChart data={sbDaily} anomalies={growthAnalysis.anomalies} />
        </motion.div>
      )}

      {/* Growth Risk */}
      {sbDaily.length >= 7 && (
        <motion.div
          className="adv-card"
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: 0.15 }}
        >
          <h3 className="card-label">Growth Risk Analysis</h3>
          <p className="card-desc">Automated analysis of daily follower patterns to detect anomalies.</p>

          <div className="growth-risk-summary">
            <div className={`growth-risk-score ${growthAnalysis.riskScore === 0 ? 'growth-risk-clean' : growthAnalysis.riskScore < 40 ? 'growth-risk-low' : growthAnalysis.riskScore < 70 ? 'growth-risk-med' : 'growth-risk-high'}`}>
              {growthAnalysis.riskScore === 0 ? 'Clean' : `Risk: ${growthAnalysis.riskScore}/100`}
            </div>
            <p className="growth-risk-text">{growthAnalysis.summary}</p>
          </div>

          {growthAnalysis.anomalies.length > 0 ? (
            <div className="flags-list">
              {growthAnalysis.anomalies.map((a, i) => (
                <div key={i} className={`flag-item ${a.severity === 'critical' ? 'flag-item-critical' : 'flag-item-warning'}`}>
                  <span className="flag-icon">
                    {a.type === 'spike' ? '\u2B06' : a.type === 'purge_cycle' ? '\u21C5' : '\u2500'}
                  </span>
                  <div className="flag-content">
                    <span className="flag-name">
                      {a.type === 'spike' ? 'Follower Spike' : a.type === 'purge_cycle' ? 'Spike & Purge' : 'Linear Growth'}
                    </span>
                    <span className="flag-detail">{a.detail}</span>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="flags-empty">No unusual growth patterns detected.</div>
          )}
        </motion.div>
      )}

      {/* Social Links */}
      {hasSocialLinks(sbChannel) && (
        <motion.div
          className="adv-card"
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: 0.2 }}
        >
          <h3 className="card-label">Social Links</h3>
          <div className="sb-social-links" style={{ borderTop: 'none', paddingTop: 0 }}>
            {sbChannel.twitterUrl && <a href={sbChannel.twitterUrl} target="_blank" rel="noopener noreferrer" className="sb-social-link">Twitter / X</a>}
            {sbChannel.youtubeUrl && <a href={sbChannel.youtubeUrl} target="_blank" rel="noopener noreferrer" className="sb-social-link">YouTube</a>}
            {sbChannel.instagramUrl && <a href={sbChannel.instagramUrl} target="_blank" rel="noopener noreferrer" className="sb-social-link">Instagram</a>}
            {sbChannel.discordUrl && <a href={sbChannel.discordUrl} target="_blank" rel="noopener noreferrer" className="sb-social-link">Discord</a>}
            {sbChannel.tiktokUrl && <a href={sbChannel.tiktokUrl} target="_blank" rel="noopener noreferrer" className="sb-social-link">TikTok</a>}
          </div>
        </motion.div>
      )}
    </div>
  );
}
