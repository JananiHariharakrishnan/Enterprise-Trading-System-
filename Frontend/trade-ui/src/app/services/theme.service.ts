import { Injectable, signal, effect } from '@angular/core';

export type Theme = 'dark' | 'light';
export type ThemePreference = 'LIGHT' | 'DARK' | 'SYSTEM';

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  private readonly THEME_KEY = 'trading-platform-theme';
  
  currentTheme = signal<Theme>(this.getInitialTheme());

  constructor() {
    // Apply theme changes to DOM
    effect(() => {
      const theme = this.currentTheme();
      this.applyTheme(theme);
      localStorage.setItem(this.THEME_KEY, theme);
    });
  }

  private getInitialTheme(): Theme {
    // Check localStorage first
    const saved = localStorage.getItem(this.THEME_KEY) as Theme | null;
    if (saved && (saved === 'dark' || saved === 'light')) {
      return saved;
    }

    // Check system preference
    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches) {
      return 'light';
    }

    return 'dark';
  }

  private applyTheme(theme: Theme): void {
    const html = document.documentElement;
    html.setAttribute('data-theme', theme);
    
    if (theme === 'light') {
      html.classList.remove('dark-theme');
      html.classList.add('light-theme');
    } else {
      html.classList.remove('light-theme');
      html.classList.add('dark-theme');
    }
  }

  toggleTheme(): void {
    const current = this.currentTheme();
    this.currentTheme.set(current === 'dark' ? 'light' : 'dark');
  }

  setTheme(theme: Theme): void {
    this.currentTheme.set(theme);
  }

  applyThemePreference(preference: ThemePreference): void {
    if (preference === 'LIGHT') {
      this.setTheme('light');
      return;
    }

    if (preference === 'DARK') {
      this.setTheme('dark');
      return;
    }

    this.setTheme(this.getSystemPreferredTheme());
  }

  private getSystemPreferredTheme(): Theme {
    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches) {
      return 'light';
    }
    return 'dark';
  }
}
