import { CommonModule } from '@angular/common';
import {
  AfterViewInit,
  Component,
  ElementRef,
  Injector,
  OnDestroy,
  QueryList,
  ViewChildren,
  computed,
  effect,
  inject,
  signal
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { LiveRoomRemoteParticipant, LiveRoomService } from './live-room.service';
import { OperatorApiService } from './operator-api.service';
import { SessionInviteResponse, SessionInviteRole, SessionResponse } from './operator.models';

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
    MatTooltipModule,
    MatSnackBarModule
  ],
  templateUrl: './operations-page.component.html',
  styleUrl: './operations-page.component.scss'
})
export class OperationsPageComponent implements AfterViewInit, OnDestroy {
  private static readonly SESSION_PAGE_SIZE = 10;
  private static readonly PUBLISHER_INVITE_ROLE = 'BROWSER' as const;
  private static readonly VIEWER_INVITE_ROLE = 'VIEWER' as const;

  private readonly injector = inject(Injector);

  private readonly videoElements = new Map<string, HTMLVideoElement>();
  private readonly audioElements = new Map<string, HTMLAudioElement>();

  @ViewChildren('participantVideoHost') private participantVideoHosts?: QueryList<ElementRef<HTMLDivElement>>;
  @ViewChildren('participantAudioHost') private participantAudioHosts?: QueryList<ElementRef<HTMLDivElement>>;

  readonly api = inject(OperatorApiService);
  readonly liveRoom = inject(LiveRoomService);
  private readonly snackBar = inject(MatSnackBar);

  readonly sessions = signal<SessionResponse[]>([]);
  readonly selectedSessionId = signal<string | null>(null);
  readonly isCreatePanelOpen = signal(false);
  readonly newSessionWorkerName = signal('');
  readonly newSessionReferenceNumber = signal('');
  readonly isRefreshing = signal(false);
  readonly isJoining = signal(false);
  readonly joiningSessionId = signal<string | null>(null);
  readonly isEnding = signal(false);
  readonly isCreatingSession = signal(false);
  readonly sharingSessionId = signal<string | null>(null);
  readonly revokingInviteId = signal<string | null>(null);
  readonly sharePanelSessionId = signal<string | null>(null);
  readonly viewerInvite = signal<SessionInviteResponse | null>(null);
  readonly publisherInvite = signal<SessionInviteResponse | null>(null);
  readonly isLoadingMore = signal(false);
  readonly hasMoreSessions = signal(false);
  readonly nextSessionCursor = signal<string | null>(null);
  readonly pageError = signal<string | null>(null);

  readonly selectedSession = computed(
    () => this.sessions().find((session) => session.id === this.selectedSessionId()) ?? null
  );
  readonly sharePanelSession = computed(
    () => this.sessions().find((session) => session.id === this.sharePanelSessionId()) ?? null
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
  readonly remoteParticipantCount = computed(() => this.liveRoom.remoteParticipants().length);

  constructor() {
    void this.refreshAll();
  }

  ngAfterViewInit(): void {
    effect(
      () => {
        const participants = this.liveRoom.remoteParticipants();
        queueMicrotask(() => this.renderRemoteParticipants(participants));
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

  toggleSharePanel(session: SessionResponse): void {
    if (this.sharePanelSessionId() === session.id) {
      this.closeSharePanel();
      return;
    }

    this.selectedSessionId.set(session.id);
    this.sharePanelSessionId.set(session.id);
    this.viewerInvite.set(null);
    this.publisherInvite.set(null);
    this.pageError.set(null);
  }

  toggleCreatePanel(): void {
    this.isCreatePanelOpen.update((open) => !open);
  }

  updateNewSessionWorkerName(value: string): void {
    this.newSessionWorkerName.set(value);
  }

  updateNewSessionReferenceNumber(value: string): void {
    this.newSessionReferenceNumber.set(value);
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

  async createSession(copyJoinLink: boolean): Promise<void> {
    const workerName = this.newSessionWorkerName().trim();
    const referenceNumber = this.newSessionReferenceNumber().trim();
    if (!workerName || !referenceNumber) {
      this.pageError.set('Worker name and reference number are required to create a session.');
      return;
    }

    this.pageError.set(null);
    this.isCreatingSession.set(true);

    try {
      const session = await this.api.createSession({
        workerName,
        referenceNumber
      });
      await this.refreshAll(true);
      this.selectedSessionId.set(session.id);
      this.isCreatePanelOpen.set(false);
      this.newSessionWorkerName.set('');
      this.newSessionReferenceNumber.set('');

      if (copyJoinLink) {
        this.sharePanelSessionId.set(session.id);
        await this.generateShareLink(session, OperationsPageComponent.VIEWER_INVITE_ROLE, true);
      } else {
        this.snackBar.open('Session created.', 'Close', { duration: 3000 });
      }
    } catch (error) {
      this.pageError.set(this.api.explainError(error));
    } finally {
      this.isCreatingSession.set(false);
    }
  }

  async generateShareLink(
    session: SessionResponse,
    participantRole: Extract<SessionInviteRole, 'BROWSER' | 'VIEWER'>,
    copyToClipboard = false
  ): Promise<void> {
    this.pageError.set(null);
    this.sharingSessionId.set(session.id);

    try {
      const invite = await this.api.createSessionInvite(session.id, participantRole);
      this.storeInvite(invite, !copyToClipboard);
      if (copyToClipboard) {
        await this.copyInvite(invite);
      }
    } catch (error) {
      this.pageError.set(this.api.explainError(error));
    } finally {
      this.sharingSessionId.set(null);
    }
  }

  async copyInvite(invite: SessionInviteResponse): Promise<void> {
    const joinUrl = this.buildJoinUrl(invite.joinPath);
    await navigator.clipboard.writeText(joinUrl);
    this.snackBar.open(
      invite.participantRole === OperationsPageComponent.PUBLISHER_INVITE_ROLE
        ? 'Publisher join link copied.'
        : 'Viewer join link copied.',
      'Close',
      { duration: 4000 }
    );
  }

  emailInvite(invite: SessionInviteResponse): void {
    const joinUrl = this.buildJoinUrl(invite.joinPath);
    const roleLabel = invite.participantRole === OperationsPageComponent.PUBLISHER_INVITE_ROLE
      ? 'Publisher'
      : 'Viewer';
    const subject = encodeURIComponent(`Session access for ${invite.referenceNumber}`);
    const body = encodeURIComponent(
      [
        `You have been invited to join session ${invite.referenceNumber}.`,
        '',
        `Access type: ${roleLabel}`,
        `Participant: ${invite.workerName}`,
        `Room: ${invite.roomName}`,
        `Link: ${joinUrl}`,
        `Expires: ${new Date(invite.expiresAt).toLocaleString()}`,
        '',
        roleLabel === 'Publisher'
          ? 'This link allows browser microphone and camera publishing.'
          : 'This link joins the room as a viewer without publishing browser media.'
      ].join('\n')
    );
    window.location.href = `mailto:?subject=${subject}&body=${body}`;
  }

  async revokeInvite(invite: SessionInviteResponse): Promise<void> {
    this.pageError.set(null);
    this.revokingInviteId.set(invite.id);

    try {
      await this.api.revokeSessionInvite(invite.sessionId, invite.id);
      if (invite.participantRole === OperationsPageComponent.PUBLISHER_INVITE_ROLE) {
        this.publisherInvite.set(null);
        this.snackBar.open('Publisher link revoked.', 'Close', { duration: 3000 });
      } else if (invite.participantRole === OperationsPageComponent.VIEWER_INVITE_ROLE) {
        this.viewerInvite.set(null);
        this.snackBar.open('Viewer link revoked.', 'Close', { duration: 3000 });
      }
    } catch (error) {
      this.pageError.set(this.api.explainError(error));
    } finally {
      this.revokingInviteId.set(null);
    }
  }

  closeSharePanel(): void {
    this.sharePanelSessionId.set(null);
    this.viewerInvite.set(null);
    this.publisherInvite.set(null);
  }

  inviteUrl(invite: SessionInviteResponse | null): string {
    if (!invite) {
      return '';
    }
    return this.buildJoinUrl(invite.joinPath);
  }

  private buildJoinUrl(joinPath: string): string {
    return new URL(joinPath, window.location.origin).toString();
  }

  private storeInvite(invite: SessionInviteResponse, notifyReady: boolean): void {
    if (invite.participantRole === OperationsPageComponent.PUBLISHER_INVITE_ROLE) {
      this.publisherInvite.set(invite);
      if (notifyReady) {
        this.snackBar.open('Publisher link ready to share.', 'Close', { duration: 3000 });
      }
      return;
    }

    if (invite.participantRole === OperationsPageComponent.VIEWER_INVITE_ROLE) {
      this.viewerInvite.set(invite);
      if (notifyReady) {
        this.snackBar.open('Viewer link ready to share.', 'Close', { duration: 3000 });
      }
    }
  }

  private detachMedia(): void {
    for (const element of this.videoElements.values()) {
      element.remove();
    }
    this.videoElements.clear();

    for (const element of this.audioElements.values()) {
      element.remove();
    }
    this.audioElements.clear();
  }

  private renderRemoteParticipants(participants: LiveRoomRemoteParticipant[]): void {
    const activeIds = new Set(participants.map((participant) => participant.id));

    for (const [participantId, element] of this.videoElements.entries()) {
      if (!activeIds.has(participantId)) {
        element.remove();
        this.videoElements.delete(participantId);
      }
    }

    for (const [participantId, element] of this.audioElements.entries()) {
      if (!activeIds.has(participantId)) {
        element.remove();
        this.audioElements.delete(participantId);
      }
    }

    for (const participant of participants) {
      this.renderParticipantVideo(participant);
      this.renderParticipantAudio(participant);
    }
  }

  private renderParticipantVideo(participant: LiveRoomRemoteParticipant): void {
    const host = this.findParticipantHost(this.participantVideoHosts, participant.id);
    if (!host) {
      return;
    }

    const existingElement = this.videoElements.get(participant.id);
    if (existingElement) {
      existingElement.remove();
      this.videoElements.delete(participant.id);
    }

    if (!participant.videoTrack) {
      return;
    }

    const element = participant.videoTrack.attach() as HTMLVideoElement;
    element.autoplay = true;
    element.playsInline = true;
    element.className = 'live-video';
    host.appendChild(element);
    this.videoElements.set(participant.id, element);
  }

  private renderParticipantAudio(participant: LiveRoomRemoteParticipant): void {
    const host = this.findParticipantHost(this.participantAudioHosts, participant.id);
    if (!host) {
      return;
    }

    const existingElement = this.audioElements.get(participant.id);
    if (existingElement) {
      existingElement.remove();
      this.audioElements.delete(participant.id);
    }

    if (!participant.audioTrack) {
      return;
    }

    const element = participant.audioTrack.attach() as HTMLAudioElement;
    element.autoplay = true;
    host.appendChild(element);
    this.audioElements.set(participant.id, element);
  }

  private findParticipantHost(
    hosts: QueryList<ElementRef<HTMLDivElement>> | undefined,
    participantId: string
  ): HTMLDivElement | null {
    return (
      hosts?.find((host) => host.nativeElement.dataset['participantId'] === participantId)?.nativeElement ?? null
    );
  }
}
