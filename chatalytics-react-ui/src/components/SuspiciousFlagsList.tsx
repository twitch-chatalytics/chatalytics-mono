import { SuspiciousFlag } from '../types/message';

interface SuspiciousFlagsListProps {
  flags: SuspiciousFlag[];
}

export default function SuspiciousFlagsList({ flags }: SuspiciousFlagsListProps) {
  if (flags.length === 0) {
    return (
      <div className="flags-empty">
        No risk indicators detected for this session.
      </div>
    );
  }

  const getFlagIcon = (flag: string) => {
    if (flag.includes('chat_engagement')) return '\u26A0';
    if (flag.includes('repetitive')) return '\u2B6F';
    if (flag.includes('single_message')) return '\u2139';
    if (flag.includes('flat_chat')) return '\u2796';
    if (flag.includes('correlation')) return '\u2195';
    return '\u26A0';
  };

  return (
    <div className="flags-list">
      {flags.map((f, i) => (
        <div key={i} className="flag-item">
          <span className="flag-icon">{getFlagIcon(f.flag)}</span>
          <div className="flag-content">
            <span className="flag-name">{formatFlagName(f.flag)}</span>
            <span className="flag-detail">{f.detail}</span>
          </div>
        </div>
      ))}
    </div>
  );
}

function formatFlagName(flag: string): string {
  return flag
    .split('_')
    .map(w => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ');
}
