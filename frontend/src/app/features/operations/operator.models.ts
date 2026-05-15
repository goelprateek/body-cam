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
  workerId: string | null;
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

export interface CursorPageResponse<T> {
  items: T[];
  nextCursor: string | null;
  hasNext: boolean;
}

export interface LiveKitTokenResponse {
  token: string;
  roomName: string;
  liveKitUrl: string;
}

export interface CreateSessionRequest {
  workerId?: string | null;
  workerName: string;
  referenceNumber: string;
}

export type SessionInviteRole = 'WORKER' | 'OPERATOR' | 'BROWSER' | 'VIEWER';

export interface SessionInviteResponse {
  id: string;
  sessionId: string;
  workerName: string;
  referenceNumber: string;
  roomName: string;
  sessionStatus: 'ACTIVE' | 'ENDED';
  participantRole: SessionInviteRole;
  inviteToken: string;
  joinPath: string;
  expiresAt: string;
  createdAt: string;
}

export interface PublicSessionInviteResponse {
  sessionId: string;
  workerName: string;
  referenceNumber: string;
  roomName: string;
  sessionStatus: 'ACTIVE' | 'ENDED';
  participantRole: SessionInviteRole;
  expiresAt: string;
}

export interface RecordingMetadataResponse {
  capturedAt: string | null;
  segmentSequence: number | null;
  segmentStartedAt: string | null;
  segmentEndedAt: string | null;
  sessionElapsedStartMs: number | null;
  sessionElapsedEndMs: number | null;
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

export type RecordingTranscriptProcessingStage =
  | 'QUEUED'
  | 'TRANSCRIBING'
  | 'TRANSCRIBED'
  | 'PUNCTUATED'
  | 'FINALIZED'
  | 'FAILED';

export type SessionRecordingIntegrityStatus =
  | 'COMPLETE'
  | 'PROCESSING_UPLOADS'
  | 'PARTIAL'
  | 'HAS_GAPS';

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
  processingStage: RecordingTranscriptProcessingStage | null;
  lastErrorStage: RecordingTranscriptProcessingStage | null;
  retryCount: number;
  lastStageAt: string | null;
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
  referenceNumber: string;
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

export interface SessionRecordingTimelineSegmentResponse {
  recordingId: string;
  segmentSequence: number | null;
  objectKey: string;
  playbackUrl: string;
  durationSeconds: number | null;
  createdAt: string;
  capturedAt: string | null;
  segmentStartedAt: string | null;
  segmentEndedAt: string | null;
  sessionElapsedStartMs: number | null;
  sessionElapsedEndMs: number | null;
  transcriptStatus?: RecordingTranscriptStatus | null;
}

export interface SessionRecordingTimelineGapResponse {
  type: 'MISSING_SEQUENCE' | 'DUPLICATE_SEQUENCE' | 'TIMING_GAP' | 'TIMING_OVERLAP' | 'MISSING_TIMING';
  label: string;
  startMs: number | null;
  endMs: number | null;
  missingCount: number | null;
}

export interface SessionRecordingTimelineResponse {
  sessionId: string;
  workerId: string;
  workerName: string;
  referenceNumber: string;
  roomName: string;
  sessionStartedAt: string | null;
  sessionEndedAt: string | null;
  totalDurationMs: number | null;
  integrityStatus: SessionRecordingIntegrityStatus;
  hasTimelineGaps: boolean;
  duplicateSegmentCount: number;
  missingSequenceCount: number;
  segmentsMissingTimingCount: number;
  gaps: SessionRecordingTimelineGapResponse[];
  segments: SessionRecordingTimelineSegmentResponse[];
}

export type SessionRecordingExportStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'READY'
  | 'FAILED';

export interface SessionRecordingExportResponse {
  id: string | null;
  sessionId: string;
  status: SessionRecordingExportStatus | null;
  objectKey: string | null;
  downloadUrl: string | null;
  expiresInSeconds: number | null;
  packageSizeBytes: number | null;
  artifactCount: number | null;
  errorMessage: string | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface SessionTranscriptSegmentResponse {
  id: string;
  recordingId: string;
  recordingSequence: number | null;
  segmentIndex: number;
  startSeconds: string;
  endSeconds: string;
  text: string;
  confidence: string | null;
}

export interface SessionTranscriptRecordingResponse {
  recordingId: string;
  recordingSequence: number | null;
  status: RecordingTranscriptStatus;
  errorMessage: string | null;
  processingStage: RecordingTranscriptProcessingStage | null;
  lastErrorStage: RecordingTranscriptProcessingStage | null;
  retryCount: number;
  lastStageAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  sessionElapsedStartMs: number | null;
  sessionElapsedEndMs: number | null;
  durationSeconds: number | null;
  transcriptSegmentCount: number;
}

export interface SessionTranscriptResponse {
  sessionId: string;
  status: RecordingTranscriptStatus;
  engine: string | null;
  model: string | null;
  languageCode: string | null;
  fullText: string | null;
  shortSummary: string | null;
  incidentSummary: string | null;
  keywords: string[];
  errorMessage: string | null;
  processingStage: RecordingTranscriptProcessingStage | null;
  lastErrorStage: RecordingTranscriptProcessingStage | null;
  retryCount: number;
  lastStageAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  totalRecordings: number;
  readyRecordings: number;
  failedRecordings: number;
  processingRecordings: number;
  pendingRecordings: number;
  notRequestedRecordings: number;
  segments: SessionTranscriptSegmentResponse[];
  recordings: SessionTranscriptRecordingResponse[];
}

export interface SessionTranscriptSearchResponse {
  sessionId: string;
  query: string;
  status: RecordingTranscriptStatus;
  totalMatches: number;
  matches: SessionTranscriptSegmentResponse[];
}

export interface RecordingInvestigationSearchHitResponse {
  sessionId: string;
  recordingId: string;
  recordingSequence: number | null;
  workerId: string;
  workerName: string;
  roomName: string;
  referenceNumber: string;
  matchedField: string;
  snippet: string;
  transcriptStartSeconds: string | null;
  createdAt: string;
}

export interface RecordingInvestigationSearchResponse {
  query: string;
  totalMatches: number;
  hits: RecordingInvestigationSearchHitResponse[];
}

export interface TranscriptSmokeCheckResponse {
  ready: boolean;
  enabled: boolean;
  engine: string | null;
  endpoint: string | null;
  pollDelayMs: number;
  maxRetryCount: number;
  availableEngines: TranscriptEngineOptionResponse[];
  checks: string[];
  warnings: string[];
}

export interface TranscriptEngineOptionResponse {
  key: string;
  label: string;
  configuredDefault: boolean;
  ready: boolean;
  endpoint: string | null;
}
