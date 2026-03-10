import { useEffect, useState, useRef } from 'react';
import { motion } from 'framer-motion';
import { ChannelProfile } from '../types/message';
import { fetchChannels } from '../api/client';
import './StreamerListPage.css';

const PAGE_SIZE = 100;

interface Props {
  onNavigate: (path: string) => void;
}

export default function StreamerListPage({ onNavigate }: Props) {
  const [allChannels, setAllChannels] = useState<ChannelProfile[]>([]);
  const [visibleCount, setVisibleCount] = useState(PAGE_SIZE);
  const [loading, setLoading] = useState(true);
  const sentinelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    fetchChannels()
      .then(channels => {
        const sorted = [...channels].sort((a, b) =>
          a.displayName.localeCompare(b.displayName, undefined, { sensitivity: 'base' }),
        );
        setAllChannels(sorted);
      })
      .finally(() => setLoading(false));
  }, []);

  const visibleChannels = allChannels.slice(0, visibleCount);
  const hasMore = visibleCount < allChannels.length;

  useEffect(() => {
    if (!sentinelRef.current || !hasMore || loading) return;

    const observer = new IntersectionObserver(
      entries => {
        if (entries[0].isIntersecting && hasMore) {
          setVisibleCount(prev => Math.min(prev + PAGE_SIZE, allChannels.length));
        }
      },
      { rootMargin: '300px' },
    );

    observer.observe(sentinelRef.current);
    return () => observer.disconnect();
  }, [hasMore, loading, allChannels.length, visibleCount]);

  return (
    <div className="streamer-list-page">
      <div className="streamer-list-header">
        <motion.h1
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4 }}
        >
          Tracked Streamers
        </motion.h1>
        {!loading && (
          <motion.span
            className="streamer-list-count"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.3, delay: 0.15 }}
          >
            {allChannels.length} channels
          </motion.span>
        )}
      </div>

      {loading ? (
        <div className="streamer-list-loading">Loading channels...</div>
      ) : allChannels.length === 0 ? (
        <div className="streamer-list-empty">No tracked streamers yet.</div>
      ) : (
        <div className="streamer-list-grid">
          {visibleChannels.map((channel, i) => (
            <motion.div
              key={channel.id}
              className="streamer-card"
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.2, delay: Math.min(i % PAGE_SIZE, 15) * 0.02 }}
              onClick={() => onNavigate(`/channel/${channel.login}`)}
            >
              {channel.profileImageUrl ? (
                <img
                  src={channel.profileImageUrl}
                  alt={channel.displayName}
                  className="streamer-card-avatar"
                />
              ) : (
                <div className="streamer-card-avatar-placeholder">
                  {channel.displayName.charAt(0).toUpperCase()}
                </div>
              )}

              <div className="streamer-card-info">
                <div className="streamer-card-name-row">
                  <span className="streamer-card-name">{channel.displayName}</span>
                  {channel.broadcasterType && (
                    <span className="streamer-card-badge">{channel.broadcasterType}</span>
                  )}
                </div>
                {channel.description && (
                  <div className="streamer-card-description">{channel.description}</div>
                )}
              </div>

              <div className="streamer-card-chevron">
                <svg width="18" height="18" viewBox="0 0 20 20" fill="none">
                  <path d="M7.5 4.5L13 10L7.5 15.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              </div>
            </motion.div>
          ))}

          <div ref={sentinelRef} className="streamer-list-sentinel">
            {hasMore && <div className="streamer-list-loading-more">Loading more...</div>}
          </div>
        </div>
      )}
    </div>
  );
}
