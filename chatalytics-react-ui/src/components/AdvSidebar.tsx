import { ChannelAuthenticityReport, ChannelProfile } from '../types/message';

interface AdvSidebarProps {
  activeView: string;
  onViewChange: (view: string) => void;
  channel: ChannelProfile | null;
  report: ChannelAuthenticityReport;
}

const NAV_ITEMS = [
  { key: 'overview', label: 'Overview', icon: IconGrid },
  { key: 'growth', label: 'Growth Intel', icon: IconTrend },
  { key: 'sessions', label: 'Sessions', icon: IconList },
  { key: 'methodology', label: 'Methodology', icon: IconInfo },
];

export default function AdvSidebar({ activeView, onViewChange, channel, report }: AdvSidebarProps) {
  return (
    <aside className="adv-sidebar">
      {/* Channel identity */}
      {channel && (
        <div className="sidebar-channel">
          {channel.profileImageUrl && (
            <img src={channel.profileImageUrl} alt="" className="sidebar-avatar" />
          )}
          <div className="sidebar-channel-info">
            <span className="sidebar-channel-name">{channel.displayName}</span>
            <span className="sidebar-channel-sub">Authenticity Report</span>
          </div>
        </div>
      )}

      <div className="sidebar-divider" />

      {/* Navigation */}
      <nav className="sidebar-nav">
        {NAV_ITEMS.map(item => (
          <button
            key={item.key}
            className={`sidebar-item ${activeView === item.key ? 'active' : ''}`}
            onClick={() => onViewChange(item.key)}
          >
            <item.icon />
            <span>{item.label}</span>
          </button>
        ))}
      </nav>

      <div className="sidebar-spacer" />

      {/* Footer badges */}
      <div className="sidebar-footer">
        <span className={`sidebar-badge sidebar-risk-${report.riskLevel}`}>
          {report.riskLevel} risk
        </span>
        <span className="sidebar-badge sidebar-sessions">
          {report.sessionsAnalyzed} sessions
        </span>
      </div>
    </aside>
  );
}

function IconGrid() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" fill="none" stroke="currentColor" strokeWidth="1.5">
      <rect x="1.5" y="1.5" width="6" height="6" rx="1.5" />
      <rect x="10.5" y="1.5" width="6" height="6" rx="1.5" />
      <rect x="1.5" y="10.5" width="6" height="6" rx="1.5" />
      <rect x="10.5" y="10.5" width="6" height="6" rx="1.5" />
    </svg>
  );
}

function IconTrend() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="1.5,13 6,8 10,11 16.5,3.5" />
      <polyline points="12,3.5 16.5,3.5 16.5,8" />
    </svg>
  );
}

function IconList() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
      <line x1="5.5" y1="4.5" x2="16" y2="4.5" />
      <line x1="5.5" y1="9" x2="16" y2="9" />
      <line x1="5.5" y1="13.5" x2="16" y2="13.5" />
      <circle cx="2.5" cy="4.5" r="1" fill="currentColor" stroke="none" />
      <circle cx="2.5" cy="9" r="1" fill="currentColor" stroke="none" />
      <circle cx="2.5" cy="13.5" r="1" fill="currentColor" stroke="none" />
    </svg>
  );
}

function IconInfo() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" fill="none" stroke="currentColor" strokeWidth="1.5">
      <circle cx="9" cy="9" r="7" />
      <line x1="9" y1="8" x2="9" y2="12.5" strokeLinecap="round" />
      <circle cx="9" cy="5.8" r="0.7" fill="currentColor" stroke="none" />
    </svg>
  );
}
