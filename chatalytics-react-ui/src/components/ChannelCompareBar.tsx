import { motion, AnimatePresence } from 'framer-motion';
import { ChannelProfile } from '../types/message';
import './CompareBar.css';

const COLORS = ['#6366f1', '#14b8a6', '#f59e0b'];

interface Props {
  items: ChannelProfile[];
  onRemove: (channelId: number) => void;
  onClear: () => void;
  bottomOffset?: number;
}

export default function ChannelCompareBar({ items, onRemove, onClear, bottomOffset = 0 }: Props) {
  const handleCompare = () => {
    if (items.length < 2) return;
    const logins = items.map(c => c.login).join(',');
    window.history.pushState(null, '', `/compare/channels?ids=${logins}`);
    window.dispatchEvent(new PopStateEvent('popstate'));
  };

  return (
    <AnimatePresence>
      {items.length > 0 && (
        <motion.div
          className="global-compare-bar"
          style={bottomOffset ? { bottom: bottomOffset } : undefined}
          initial={{ y: 80, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          exit={{ y: 80, opacity: 0 }}
          transition={{ type: 'spring', damping: 25, stiffness: 300 }}
        >
          <div className="global-compare-bar-inner">
            <div className="global-compare-items">
              {items.map((channel, i) => (
                <motion.div
                  key={channel.id}
                  className="global-compare-item"
                  layout
                  initial={{ opacity: 0, scale: 0.9 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.9 }}
                  transition={{ duration: 0.2 }}
                >
                  <span
                    className="global-compare-item-dot"
                    style={{ background: COLORS[i] }}
                  />
                  {channel.profileImageUrl && (
                    <img
                      src={channel.profileImageUrl}
                      alt=""
                      className="global-compare-item-avatar"
                    />
                  )}
                  <div className="global-compare-item-text">
                    <span className="global-compare-item-channel">{channel.displayName}</span>
                  </div>
                  <button
                    className="global-compare-item-remove"
                    onClick={() => onRemove(channel.id)}
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
