import { useEffect, useRef, useState } from 'react';

export function useAnimatedNumber(target: number, duration = 1200) {
  const [value, setValue] = useState(0);
  const prevRef = useRef(0);

  useEffect(() => {
    if (target === 0) { setValue(0); prevRef.current = 0; return; }
    const from = prevRef.current;
    const start = performance.now();

    function tick(now: number) {
      const elapsed = now - start;
      const progress = Math.min(elapsed / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      setValue(Math.round(from + (target - from) * eased));
      if (progress < 1) {
        requestAnimationFrame(tick);
      } else {
        prevRef.current = target;
      }
    }

    requestAnimationFrame(tick);
  }, [target, duration]);

  return value;
}
