import { motion, AnimatePresence } from 'framer-motion';
import { CompareItem } from '../types/message';
import './CompareBar.css';

const STREAM_COLORS = ['#6366f1', '#14b8a6', '#f59e0b'];

function formatShortDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-US', {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
  });
}

interface Props {
  items: CompareItem[];
  onRemove: (sessionId: number) => void;
  onClear: () => void;
}

export default function CompareBar({ items, onRemove, onClear }: Props) {
  const handleCompare = () => {
    if (items.length < 2) return;
    const ids = items.map(i => i.sessionId).join(',');
    window.history.pushState(null, '', `/compare/streams?sessions=${ids}`);
    window.dispatchEvent(new PopStateEvent('popstate'));
  };

  return (
    <AnimatePresence>
      {items.length > 0 && (
        <motion.div
          className="global-compare-bar"
          initial={{ y: 80, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          exit={{ y: 80, opacity: 0 }}
          transition={{ type: 'spring', damping: 25, stiffness: 300 }}
        >
          <div className="global-compare-bar-inner">
            <div className="global-compare-items">
              {items.map((item, i) => (
                <motion.div
                  key={item.sessionId}
                  className="global-compare-item"
                  layout
                  initial={{ opacity: 0, scale: 0.9 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.9 }}
                  transition={{ duration: 0.2 }}
                >
                  <span
                    className="global-compare-item-dot"
                    style={{ background: STREAM_COLORS[i] }}
                  />
                  {item.profileImageUrl && (
                    <img
                      src={item.profileImageUrl}
                      alt=""
                      className="global-compare-item-avatar"
                    />
                  )}
                  <div className="global-compare-item-text">
                    <span className="global-compare-item-channel">{item.channelDisplayName}</span>
                    <span className="global-compare-item-date">{formatShortDate(item.startTime)}</span>
                  </div>
                  <button
                    className="global-compare-item-remove"
                    onClick={() => onRemove(item.sessionId)}
                    title="Remove"
                  >
                    <svg width="12" height="12" viewBox="0 0 16 16" fill="none">
                      <path d="M4 4l8 8M12 4l-8 8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
                    </svg>
                  </button>
                </motion.div>
              ))}
            </div>

            <div className="global-compare-actions">
              <button className="global-compare-clear" onClick={onClear}>
                Clear
              </button>
              <button
                className="global-compare-btn"
                onClick={handleCompare}
                disabled={items.length < 2}
              >
                Compare{items.length >= 2 ? ` (${items.length})` : ''}
              </button>
            </div>
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
