import { createContext, useContext } from 'react';

export type Theme = 'light' | 'dark';
export interface Preferences {
  theme: Theme;
  setTheme: (theme: Theme) => void;
}
export const PreferencesContext = createContext<Preferences | null>(null);
export function usePreferences() {
  const context = useContext(PreferencesContext);
  if (!context) throw new Error('PreferencesProvider is required');
  return context;
}
