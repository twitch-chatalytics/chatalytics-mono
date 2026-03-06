import { useMemo, useState } from 'react';
import { AnimatePresence } from 'framer-motion';
import { Message } from '../types/message';
import { fetchMessageContext } from '../api/client';
import ContextSlice, { ContextLoading } from './MessageContext';
import './MessageItem.css';

function highlightText(text: string, term?: string) {
  if (!term?.trim()) return text;
  const regex = new RegExp(`(${term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi');
  const parts = text.split(regex);
  return parts.map((part, i) =>
    regex.test(part) ? <mark key={i} className="highlight">{part}</mark> : part
  );
}

interface MessageItemProps {
  message: Message;
  highlightTerm?: string;
}

export default function MessageItem({ message, highlightTerm }: MessageItemProps) {
  const [expanded, setExpanded] = useState(false);
  const [contextMessages, setContextMessages] = useState<Message[]>([]);
  const [isLoadingContext, setIsLoadingContext] = useState(false);
  const time = new Date(message.timestamp).toLocaleString();

  const { before, after } = useMemo(() => {
    const idx = contextMessages.findIndex((m) => m.id === message.id);
    if (idx === -1) {
      return { before: contextMessages, after: [] };
    }
    return {
      before: contextMessages.slice(0, idx),
      after: contextMessages.slice(idx + 1),
    };
  }, [contextMessages, message.id]);

  const handleToggleContext = async () => {
    if (expanded) {
      setExpanded(false);
      return;
    }

    setExpanded(true);
    setIsLoadingContext(true);
    const results = await fetchMessageContext(message.id);
    setContextMessages(results);
    setIsLoadingContext(false);
  };

  return (
    <div className={`message-item${expanded ? ' message-item-expanded' : ''}`}>
      <AnimatePresence>
        {expanded && isLoadingContext && <ContextLoading />}
        {expanded && !isLoadingContext && before.length > 0 && (
          <ContextSlice key="before" messages={before} direction="before" />
        )}
      </AnimatePresence>

      <div className="message-content" onClick={handleToggleContext}>
        <div className={`message-anchor${expanded ? ' message-anchor-active' : ''}`}>
          <div className="message-meta">
            <span className="message-author">{message.author}</span>
            <span className="message-time">{time}</span>
            <span className="message-context-hint">
              {expanded ? 'hide context' : 'show context'}
            </span>
          </div>
          <div className="message-text">{highlightText(message.messageText, highlightTerm)}</div>
        </div>
      </div>

      <AnimatePresence>
        {expanded && !isLoadingContext && after.length > 0 && (
          <ContextSlice key="after" messages={after} direction="after" />
        )}
      </AnimatePresence>
    </div>
  );
}
