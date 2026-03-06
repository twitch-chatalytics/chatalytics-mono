import { useState, useEffect } from 'react';
import './MessageFilter.css';

interface Props {
  matchCount: number;
  totalCount: number;
  onFilterChange: (term: string) => void;
}

export default function MessageFilter({ matchCount, totalCount, onFilterChange }: Props) {
  const [input, setInput] = useState('');

  useEffect(() => {
    const timer = setTimeout(() => onFilterChange(input), 150);
    return () => clearTimeout(timer);
  }, [input, onFilterChange]);

  const hasFilter = input.trim().length > 0;

  return (
    <div className="message-filter">
      <input
        type="text"
        className="message-filter-input"
        placeholder="Filter messages..."
        value={input}
        onChange={(e) => setInput(e.target.value)}
      />
      {hasFilter && (
        <span className="message-filter-count">
          {matchCount} of {totalCount} match
        </span>
      )}
    </div>
  );
}
