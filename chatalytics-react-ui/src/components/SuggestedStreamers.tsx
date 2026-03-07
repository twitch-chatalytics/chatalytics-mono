import { useState, useEffect, useCallback, useRef } from 'react';
import { motion } from 'framer-motion';
import { AuthUser, StreamerRequestSummary } from '../types/message';
import { fetchPendingRequestsPaged, requestStreamer } from '../api/client';
import './SuggestedStreamers.css';

interface Props {
  user: AuthUser | null;
}

const PAGE_SIZE = 20;

export default function SuggestedStreamers({ user }: Props) {
  const [requests, setRequests] = useState<StreamerRequestSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [votedFor, setVotedFor] = useState<Set<string>>(new Set());
  const [voteMessage, setVoteMessage] = useState<{ login: string; text: string } | null>(null);
  const sentinelRef = useRef<HTMLDivElement>(null);

  const loadPage = useCallback(async (offset: number, append: boolean) => {
    if (append) setLoadingMore(true); else setLoading(true);
    try {
      const result = await fetchPendingRequestsPaged(PAGE_SIZE, offset);
      setRequests(prev => append ? [...prev, ...result.items] : result.items);
      setTotal(result.total);
    } finally {
      setLoading(false);
      setLoadingMore(false);
    }
  }, []);

  useEffect(() => { loadPage(0, false); }, [loadPage]);

  useEffect(() => {
    const sentinel = sentinelRef.current;
    if (!sentinel) return;

    const observer = new IntersectionObserver(entries => {
      if (entries[0].isIntersecting && !loadingMore && requests.length < total) {
        loadPage(requests.length, true);
      }
    }, { rootMargin: '200px' });

    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [requests.length, total, loadingMore, loadPage]);

  const handleVote = async (login: string) => {
    try {
      const result = await requestStreamer(login);
      setVotedFor(prev => new Set(prev).add(login.toLowerCase()));
      if (result.added) {
        setVoteMessage({ login, text: `${login} has been added!` });
        loadPage(0, false);
      } else if (!result.voted) {
        setVoteMessage({ login, text: 'Already voted' });
      } else {
        setVoteMessage({ login, text: `Vote counted! (${result.voteCount}/10)` });
        setRequests(prev => prev.map(r =>
          r.streamerLogin === login.toLowerCase()
            ? { ...r, voteCount: result.voteCount }
            : r
        ));
      }
    } catch {
      setVoteMessage({ login, text: 'Login required to vote' });
    }
  };

  const navigateBack = () => {
    window.history.pushState(null, '', '/');
    window.dispatchEvent(new PopStateEvent('popstate'));
  };

  return (
    <div className="suggested">
      <motion.section
        className="suggested-hero"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
      >
        <button className="suggested-back" onClick={navigateBack}>
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
            <path d="M10 3L5 8l5 5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          Back
        </button>
        <h1 className="suggested-title">Community Requests</h1>
        <p className="suggested-subtitle">
          Streamers the community wants tracked — vote to help them reach the 10-vote threshold
        </p>
        {total > 0 && (
          <span className="suggested-count-badge">{total} pending</span>
        )}
      </motion.section>

      {loading ? (
        <div className="suggested-loading">
          <div className="suggested-spinner" />
          Loading requests...
        </div>
      ) : requests.length === 0 ? (
        <div className="suggested-empty">
          No pending requests yet. Search for a streamer to request one!
        </div>
      ) : (
        <div className="suggested-list">
          {requests.map((request, i) => (
            <motion.div
              key={request.streamerLogin}
              className="suggested-card"
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.25, delay: Math.min(i, 20) * 0.03 }}
            >
              {request.profileImageUrl && (
                <img
                  src={request.profileImageUrl}
                  alt={request.displayName}
                  className="suggested-avatar"
                />
              )}
              <div className="suggested-info">
                <span className="suggested-name">{request.displayName}</span>
                <span className="suggested-login">@{request.streamerLogin}</span>
              </div>
              <div className="suggested-votes">
                <div className="suggested-progress-bar">
                  <div
                    className="suggested-progress-fill"
                    style={{ width: `${Math.min((request.voteCount / 10) * 100, 100)}%` }}
                  />
                </div>
                <span className="suggested-progress-label">{request.voteCount}/10 votes</span>
              </div>
              <div className="suggested-action">
                <button
                  className={`suggested-btn${!user ? ' disabled' : ''}`}
                  onClick={() => user && handleVote(request.streamerLogin)}
                  disabled={!user || votedFor.has(request.streamerLogin.toLowerCase())}
                  title={!user ? 'Login to vote' : undefined}
                >
                  {votedFor.has(request.streamerLogin.toLowerCase()) ? 'Voted' : 'Vote'}
                </button>
                {voteMessage?.login === request.streamerLogin && (
                  <span className="suggested-vote-msg">{voteMessage.text}</span>
                )}
              </div>
            </motion.div>
          ))}

          <div ref={sentinelRef} className="suggested-sentinel">
            {loadingMore && (
              <div className="suggested-loading">
                <div className="suggested-spinner" />
                Loading more...
              </div>
            )}
          </div>
        </div>
      )}

      {!user && requests.length > 0 && (
        <p className="suggested-login-hint">Log in with Twitch to vote for streamers</p>
      )}
    </div>
  );
}
