import { useEffect, useRef, useState, useCallback } from 'react';
import './TickerNumber.css';

interface TickerNumberProps {
  value: number;
  label?: string;
  size?: 'default' | 'hero';
}

/**
 * Smooth-counting number display. Animates from the previous value to the new
 * one over ~180ms — fast enough to settle before the next SSE update (~200ms),
 * slow enough to look like a fluid count-up rather than a hard snap.
 */
export default function TickerNumber({ value, label, size = 'default' }: TickerNumberProps) {
  const [display, setDisplay] = useState(value);
  const fromRef = useRef(value);
  const rafRef = useRef(0);

  const animate = useCallback((target: number) => {
    cancelAnimationFrame(rafRef.current);

    const from = fromRef.current;
    if (from === target) { setDisplay(target); return; }

    const duration = 180;
    const start = performance.now();

    function tick(now: number) {
      const t = Math.min((now - start) / duration, 1);
      // ease-out quad
      const eased = 1 - (1 - t) * (1 - t);
      const current = Math.round(from + (target - from) * eased);
      setDisplay(current);

      if (t < 1) {
        rafRef.current = requestAnimationFrame(tick);
      } else {
        fromRef.current = target;
      }
    }

    rafRef.current = requestAnimationFrame(tick);
  }, []);

  useEffect(() => {
    animate(value);
    return () => cancelAnimationFrame(rafRef.current);
  }, [value, animate]);

  // When a new target arrives mid-animation, snap fromRef to current display
  // so the next animation starts from where we visually are, not from the old target
  useEffect(() => {
    fromRef.current = display;
  }, [value]); // eslint-disable-line react-hooks/exhaustive-deps

  const formatted = display.toLocaleString();

  return (
    <div className={`ticker-number ${size === 'hero' ? 'ticker-number--hero' : ''}`}>
      <span className="ticker-value" aria-label={formatted}>
        {formatted.split('').map((ch, i) =>
          ch === ',' ? (
            <span key={`sep-${i}`} className="ticker-comma">,</span>
          ) : (
            <span key={`d-${i}`} className="ticker-digit">{ch}</span>
          )
        )}
      </span>
      {label && <span className="ticker-label">{label}</span>}
    </div>
  );
}
