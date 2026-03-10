import { useEffect, useState } from 'react';
import { AuthUser, ChannelProfile, CompareItem } from './types/message';
import { fetchMe, logout, fetchChannel } from './api/client';
import StatsPanel from './components/StatsPanel';
import StreamsPage from './components/StreamsPage';
import ChatterDrawer from './components/ChatterDrawer';
import StreamerDirectory from './components/StreamerDirectory';
import SuggestedStreamers from './components/SuggestedStreamers';
import ChannelCompareView from './components/ChannelCompareView';
import CompareView from './components/CompareView';
import CompareBar from './components/CompareBar';
import ChannelCompareBar from './components/ChannelCompareBar';
import NavbarSearch from './components/NavbarSearch';
import AdvertiserDashboard from './components/AdvertiserDashboard';
import ChannelReport from './components/ChannelReport';
import StreamerListPage from './components/StreamerListPage';

export default function App() {
  const [drawerAuthor, setDrawerAuthor] = useState<string | null>(null);
  const [user, setUser] = useState<AuthUser | null>(null);
  const [authLoading, setAuthLoading] = useState(true);
  const [currentPath, setCurrentPath] = useState(window.location.pathname);
  const [compareItems, setCompareItems] = useState<CompareItem[]>([]);
  const [streamCompareLabels, setStreamCompareLabels] = useState<string[]>([]);
  const [streamCompareLoading, setStreamCompareLoading] = useState(false);
  const [channelCompareItems, setChannelCompareItems] = useState<ChannelProfile[]>([]);

  const addChannelCompare = (channel: ChannelProfile) => {
    setChannelCompareItems(prev => {
      if (prev.length >= 3 || prev.some(c => c.id === channel.id)) return prev;
      return [...prev, channel];
    });
  };

  const removeChannelCompare = (channelId: number) => {
    setChannelCompareItems(prev => prev.filter(c => c.id !== channelId));
  };

  const clearChannelCompare = () => setChannelCompareItems([]);

  const addCompareItem = (item: CompareItem) => {
    setCompareItems(prev => {
      if (prev.length >= 3 || prev.some(i => i.sessionId === item.sessionId)) return prev;
      return [...prev, item];
    });
  };

  const removeCompareItem = (sessionId: number) => {
    setCompareItems(prev => prev.filter(i => i.sessionId !== sessionId));
  };

  const clearCompare = () => setCompareItems([]);

  useEffect(() => {
    fetchMe().then(setUser).catch(() => {}).finally(() => setAuthLoading(false));
  }, []);

  useEffect(() => {
    const handleNav = () => setCurrentPath(window.location.pathname);
    window.addEventListener('popstate', handleNav);
    return () => window.removeEventListener('popstate', handleNav);
  }, []);

  const handleChatterClick = (author: string) => setDrawerAuthor(author);

  const channelMatch = currentPath.match(/^\/channel\/([^/]+)/);
  const channelLogin = channelMatch ? channelMatch[1] : null;
  const isChannelCompare = currentPath === '/compare/channels';
  const isStreamCompare = currentPath === '/compare/streams';
  const advertiserMatch = currentPath.match(/^\/authenticity\/([^/]+)/);
  const advertiserChannelLogin = advertiserMatch ? advertiserMatch[1] : null;
  const reportMatch = currentPath.match(/^\/report\/([^/]+)/);
  const reportChannelLogin = reportMatch ? reportMatch[1] : null;
  const isAdvertiser = user?.roles?.includes('ADVERTISER') ?? false;

  // Parse session IDs from URL for stream compare
  const streamCompareIds = (() => {
    if (!isStreamCompare) return [];
    const params = new URLSearchParams(window.location.search);
    const raw = params.get('sessions') || '';
    return raw.split(',').map(Number).filter(n => n > 0).slice(0, 3);
  })();

  // Resolve labels for stream compare (from compareItems or async fetch)
  useEffect(() => {
    if (streamCompareIds.length < 2) {
      setStreamCompareLabels([]);
      return;
    }

    // Try to derive labels from compareItems
    const labels = streamCompareIds.map(id => {
      const item = compareItems.find(i => i.sessionId === id);
      return item?.channelDisplayName || '';
    });

    if (labels.every(l => l)) {
      setStreamCompareLabels(labels);
      return;
    }

    // Shareable link — fetch labels from recap data
    setStreamCompareLoading(true);
    Promise.all(
      streamCompareIds.map(async id => {
        try {
          const resp = await fetch(`/public/sessions/${id}/recap`);
          if (!resp.ok) return '';
          const recap = await resp.json();
          if (recap.snapshots?.length > 0) {
            const ch = await fetchChannel(recap.snapshots[0].channelId);
            return ch?.displayName || '';
          }
        } catch {}
        return '';
      })
    ).then(names => {
      setStreamCompareLabels(names);
      setStreamCompareLoading(false);
    });
  }, [streamCompareIds.join(',')]);

  const navigateTo = (path: string) => {
    window.history.pushState(null, '', path);
    setCurrentPath(path);
  };

  const handleLogin = () => {
    window.location.href = '/oauth2/authorization/twitch';
  };

  const handleLogout = async () => {
    await logout();
    setUser(null);
  };

  return (
    <>
      <nav className="app-navbar">
        <div className="app-navbar-inner">
          <div className="app-nav-left">
            <span className="app-logo" onClick={() => navigateTo('/')}>
              chatalytics
            </span>
            <button className="app-nav-link" onClick={() => navigateTo('/streamers')}>
              Streamers
            </button>
          </div>
          <NavbarSearch user={user} onNavigate={navigateTo} />
          <div className="app-nav-right">
            {!authLoading && (
              user ? (
                <div className="app-nav-user">
                  {isAdvertiser && channelLogin && (
                    <button
                      className="app-nav-advertiser-link"
                      onClick={() => navigateTo(`/authenticity/${channelLogin}`)}
                    >
                      Authenticity
                    </button>
                  )}
                  {user.profileImageUrl && (
                    <img src={user.profileImageUrl} alt="" className="app-nav-avatar" />
                  )}
                  <span className="app-nav-username">{user.displayName}</span>
                  <button onClick={handleLogout} className="app-nav-logout">Logout</button>
                </div>
              ) : (
                <button onClick={handleLogin} className="app-nav-login">
                  <svg width="16" height="16" viewBox="0 0 256 268" fill="currentColor">
                    <path d="M17.458 0L0 46.556v185.262h63.983v34.234h34.234l34.234-34.234h51.366L256 159.73V0H17.458zm20.514 23.39H232.61v128.028l-42.885 42.885h-63.497l-34.234 34.234v-34.234H37.972V23.39zM102.94 66.418v68.954h23.39V66.418h-23.39zm63.497 0v68.954h23.39V66.418h-23.39z"/>
                  </svg>
                  Login with Twitch
                </button>
              )
            )}
          </div>
        </div>
      </nav>

      <div className="app">
        {reportChannelLogin ? (
          <ChannelReport channelLogin={reportChannelLogin} />
        ) : advertiserChannelLogin ? (
          <AdvertiserDashboard channelLogin={advertiserChannelLogin} />
        ) : channelLogin ? (
          <StreamsPage
            channelLogin={channelLogin}
            onChatterClick={handleChatterClick}
            compareItems={compareItems}
            onAddCompare={addCompareItem}
            onRemoveCompare={removeCompareItem}
            channelCompareItems={channelCompareItems}
            onAddChannelCompare={addChannelCompare}
            onRemoveChannelCompare={removeChannelCompare}
          />
        ) : isChannelCompare ? (
          <ChannelCompareView
            onBack={() => navigateTo('/')}
            onChatterClick={handleChatterClick}
          />
        ) : isStreamCompare && streamCompareIds.length >= 2 ? (
          streamCompareLoading ? (
            <div className="recap-loading">
              <div className="recap-spinner" />
              Loading comparison...
            </div>
          ) : (
            <CompareView
              sessionIds={streamCompareIds}
              channelLogin=""
              onBack={() => navigateTo('/')}
              onChatterClick={handleChatterClick}
              labels={streamCompareLabels}
            />
          )
        ) : currentPath === '/suggested' ? (
          <SuggestedStreamers user={user} />
        ) : currentPath === '/streamers' ? (
          <StreamerListPage onNavigate={navigateTo} />
        ) : (
          <StreamerDirectory user={user} />
        )}
      </div>

      {currentPath !== '/suggested' && !isChannelCompare && !isStreamCompare && (
        <StatsPanel channelLogin={channelLogin ?? undefined} onAuthorClick={handleChatterClick} />
      )}
      <ChannelCompareBar
        items={channelCompareItems}
        onRemove={removeChannelCompare}
        onClear={clearChannelCompare}
        bottomOffset={compareItems.length > 0 ? 64 : 0}
      />
      <CompareBar
        items={compareItems}
        onRemove={removeCompareItem}
        onClear={clearCompare}
      />
      <ChatterDrawer author={drawerAuthor} onClose={() => setDrawerAuthor(null)} />
    </>
  );
}
