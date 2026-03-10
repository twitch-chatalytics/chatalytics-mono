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
  { key: 'compare', label: 'Compare', icon: IconCompare },
  { key: 'brand-safety', label: 'Brand Safety', icon: IconShield },
  { key: 'alerts', label: 'Alerts', icon: IconBell },
  { key: 'campaigns', label: 'Campaigns', icon: IconCampaign },
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

      {/* Export Report */}
      {channel && (
        <div className="sidebar-export">
          <button
            className="sidebar-export-btn"
            onClick={() => window.open(`/report/${channel.login}`, '_blank')}
          >
            <IconExport />
            <span>Export Report</span>
          </button>
        </div>
      )}

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

function IconCompare() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
      <rect x="1.5" y="3" width="5" height="12" rx="1" />
      <rect x="11.5" y="3" width="5" height="12" rx="1" />
      <line x1="8" y1="7" x2="10" y2="7" />
      <line x1="8" y1="11" x2="10" y2="11" />
    </svg>
  );
}

function IconShield() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
      <path d="M9 1.5L2.5 4.5V8.5C2.5 12.5 5.5 15.5 9 16.5C12.5 15.5 15.5 12.5 15.5 8.5V4.5L9 1.5Z" />
      <polyline points="6,9 8,11 12,7" />
    </svg>
  );
}

function IconBell() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
      <path d="M13.5 6.5a4.5 4.5 0 1 0-9 0c0 5-2.25 6.5-2.25 6.5h13.5s-2.25-1.5-2.25-6.5" />
      <path d="M10.3 15a1.5 1.5 0 0 1-2.6 0" />
    </svg>
  );
}

function IconCampaign() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
      <path d="M2 4.5v9l5-2.5V7z" />
      <path d="M7 7l7-3v9l-7-3" />
      <line x1="14" y1="9" x2="16.5" y2="9" />
      <line x1="14.5" y1="6.5" x2="16" y2="5.5" />
      <line x1="14.5" y1="11.5" x2="16" y2="12.5" />
    </svg>
  );
}

function IconExport() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
      <path d="M14 10v3a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1v-3" />
      <polyline points="4.5,6.5 8,3 11.5,6.5" />
      <line x1="8" y1="3" x2="8" y2="11" />
    </svg>
  );
}
