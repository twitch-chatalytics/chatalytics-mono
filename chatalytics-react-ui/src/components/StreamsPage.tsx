import { useEffect, useState } from 'react';
import { ChannelProfile, CompareItem } from '../types/message';
import { fetchChannelByLogin } from '../api/client';
import StreamList from './StreamList';
import StreamRecapView from './StreamRecapView';
import CompareView from './CompareView';

interface Props {
  channelLogin: string;
  onChatterClick: (author: string) => void;
  compareItems: CompareItem[];
  onAddCompare: (item: CompareItem) => void;
  onRemoveCompare: (sessionId: number) => void;
  channelCompareItems: ChannelProfile[];
  onAddChannelCompare: (channel: ChannelProfile) => void;
  onRemoveChannelCompare: (channelId: number) => void;
}

function parseUrl(): { session: number | null; compareIds: number[] } {
  const sessionMatch = window.location.pathname.match(/^\/channel\/[^/]+\/streams\/(\d+)$/);
  const compareMatch = window.location.pathname.match(/^\/channel\/[^/]+\/compare$/);

  if (sessionMatch) {
    return { session: Number(sessionMatch[1]), compareIds: [] };
  }

  if (compareMatch) {
    const params = new URLSearchParams(window.location.search);
    const raw = params.get('sessions') || '';
    const ids = raw.split(',').map(Number).filter(n => n > 0);
    if (ids.length >= 2) {
      return { session: null, compareIds: ids.slice(0, 3) };
    }
  }

  return { session: null, compareIds: [] };
}

export default function StreamsPage({ channelLogin, onChatterClick, compareItems, onAddCompare, onRemoveCompare, channelCompareItems, onAddChannelCompare, onRemoveChannelCompare }: Props) {
  const [twitchId, setTwitchId] = useState<number | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [selectedSession, setSelectedSession] = useState<number | null>(() => parseUrl().session);
  const [compareSessionIds, setCompareSessionIds] = useState<number[]>(() => parseUrl().compareIds);

  useEffect(() => {
    setTwitchId(null);
    setNotFound(false);
    fetchChannelByLogin(channelLogin).then(ch => {
      if (ch) setTwitchId(ch.id);
      else setNotFound(true);
    });
  }, [channelLogin]);

  useEffect(() => {
    const handlePopState = () => {
      const { session, compareIds } = parseUrl();
      setSelectedSession(session);
      setCompareSessionIds(compareIds);
    };
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, [channelLogin]);

  const handleSelect = (sessionId: number) => {
    setSelectedSession(sessionId);
    setCompareSessionIds([]);
    window.history.pushState(null, '', `/channel/${channelLogin}/streams/${sessionId}`);
    document.title = `Stream Recap — chatalytics`;
  };

  const handleBack = () => {
    setSelectedSession(null);
    setCompareSessionIds([]);
    window.history.pushState(null, '', `/channel/${channelLogin}`);
    document.title = 'chatalytics';
  };

  if (notFound) {
    return <div className="stream-list-empty">Channel "{channelLogin}" not found.</div>;
  }

  if (twitchId === null) {
    return <div className="stream-list-loading">Loading channel...</div>;
  }

  if (compareSessionIds.length >= 2) {
    return (
      <CompareView
        sessionIds={compareSessionIds}
        channelLogin={channelLogin}
        onBack={handleBack}
        onChatterClick={onChatterClick}
      />
    );
  }

  if (selectedSession) {
    return <StreamRecapView sessionId={selectedSession} onBack={handleBack} onChatterClick={onChatterClick} />;
  }

  return (
    <StreamList
      twitchId={twitchId}
      channelLogin={channelLogin}
      onSelectSession={handleSelect}
      compareItems={compareItems}
      onAddCompare={onAddCompare}
      onRemoveCompare={onRemoveCompare}
      channelCompareItems={channelCompareItems}
      onAddChannelCompare={onAddChannelCompare}
      onRemoveChannelCompare={onRemoveChannelCompare}
    />
  );
}
