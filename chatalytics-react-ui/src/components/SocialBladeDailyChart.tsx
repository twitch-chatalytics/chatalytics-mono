import { SocialBladeDailyPoint } from '../types/message';
import { GrowthAnomaly } from '../util/growthAnomalyDetector';

interface SocialBladeDailyChartProps {
  data: SocialBladeDailyPoint[];
  anomalies?: GrowthAnomaly[];
}

export default function SocialBladeDailyChart({ data, anomalies = [] }: SocialBladeDailyChartProps) {
  // Sort chronologically
  const sorted = [...data].sort((a, b) => a.date.localeCompare(b.date));
  const followers = sorted.map(d => d.followers ?? 0);
  const min = Math.min(...followers);
  const max = Math.max(...followers);
  const range = max - min || 1;

  // Compute daily changes for bar chart
  const changes: (number | null)[] = sorted.map((d, i) => {
    if (d.followerChange != null) return d.followerChange;
    if (i > 0 && d.followers != null && sorted[i - 1].followers != null)
      return d.followers! - sorted[i - 1].followers!;
    return null;
  });
  const maxChange = Math.max(1, ...changes.map(c => Math.abs(c ?? 0)));

  const w = 800;
  const h = 260;
  const pad = { top: 10, right: 10, bottom: 30, left: 10 };
  const barAreaH = 50;
  const plotH = h - pad.top - pad.bottom - barAreaH - 8;
  const plotW = w - pad.left - pad.right;
  const barY = pad.top + plotH + 8;

  const xPos = (i: number) => pad.left + (i / (sorted.length - 1)) * plotW;
  const yPos = (val: number) => pad.top + plotH - (((val - min) / range) * plotH);

  // Build anomaly date lookup
  const anomalyByDate = new Map<string, GrowthAnomaly>();
  for (const a of anomalies) {
    const key = a.date.substring(0, 10);
    if (!anomalyByDate.has(key) || a.severity === 'critical') {
      anomalyByDate.set(key, a);
    }
  }

  const points = sorted.map((d, i) => ({
    x: xPos(i),
    y: yPos(d.followers ?? 0),
  }));

  const areaPath = `M${points[0].x},${points[0].y} ${points.map(p => `L${p.x},${p.y}`).join(' ')} L${points[points.length - 1].x},${pad.top + plotH} L${points[0].x},${pad.top + plotH} Z`;
  const linePath = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x},${p.y}`).join(' ');

  // X-axis labels
  const labelIndices = [0, Math.floor(sorted.length / 4), Math.floor(sorted.length / 2), Math.floor(sorted.length * 3 / 4), sorted.length - 1];

  return (
    <div className="sb-daily-chart">
      <svg viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="xMidYMid meet">
        <defs>
          <linearGradient id="sbFillGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#4f46e5" stopOpacity="0.15" />
            <stop offset="100%" stopColor="#4f46e5" stopOpacity="0.01" />
          </linearGradient>
        </defs>

        {/* Follower line chart */}
        <path d={areaPath} fill="url(#sbFillGrad)" />
        <path d={linePath} fill="none" stroke="#4f46e5" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />

        {/* Anomaly markers on the line */}
        {sorted.map((d, i) => {
          const dateKey = d.date.substring(0, 10);
          const anomaly = anomalyByDate.get(dateKey);
          if (!anomaly) return null;
          const color = anomaly.severity === 'critical' ? '#ef4444' : '#eab308';
          return (
            <g key={`anomaly-${i}`}>
              <circle cx={points[i].x} cy={points[i].y} r={6} fill={color} opacity={0.25} />
              <circle cx={points[i].x} cy={points[i].y} r={3.5} fill={color} stroke="#fff" strokeWidth={1}>
                <title>{anomaly.detail}</title>
              </circle>
            </g>
          );
        })}

        {/* Daily change bars */}
        {changes.map((change, i) => {
          if (change == null || i === 0) return null;
          const barH = Math.max(1, (Math.abs(change) / maxChange) * (barAreaH - 4));
          const isPositive = change >= 0;
          const dateKey = sorted[i].date.substring(0, 10);
          const hasAnomaly = anomalyByDate.has(dateKey);
          const barWidth = Math.max(1, plotW / sorted.length - 1);

          let fill: string;
          if (hasAnomaly) {
            fill = anomalyByDate.get(dateKey)!.severity === 'critical' ? '#ef4444' : '#eab308';
          } else {
            fill = isPositive ? '#86efac' : '#fca5a5';
          }

          return (
            <rect
              key={`bar-${i}`}
              x={xPos(i) - barWidth / 2}
              y={isPositive ? barY + (barAreaH / 2) - barH : barY + (barAreaH / 2)}
              width={barWidth}
              height={barH}
              fill={fill}
              opacity={hasAnomaly ? 0.9 : 0.5}
              rx={1}
            >
              <title>{`${formatShortDate(sorted[i].date)}: ${change >= 0 ? '+' : ''}${change.toLocaleString()} followers`}</title>
            </rect>
          );
        })}

        {/* Zero line for bar chart */}
        <line x1={pad.left} x2={pad.left + plotW} y1={barY + barAreaH / 2} y2={barY + barAreaH / 2} stroke="#e2e8f0" strokeWidth={0.5} />

        {/* X-axis labels */}
        {labelIndices.map(idx => {
          if (idx >= sorted.length) return null;
          const d = sorted[idx];
          const x = xPos(idx);
          return (
            <text key={idx} x={x} y={h - 4} textAnchor="middle" fill="#94a3b8" fontSize="11" fontFamily="inherit">
              {formatShortDate(d.date)}
            </text>
          );
        })}
      </svg>
      <div className="sb-chart-range">
        <span>{formatCompact(min)} followers</span>
        <span>{formatCompact(max)} followers</span>
      </div>
    </div>
  );
}

function formatCompact(n: number): string {
  if (n >= 1_000_000_000) return `${(n / 1_000_000_000).toFixed(1)}B`;
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`;
  return n.toLocaleString();
}

function formatShortDate(dateStr: string): string {
  const d = new Date(dateStr);
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}
