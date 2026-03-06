import { useEffect, useRef } from 'react';
import { Message } from '../types/message';
import MessageItem from './MessageItem';
import './MessageList.css';

interface MessageListProps {
  messages: Message[];
  author: string;
  highlightTerm?: string;
  hasMore?: boolean;
  isLoadingMore?: boolean;
  onLoadMore?: () => void;
}

export default function MessageList({ messages, author, highlightTerm, hasMore, isLoadingMore, onLoadMore }: MessageListProps) {
  const sentinelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!hasMore || !onLoadMore) return;
    const sentinel = sentinelRef.current;
    if (!sentinel) return;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting) {
          onLoadMore();
        }
      },
      { rootMargin: '200px' },
    );

    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [hasMore, onLoadMore]);

  return (
    <div className="message-list">
      <div className="message-list-header">
        {messages.length} message{messages.length !== 1 ? 's' : ''} from <strong>{author}</strong>
      </div>
      <div className="message-list-items">
        {messages.map((msg) => (
          <MessageItem key={msg.id} message={msg} highlightTerm={highlightTerm} />
        ))}
      </div>
      {hasMore && (
        <div ref={sentinelRef} className="message-list-sentinel">
          {isLoadingMore && <div className="message-list-loading">Loading more...</div>}
        </div>
      )}
    </div>
  );
}
