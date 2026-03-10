import { useEffect, useState, useRef, useCallback } from 'react';
import { motion } from 'framer-motion';
import { ChannelProfile, CompareItem, SessionSummaryView } from '../types/message';
import { fetchChannel, fetchSessions, DateRange, SESSIONS_PAGE_SIZE } from '../api/client';
import { useLiveMetrics } from '../hooks/useLiveMetrics';
import DateRangeFilter, { Preset } from './DateRangeFilter';
import './StreamList.css';

const STREAM_PRESETS: Preset[] = [
  { key: 'all', label: 'All time' },
  { key: '24h', label: '24 hours', minutes: 1440 },
  { key: '7d', label: '7 days', minutes: 10080 },
  { key: '30d', label: '30 days', minutes: 43200 },
  { key: '90d', label: '90 days', minutes: 129600 },
  { key: 'custom', label: 'Custom' },
];

function formatDuration(start: string, end: string): string {
  const ms = new Date(end).getTime() - new Date(start).getTime();
  const hours = Math.floor(ms / 3600000);
  const minutes = Math.floor((ms % 3600000) / 60000);
  return hours > 0 ? `${hours}h ${minutes}m` : `${minutes}m`;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

function formatNumber(n: number): string {
  if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k';
  return n.toLocaleString();
}

interface Props {
  twitchId: number;
  channelLogin: string;
  onSelectSession: (sessionId: number) => void;
  compareItems: CompareItem[];
  onAddCompare: (item: CompareItem) => void;
  onRemoveCompare: (sessionId: number) => void;
  channelCompareItems: ChannelProfile[];
  onAddChannelCompare: (channel: ChannelProfile) => void;
  onRemoveChannelCompare: (channelId: number) => void;
}

export default function StreamList({ twitchId, channelLogin, onSelectSession, compareItems, onAddCompare, onRemoveCompare, channelCompareItems, onAddChannelCompare, onRemoveChannelCompare }: Props) {
  const [channel, setChannel] = useState<ChannelProfile | null>(null);
  const [sessions, setSessions] = useState<SessionSummaryView[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const [dateRange, setDateRange] = useState<DateRange>({});

  const hasLiveSession = sessions.some(s => !s.endTime);
  const { metrics: liveMetrics } = useLiveMetrics(hasLiveSession ? twitchId : null);

  const [now, setNow] = useState(() => new Date().toISOString());
  useEffect(() => {
    if (!hasLiveSession) return;
    const id = setInterval(() => setNow(new Date().toISOString()), 60_000);
    return () => clearInterval(id);
  }, [hasLiveSession]);

  const loadSessions = useCallback(async (range: DateRange, cursorSession?: SessionSummaryView) => {
    const cursor = cursorSession
      ? { startTime: cursorSession.startTime, id: cursorSession.sessionId }
      : undefined;

    const data = await fetchSessions(range, cursor, undefined, twitchId);
    return data;
  }, [twitchId]);

  useEffect(() => {
    fetchChannel(twitchId).then(setChannel);
  }, [twitchId]);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setSessions([]);
    setHasMore(true);

    loadSessions(dateRange).then((data) => {
      if (active) {
        setSessions(data);
        setHasMore(data.length >= SESSIONS_PAGE_SIZE);
        setLoading(false);
      }
    });

    return () => { active = false; };
  }, [dateRange, loadSessions]);

  const sentinelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!sentinelRef.current || !hasMore || loading) return;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && !loadingMore && hasMore) {
          setLoadingMore(true);
          const lastSession = sessions[sessions.length - 1];
          loadSessions(dateRange, lastSession).then((data) => {
            setSessions(prev => [...prev, ...data]);
            setHasMore(data.length >= SESSIONS_PAGE_SIZE);
            setLoadingMore(false);
          });
        }
      },
      { rootMargin: '200px' }
    );

    observer.observe(sentinelRef.current);
    return () => observer.disconnect();
  }, [sessions, hasMore, loading, loadingMore, dateRange, loadSessions]);

  const toggleSelection = (session: SessionSummaryView) => {
    const isSelected = compareItems.some(i => i.sessionId === session.sessionId);
    if (isSelected) {
      onRemoveCompare(session.sessionId);
    } else {
      onAddCompare({
        sessionId: session.sessionId,
        channelLogin,
        channelDisplayName: channel?.displayName || channelLogin,
        profileImageUrl: channel?.profileImageUrl || undefined,
        startTime: session.startTime,
        gameName: session.lastGameName || undefined,
      });
    }
  };

  const handleDateChange = (range: DateRange) => {
    setDateRange(range);
  };

  return (
    <div className="stream-list-container">
      {!channel && (
        <div className="stream-hero">
          <div className="stream-hero-content no-banner">
            <div className="skel-bone skel-avatar" />
            <div className="stream-hero-info">
              <div className="stream-hero-name-row">
                <div className="skel-bone" style={{ width: 160, height: 28 }} />
                <div className="skel-bone" style={{ width: 60, height: 22, borderRadius: 20 }} />
              </div>
              <div className="skel-bone" style={{ width: 240, height: 14, marginTop: 6 }} />
            </div>
          </div>
        </div>
      )}
      {channel && (
        <div className="stream-hero">
          <div className="stream-hero-content no-banner">
            {channel.profileImageUrl && (
              <img
                src={channel.profileImageUrl}
                alt={channel.displayName}
                className="stream-hero-avatar no-banner"
              />
            )}
            <div className="stream-hero-info">
              <div className="stream-hero-name-row">
                <h1 className="stream-hero-name">{channel.displayName}</h1>
                {channel.broadcasterType && (
                  <span className="stream-hero-badge">
                    {channel.broadcasterType.charAt(0).toUpperCase() + channel.broadcasterType.slice(1)}
                  </span>
                )}
                {(() => {
                  const isInCompare = channelCompareItems.some(c => c.id === channel.id);
                  const isFull = !isInCompare && channelCompareItems.length >= 3;
                  return (
                    <button
                      className={`stream-hero-compare-btn${isInCompare ? ' active' : ''}`}
                      onClick={() => isInCompare ? onRemoveChannelCompare(channel.id) : onAddChannelCompare(channel)}
                      disabled={isFull}
                      title={isFull ? 'Compare limit reached (3 max)' : undefined}
                    >
                      {isInCompare ? (
                        <>
                          <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
                            <path d="M3 8.5l3.5 3.5L13 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                          </svg>
                          Comparing
                        </>
                      ) : (
                        <>
                          <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
                            <path d="M8 3v10M3 8h10" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/>
                          </svg>
                          Compare
                        </>
                      )}
                    </button>
                  );
                })()}
              </div>
              {channel.description && (
                <p className="stream-hero-description">{channel.description}</p>
              )}
            </div>
          </div>
        </div>
      )}

      <DateRangeFilter onChange={handleDateChange} presets={STREAM_PRESETS} />

      {loading ? (
        <div className="stream-list">
          {Array.from({ length: 5 }, (_, i) => (
            <div key={i} className="stream-card stream-card-skeleton">
              <div className="skel-checkbox skel-bone" />
              <div className="stream-card-body">
                <div className="stream-card-header">
                  <div className="skel-bone" style={{ width: 140, height: 14 }} />
                </div>
                <div className="skel-bone" style={{ width: 180, height: 16, marginBottom: 8 }} />
                <div className="stream-card-pills">
                  <div className="skel-bone skel-pill" />
                  <div className="skel-bone skel-pill" />
                  <div className="skel-bone skel-pill" />
                </div>
              </div>
              <div className="skel-bone skel-chevron" />
            </div>
          ))}
        </div>
      ) : sessions.length === 0 ? (
        <div className="stream-list-empty">No streams found.</div>
      ) : (
        <div className="stream-list">
          {sessions.map((session, i) => {
            const isSelected = compareItems.some(item => item.sessionId === session.sessionId);
            const isDisabled = !isSelected && compareItems.length >= 3;
            return (
              <motion.div
                key={session.sessionId}
                className={`stream-card${isSelected ? ' selected' : ''}`}
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.25, delay: Math.min(i, 20) * 0.03 }}
              >
                <label
                  className={`stream-card-checkbox${isDisabled ? ' disabled' : ''}`}
                  onClick={(e) => e.stopPropagation()}
                >
                  <input
                    type="checkbox"
                    checked={isSelected}
                    disabled={isDisabled}
                    onChange={() => toggleSelection(session)}
                  />
                  <span className="checkmark" />
                </label>

                <div
                  className="stream-card-body"
                  onClick={() => onSelectSession(session.sessionId)}
                >
                  <div className="stream-card-header">
                    <span className="stream-card-date">{formatDate(session.startTime)}</span>
                    {!session.endTime && <span className="stream-card-live">LIVE</span>}
                  </div>
                  <div className="stream-card-game">
                    {session.lastGameName || 'Unknown Category'}
                  </div>
                  <div className="stream-card-pills">
                    {(() => {
                      const isLive = !session.endTime;
                      const live = isLive && liveMetrics?.sessionId === session.sessionId ? liveMetrics : null;
                      return (
                        <>
                          <span className={`pill${live ? ' pill-live' : ''}`}>
                            {formatNumber(live?.totalMessages ?? session.totalMessages)} msgs
                          </span>
                          <span className={`pill${live ? ' pill-live' : ''}`}>
                            {formatNumber(live?.totalChatters ?? session.totalChatters)} chatters
                          </span>
                          <span className={`pill${live ? ' pill-live' : ''}`}>
                            {isLive ? formatDuration(session.startTime, now) : formatDuration(session.startTime, session.endTime!)}
                          </span>
                          {(live?.viewerCount ?? session.peakViewerCount) != null && (
                            <span className={`pill${live ? ' pill-live' : ''}`}>
                              {formatNumber(live?.viewerCount ?? session.peakViewerCount!)} {live ? 'viewers' : 'peak'}
                            </span>
                          )}
                          {(live?.messagesPerMinute ?? session.messagesPerMinute) != null && (
                            <span className={`pill${live?.isHype ? ' pill-hype' : live ? ' pill-live' : ''}`}>
                              {(live?.messagesPerMinute ?? session.messagesPerMinute!).toFixed(1)} msg/min
                            </span>
                          )}
                        </>
                      );
                    })()}
                  </div>
                </div>

                <div
                  className="stream-card-chevron"
                  onClick={() => onSelectSession(session.sessionId)}
                >
                  <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
                    <path d="M7.5 4.5L13 10L7.5 15.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                </div>
              </motion.div>
            );
          })}

          <div ref={sentinelRef} className="stream-list-sentinel">
            {loadingMore && (
              <div className="stream-card stream-card-skeleton" style={{ marginTop: 12 }}>
                <div className="skel-checkbox skel-bone" />
                <div className="stream-card-body">
                  <div className="stream-card-header">
                    <div className="skel-bone" style={{ width: 140, height: 14 }} />
                  </div>
                  <div className="skel-bone" style={{ width: 180, height: 16, marginBottom: 8 }} />
                  <div className="stream-card-pills">
                    <div className="skel-bone skel-pill" />
                    <div className="skel-bone skel-pill" />
                    <div className="skel-bone skel-pill" />
                  </div>
                </div>
                <div className="skel-bone skel-chevron" />
              </div>
            )}
          </div>
        </div>
      )}

    </div>
  );
}
