import { motion } from 'framer-motion';

interface AuthenticityGaugeProps {
  score: number;
  confidenceLevel?: string;
  size?: number;
}

export default function AuthenticityGauge({ score, confidenceLevel, size = 200 }: AuthenticityGaugeProps) {
  const radius = (size - 20) / 2;

  const getColor = (s: number) => {
    if (s >= 70) return '#10b981';
    if (s >= 40) return '#eab308';
    return '#ef4444';
  };

  const getLabel = (s: number) => {
    if (s >= 80) return 'Strong';
    if (s >= 60) return 'Moderate';
    if (s >= 40) return 'Fair';
    if (s >= 20) return 'Weak';
    return 'Very Weak';
  };

  const color = getColor(score);
  const textColor = '#f3f3f3';
  const trackColor = 'rgba(255, 255, 255, 0.08)';
  const cx = size / 2;
  const cy = size / 2 + 10;

  return (
    <div className="authenticity-gauge" style={{ '--gauge-color': color } as React.CSSProperties}>
      <div
        className="gauge-glow"
        style={{ background: `radial-gradient(ellipse, ${color}, transparent 70%)` }}
      />
      <svg width={size} height={size * 0.65} viewBox={`0 0 ${size} ${size * 0.65}`}>
        {/* Background arc */}
        <path
          className="gauge-track"
          d={describeArc(cx, cy, radius, 180, 360)}
          fill="none"
          stroke={trackColor}
          strokeWidth={14}
          strokeLinecap="round"
        />
        {/* Score arc */}
        <motion.path
          d={describeArc(cx, cy, radius, 180, 180 + (score / 100) * 180)}
          fill="none"
          stroke={color}
          strokeWidth={14}
          strokeLinecap="round"
          initial={{ pathLength: 0 }}
          animate={{ pathLength: 1 }}
          transition={{ duration: 1.2, ease: 'easeOut' }}
        />
        {/* Score text */}
        <text
          className="gauge-score-text"
          x={cx} y={cy - 15}
          textAnchor="middle"
          fontSize={size * 0.24}
          fontWeight={800}
          fill={textColor}
        >
          {score}
        </text>
        <text
          className="gauge-label-text"
          x={cx} y={cy + 10}
          textAnchor="middle"
          fontSize={13}
          fontWeight={600}
          fill={color}
        >
          {getLabel(score)}
        </text>
      </svg>
      {confidenceLevel && (
        <div className="gauge-confidence">
          <span className={`confidence-badge confidence-${confidenceLevel}`}>
            {confidenceLevel} confidence
          </span>
        </div>
      )}
    </div>
  );
}

function describeArc(cx: number, cy: number, r: number, startAngle: number, endAngle: number): string {
  const start = polarToCartesian(cx, cy, r, endAngle);
  const end = polarToCartesian(cx, cy, r, startAngle);
  const largeArc = endAngle - startAngle > 180 ? 1 : 0;
  return `M ${start.x} ${start.y} A ${r} ${r} 0 ${largeArc} 0 ${end.x} ${end.y}`;
}

function polarToCartesian(cx: number, cy: number, r: number, angle: number) {
  const rad = ((angle - 90) * Math.PI) / 180;
  return { x: cx + r * Math.cos(rad), y: cy + r * Math.sin(rad) };
}
