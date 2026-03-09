import { motion } from 'framer-motion';

interface SignalBreakdownCardProps {
  label: string;
  value: number | null;
  weight: string;
  description: string;
  interpretation?: string;
  details?: { label: string; value: number | null; format?: 'percent' | 'decimal' | 'correlation' }[];
}

export default function SignalBreakdownCard({ label, value, weight, description, interpretation, details }: SignalBreakdownCardProps) {
  const displayValue = value != null ? Math.round(value * 100) : null;

  const getBarColor = (v: number) => {
    if (v >= 70) return '#22c55e';
    if (v >= 40) return '#eab308';
    return '#ef4444';
  };

  const getVerdictClass = (v: number | null) => {
    if (v == null) return '';
    if (v >= 70) return 'signal-verdict-good';
    if (v >= 40) return 'signal-verdict-fair';
    return 'signal-verdict-poor';
  };

  const getVerdictLabel = (v: number | null) => {
    if (v == null) return '';
    if (v >= 80) return 'Healthy';
    if (v >= 60) return 'Typical';
    if (v >= 40) return 'Below average';
    if (v >= 20) return 'Concerning';
    return 'Atypical';
  };

  const formatDetail = (d: { value: number | null; format?: string }) => {
    if (d.value == null) return 'N/A';
    if (d.format === 'correlation') return d.value.toFixed(2);
    if (d.format === 'decimal') return d.value.toFixed(2);
    return `${(d.value * 100).toFixed(1)}%`;
  };

  return (
    <div className="signal-card">
      <div className="signal-header">
        <span className="signal-label">{label}</span>
        <div className="signal-header-right">
          {displayValue != null && (
            <span className={`signal-verdict ${getVerdictClass(displayValue)}`}>
              {getVerdictLabel(displayValue)}
            </span>
          )}
          <span className="signal-weight">{weight}</span>
        </div>
      </div>
      <div className="signal-bar-container">
        <motion.div
          className="signal-bar"
          style={{ backgroundColor: displayValue != null ? getBarColor(displayValue) : '#e2e8f0' }}
          initial={{ width: 0 }}
          animate={{ width: displayValue != null ? `${displayValue}%` : '0%' }}
          transition={{ duration: 0.8, ease: 'easeOut' }}
        />
      </div>
      <div className="signal-score">
        {displayValue != null ? `${displayValue}/100` : 'N/A'}
      </div>
      <p className="signal-description">{description}</p>
      {interpretation && (
        <p className="signal-interpretation">{interpretation}</p>
      )}
      {details && details.length > 0 && (
        <div className="signal-details">
          {details.map((d, i) => (
            <div key={i} className="signal-detail-row">
              <span className="signal-detail-label">{d.label}</span>
              <span className="signal-detail-value">
                {formatDetail(d)}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
