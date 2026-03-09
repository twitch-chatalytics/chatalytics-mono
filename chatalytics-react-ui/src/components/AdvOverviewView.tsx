import { motion } from 'framer-motion';
import { ChannelAuthenticityReport, AuthenticityTrendPoint } from '../types/message';
import { interpretChannelScore } from './advertiserUtils';
import AuthenticityGauge from './AuthenticityGauge';
import AuthenticityTrendChart from './AuthenticityTrendChart';

interface AdvOverviewViewProps {
  report: ChannelAuthenticityReport;
  trend: AuthenticityTrendPoint[];
  avg: number;
}

export default function AdvOverviewView({ report, trend, avg }: AdvOverviewViewProps) {
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

      {/* Score History */}
      {trend.length >= 2 && (
        <motion.div
          className="adv-card"
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: 0.1 }}
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
          transition={{ duration: 0.3, delay: 0.15 }}
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
