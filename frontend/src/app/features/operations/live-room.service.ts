import { Injectable, signal } from '@angular/core';
import {
  ConnectionState,
  RemoteAudioTrack,
  RemoteParticipant,
  RemoteTrack,
  RemoteVideoTrack,
  Room,
  RoomEvent,
  Track
} from 'livekit-client';
import { LiveKitTokenResponse } from './operator.models';

@Injectable({ providedIn: 'root' })
export class LiveRoomService {
  readonly sessionId = signal<string | null>(null);
  readonly roomName = signal<string | null>(null);
  readonly connectionLabel = signal('Idle');
  readonly lastEvent = signal('Waiting for operator to join a live room');
  readonly participantNames = signal<string[]>([]);
  readonly remoteVideoTrack = signal<RemoteVideoTrack | null>(null);
  readonly remoteAudioTrack = signal<RemoteAudioTrack | null>(null);
  readonly focusParticipant = signal<string | null>(null);
  readonly error = signal<string | null>(null);

  private room: Room | null = null;

  async connect(
    sessionId: string,
    participantName: string,
    tokenResponse: LiveKitTokenResponse
  ): Promise<void> {
    this.disconnect();

    const room = new Room({
      adaptiveStream: false,
      dynacast: false
    });

    this.room = room;
    this.sessionId.set(sessionId);
    this.roomName.set(tokenResponse.roomName);
    this.connectionLabel.set('Connecting');
    this.lastEvent.set(`Joining ${tokenResponse.roomName} as ${participantName}`);
    this.error.set(null);

    room.on(RoomEvent.Connected, () => {
      this.connectionLabel.set('Live');
      this.lastEvent.set('Connected to LiveKit room');
      this.syncRemoteState();
    });

    room.on(RoomEvent.ConnectionStateChanged, (state) => {
      this.connectionLabel.set(this.mapConnectionState(state));
    });

    room.on(RoomEvent.Reconnecting, () => {
      this.connectionLabel.set('Reconnecting');
      this.lastEvent.set('Live stream is reconnecting');
    });

    room.on(RoomEvent.Reconnected, () => {
      this.connectionLabel.set('Live');
      this.lastEvent.set('Live stream reconnected');
      this.syncRemoteState();
    });

    room.on(RoomEvent.ParticipantConnected, (participant) => {
      this.focusParticipant.set(this.participantLabel(participant));
      this.lastEvent.set(`${this.participantLabel(participant)} joined the room`);
      this.syncRemoteState();
    });

    room.on(RoomEvent.ParticipantDisconnected, (participant) => {
      this.lastEvent.set(`${this.participantLabel(participant)} left the room`);
      this.syncRemoteState();
    });

    room.on(RoomEvent.TrackPublished, (publication, participant) => {
      if (!publication.isSubscribed) {
        publication.setSubscribed(true);
      }

      this.lastEvent.set(
        `${this.participantLabel(participant)} announced ${publication.kind} track`
      );
      this.syncRemoteState();
    });

    room.on(RoomEvent.TrackSubscribed, (track, _publication, participant) => {
      this.applyTrack(track, participant);
      this.lastEvent.set(`${this.participantLabel(participant)} published ${track.kind}`);
      this.syncRemoteState();
    });

    room.on(RoomEvent.TrackSubscriptionFailed, (trackSid, participant, error) => {
      this.error.set(
        `Failed to subscribe to ${this.participantLabel(participant)} track ${trackSid}: ${this.formatError(error)}`
      );
      this.lastEvent.set(`Unable to subscribe to ${this.participantLabel(participant)} media`);
      this.syncRemoteState();
    });

    room.on(RoomEvent.TrackUnsubscribed, (track) => {
      if (track.kind === Track.Kind.Video) {
        this.remoteVideoTrack.set(null);
      }

      if (track.kind === Track.Kind.Audio) {
        this.remoteAudioTrack.set(null);
      }

      this.lastEvent.set(`Remote ${track.kind} track stopped`);
      this.syncRemoteState();
    });

    room.on(RoomEvent.Disconnected, () => {
      this.connectionLabel.set('Offline');
      this.lastEvent.set('Disconnected from live room');
    });

    try {
      await room.connect(tokenResponse.liveKitUrl, tokenResponse.token);
      this.syncRemoteState();
    } catch (error) {
      this.error.set(this.formatError(error));
      this.connectionLabel.set('Connection failed');
      room.disconnect();
      this.room = null;
      throw error;
    }
  }

  disconnect(): void {
    if (this.room) {
      this.room.disconnect();
      this.room = null;
    }

    this.sessionId.set(null);
    this.roomName.set(null);
    this.connectionLabel.set('Idle');
    this.lastEvent.set('Waiting for operator to join a live room');
    this.participantNames.set([]);
    this.remoteVideoTrack.set(null);
    this.remoteAudioTrack.set(null);
    this.focusParticipant.set(null);
    this.error.set(null);
  }

  private syncRemoteState(): void {
    const room = this.room;
    if (!room) {
      this.participantNames.set([]);
      return;
    }

    const participants = [...room.remoteParticipants.values()];
    this.participantNames.set(
      participants.map((participant) => this.participantLabel(participant))
    );

    let videoTrack: RemoteVideoTrack | null = this.remoteVideoTrack();
    let audioTrack: RemoteAudioTrack | null = this.remoteAudioTrack();
    let focusParticipant: string | null = this.focusParticipant();
    let sawVideoPublication = false;
    let sawAudioPublication = false;

    for (const participant of participants) {
      for (const publication of participant.trackPublications.values()) {
        if (!publication.isSubscribed) {
          publication.setSubscribed(true);
        }

        const track = publication.track;
        if (publication.kind === Track.Kind.Video) {
          sawVideoPublication = true;
        }

        if (publication.kind === Track.Kind.Audio) {
          sawAudioPublication = true;
        }

        if (!track) {
          continue;
        }

        if (track.kind === Track.Kind.Video) {
          videoTrack = track as RemoteVideoTrack;
          focusParticipant = this.participantLabel(participant);
        }

        if (track.kind === Track.Kind.Audio) {
          audioTrack = track as RemoteAudioTrack;
          focusParticipant ??= this.participantLabel(participant);
        }
      }
    }

    if (!sawVideoPublication) {
      videoTrack = null;
    }

    if (!sawAudioPublication) {
      audioTrack = null;
    }

    this.remoteVideoTrack.set(videoTrack);
    this.remoteAudioTrack.set(audioTrack);
    this.focusParticipant.set(focusParticipant);
  }

  private applyTrack(track: RemoteTrack, participant: RemoteParticipant): void {
    if (track.kind === Track.Kind.Video) {
      this.remoteVideoTrack.set(track as RemoteVideoTrack);
      this.focusParticipant.set(this.participantLabel(participant));
    }

    if (track.kind === Track.Kind.Audio) {
      this.remoteAudioTrack.set(track as RemoteAudioTrack);
      this.focusParticipant.set(this.participantLabel(participant));
    }
  }

  private participantLabel(participant: RemoteParticipant): string {
    return participant.name || participant.identity;
  }

  private mapConnectionState(state: ConnectionState): string {
    switch (state) {
      case ConnectionState.Connected:
        return 'Live';
      case ConnectionState.Connecting:
        return 'Connecting';
      case ConnectionState.Reconnecting:
        return 'Reconnecting';
      case ConnectionState.Disconnected:
        return 'Offline';
      default:
        return 'Idle';
    }
  }

  private formatError(error: unknown): string {
    if (error instanceof Error) {
      return error.message;
    }

    return 'Unable to connect to live room';
  }
}
