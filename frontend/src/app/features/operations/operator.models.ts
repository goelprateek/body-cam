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

export type RecordingTranscriptStatus =
  | 'NOT_REQUESTED'
  | 'PENDING'
  | 'PROCESSING'
  | 'READY'
  | 'FAILED';

export interface RecordingTranscriptSegmentResponse {
  id: string;
  segmentIndex: number;
  startSeconds: string;
  endSeconds: string;
  text: string;
  confidence: string | null;
}

export interface RecordingTranscriptResponse {
  id: string | null;
  recordingId: string;
  status: RecordingTranscriptStatus;
  engine: string | null;
  model: string | null;
  languageCode: string | null;
  fullText: string | null;
  errorMessage: string | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  segments: RecordingTranscriptSegmentResponse[];
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
  transcriptStatus?: RecordingTranscriptStatus | null;
}

export interface RecordingPlaybackResponse {
  recordingId: string;
  playbackUrl: string;
  expiresInSeconds: number;
}
