import { motion, AnimatePresence } from 'framer-motion';
import { SessionAuthenticityReport } from '../types/message';
import {
  interpretChatRatio,
  interpretMessageQuality,
  interpretChatterBehavior,
  interpretEngagement,
  interpretCrossSession,
  getScoreColor,
  formatShortDate,
} from './advertiserUtils';
import AuthenticityGauge from './AuthenticityGauge';
import SignalBreakdownCard from './SignalBreakdownCard';
import SuspiciousFlagsList from './SuspiciousFlagsList';

interface AdvSessionsViewProps {
  sessions: SessionAuthenticityReport[];
  selectedSession: SessionAuthenticityReport | null;
  onSessionSelect: (session: SessionAuthenticityReport | null) => void;
}

export default function AdvSessionsView({ sessions, selectedSession, onSessionSelect }: AdvSessionsViewProps) {
  if (sessions.length === 0) {
    return (
      <div className="view-container">
        <h1 className="view-title">Sessions</h1>
        <div className="adv-card">
          <p className="empty-text">No session data available yet. Scores are computed as streams complete.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="view-container">
      <h1 className="view-title">Sessions</h1>

      <div className="sessions-layout">
        {/* Session List */}
        <div className="session-list-card adv-card">
          <h3 className="card-label">Stream History</h3>
          <div className="session-list">
            {sessions.map(s => (
              <button
                key={s.sessionId}
                className={`session-row ${selectedSession?.sessionId === s.sessionId ? 'active' : ''}`}
                onClick={() => onSessionSelect(selectedSession?.sessionId === s.sessionId ? null : s)}
              >
                <span className="session-date">{formatShortDate(s.sessionStartTime ?? s.computedAt)}</span>
                <span className="session-score" style={{ color: getScoreColor(s.authenticityScore) }}>
                  {s.authenticityScore}
                </span>
                <span className="session-confidence">{s.confidenceLevel}</span>
              </button>
            ))}
          </div>
        </div>

        {/* Session Detail */}
        <div className="session-detail">
          <AnimatePresence mode="wait">
            {selectedSession ? (
              <motion.div
                key={selectedSession.sessionId}
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.2 }}
              >
                {/* Gauge */}
                <div className="adv-card" style={{ textAlign: 'center', marginBottom: 16 }}>
                  <AuthenticityGauge
                    score={selectedSession.authenticityScore}
                    confidenceLevel={selectedSession.confidenceLevel}
                    size={160}
                  />
                </div>

                {/* Signal Breakdown */}
                <div className="adv-card" style={{ marginBottom: 16 }}>
                  <h3 className="card-label">Signal Breakdown</h3>
                  <p className="card-desc">Each signal measures a different dimension of audience authenticity.</p>
                  <div className="adv-signals-grid">
                    <SignalBreakdownCard
                      label="Chat-to-Viewer Ratio"
                      value={selectedSession.chatRatioDeviation != null ? Math.min(1, selectedSession.chatRatioDeviation) : null}
                      weight="25%"
                      description="Compares actual chat participation against expected rates for the viewer count."
                      interpretation={interpretChatRatio(selectedSession.chatViewerRatio, selectedSession.expectedChatRatio, selectedSession.chatRatioDeviation)}
                      details={[
                        { label: 'Actual participation', value: selectedSession.chatViewerRatio },
                        { label: 'Expected for size', value: selectedSession.expectedChatRatio },
                      ]}
                    />
                    <SignalBreakdownCard
                      label="Message Quality"
                      value={selectedSession.vocabularyDiversity != null
                        ? (0.4 * Math.min(1, selectedSession.vocabularyDiversity * 2)
                           + 0.3 * (1 - (selectedSession.emoteOnlyRatio ?? 0))
                           + 0.3 * (1 - (selectedSession.repetitiveMessageRatio ?? 0)))
                        : null}
                      weight="20%"
                      description="Evaluates vocabulary diversity and repetitive messaging patterns."
                      interpretation={interpretMessageQuality(selectedSession.vocabularyDiversity, selectedSession.repetitiveMessageRatio)}
                      details={[
                        { label: 'Vocabulary diversity', value: selectedSession.vocabularyDiversity },
                        { label: 'Repetitive messages', value: selectedSession.repetitiveMessageRatio },
                      ]}
                    />
                    <SignalBreakdownCard
                      label="Chatter Behavior"
                      value={selectedSession.singleMessageChatterRatio != null
                        ? (0.6 * (1 - selectedSession.singleMessageChatterRatio)
                           + 0.4 * (1 - (selectedSession.timingUniformityScore ?? 0)))
                        : null}
                      weight="25%"
                      description="Examines how chatters behave — message frequency, timing patterns."
                      interpretation={interpretChatterBehavior(selectedSession.singleMessageChatterRatio, selectedSession.timingUniformityScore)}
                      details={[
                        { label: 'Single-msg chatters', value: selectedSession.singleMessageChatterRatio },
                        { label: 'Timing uniformity', value: selectedSession.timingUniformityScore },
                      ]}
                    />
                    <SignalBreakdownCard
                      label="Engagement Authenticity"
                      value={selectedSession.organicFlowScore != null
                        ? (0.6 * selectedSession.organicFlowScore + 0.4 * (selectedSession.conversationDepthScore ?? 0))
                        : null}
                      weight="15%"
                      description="Measures whether chat ebbs and flows naturally with stream events."
                      interpretation={interpretEngagement(selectedSession.organicFlowScore, selectedSession.conversationDepthScore)}
                      details={[
                        { label: 'Organic flow', value: selectedSession.organicFlowScore },
                        { label: 'Conversation depth', value: selectedSession.conversationDepthScore },
                      ]}
                    />
                    <SignalBreakdownCard
                      label="Cross-Session Consistency"
                      value={selectedSession.viewerChatCorrelation != null
                        ? (selectedSession.viewerChatCorrelation + 1) / 2
                        : null}
                      weight="15%"
                      description="Checks whether chat participation scales naturally with viewer count across streams."
                      interpretation={interpretCrossSession(selectedSession.viewerChatCorrelation)}
                      details={[
                        { label: 'Viewer-chat correlation', value: selectedSession.viewerChatCorrelation, format: 'correlation' as const },
                      ]}
                    />
                  </div>
                </div>

                {/* Risk Indicators */}
                <div className="adv-card">
                  <h3 className="card-label">Risk Indicators</h3>
                  <SuspiciousFlagsList flags={selectedSession.suspiciousPatternFlags} />
                </div>
              </motion.div>
            ) : (
              <motion.div
                key="empty"
                className="adv-card session-empty-state"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
              >
                <p className="empty-text">Select a session from the list to view its detailed signal breakdown.</p>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>
    </div>
  );
}
