import { useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { ChannelStats } from '../types/message';
import { fetchStats } from '../api/client';
import { useAnimatedNumber } from '../hooks/useAnimatedNumber';
import './StatsPanel.css';

function StatItem({ label, value }: { label: string; value: string }) {
  return (
    <motion.div
      className="footer-stat"
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4 }}
    >
      <span className="footer-stat-value">{value}</span>
      <span className="footer-stat-label">{label}</span>
    </motion.div>
  );
}

function formatHour(hour: number): string {
  const suffix = hour >= 12 ? 'PM' : 'AM';
  const h = hour % 12 || 12;
  return `${h} ${suffix}`;
}

interface StatsPanelProps {
  onAuthorClick: (author: string) => void;
}

export default function StatsPanel({ onAuthorClick }: StatsPanelProps) {
  const [stats, setStats] = useState<ChannelStats | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchStats()
      .then(setStats)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  const animatedMessages = useAnimatedNumber(stats?.totalMessages ?? 0);
  const animatedChatters = useAnimatedNumber(stats?.uniqueChatters ?? 0);

  if (loading) {
    return (
      <footer className="stats-footer">
        <div className="stats-footer-inner stats-loading">
          <div className="stats-spinner" />
        </div>
      </footer>
    );
  }

  if (!stats) return null;

  return (
    <footer className="stats-footer">
      <div className="stats-footer-inner">
        <div className="footer-left">
          <div className="footer-stats-row">
            <StatItem label="Messages" value={animatedMessages.toLocaleString()} />
            <div className="footer-stat-divider" />
            <StatItem label="Chatters" value={animatedChatters.toLocaleString()} />
            {stats.peakHour !== null && (
              <>
                <div className="footer-stat-divider" />
                <StatItem label="Peak Hour" value={formatHour(stats.peakHour)} />
              </>
            )}
          </div>
        </div>

        <AnimatePresence>
          {stats.topChatters.length > 0 && (
            <motion.div
              className="footer-right"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ duration: 0.4, delay: 0.2 }}
            >
              <div className="footer-leaderboard-label">Top Chatters</div>
              <div className="footer-leaderboard">
                {stats.topChatters.slice(0, 5).map((chatter, i) => (
                  <motion.button
                    key={chatter.author}
                    className="footer-chatter"
                    onClick={() => onAuthorClick(chatter.author)}
                    initial={{ opacity: 0, y: 6 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.3, delay: 0.3 + i * 0.04 }}
                  >
                    <span className="footer-chatter-rank">{i + 1}</span>
                    <span className="footer-chatter-name">{chatter.author}</span>
                    <span className="footer-chatter-count">{chatter.messageCount.toLocaleString()}</span>
                  </motion.button>
                ))}
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </footer>
  );
}
