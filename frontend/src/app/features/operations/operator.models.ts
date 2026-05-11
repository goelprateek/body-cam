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
  referenceNumber: string;
  roomName: string;
  status: 'ACTIVE' | 'ENDED';
  startedAt: string | null;
  endedAt: string | null;
  createdAt: string;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  hasNext: boolean;
}

export interface LiveKitTokenResponse {
  token: string;
  roomName: string;
  liveKitUrl: string;
}

export interface RecordingMetadataResponse {
  capturedAt: string | null;
  latitude: string | null;
  longitude: string | null;
  altitudeMeters: string | null;
  locationAccuracyMeters: string | null;
  cameraFacing: string | null;
  thermalEnabled: boolean | null;
  thermalMinC: string | null;
  thermalMaxC: string | null;
  thermalAvgC: string | null;
  sensorPayload: unknown;
}

export interface RecordingResponse {
  id: string;
  sessionId: string;
  workerId: string;
  workerName: string;
  roomName: string;
  objectKey: string;
  playbackUrl: string | null;
  durationSeconds: number | null;
  createdAt: string;
  metadata?: RecordingMetadataResponse | null;
}

export interface RecordingPlaybackResponse {
  recordingId: string;
  playbackUrl: string;
  expiresInSeconds: number;
}
