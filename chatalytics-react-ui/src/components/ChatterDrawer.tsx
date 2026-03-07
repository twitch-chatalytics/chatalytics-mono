import { useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { Message } from '../types/message';
import { fetchMessagesByAuthor } from '../api/client';
import { useChatterProfile } from '../hooks/useChatterProfile';
import { useActivityBuckets } from '../hooks/useActivityBuckets';
import ChatterProfileCard from './ChatterProfile';
import ActivityTimeline from './ActivityTimeline';
import './ChatterDrawer.css';

interface Props {
  author: string | null;
  onClose: () => void;
}

export default function ChatterDrawer({ author, onClose }: Props) {
  const [messages, setMessages] = useState<Message[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!author) {
      setMessages([]);
      return;
    }
    setLoading(true);
    fetchMessagesByAuthor(author, {}, undefined, 20)
      .then(setMessages)
      .catch(() => setMessages([]))
      .finally(() => setLoading(false));
  }, [author]);

  const profile = useChatterProfile(author ?? '', messages);
  const activityBuckets = useActivityBuckets(messages);

  // Close on Escape
  useEffect(() => {
    if (!author) return;
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  }, [author, onClose]);

  return (
    <AnimatePresence>
      {author && (
        <>
          <motion.div
            className="modal-backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
            onClick={onClose}
          />
          <div className="modal-wrapper">
            <motion.div
              className="modal-dialog"
              initial={{ opacity: 0, scale: 0.95, y: 12 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 12 }}
              transition={{ type: 'spring', damping: 28, stiffness: 320 }}
            >
              <button className="modal-close" onClick={onClose} aria-label="Close">
                <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                  <path d="M4 4l8 8M12 4l-8 8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
                </svg>
              </button>

              <div className="modal-content">
                {loading ? (
                  <div className="modal-loading">
                    <div className="modal-spinner" />
                    Loading profile...
                  </div>
                ) : profile ? (
                  <ChatterProfileCard profile={profile}>
                    <ActivityTimeline buckets={activityBuckets} />
                  </ChatterProfileCard>
                ) : (
                  <div className="modal-empty">No data for this chatter.</div>
                )}
              </div>
            </motion.div>
          </div>
        </>
      )}
    </AnimatePresence>
  );
}
