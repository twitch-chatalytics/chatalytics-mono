import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { ChannelStats, GlobalStats } from '../types/message';
import { fetchStats, fetchChannelByLogin, fetchGlobalStats } from '../api/client';
import TickerNumber from './TickerNumber';
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

function timeAgo(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  return `${days}d ago`;
}

interface StatsPanelProps {
  channelLogin?: string;
  onAuthorClick: (author: string) => void;
}

export default function StatsPanel({ channelLogin }: StatsPanelProps) {
  const [channelStats, setChannelStats] = useState<ChannelStats | null>(null);
  const [globalStats, setGlobalStats] = useState<GlobalStats | null>(null);
  const [channelDisplayName, setChannelDisplayName] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const isGlobal = !channelLogin;

  useEffect(() => {
    setLoading(true);
    if (channelLogin) {
      fetchChannelByLogin(channelLogin).then(ch => {
        if (ch) {
          setChannelDisplayName(ch.displayName || ch.login);
          return fetchStats(ch.id);
        }
        return null;
      })
        .then(s => { if (s) setChannelStats(s); })
        .catch(() => {})
        .finally(() => setLoading(false));
    } else {
      fetchGlobalStats()
        .then(s => { if (s) setGlobalStats(s); })
        .catch(() => {})
        .finally(() => setLoading(false));
    }
  }, [channelLogin]);

  const stats = isGlobal ? globalStats : channelStats;
  const totalMessages = isGlobal
    ? (globalStats?.totalMessages ?? 0)
    : (channelStats?.totalMessages ?? 0);
  const uniqueChatters = isGlobal
    ? (globalStats?.uniqueChatters ?? 0)
    : (channelStats?.uniqueChatters ?? 0);

  const streams = isGlobal ? (globalStats?.totalStreams ?? 0) : 0;
  const channels = isGlobal ? (globalStats?.trackedChannels ?? 0) : 0;

  if (loading) {
    return (
      <footer className="stats-footer">
        <div className="stats-footer-inner">
          <div className="footer-stats-row">
            {[...Array(isGlobal ? 4 : 3)].map((_, i) => (
              <div key={i} className="footer-stat">
                <div className="skeleton skeleton-value" />
                <div className="skeleton skeleton-label" />
              </div>
            ))}
          </div>
        </div>
      </footer>
    );
  }

  if (!stats) return null;

  const globalStatItems = (
    <>
      <TickerNumber value={totalMessages} label="Messages" />
      <span className="footer-stat-divider" />
      <TickerNumber value={uniqueChatters} label="Chatters" />
      <span className="footer-stat-divider" />
      <TickerNumber value={streams} label="Streams" />
      <span className="footer-stat-divider" />
      <TickerNumber value={channels} label="Channels" />
    </>
  );

  const channelStatItems = (
    <>
      <TickerNumber value={totalMessages} label="Messages" />
      <span className="footer-stat-divider" />
      <TickerNumber value={uniqueChatters} label="Chatters" />
      {channelStats?.peakHour !== null && channelStats?.peakHour !== undefined && (
        <>
          <span className="footer-stat-divider" />
          <StatItem label="Peak Hour" value={formatHour(channelStats.peakHour)} />
        </>
      )}
    </>
  );

  const footerTitle = isGlobal
    ? 'All Channels'
    : channelDisplayName ?? channelLogin;

  return (
    <footer className="stats-footer">
      <div className="stats-footer-inner">
        <span className="footer-context-title">{footerTitle}</span>
        <div className="footer-stats-row">
          {isGlobal ? globalStatItems : channelStatItems}
        </div>

        {isGlobal && globalStats?.updatedAt && (
          <motion.div
            className="footer-updated"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.3 }}
          >
            <span className="footer-updated-dot" />
            Updated {timeAgo(globalStats.updatedAt)}
          </motion.div>
        )}
      </div>
    </footer>
  );
}
