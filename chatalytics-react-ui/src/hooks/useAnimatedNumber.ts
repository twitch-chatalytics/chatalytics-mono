import { useEffect, useState } from 'react';

export function useAnimatedNumber(target: number, duration = 1200) {
  const [value, setValue] = useState(0);

  useEffect(() => {
    if (target === 0) { setValue(0); return; }
    const start = performance.now();

    function tick(now: number) {
      const elapsed = now - start;
      const progress = Math.min(elapsed / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      setValue(Math.round(target * eased));
      if (progress < 1) requestAnimationFrame(tick);
    }

    requestAnimationFrame(tick);
  }, [target, duration]);

  return value;
}
