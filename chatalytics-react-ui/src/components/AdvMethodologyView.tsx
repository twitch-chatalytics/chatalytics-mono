import { motion } from 'framer-motion';

export default function AdvMethodologyView() {
  return (
    <div className="view-container">
      <h1 className="view-title">Methodology</h1>

      {/* How We Score */}
      <motion.div
        className="adv-card"
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
      >
        <h3 className="card-label">How We Score</h3>
        <div className="adv-method-grid">
          <div className="adv-method-item">
            <div className="adv-method-number">1</div>
            <div className="adv-method-content">
              <strong>Collect stream data</strong>
              <p>We analyze chat messages, viewer counts, participation rates, and message content from each completed stream session.</p>
            </div>
          </div>
          <div className="adv-method-item">
            <div className="adv-method-number">2</div>
            <div className="adv-method-content">
              <strong>Evaluate 5 signals</strong>
              <p>Each stream is assessed across five independent dimensions: chat-to-viewer ratio (25%), message quality (20%), chatter behavior (25%), engagement patterns (15%), and cross-session consistency (15%).</p>
            </div>
          </div>
          <div className="adv-method-item">
            <div className="adv-method-number">3</div>
            <div className="adv-method-content">
              <strong>Compare against benchmarks</strong>
              <p>Signals are compared to expected values for the channel's size. A 50-viewer stream and a 50,000-viewer stream have different chat participation norms.</p>
            </div>
          </div>
          <div className="adv-method-item">
            <div className="adv-method-number">4</div>
            <div className="adv-method-content">
              <strong>Generate a weighted score</strong>
              <p>Individual signals are combined into a 0-100 score. Higher scores suggest more authentic audience engagement. Accuracy improves as more sessions are analyzed.</p>
            </div>
          </div>
        </div>
      </motion.div>

      {/* Score Guide */}
      <motion.div
        className="adv-card"
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3, delay: 0.05 }}
      >
        <h3 className="card-label">Score Guide</h3>
        <div className="adv-score-ranges">
          <div className="adv-score-range">
            <span className="adv-range-bar" style={{ background: '#22c55e' }} />
            <span className="adv-range-label"><strong>80-100</strong> Strong authenticity — chat behavior is consistent with a genuine, engaged audience.</span>
          </div>
          <div className="adv-score-range">
            <span className="adv-range-bar" style={{ background: '#86efac' }} />
            <span className="adv-range-label"><strong>60-79</strong> Moderate authenticity — mostly organic patterns with some areas worth monitoring.</span>
          </div>
          <div className="adv-score-range">
            <span className="adv-range-bar" style={{ background: '#eab308' }} />
            <span className="adv-range-label"><strong>40-59</strong> Fair — mixed signals that warrant closer examination before committing ad spend.</span>
          </div>
          <div className="adv-score-range">
            <span className="adv-range-bar" style={{ background: '#ef4444' }} />
            <span className="adv-range-label"><strong>0-39</strong> Low authenticity — multiple indicators suggest significant artificial activity.</span>
          </div>
        </div>
      </motion.div>

      {/* Disclaimer */}
      <motion.div
        className="adv-card"
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3, delay: 0.1 }}
      >
        <h3 className="card-label">Disclaimer</h3>
        <p className="card-desc" style={{ margin: 0 }}>
          This analysis identifies patterns that may suggest artificial audience activity. Scores are
          algorithmic estimates based on behavioral signals and should be considered alongside other
          factors when evaluating a channel. No score should be treated as definitive proof of bot
          activity — context matters.
        </p>
      </motion.div>
    </div>
  );
}
