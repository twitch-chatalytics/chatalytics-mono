import { useState } from 'react';
import { ActivityBucket } from '../types/message';
import './ActivityTimeline.css';

interface Props {
  buckets: ActivityBucket[];
}

export default function ActivityTimeline({ buckets }: Props) {
  const [tooltip, setTooltip] = useState<{ x: number; label: string } | null>(null);

  if (buckets.length < 3) return null;

  const maxCount = Math.max(...buckets.map((b) => b.count));
  const barWidth = Math.max(2, Math.min(8, 500 / buckets.length));
  const gap = Math.max(1, barWidth * 0.3);
  const svgWidth = buckets.length * (barWidth + gap);
  const svgHeight = 48;
  const labelY = svgHeight + 14;

  const labelIndices = [0, Math.floor(buckets.length / 2), buckets.length - 1];

  return (
    <div className="activity-timeline">
      <svg
        viewBox={`0 0 ${svgWidth} ${svgHeight + 20}`}
        preserveAspectRatio="none"
        className="activity-timeline-svg"
      >
        {buckets.map((b, i) => {
          const height = maxCount > 0 ? (b.count / maxCount) * svgHeight : 0;
          const x = i * (barWidth + gap);
          const opacity = maxCount > 0 ? 0.3 + 0.7 * (b.count / maxCount) : 0.3;
          return (
            <rect
              key={b.date}
              x={x}
              y={svgHeight - height}
              width={barWidth}
              height={height}
              rx={1}
              fill="#3b82f6"
              opacity={opacity}
              onMouseEnter={() => setTooltip({ x: x + barWidth / 2, label: `${b.date}: ${b.count}` })}
              onMouseLeave={() => setTooltip(null)}
            />
          );
        })}
        {labelIndices.map((idx) => (
          <text
            key={idx}
            x={idx * (barWidth + gap) + barWidth / 2}
            y={labelY}
            textAnchor="middle"
            className="activity-timeline-label"
          >
            {buckets[idx].date.slice(5)}
          </text>
        ))}
      </svg>
      {tooltip && (
        <div className="activity-timeline-tooltip" style={{ left: `${(tooltip.x / svgWidth) * 100}%` }}>
          {tooltip.label}
        </div>
      )}
    </div>
  );
}
