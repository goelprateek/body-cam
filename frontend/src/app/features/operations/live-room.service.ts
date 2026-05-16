import { Injectable, signal } from '@angular/core';
import {
  ConnectionState,
  RemoteAudioTrack,
  RemoteParticipant,
  RemoteVideoTrack,
  Room,
  RoomEvent,
  Track
} from 'livekit-client';
import { LiveKitTokenResponse } from './operator.models';

export interface LiveRoomRemoteParticipant {
  id: string;
  name: string;
  videoTrack: RemoteVideoTrack | null;
  audioTrack: RemoteAudioTrack | null;
}

@Injectable({ providedIn: 'root' })
export class LiveRoomService {
  readonly sessionId = signal<string | null>(null);
  readonly roomName = signal<string | null>(null);
  readonly connectionLabel = signal('Idle');
  readonly lastEvent = signal('Waiting for operator to join a live room');
  readonly participantNames = signal<string[]>([]);
  readonly remoteParticipants = signal<LiveRoomRemoteParticipant[]>([]);
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
    this.remoteParticipants.set([]);
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

    const remoteParticipants: LiveRoomRemoteParticipant[] = [];

    for (const participant of participants) {
      let videoTrack: RemoteVideoTrack | null = null;
      let audioTrack: RemoteAudioTrack | null = null;

      for (const publication of participant.trackPublications.values()) {
        if (!publication.isSubscribed) {
          publication.setSubscribed(true);
        }

        const track = publication.track;
        if (!track) {
          continue;
        }

        if (track.kind === Track.Kind.Video) {
          videoTrack = track as RemoteVideoTrack;
        }

        if (track.kind === Track.Kind.Audio) {
          audioTrack = track as RemoteAudioTrack;
        }
      }

      remoteParticipants.push({
        id: participant.identity,
        name: this.participantLabel(participant),
        videoTrack,
        audioTrack
      });
    }

    const featuredParticipant =
      remoteParticipants.find((candidate) => candidate.videoTrack) ??
      remoteParticipants.find((candidate) => candidate.audioTrack) ??
      null;

    this.remoteParticipants.set(remoteParticipants);
    this.remoteVideoTrack.set(featuredParticipant?.videoTrack ?? null);
    this.remoteAudioTrack.set(featuredParticipant?.audioTrack ?? null);
    this.focusParticipant.set(featuredParticipant?.name ?? null);
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
