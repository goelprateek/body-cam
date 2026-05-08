import { Injectable, effect, signal } from '@angular/core';

export type ThemeMode = 'light' | 'dark';

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  private readonly THEME_KEY = 'karebo-theme-preference';
  
  readonly theme = signal<ThemeMode>('light');

  constructor() {
    // Initialize theme from localStorage or system preference
    const storedTheme = localStorage.getItem(this.THEME_KEY) as ThemeMode;
    if (storedTheme) {
      this.theme.set(storedTheme);
    } else if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
      this.theme.set('dark');
    }

    // Effect to apply the theme class to the document body/html
    effect(() => {
      const currentTheme = this.theme();
      localStorage.setItem(this.THEME_KEY, currentTheme);
      
      if (currentTheme === 'dark') {
        document.documentElement.classList.add('dark-theme');
      } else {
        document.documentElement.classList.remove('dark-theme');
      }
    });
  }

  toggleTheme(): void {
    this.theme.update(current => current === 'light' ? 'dark' : 'light');
  }
}
