import { useEffect, useRef } from 'react';
import { motion } from 'framer-motion';
import { Message } from '../types/message';
import './MessageContext.css';

interface ContextSliceProps {
  messages: Message[];
  direction: 'before' | 'after';
}

function ContextRow({ msg, index, direction }: { msg: Message; index: number; direction: 'before' | 'after' }) {
  const time = new Date(msg.timestamp).toLocaleTimeString();
  const yOffset = direction === 'before' ? 6 : -6;

  return (
    <motion.div
      className="context-msg"
      initial={{ opacity: 0, y: yOffset }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.2, delay: index * 0.025 }}
    >
      <span className="context-msg-time">{time}</span>
      <span className="context-msg-author">{msg.author}</span>
      <span className="context-msg-text">{msg.messageText}</span>
    </motion.div>
  );
}

export default function ContextSlice({ messages, direction }: ContextSliceProps) {
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (direction === 'before' && scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [direction, messages]);

  if (messages.length === 0) return null;

  const label = direction === 'before' ? 'before' : 'after';
  const staggerMessages = direction === 'before' ? [...messages].reverse() : messages;

  return (
    <motion.div
      className={`context-slice context-slice-${direction}`}
      initial={{ opacity: 0, height: 0 }}
      animate={{ opacity: 1, height: 'auto' }}
      exit={{ opacity: 0, height: 0 }}
      transition={{ duration: 0.3, ease: [0.25, 0.1, 0.25, 1] }}
    >
      <div className="context-divider">
        <div className="context-line" />
        <span className="context-label">{messages.length} {label}</span>
        <div className="context-line" />
      </div>
      <div className="context-messages" ref={scrollRef}>
        {messages.map((msg) => {
          const staggerIdx = staggerMessages.findIndex((m) => m.id === msg.id);
          return (
            <ContextRow key={msg.id} msg={msg} index={staggerIdx} direction={direction} />
          );
        })}
      </div>
    </motion.div>
  );
}

export function ContextLoading() {
  return (
    <motion.div
      className="context-loading-wrapper"
      initial={{ opacity: 0, height: 0 }}
      animate={{ opacity: 1, height: 'auto' }}
      exit={{ opacity: 0, height: 0 }}
      transition={{ duration: 0.25 }}
    >
      <div className="context-loading">
        <div className="context-spinner" />
        <span>Loading context...</span>
      </div>
    </motion.div>
  );
}
