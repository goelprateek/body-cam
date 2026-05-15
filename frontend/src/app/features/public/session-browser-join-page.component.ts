import { CommonModule } from '@angular/common';
import { Component, ElementRef, OnDestroy, ViewChild, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import {
  LocalTrack,
  RemoteAudioTrack,
  RemoteParticipant,
  RemoteTrack,
  RemoteVideoTrack,
  Room,
  RoomEvent,
  Track
} from 'livekit-client';
import { OperatorApiService } from '@features/operations/operator-api.service';
import { PublicSessionInviteResponse } from '@features/operations/operator.models';

@Component({
  selector: 'app-session-browser-join-page',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule, MatSnackBarModule],
  templateUrl: './session-browser-join-page.component.html',
  styleUrl: './session-browser-join-page.component.scss'
})
export class SessionBrowserJoinPageComponent implements OnDestroy {
  @ViewChild('videoHost') private videoHost?: ElementRef<HTMLDivElement>;

  readonly api = inject(OperatorApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly snackBar = inject(MatSnackBar);

  readonly inviteToken = this.route.snapshot.paramMap.get('inviteToken') ?? '';
  readonly invite = signal<PublicSessionInviteResponse | null>(null);
  readonly participantName = signal('');
  readonly pageError = signal<string | null>(null);
  readonly isLoading = signal(false);
  readonly isJoining = signal(false);
  readonly isJoined = signal(false);
  readonly connectionLabel = signal<'Ready' | 'Connecting' | 'Live' | 'Reconnecting' | 'Disconnected'>('Ready');
  readonly canJoin = computed(() => !!this.invite() && !this.isJoining() && !this.isJoined());
  readonly canPublishMedia = computed(() => {
    const participantRole = this.invite()?.participantRole;
    return participantRole === 'BROWSER' || participantRole === 'WORKER';
  });
  readonly joinActionLabel = computed(() => {
    if (this.isJoining()) {
      return 'Joining...';
    }
    if (this.isJoined()) {
      return 'Connected';
    }
    return this.canPublishMedia() ? 'Join And Publish' : 'Join As Viewer';
  });

  private room: Room | null = null;
  private localVideoElement: HTMLVideoElement | null = null;
  private remoteVideoElement: HTMLVideoElement | null = null;
  private remoteAudioElement: HTMLAudioElement | null = null;

  constructor() {
    void this.loadInvite();
  }

  ngOnDestroy(): void {
    this.disconnect();
  }

  updateParticipantName(value: string): void {
    this.participantName.set(value);
  }

  async joinSession(): Promise<void> {
    const invite = this.invite();
    const participantName = this.participantName().trim();
    if (!invite || !participantName) {
      this.pageError.set('Participant name is required.');
      return;
    }

    this.pageError.set(null);
    this.isJoining.set(true);
    this.connectionLabel.set('Connecting');

    try {
      const tokenResponse = await this.api.createPublicInviteJoinToken(this.inviteToken, participantName);
      this.disconnect();

      const room = new Room();
      this.bindRoomEvents(room);
      await room.connect(tokenResponse.liveKitUrl, tokenResponse.token);

      let cameraPublication = null;
      if (this.canPublishMedia()) {
        await room.localParticipant.setMicrophoneEnabled(true);
        cameraPublication = await room.localParticipant.setCameraEnabled(true);
      }

      this.room = room;
      this.isJoined.set(true);
      this.connectionLabel.set('Live');
      this.syncRemoteTracks(room);
      this.attachLocalTrack(cameraPublication?.track ?? null);
      this.snackBar.open('Joined live session.', 'Close', { duration: 3000 });
    } catch (error) {
      this.pageError.set(this.api.explainError(error));
      this.connectionLabel.set('Disconnected');
    } finally {
      this.isJoining.set(false);
    }
  }

  leaveSession(): void {
    this.disconnect();
    this.connectionLabel.set('Disconnected');
    this.isJoined.set(false);
  }

  private async loadInvite(): Promise<void> {
    if (!this.inviteToken) {
      this.pageError.set('Session invite is missing.');
      return;
    }

    this.isLoading.set(true);
    this.pageError.set(null);
    try {
      const invite = await this.api.getPublicSessionInvite(this.inviteToken);
      this.invite.set(invite);
      if (!this.participantName().trim()) {
        this.participantName.set(invite.workerName);
      }
      if (invite.sessionStatus !== 'ACTIVE') {
        this.pageError.set('This session is no longer active.');
      }
    } catch (error) {
      this.pageError.set(this.api.explainError(error));
    } finally {
      this.isLoading.set(false);
    }
  }

  private bindRoomEvents(room: Room): void {
    room.on(RoomEvent.Reconnecting, () => this.connectionLabel.set('Reconnecting'));
    room.on(RoomEvent.Reconnected, () => this.connectionLabel.set('Live'));
    room.on(RoomEvent.Disconnected, () => {
      this.connectionLabel.set('Disconnected');
      this.isJoined.set(false);
      this.detachRemoteTracks();
      this.detachLocalVideo();
    });
    room.on(RoomEvent.LocalTrackPublished, (publication) => {
      if (!this.canPublishMedia()) {
        return;
      }
      if (publication.source === Track.Source.Camera) {
        this.attachLocalTrack(publication.track ?? null);
      }
    });
    room.on(RoomEvent.ParticipantConnected, () => {
      this.syncRemoteTracks(room);
    });
    room.on(RoomEvent.ParticipantDisconnected, () => {
      this.syncRemoteTracks(room);
    });
    room.on(RoomEvent.TrackPublished, (publication) => {
      if (!publication.isSubscribed) {
        publication.setSubscribed(true);
      }
      this.syncRemoteTracks(room);
    });
    room.on(RoomEvent.TrackSubscribed, (track, _publication, participant) => {
      this.applyRemoteTrack(track, participant);
    });
    room.on(RoomEvent.TrackUnsubscribed, (track) => {
      if (track.kind === Track.Kind.Video) {
        this.detachRemoteVideo();
      }
      if (track.kind === Track.Kind.Audio) {
        this.detachRemoteAudio();
      }
    });
  }

  private attachLocalTrack(track: LocalTrack | null): void {
    const host = this.videoHost?.nativeElement;
    if (!host) {
      return;
    }

    this.detachLocalVideo();
    if (!track) {
      return;
    }

    const element = track.attach() as HTMLVideoElement;
    element.autoplay = true;
    element.muted = true;
    element.playsInline = true;
    element.className = 'join-preview-video';
    host.appendChild(element);
    this.localVideoElement = element;
  }

  private detachLocalVideo(): void {
    if (this.localVideoElement) {
      this.localVideoElement.remove();
      this.localVideoElement = null;
    }
  }

  private syncRemoteTracks(room: Room): void {
    let remoteVideoTrack: RemoteVideoTrack | null = null;
    let remoteAudioTrack: RemoteAudioTrack | null = null;

    for (const participant of room.remoteParticipants.values()) {
      for (const publication of participant.trackPublications.values()) {
        if (!publication.isSubscribed) {
          publication.setSubscribed(true);
        }

        const track = publication.track;
        if (!track) {
          continue;
        }

        if (!remoteVideoTrack && track.kind === Track.Kind.Video) {
          remoteVideoTrack = track as RemoteVideoTrack;
        }

        if (!remoteAudioTrack && track.kind === Track.Kind.Audio) {
          remoteAudioTrack = track as RemoteAudioTrack;
        }
      }
    }

    this.attachRemoteVideo(remoteVideoTrack);
    this.attachRemoteAudio(remoteAudioTrack);
  }

  private applyRemoteTrack(track: RemoteTrack, _participant: RemoteParticipant): void {
    if (track.kind === Track.Kind.Video) {
      this.attachRemoteVideo(track as RemoteVideoTrack);
    }

    if (track.kind === Track.Kind.Audio) {
      this.attachRemoteAudio(track as RemoteAudioTrack);
    }
  }

  private attachRemoteVideo(track: RemoteVideoTrack | null): void {
    const host = this.videoHost?.nativeElement;
    if (!host) {
      return;
    }

    this.detachRemoteVideo();
    if (!track) {
      this.restoreLocalPreviewIfAvailable();
      return;
    }

    this.detachLocalVideo();
    const element = track.attach() as HTMLVideoElement;
    element.autoplay = true;
    element.muted = false;
    element.playsInline = true;
    element.className = 'join-preview-video';
    host.appendChild(element);
    this.remoteVideoElement = element;
  }

  private attachRemoteAudio(track: RemoteAudioTrack | null): void {
    this.detachRemoteAudio();
    if (!track) {
      return;
    }

    const element = track.attach() as HTMLAudioElement;
    element.autoplay = true;
    this.remoteAudioElement = element;
  }

  private detachRemoteVideo(): void {
    if (this.remoteVideoElement) {
      this.remoteVideoElement.remove();
      this.remoteVideoElement = null;
    }
  }

  private detachRemoteAudio(): void {
    if (this.remoteAudioElement) {
      this.remoteAudioElement.remove();
      this.remoteAudioElement = null;
    }
  }

  private detachRemoteTracks(): void {
    this.detachRemoteVideo();
    this.detachRemoteAudio();
  }

  private restoreLocalPreviewIfAvailable(): void {
    if (!this.canPublishMedia() || !this.room) {
      return;
    }

    const cameraPublication = [...this.room.localParticipant.trackPublications.values()].find(
      (publication) => publication.source === Track.Source.Camera
    );
    this.attachLocalTrack((cameraPublication?.track as LocalTrack | null) ?? null);
  }

  private disconnect(): void {
    this.detachRemoteTracks();
    this.detachLocalVideo();
    if (this.room) {
      this.room.disconnect();
      this.room = null;
    }
  }
}
