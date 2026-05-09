import { Injectable, signal } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class LayoutService {
  readonly sidebarExpanded = signal(false);

  toggleSidebar(): void {
    this.sidebarExpanded.update((v) => !v);
  }
}
