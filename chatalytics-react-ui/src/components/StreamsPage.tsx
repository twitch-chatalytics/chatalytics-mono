import { useEffect, useState } from 'react';
import StreamList from './StreamList';
import StreamRecapView from './StreamRecapView';

function getSessionFromPath(): number | null {
  const match = window.location.pathname.match(/^\/streams\/(\d+)$/);
  return match ? Number(match[1]) : null;
}

export default function StreamsPage() {
  const [selectedSession, setSelectedSession] = useState<number | null>(() => getSessionFromPath());

  useEffect(() => {
    const handlePopState = () => {
      setSelectedSession(getSessionFromPath());
    };
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

  const handleSelect = (sessionId: number) => {
    setSelectedSession(sessionId);
    window.history.pushState(null, '', `/streams/${sessionId}`);
    document.title = `Stream Recap — chatalytics`;
  };

  const handleBack = () => {
    setSelectedSession(null);
    window.history.pushState(null, '', '/streams');
    document.title = 'Streams — chatalytics';
  };

  if (selectedSession) {
    return <StreamRecapView sessionId={selectedSession} onBack={handleBack} />;
  }

  return <StreamList onSelectSession={handleSelect} />;
}
