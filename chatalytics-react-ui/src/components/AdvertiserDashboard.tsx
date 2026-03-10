import { useEffect, useMemo, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import {
  ChannelAuthenticityReport,
  AuthenticityTrendPoint,
  SessionAuthenticityReport,
  ChannelProfile,
  ChannelBenchmark,
  ChannelBrandSafety,
  SocialBladeChannel,
  SocialBladeDailyPoint,
} from '../types/message';
import {
  fetchChannelAuthenticity,
  fetchAuthenticityTrend,
  fetchChannelBenchmark,
  fetchChannelBrandSafety,
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
import AdvBrandSafetyView from './AdvBrandSafetyView';
import AdvAlertsView from './AdvAlertsView';
import AdvCampaignsView from './AdvCampaignsView';
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
  const [benchmark, setBenchmark] = useState<ChannelBenchmark | null>(null);
  const [brandSafety, setBrandSafety] = useState<ChannelBrandSafety | null>(null);
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
        fetchChannelBenchmark(ch.id),
        fetchChannelBrandSafety(ch.id),
      ]).then(([channelReport, trendData, sessionsData, sbData, sbDailyData, benchmarkData, brandSafetyData]) => {
        setReport(channelReport);
        setTrend(trendData);
        setSessions(sessionsData);
        setSbChannel(sbData);
        setSbDaily(sbDailyData);
        setBenchmark(benchmarkData);
        setBrandSafety(brandSafetyData);
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
      <div className="adv-dashboard">
        <div className="adv-sidebar">
          <div className="sidebar-channel">
            <div className="adv-skel" style={{ width: 36, height: 36, borderRadius: '50%', flexShrink: 0 }} />
            <div style={{ flex: 1 }}>
              <div className="adv-skel" style={{ width: 100, height: 14, marginBottom: 6 }} />
              <div className="adv-skel" style={{ width: 60, height: 10 }} />
            </div>
          </div>
          <div style={{ padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: 8 }}>
            {Array.from({ length: 6 }, (_, i) => (
              <div key={i} className="adv-skel" style={{ width: '100%', height: 36, borderRadius: 8 }} />
            ))}
          </div>
        </div>
        <main className="adv-main" style={{ paddingTop: 40 }}>
          <div className="adv-skel" style={{ width: 200, height: 32, marginBottom: 12 }} />
          <div className="adv-skel" style={{ width: 300, height: 16, marginBottom: 40 }} />
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 16, marginBottom: 40 }}>
            {Array.from({ length: 3 }, (_, i) => (
              <div key={i} className="adv-skel" style={{ height: 120, borderRadius: 14 }} />
            ))}
          </div>
          <div className="adv-skel" style={{ width: '100%', height: 240, borderRadius: 14 }} />
        </main>
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
              <AdvOverviewView report={report} trend={trend} avg={avg} benchmark={benchmark} />
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
              <AdvCompareView currentChannel={channel} />
            )}
            {activeView === 'brand-safety' && (
              <AdvBrandSafetyView brandSafety={brandSafety} />
            )}
            {activeView === 'alerts' && (
              <AdvAlertsView channelId={channel?.id ?? 0} />
            )}
            {activeView === 'campaigns' && (
              <AdvCampaignsView channelId={channel?.id ?? 0} />
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
