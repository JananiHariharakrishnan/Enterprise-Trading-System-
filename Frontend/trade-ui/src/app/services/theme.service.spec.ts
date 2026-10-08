import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  const setMatchMedia = (matches: boolean) => {
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      writable: true,
      value: vi.fn().mockReturnValue({ matches })
    });
  };

  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    document.documentElement.classList.remove('dark-theme');
    document.documentElement.classList.remove('light-theme');
    setMatchMedia(false);

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [ThemeService] });
  });

  it('defaults to dark theme when no saved setting and no light preference', () => {
    const service = TestBed.inject(ThemeService);

    expect(service.currentTheme()).toBe('dark');
  });

  it('uses saved theme from localStorage', () => {
    localStorage.setItem('trading-platform-theme', 'light');

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [ThemeService] });
    const service = TestBed.inject(ThemeService);

    expect(service.currentTheme()).toBe('light');
  });

  it('uses system light preference when no saved theme', () => {
    setMatchMedia(true);

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [ThemeService] });
    const service = TestBed.inject(ThemeService);

    expect(service.currentTheme()).toBe('light');
  });

  it('toggles theme and persists to localStorage', () => {
    const service = TestBed.inject(ThemeService);

    const first = service.currentTheme();
    service.toggleTheme();

    const second = service.currentTheme();
    expect(second).not.toBe(first);
  });

  it('sets theme explicitly', () => {
    const service = TestBed.inject(ThemeService);

    service.setTheme('light');
    expect(service.currentTheme()).toBe('light');

    service.setTheme('dark');
    expect(service.currentTheme()).toBe('dark');
  });

  it('applies light and dark theme classes', () => {
    const service = TestBed.inject(ThemeService) as any;

    service.applyTheme('light');
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
    expect(document.documentElement.classList.contains('light-theme')).toBe(true);

    service.applyTheme('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(document.documentElement.classList.contains('dark-theme')).toBe(true);
  });

  it('applies a saved light, dark, or system preference', () => {
    const service = TestBed.inject(ThemeService);
    service.applyThemePreference('LIGHT');
    expect(service.currentTheme()).toBe('light');
    service.applyThemePreference('DARK');
    expect(service.currentTheme()).toBe('dark');

    setMatchMedia(true);
    service.applyThemePreference('SYSTEM');
    expect(service.currentTheme()).toBe('light');

    setMatchMedia(false);
    service.applyThemePreference('SYSTEM');
    expect(service.currentTheme()).toBe('dark');
  });
});
