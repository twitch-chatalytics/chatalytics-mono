import { useState } from 'react';
import { RepeatGroup } from '../types/message';
import './RepeatDetector.css';

interface Props {
  groups: RepeatGroup[];
}

export default function RepeatDetector({ groups }: Props) {
  const [expanded, setExpanded] = useState(false);

  if (groups.length === 0) return null;

  const visible = expanded ? groups : groups.slice(0, 5);
  const remaining = groups.length - 5;

  return (
    <div className="repeat-detector">
      <div className="repeat-pills">
        {visible.map((g) => (
          <span key={g.text} className="repeat-pill">
            "{g.text}" <span className="repeat-pill-count">&times;{g.count}</span>
          </span>
        ))}
        {!expanded && remaining > 0 && (
          <button className="repeat-more" onClick={() => setExpanded(true)}>
            +{remaining} more
          </button>
        )}
      </div>
    </div>
  );
}
