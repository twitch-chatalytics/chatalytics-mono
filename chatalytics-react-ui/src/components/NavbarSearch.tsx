import { useState, useEffect, useRef } from 'react';
import { AuthUser, TwitchSearchResult } from '../types/message';
import { searchChannels, requestStreamer } from '../api/client';
import './NavbarSearch.css';

interface Props {
  user: AuthUser | null;
  onNavigate: (path: string) => void;
}

export default function NavbarSearch({ user, onNavigate }: Props) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<TwitchSearchResult[]>([]);
  const [searching, setSearching] = useState(false);
  const [votedFor, setVotedFor] = useState<Set<string>>(new Set());
  const [requesting, setRequesting] = useState<string | null>(null);
  const [voteMessage, setVoteMessage] = useState<{ login: string; text: string } | null>(null);
  const wrapperRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (query.length < 2) { setResults([]); return; }
    setSearching(true);
    const timer = setTimeout(() => {
      searchChannels(query).then(setResults).finally(() => setSearching(false));
    }, 300);
    return () => clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    const handle = (e: MouseEvent) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setResults([]);
        setQuery('');
      }
    };
    document.addEventListener('mousedown', handle);
    return () => document.removeEventListener('mousedown', handle);
  }, []);

  const handleVote = async (login: string) => {
    setRequesting(login.toLowerCase());
    try {
      const result = await requestStreamer(login);
      setVotedFor(prev => new Set(prev).add(login.toLowerCase()));
      if (result.added) {
        setVoteMessage({ login, text: `${login} added!` });
      } else if (!result.voted) {
        setVoteMessage({ login, text: 'Already voted or tracked' });
      } else {
        setVoteMessage({ login, text: `Vote counted! (${result.voteCount}/10)` });
      }
    } catch {
      setVoteMessage({ login, text: 'Login required to vote' });
    } finally {
      setRequesting(null);
    }
  };

  const handleNavigate = (login: string) => {
    setQuery('');
    setResults([]);
    onNavigate(`/channel/${login}`);
  };

  return (
    <div className="navbar-search" ref={wrapperRef}>
      <div className="navbar-search-input-wrapper">
        <svg className="navbar-search-icon" width="16" height="16" viewBox="0 0 20 20" fill="none">
          <circle cx="9" cy="9" r="6.5" stroke="currentColor" strokeWidth="1.5" />
          <path d="M14 14l4 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
        </svg>
        <input
          className="navbar-search-input"
          type="text"
          placeholder="Search streamers..."
          value={query}
          onChange={e => setQuery(e.target.value)}
        />
        {query && (
          <button
            className="navbar-search-clear"
            onClick={() => { setQuery(''); setResults([]); }}
          >
            <svg width="12" height="12" viewBox="0 0 16 16" fill="none">
              <path d="M4 4l8 8M12 4l-8 8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
            </svg>
          </button>
        )}
      </div>

      {(results.length > 0 || searching) && (
        <div className="navbar-search-results">
          {searching && results.length === 0 && (
            <div className="navbar-search-skeletons">
              {Array.from({ length: 3 }, (_, i) => (
                <div key={i} className="navbar-search-result">
                  <div className="navbar-skel" style={{ width: 32, height: 32, borderRadius: '50%', flexShrink: 0 }} />
                  <div className="navbar-result-info">
                    <div className="navbar-skel" style={{ width: 100, height: 14, marginBottom: 4 }} />
                    <div className="navbar-skel" style={{ width: 70, height: 11 }} />
                  </div>
                  <div className="navbar-skel" style={{ width: 56, height: 30, borderRadius: 8, flexShrink: 0 }} />
                </div>
              ))}
            </div>
          )}
          {results.map(result => (
            <div key={result.id} className="navbar-search-result">
              <img
                src={result.profileImageUrl}
                alt={result.displayName}
                className="navbar-result-avatar"
              />
              <div className="navbar-result-info">
                <span className="navbar-result-name">{result.displayName}</span>
                <span className="navbar-result-login">{result.login}</span>
              </div>
              {result.broadcasterType && (
                <span className="navbar-broadcaster-badge">
                  {result.broadcasterType.charAt(0).toUpperCase() + result.broadcasterType.slice(1)}
                </span>
              )}
              {result.alreadyTracked ? (
                <button
                  className="navbar-btn navbar-btn-secondary"
                  onClick={() => handleNavigate(result.login)}
                >
                  View
                </button>
              ) : (
                <div className="navbar-vote-action">
                  <button
                    className={`navbar-btn navbar-btn-primary${!user ? ' disabled' : ''}`}
                    onClick={() => user && handleVote(result.login)}
                    disabled={!user || votedFor.has(result.login.toLowerCase()) || requesting === result.login.toLowerCase()}
                    title={!user ? 'Login to vote' : undefined}
                  >
                    {requesting === result.login.toLowerCase() ? 'Requesting...' : votedFor.has(result.login.toLowerCase()) ? 'Voted' : 'Request'}
                  </button>
                  {voteMessage?.login === result.login && (
                    <span className="navbar-vote-msg">{voteMessage.text}</span>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
