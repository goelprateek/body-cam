import { CommonModule } from '@angular/common';
import {
  AfterViewInit,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  OnDestroy,
  ViewChild,
  computed,
  effect,
  inject,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RemoteAudioTrack, RemoteVideoTrack } from 'livekit-client';
import { LiveRoomService } from './live-room.service';
import { OperatorApiService } from './operator-api.service';
import { SessionResponse } from './operator.models';

@Component({
  selector: 'app-operations-page',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatCardModule,
    MatDividerModule,
    MatProgressBarModule,
    MatIconModule,
    MatTooltipModule
  ],
  templateUrl: './operations-page.component.html',
  styleUrl: './operations-page.component.scss'
})
export class OperationsPageComponent implements AfterViewInit, OnDestroy {
  private static readonly SESSION_PAGE_SIZE = 10;

  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);

  private videoElement: HTMLMediaElement | null = null;
  private audioElement: HTMLMediaElement | null = null;

  @ViewChild('videoHost') private videoHost?: ElementRef<HTMLDivElement>;
  @ViewChild('audioHost') private audioHost?: ElementRef<HTMLDivElement>;

  readonly api = inject(OperatorApiService);
  readonly liveRoom = inject(LiveRoomService);

  readonly sessions = signal<SessionResponse[]>([]);
  readonly selectedSessionId = signal<string | null>(null);
  readonly isRefreshing = signal(false);
  readonly isJoining = signal(false);
  readonly joiningSessionId = signal<string | null>(null);
  readonly isEnding = signal(false);
  readonly isLoadingMore = signal(false);
  readonly hasMoreSessions = signal(false);
  readonly nextSessionCursor = signal<string | null>(null);
  readonly pageError = signal<string | null>(null);

  readonly selectedSession = computed(
    () => this.sessions().find((session) => session.id === this.selectedSessionId()) ?? null
  );
  readonly viewerMessage = computed(() => {
    if (this.liveRoom.connectionLabel() === 'Connecting') {
      return 'Joining live stream';
    }

    if (this.liveRoom.connectionLabel() === 'Reconnecting') {
      return 'Reconnecting';
    }

    if (this.selectedSession()?.status === 'ACTIVE') {
      return 'Waiting for video';
    }

    return 'Select a session to start';
  });

  constructor() {
    void this.refreshAll();
  }

  ngAfterViewInit(): void {
    effect(
      () => {
        this.renderVideoTrack(this.liveRoom.remoteVideoTrack());
      },
      { injector: this.injector }
    );

    effect(
      () => {
        this.renderAudioTrack(this.liveRoom.remoteAudioTrack());
      },
      { injector: this.injector }
    );
  }

  ngOnDestroy(): void {
    this.detachMedia();
    this.liveRoom.disconnect();
  }

  selectSession(sessionId: string): void {
    this.selectedSessionId.set(sessionId);
  }

  async refreshAll(silent = false): Promise<void> {
    if (!silent) {
      this.isRefreshing.set(true);
      this.pageError.set(null);
    }

    try {
      this.nextSessionCursor.set(null);
      this.hasMoreSessions.set(false);
      const page = await this.api.listActiveSessionsCursor(null, OperationsPageComponent.SESSION_PAGE_SIZE);

      this.sessions.set(page.items);
      this.nextSessionCursor.set(page.nextCursor);
      this.hasMoreSessions.set(page.hasNext);

      if (
        page.items.length &&
        !page.items.some((session) => session.id === this.selectedSessionId())
      ) {
        this.selectedSessionId.set(page.items[0].id);
      }

      if (!page.items.length) {
        this.selectedSessionId.set(null);
        this.liveRoom.disconnect();
      }
    } catch (error) {
      if (!silent) {
        this.pageError.set(this.api.explainError(error));
      }
    } finally {
      if (!silent) {
        this.isRefreshing.set(false);
      }
    }
  }

  async loadMoreSessions(): Promise<void> {
    if (
      this.isRefreshing() ||
      this.isLoadingMore() ||
      !this.hasMoreSessions()
    ) {
      return;
    }

    this.isLoadingMore.set(true);

    try {
      const page = await this.api.listActiveSessionsCursor(
        this.nextSessionCursor(),
        OperationsPageComponent.SESSION_PAGE_SIZE
      );

      const knownIds = new Set(this.sessions().map((session) => session.id));
      const nextItems = page.items.filter((session) => !knownIds.has(session.id));
      this.sessions.update((sessions) => [...sessions, ...nextItems]);
      this.nextSessionCursor.set(page.nextCursor);
      this.hasMoreSessions.set(page.hasNext);
    } catch (error) {
      this.pageError.set(this.api.explainError(error));
    } finally {
      this.isLoadingMore.set(false);
    }
  }

  async joinSession(session: SessionResponse): Promise<void> {
    const participantName = this.api.operatorLabel();
    this.pageError.set(null);
    this.isJoining.set(true);
    this.joiningSessionId.set(session.id);
    this.selectedSessionId.set(session.id);

    try {
      const tokenResponse = await this.api.createJoinToken(session.id, participantName);
      await this.liveRoom.connect(session.id, participantName, tokenResponse);
    } catch (error) {
      this.pageError.set(this.api.explainError(error));
    } finally {
      this.isJoining.set(false);
      this.joiningSessionId.set(null);
    }
  }

  async endSession(session: SessionResponse): Promise<void> {
    this.pageError.set(null);
    this.isEnding.set(true);

    try {
      await this.api.endSession(session.id);
      if (this.liveRoom.sessionId() === session.id) {
        this.liveRoom.disconnect();
      }

      await this.refreshAll();
    } catch (error) {
      this.pageError.set(this.api.explainError(error));
    } finally {
      this.isEnding.set(false);
    }
  }

  onSessionListScroll(event: Event): void {
    const host = event.target as HTMLDivElement | null;
    if (!host) {
      return;
    }

    const remaining = host.scrollHeight - host.scrollTop - host.clientHeight;
    if (remaining <= 120) {
      void this.loadMoreSessions();
    }
  }

  private renderVideoTrack(track: RemoteVideoTrack | null): void {
    const host = this.videoHost?.nativeElement;
    if (!host) {
      return;
    }

    if (this.videoElement) {
      this.videoElement.remove();
      this.videoElement = null;
    }

    if (!track) {
      return;
    }

    const element = track.attach() as HTMLVideoElement;
    element.autoplay = true;
    element.playsInline = true;
    element.className = 'live-video';
    host.appendChild(element);
    this.videoElement = element;
  }

  private renderAudioTrack(track: RemoteAudioTrack | null): void {
    const host = this.audioHost?.nativeElement;
    if (!host) {
      return;
    }

    if (this.audioElement) {
      this.audioElement.remove();
      this.audioElement = null;
    }

    if (!track) {
      return;
    }

    const element = track.attach();
    element.autoplay = true;
    host.appendChild(element);
    this.audioElement = element;
  }

  private detachMedia(): void {
    if (this.videoElement) {
      this.videoElement.remove();
      this.videoElement = null;
    }

    if (this.audioElement) {
      this.audioElement.remove();
      this.audioElement = null;
    }
  }
}
