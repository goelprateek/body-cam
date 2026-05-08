export interface LoginResponse {
  accessToken: string;
  userId: string;
  username: string;
  displayName: string;
  role: string;
}

export interface CurrentUserResponse {
  userId: string;
  username: string;
  displayName: string;
  role: string;
}

export interface SessionResponse {
  id: string;
  workerId: string;
  workerName: string;
  roomName: string;
  status: 'ACTIVE' | 'ENDED';
  startedAt: string | null;
  endedAt: string | null;
  createdAt: string;
}

export interface LiveKitTokenResponse {
  token: string;
  roomName: string;
  liveKitUrl: string;
}

export interface RecordingResponse {
  id: string;
  sessionId: string;
  roomName: string;
  objectKey: string;
  playbackUrl: string | null;
  durationSeconds: number | null;
  createdAt: string;
}
