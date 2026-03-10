import { motion } from 'framer-motion';
import { ChannelBrandSafety } from '../types/message';
import { getScoreColor } from './advertiserUtils';

interface AdvBrandSafetyViewProps {
  brandSafety: ChannelBrandSafety | null;
}

export default function AdvBrandSafetyView({ brandSafety }: AdvBrandSafetyViewProps) {
  if (!brandSafety) {
    return (
      <div className="view-container">
        <h1 className="view-title">Brand Safety</h1>
        <div className="adv-card">
          <p style={{ color: '#94a3b8' }}>
            Brand safety data is not yet available. It will be computed automatically as chat messages are analyzed.
          </p>
        </div>
      </div>
    );
  }

  const score = brandSafety.brandSafetyScore;
  const scoreColor = getScoreColor(score);

  const sentimentData = [
    { label: 'Positive', value: brandSafety.positiveRate, color: '#22c55e' },
    { label: 'Neutral', value: brandSafety.neutralRate, color: '#64748b' },
    { label: 'Negative', value: brandSafety.negativeRate, color: '#ef4444' },
  ];

  const totalSentiment = sentimentData.reduce((s, d) => s + (d.value ?? 0), 0);

  return (
    <div className="view-container">
      <h1 className="view-title">Brand Safety</h1>

      {/* Score Card */}
      <motion.div
        className="adv-card"
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
      >
        <div className="bs-score-layout">
          <div className="bs-score-circle" style={{ borderColor: scoreColor }}>
            <span className="bs-score-value" style={{ color: scoreColor }}>{score}</span>
            <span className="bs-score-label">/ 100</span>
          </div>
          <div className="bs-score-info">
            <h3 className="card-label" style={{ marginBottom: 8 }}>Brand Safety Score</h3>
            <p className="bs-score-desc">
              {score >= 90
                ? 'Excellent. This channel has a very clean chat environment suitable for any brand.'
                : score >= 70
                ? 'Good. This chat is generally brand-safe with minimal toxicity.'
                : score >= 50
                ? 'Moderate. Some toxicity or negativity detected — review before sponsoring.'
                : 'Caution. Elevated levels of toxic or negative content detected in chat.'}
            </p>
            <span className="bs-sessions-note">
              Based on {brandSafety.sessionsAnalyzed} sessions analyzed
            </span>
          </div>
        </div>
      </motion.div>

      {/* Sentiment Distribution */}
      <motion.div
        className="adv-card"
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3, delay: 0.05 }}
      >
        <h3 className="card-label">Chat Sentiment</h3>
        <div className="bs-sentiment-bar">
          {sentimentData.map(seg => {
            const pct = totalSentiment > 0 ? ((seg.value ?? 0) / totalSentiment) * 100 : 0;
            if (pct < 1) return null;
            return (
              <motion.div
                key={seg.label}
                className="bs-sentiment-segment"
                style={{ backgroundColor: seg.color }}
                initial={{ width: 0 }}
                animate={{ width: `${pct}%` }}
                transition={{ duration: 0.8, ease: 'easeOut' }}
                title={`${seg.label}: ${(pct).toFixed(1)}%`}
              />
            );
          })}
        </div>
        <div className="bs-sentiment-legend">
          {sentimentData.map(seg => (
            <div key={seg.label} className="bs-legend-item">
              <span className="bs-legend-dot" style={{ backgroundColor: seg.color }} />
              <span className="bs-legend-label">{seg.label}</span>
              <span className="bs-legend-value">{((seg.value ?? 0) * 100).toFixed(1)}%</span>
            </div>
          ))}
        </div>
      </motion.div>

      {/* Engagement Metrics */}
      <motion.div
        className="adv-card"
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3, delay: 0.1 }}
      >
        <h3 className="card-label">Chat Quality</h3>
        <div className="bs-metrics-grid">
          <MetricItem
            label="Toxicity Rate"
            value={`${((brandSafety.toxicityRate ?? 0) * 100).toFixed(2)}%`}
            color={(brandSafety.toxicityRate ?? 0) < 0.01 ? '#22c55e' : (brandSafety.toxicityRate ?? 0) < 0.05 ? '#eab308' : '#ef4444'}
            desc="Messages containing toxic language"
          />
          <MetricItem
            label="Emote Spam Rate"
            value={`${((brandSafety.emoteSpamRate ?? 0) * 100).toFixed(1)}%`}
            color={(brandSafety.emoteSpamRate ?? 0) < 0.4 ? '#22c55e' : '#eab308'}
            desc="Short/emote-only messages"
          />
          <MetricItem
            label="Conversation Ratio"
            value={`${((brandSafety.conversationRatio ?? 0) * 100).toFixed(1)}%`}
            color={(brandSafety.conversationRatio ?? 0) > 0.15 ? '#22c55e' : '#eab308'}
            desc="Messages with meaningful content"
          />
        </div>
      </motion.div>

      {/* Top Topics */}
      {brandSafety.topTopics.length > 0 && (
        <motion.div
          className="adv-card"
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: 0.15 }}
        >
          <h3 className="card-label">Top Topics</h3>
          <p className="card-desc">Most frequently mentioned words in chat (excluding common words).</p>
          <div className="bs-topics-list">
            {brandSafety.topTopics.map((topic, i) => {
              const maxCount = brandSafety.topTopics[0].count;
              const pct = (topic.count / maxCount) * 100;
              return (
                <div key={topic.topic} className="bs-topic-row">
                  <span className="bs-topic-rank">#{i + 1}</span>
                  <span className="bs-topic-name">{topic.topic}</span>
                  <div className="bs-topic-bar-track">
                    <motion.div
                      className="bs-topic-bar-fill"
                      initial={{ width: 0 }}
                      animate={{ width: `${pct}%` }}
                      transition={{ duration: 0.6, delay: i * 0.05 }}
                    />
                  </div>
                  <span className="bs-topic-count">{topic.count}</span>
                </div>
              );
            })}
          </div>
        </motion.div>
      )}

      {/* Language Distribution */}
      {Object.keys(brandSafety.languageDistribution).length > 0 && (
        <motion.div
          className="adv-card"
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: 0.2 }}
        >
          <h3 className="card-label">Language Distribution</h3>
          <div className="bs-lang-grid">
            {Object.entries(brandSafety.languageDistribution)
              .sort(([, a], [, b]) => b - a)
              .map(([lang, pct]) => (
                <div key={lang} className="bs-lang-item">
                  <span className="bs-lang-name">{lang}</span>
                  <span className="bs-lang-pct">{(pct * 100).toFixed(1)}%</span>
                </div>
              ))}
          </div>
        </motion.div>
      )}
    </div>
  );
}

function MetricItem({ label, value, color, desc }: {
  label: string;
  value: string;
  color: string;
  desc: string;
}) {
  return (
    <div className="bs-metric-item">
      <span className="bs-metric-value" style={{ color }}>{value}</span>
      <span className="bs-metric-label">{label}</span>
      <span className="bs-metric-desc">{desc}</span>
    </div>
  );
}
