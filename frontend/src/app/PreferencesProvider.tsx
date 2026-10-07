import { useEffect, useState, type ReactNode } from 'react';
import { PreferencesContext, type Theme } from './preferences';

export function PreferencesProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<Theme>(() => {
    const stored = localStorage.getItem('stockflow.theme');
    if (stored === 'light' || stored === 'dark') return stored;

    // Migrate the former "system" preference to an explicit, predictable theme.
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  });
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem('stockflow.theme', theme);
  }, [theme]);
  return <PreferencesContext value={{ theme, setTheme }}>{children}</PreferencesContext>;
}
