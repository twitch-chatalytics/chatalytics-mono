import { useEffect, useMemo, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import {
  ChannelAuthenticityReport,
  AuthenticityTrendPoint,
  SessionAuthenticityReport,
  ChannelProfile,
  SocialBladeChannel,
  SocialBladeDailyPoint,
} from '../types/message';
import {
  fetchChannelAuthenticity,
  fetchAuthenticityTrend,
  fetchChannelByLogin,
  fetchChannelSessions,
  fetchSocialBladeChannel,
  fetchSocialBladeDaily,
} from '../api/client';
import { analyzeGrowthAnomalies } from '../util/growthAnomalyDetector';
import AdvSidebar from './AdvSidebar';
import AdvOverviewView from './AdvOverviewView';
import AdvGrowthView from './AdvGrowthView';
import AdvSessionsView from './AdvSessionsView';
import AdvMethodologyView from './AdvMethodologyView';
import AdvCompareView from './AdvCompareView';
import './AdvertiserDashboard.css';

interface AdvertiserDashboardProps {
  channelLogin: string;
}

export default function AdvertiserDashboard({ channelLogin }: AdvertiserDashboardProps) {
  const [channel, setChannel] = useState<ChannelProfile | null>(null);
  const [report, setReport] = useState<ChannelAuthenticityReport | null>(null);
  const [trend, setTrend] = useState<AuthenticityTrendPoint[]>([]);
  const [sessions, setSessions] = useState<SessionAuthenticityReport[]>([]);
  const [selectedSession, setSelectedSession] = useState<SessionAuthenticityReport | null>(null);
  const [sbChannel, setSbChannel] = useState<SocialBladeChannel | null>(null);
  const [sbDaily, setSbDaily] = useState<SocialBladeDailyPoint[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeView, setActiveView] = useState('overview');

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
        fetchChannelSessions(ch.id),
        fetchSocialBladeChannel(ch.id),
        fetchSocialBladeDaily(ch.id),
      ]).then(([channelReport, trendData, sessionsData, sbData, sbDailyData]) => {
        setReport(channelReport);
        setTrend(trendData);
        setSessions(sessionsData);
        setSbChannel(sbData);
        setSbDaily(sbDailyData);
        setLoading(false);
      }).catch(() => {
        setError('Failed to load authenticity data');
        setLoading(false);
      });
    });
  }, [channelLogin]);

  const growthAnalysis = useMemo(() => analyzeGrowthAnomalies(sbDaily), [sbDaily]);

  if (loading) {
    return (
      <div className="adv-loading">
        <div className="recap-spinner" />
        Loading authenticity report...
      </div>
    );
  }

  if (error) {
    return <div className="adv-error">{error}</div>;
  }

  if (!report) {
    return (
      <div className="adv-empty">
        No authenticity data available yet. Scores are computed automatically as streams complete.
      </div>
    );
  }

  const avg = Math.round(report.avgAuthenticityScore ?? 0);

  return (
    <div className="adv-dashboard">
      <AdvSidebar
        activeView={activeView}
        onViewChange={setActiveView}
        channel={channel}
        report={report}
      />
      <main className="adv-main">
        <AnimatePresence mode="wait">
          <motion.div
            key={activeView}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
          >
            {activeView === 'overview' && (
              <AdvOverviewView report={report} trend={trend} avg={avg} />
            )}
            {activeView === 'growth' && (
              <AdvGrowthView
                avg={avg}
                sbChannel={sbChannel}
                sbDaily={sbDaily}
                growthAnalysis={growthAnalysis}
              />
            )}
            {activeView === 'sessions' && (
              <AdvSessionsView
                sessions={sessions}
                selectedSession={selectedSession}
                onSessionSelect={setSelectedSession}
              />
            )}
            {activeView === 'compare' && (
              <AdvCompareView />
            )}
            {activeView === 'methodology' && (
              <AdvMethodologyView />
            )}
          </motion.div>
        </AnimatePresence>
      </main>
    </div>
  );
}
