import { AuthenticityTrendPoint } from '../types/message';

interface AuthenticityTrendChartProps {
  data: AuthenticityTrendPoint[];
}

export default function AuthenticityTrendChart({ data }: AuthenticityTrendChartProps) {
  if (data.length < 2) {
    return (
      <div className="trend-chart-empty">
        Not enough sessions to display a trend yet.
      </div>
    );
  }

  const width = 800;
  const height = 220;
  const padding = { top: 20, right: 20, bottom: 30, left: 40 };
  const chartW = width - padding.left - padding.right;
  const chartH = height - padding.top - padding.bottom;

  const scores = data.map(d => d.score);
  const minScore = Math.max(0, Math.min(...scores) - 10);
  const maxScore = Math.min(100, Math.max(...scores) + 10);

  const xScale = (i: number) => padding.left + (i / (data.length - 1)) * chartW;
  const yScale = (v: number) => padding.top + chartH - ((v - minScore) / (maxScore - minScore)) * chartH;

  const linePath = data
    .map((d, i) => `${i === 0 ? 'M' : 'L'} ${xScale(i)} ${yScale(d.score)}`)
    .join(' ');

  // Color zones
  const zoneY70 = yScale(70);
  const zoneY40 = yScale(40);

  const formatDate = (dateStr: string) => {
    const d = new Date(dateStr);
    return `${d.getMonth() + 1}/${d.getDate()}`;
  };

  // Show ~5 x-axis labels
  const labelStep = Math.max(1, Math.floor(data.length / 5));

  return (
    <div className="trend-chart">
      <svg viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="xMidYMid meet">
        {/* Zone backgrounds */}
        <rect x={padding.left} y={padding.top} width={chartW} height={zoneY40 - padding.top}
              fill="#fef2f2" opacity={0.5} />
        <rect x={padding.left} y={zoneY40} width={chartW} height={zoneY70 - zoneY40}
              fill="#fefce8" opacity={0.5} />
        <rect x={padding.left} y={zoneY70} width={chartW} height={padding.top + chartH - zoneY70}
              fill="#f0fdf4" opacity={0.5} />

        {/* Grid lines */}
        {[minScore, 40, 70, maxScore].map(v => (
          <line key={v} x1={padding.left} x2={padding.left + chartW}
                y1={yScale(v)} y2={yScale(v)} stroke="#e2e8f0" strokeDasharray="4 4" />
        ))}

        {/* Y-axis labels */}
        {[minScore, 40, 70, maxScore].map(v => (
          <text key={v} x={padding.left - 8} y={yScale(v) + 4}
                textAnchor="end" fontSize={10} fill="#94a3b8">{v}</text>
        ))}

        {/* X-axis labels */}
        {data.map((d, i) =>
          i % labelStep === 0 ? (
            <text key={i} x={xScale(i)} y={height - 5}
                  textAnchor="middle" fontSize={10} fill="#94a3b8">
              {formatDate(d.date)}
            </text>
          ) : null
        )}

        {/* Score line */}
        <path d={linePath} fill="none" stroke="#4f46e5" strokeWidth={2.5} strokeLinejoin="round" />

        {/* Data points */}
        {data.map((d, i) => (
          <circle key={i} cx={xScale(i)} cy={yScale(d.score)} r={3}
                  fill="#4f46e5" stroke="#fff" strokeWidth={1.5} />
        ))}
      </svg>
    </div>
  );
}
