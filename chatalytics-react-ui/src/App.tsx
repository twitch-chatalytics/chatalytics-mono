import { useCallback, useEffect, useRef, useState } from 'react';
import { Message } from './types/message';
import { DateRange, fetchMessagesByAuthor, PAGE_SIZE } from './api/client';
import { useChatterProfile } from './hooks/useChatterProfile';
import { useActivityBuckets } from './hooks/useActivityBuckets';
import { useMessageFilter } from './hooks/useMessageFilter';
import StatsPanel from './components/StatsPanel';
import SearchBar from './components/SearchBar';
import ChatterProfileCard from './components/ChatterProfile';
import ActivityTimeline from './components/ActivityTimeline';
import RepeatDetector from './components/RepeatDetector';
import MessageFilter from './components/MessageFilter';
import MessageList from './components/MessageList';
import EmptyState from './components/EmptyState';
import LoadingSpinner from './components/LoadingSpinner';
import StreamsPage from './components/StreamsPage';

type Page = 'search' | 'streams';

function getPageFromPath(): Page {
  if (window.location.pathname.startsWith('/streams')) return 'streams';
  return 'search';
}

function getUserFromPath(): string {
  const match = window.location.pathname.match(/^\/user\/(.+)$/);
  return match ? decodeURIComponent(match[1]) : '';
}

export default function App() {
  const [page, setPage] = useState<Page>(() => getPageFromPath());
  const [messages, setMessages] = useState<Message[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [hasSearched, setHasSearched] = useState(false);
  const [searchedAuthor, setSearchedAuthor] = useState('');
  const [searchBarValue, setSearchBarValue] = useState(() => getUserFromPath());
  const [error, setError] = useState('');
  const [filterTerm, setFilterTerm] = useState('');
  const dateRangeRef = useRef<DateRange>({});

  const profile = useChatterProfile(searchedAuthor, messages);
  const activityBuckets = useActivityBuckets(messages);
  const { filtered: displayMessages, total } = useMessageFilter(messages, filterTerm);

  // Load user from URL on mount
  useEffect(() => {
    const user = getUserFromPath();
    if (user) {
      doSearch(user, {}, false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Handle browser back/forward
  useEffect(() => {
    const handlePopState = () => {
      setPage(getPageFromPath());
      const user = getUserFromPath();
      if (user) {
        doSearch(user, {}, false);
      } else if (!window.location.pathname.startsWith('/streams')) {
        setMessages([]);
        setSearchedAuthor('');
        setSearchBarValue('');
        setHasSearched(false);
        setHasMore(false);
        document.title = 'chatalytics';
      }
    };
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const doSearch = async (username: string, dateRange: DateRange, pushState = true) => {
    setIsLoading(true);
    setError('');
    setFilterTerm('');
    try {
      const results = await fetchMessagesByAuthor(username, dateRange);
      setMessages(results);
      setSearchedAuthor(username);
      setSearchBarValue(username);
      setHasSearched(true);
      setHasMore(results.length >= PAGE_SIZE);

      const targetPath = `/user/${encodeURIComponent(username)}`;
      document.title = `${username} — chatalytics`;
      if (pushState && window.location.pathname !== targetPath) {
        window.history.pushState({ author: username }, '', targetPath);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong');
      setMessages([]);
      setHasMore(false);
    } finally {
      setIsLoading(false);
    }
  };

  const loadMore = useCallback(async () => {
    if (isLoadingMore || !hasMore || messages.length === 0) return;

    const lastMessage = messages[messages.length - 1];
    setIsLoadingMore(true);
    try {
      const results = await fetchMessagesByAuthor(
        searchedAuthor,
        dateRangeRef.current,
        { timestamp: lastMessage.timestamp, id: lastMessage.id },
      );
      setMessages((prev) => [...prev, ...results]);
      setHasMore(results.length >= PAGE_SIZE);
    } catch {
      setHasMore(false);
    } finally {
      setIsLoadingMore(false);
    }
  }, [isLoadingMore, hasMore, messages, searchedAuthor]);

  const handleSearch = (username: string) => {
    setPage('search');
    doSearch(username, dateRangeRef.current);
  };

  const handleDateRangeChange = (range: DateRange) => {
    dateRangeRef.current = range;
    if (searchedAuthor) {
      doSearch(searchedAuthor, range);
    }
  };

  const navigateTo = (target: Page) => {
    setPage(target);
    if (target === 'streams') {
      window.history.pushState(null, '', '/streams');
      document.title = 'Streams — chatalytics';
    } else {
      window.history.pushState(null, '', '/');
      setMessages([]);
      setSearchedAuthor('');
      setSearchBarValue('');
      setHasSearched(false);
      setHasMore(false);
      setFilterTerm('');
      document.title = 'chatalytics';
    }
  };

  return (
    <>
      <div className="app">
        <header className="app-header">
          <h1 className="app-title" onClick={() => navigateTo('search')} style={{ cursor: 'pointer' }}>
            chatalytics
          </h1>
          <nav className="app-nav">
            <button
              className={`app-nav-btn${page === 'search' ? ' active' : ''}`}
              onClick={() => navigateTo('search')}
            >
              Chatters
            </button>
            <button
              className={`app-nav-btn${page === 'streams' ? ' active' : ''}`}
              onClick={() => navigateTo('streams')}
            >
              Streams
            </button>
          </nav>
        </header>

        {page === 'streams' ? (
          <StreamsPage />
        ) : (
          <>
            <SearchBar onSearch={handleSearch} onDateRangeChange={handleDateRangeChange} isLoading={isLoading} initialValue={searchBarValue} />

            {error && <div className="error-message">{error}</div>}

            {profile && (
              <ChatterProfileCard profile={profile}>
                <ActivityTimeline buckets={activityBuckets} />
                <RepeatDetector groups={profile.repeatedMessages} />
              </ChatterProfileCard>
            )}

            {isLoading ? (
              <LoadingSpinner />
            ) : messages.length > 0 ? (
              <>
                <MessageFilter
                  matchCount={displayMessages.length}
                  totalCount={total}
                  onFilterChange={setFilterTerm}
                />
                <MessageList
                  messages={displayMessages}
                  author={searchedAuthor}
                  highlightTerm={filterTerm}
                  hasMore={hasMore && !filterTerm.trim()}
                  isLoadingMore={isLoadingMore}
                  onLoadMore={loadMore}
                />
              </>
            ) : (
              <EmptyState hasSearched={hasSearched} />
            )}
          </>
        )}
      </div>

      <StatsPanel onAuthorClick={handleSearch} />
    </>
  );
}
