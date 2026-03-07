import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { AuthUser, ChannelProfile, StreamerRequestSummary } from '../types/message';
import { fetchChannels, fetchPendingRequests, requestStreamer } from '../api/client';
import './StreamerDirectory.css';

interface Props {
  user: AuthUser | null;
}

const cardVariants = {
  hidden: { opacity: 0, y: 12 },
  visible: { opacity: 1, y: 0 },
};

export default function StreamerDirectory({ user }: Props) {
  const [channels, setChannels] = useState<ChannelProfile[]>([]);
  const [pendingRequests, setPendingRequests] = useState<StreamerRequestSummary[]>([]);
  const [votedFor, setVotedFor] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([fetchChannels(), fetchPendingRequests()])
      .then(([ch, pr]) => {
        setChannels(ch);
        setPendingRequests(pr);
      })
      .finally(() => setLoading(false));
  }, []);

  const handleVote = async (login: string) => {
    try {
      const result = await requestStreamer(login);
      setVotedFor(prev => new Set(prev).add(login.toLowerCase()));
      if (result.added) {
        const [updated, updatedRequests] = await Promise.all([fetchChannels(), fetchPendingRequests()]);
        setChannels(updated);
        setPendingRequests(updatedRequests);
      } else if (result.voted) {
        setPendingRequests(prev => prev.map(r =>
          r.streamerLogin === login.toLowerCase()
            ? { ...r, voteCount: result.voteCount }
            : r
        ));
      }
    } catch {
      // requires login
    }
  };

  const navigateToChannel = (login: string) => {
    window.history.pushState(null, '', `/channel/${login}`);
    window.dispatchEvent(new PopStateEvent('popstate'));
  };

  return (
    <div className="directory">
      {/* Hero Banner */}
      <motion.section
        className="directory-hero"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
      >
        <div className="directory-hero-content">
          <span className="directory-hero-badge">Explore Streamers</span>
          <h1 className="directory-title">
            Real-time insights from<br />Twitch chat
          </h1>
          <p className="directory-subtitle">
            Chat patterns, emote trends, and community analytics across your favorite streamers
          </p>
          {channels.length > 0 && (
            <div className="directory-hero-stats">
              <span className="directory-hero-stat">{channels.length} channels tracked</span>
            </div>
          )}
        </div>
      </motion.section>

      {/* Tracked Channels */}
      <motion.section
        className="directory-section"
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, delay: 0.2 }}
      >
        <div className="directory-section-header">
          <h2 className="directory-section-title">Tracked Channels</h2>
          <span className="directory-count-badge">{channels.length}</span>
        </div>

        {loading ? (
          <div className="directory-loading">
            <div className="directory-spinner" />
            Loading channels...
          </div>
        ) : channels.length === 0 ? (
          <div className="directory-empty">No channels tracked yet. Search above to request one!</div>
        ) : (
          <div className="directory-channels-grid">
            {channels.map((channel, i) => (
              <motion.div
                key={channel.id}
                className="directory-channel-card"
                variants={cardVariants}
                initial="hidden"
                animate="visible"
                transition={{ duration: 0.25, delay: Math.min(i, 20) * 0.04 }}
                onClick={() => navigateToChannel(channel.login)}
              >
                <div className="directory-card-body">
                  {channel.profileImageUrl && (
                    <img
                      src={channel.profileImageUrl}
                      alt={channel.displayName}
                      className="directory-channel-avatar"
                    />
                  )}
                  <div className="directory-channel-info">
                    <div className="directory-channel-name-row">
                      <span className="directory-channel-name">{channel.displayName}</span>
                      {channel.broadcasterType && (
                        <span className="directory-broadcaster-badge small">
                          {channel.broadcasterType.charAt(0).toUpperCase() + channel.broadcasterType.slice(1)}
                        </span>
                      )}
                    </div>
                    {channel.description && (
                      <span className="directory-channel-description">{channel.description}</span>
                    )}
                  </div>
                </div>
              </motion.div>
            ))}
          </div>
        )}
      </motion.section>

      {/* Pending Requests */}
      {pendingRequests.length > 0 && (
        <motion.section
          className="directory-section"
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.3 }}
        >
          <div className="directory-section-header">
            <h2 className="directory-section-title">Community Requests</h2>
            <button
              className="directory-btn directory-btn-secondary"
              onClick={() => {
                window.history.pushState(null, '', '/suggested');
                window.dispatchEvent(new PopStateEvent('popstate'));
              }}
            >
              View All
            </button>
          </div>

          <div className="directory-requests-list">
            {pendingRequests.map(request => (
              <div key={request.streamerId} className="directory-request-item">
                {request.profileImageUrl && (
                  <img
                    src={request.profileImageUrl}
                    alt={request.displayName}
                    className="directory-request-avatar"
                  />
                )}
                <div className="directory-request-info">
                  <span className="directory-request-name">{request.displayName}</span>
                  <div className="directory-progress-bar">
                    <div
                      className="directory-progress-fill"
                      style={{ width: `${Math.min((request.voteCount / 10) * 100, 100)}%` }}
                    />
                  </div>
                  <span className="directory-progress-label">{request.voteCount}/10 votes</span>
                </div>
                <button
                  className={`directory-btn directory-btn-primary${!user ? ' disabled' : ''}`}
                  onClick={() => user && handleVote(request.streamerLogin)}
                  disabled={!user || votedFor.has(request.streamerLogin.toLowerCase())}
                  title={!user ? 'Login to vote' : undefined}
                >
                  {votedFor.has(request.streamerLogin.toLowerCase()) ? 'Voted' : 'Vote'}
                </button>
              </div>
            ))}
          </div>

          {!user && (
            <p className="directory-login-hint">Log in with Twitch to vote for streamers</p>
          )}
        </motion.section>
      )}
    </div>
  );
}
