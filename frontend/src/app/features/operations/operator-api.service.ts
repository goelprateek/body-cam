import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '@env/environment';
import {
  CurrentUserResponse,
  CursorPageResponse,
  LiveKitTokenResponse,
  LoginResponse,
  PageResponse,
  RecordingInvestigationSearchResponse,
  RecordingPlaybackResponse,
  RecordingResponse,
  RecordingTranscriptResponse,
  SessionRecordingExportResponse,
  SessionRecordingTimelineResponse,
  SessionTranscriptSearchResponse,
  SessionTranscriptResponse,
  SessionResponse,
  TranscriptEngineOptionResponse,
  TranscriptSmokeCheckResponse
} from './operator.models';

const ACCESS_TOKEN_KEY = 'bodycam.operator.access-token';

@Injectable({ providedIn: 'root' })
export class OperatorApiService {
  private readonly http = inject(HttpClient);

  readonly apiBase = signal(environment.apiBaseUrl);
  readonly accessToken = signal<string | null>(localStorage.getItem(ACCESS_TOKEN_KEY));
  readonly currentUser = signal<CurrentUserResponse | null>(null);
  readonly operatorLabel = computed(() => {
    const user = this.currentUser();
    if (!user) {
      return 'Backoffice Operator';
    }

    return user.displayName || user.username;
  });

  async restoreSession(): Promise<boolean> {
    if (!this.accessToken()) {
      return false;
    }

    try {
      const currentUser = await this.getCurrentUser();
      this.currentUser.set(currentUser);
      return true;
    } catch {
      this.clearSession();
      return false;
    }
  }

  async login(username: string, password: string): Promise<CurrentUserResponse> {
    const response = await firstValueFrom(
      this.http.post<LoginResponse>(this.url('/auth/login'), {
        username,
        password
      })
    );

    localStorage.setItem(ACCESS_TOKEN_KEY, response.accessToken);
    this.accessToken.set(response.accessToken);

    const currentUser: CurrentUserResponse = {
      userId: response.userId,
      username: response.username,
      displayName: response.displayName,
      role: response.role
    };

    this.currentUser.set(currentUser);
    return currentUser;
  }

  logout(): void {
    this.clearSession();
  }

  async listSessions(): Promise<SessionResponse[]> {
    return firstValueFrom(
      this.http.get<SessionResponse[]>(this.url('/sessions'), {
        headers: this.authHeaders()
      })
    );
  }

  async listActiveSessions(page: number, size: number): Promise<PageResponse<SessionResponse>> {
    return firstValueFrom(
      this.http.get<PageResponse<SessionResponse>>(this.url('/sessions/active'), {
        headers: this.authHeaders(),
        params: {
          page,
          size
        }
      })
    );
  }

  async listActiveSessionsCursor(cursor: string | null, size: number): Promise<CursorPageResponse<SessionResponse>> {
    const params: Record<string, any> = { size };
    if (cursor) {
      params['cursor'] = cursor;
    }
    return firstValueFrom(
      this.http.get<CursorPageResponse<SessionResponse>>(this.url('/sessions/active-cursor'), {
        headers: this.authHeaders(),
        params
      })
    );
  }

  async listRecordings(cursor: string | null, size: number): Promise<CursorPageResponse<RecordingResponse>> {
    const params: Record<string, any> = { size };
    if (cursor) {
      params['cursor'] = cursor;
    }
    return firstValueFrom(
      this.http.get<CursorPageResponse<RecordingResponse>>(this.url('/recordings'), {
        headers: this.authHeaders(),
        params
      })
    );
  }

  async getRecordingPlaybackUrl(recordingId: string): Promise<RecordingPlaybackResponse> {
    return firstValueFrom(
      this.http.get<RecordingPlaybackResponse>(this.url(`/recordings/${recordingId}/playback-url`), {
        headers: this.authHeaders()
      })
    );
  }

  async getSessionRecordingTimeline(sessionId: string): Promise<SessionRecordingTimelineResponse> {
    return firstValueFrom(
      this.http.get<SessionRecordingTimelineResponse>(this.url(`/sessions/${sessionId}/recordings/timeline`), {
        headers: this.authHeaders()
      })
    );
  }

  async getSessionRecordingExport(sessionId: string): Promise<SessionRecordingExportResponse> {
    return firstValueFrom(
      this.http.get<SessionRecordingExportResponse>(this.url(`/sessions/${sessionId}/recordings/export-package`), {
        headers: this.authHeaders()
      })
    );
  }

  async requestSessionRecordingExport(sessionId: string): Promise<SessionRecordingExportResponse> {
    return firstValueFrom(
      this.http.post<SessionRecordingExportResponse>(
        this.url(`/sessions/${sessionId}/recordings/export-package`),
        {},
        {
          headers: this.authHeaders()
        }
      )
    );
  }

  async getSessionTranscript(sessionId: string): Promise<SessionTranscriptResponse> {
    return firstValueFrom(
      this.http.get<SessionTranscriptResponse>(this.url(`/sessions/${sessionId}/transcript`), {
        headers: this.authHeaders()
      })
    );
  }

  async getTranscriptSmokeCheck(): Promise<TranscriptSmokeCheckResponse> {
    return firstValueFrom(
      this.http.get<TranscriptSmokeCheckResponse>(this.url('/transcripts/smoke-check'), {
        headers: this.authHeaders()
      })
    );
  }

  async getTranscriptEngines(): Promise<TranscriptEngineOptionResponse[]> {
    return firstValueFrom(
      this.http.get<TranscriptEngineOptionResponse[]>(this.url('/transcripts/engines'), {
        headers: this.authHeaders()
      })
    );
  }

  async generateSessionTranscript(sessionId: string, engine: string | null): Promise<SessionTranscriptResponse> {
    return firstValueFrom(
      this.http.post<SessionTranscriptResponse>(
        this.url(`/sessions/${sessionId}/transcript/generate`),
        { engine },
        {
          headers: this.authHeaders()
        }
      )
    );
  }

  async retryFailedSessionTranscript(sessionId: string, engine: string | null): Promise<SessionTranscriptResponse> {
    return firstValueFrom(
      this.http.post<SessionTranscriptResponse>(
        this.url(`/sessions/${sessionId}/transcript/retry-failed`),
        { engine },
        {
          headers: this.authHeaders()
        }
      )
    );
  }

  async summarizeSessionTranscript(sessionId: string): Promise<SessionTranscriptResponse> {
    return firstValueFrom(
      this.http.post<SessionTranscriptResponse>(
        this.url(`/sessions/${sessionId}/transcript/summary`),
        {},
        {
          headers: this.authHeaders()
        }
      )
    );
  }

  async searchSessionTranscript(sessionId: string, query: string): Promise<SessionTranscriptSearchResponse> {
    return firstValueFrom(
      this.http.get<SessionTranscriptSearchResponse>(this.url(`/sessions/${sessionId}/transcript/search`), {
        headers: this.authHeaders(),
        params: { q: query }
      })
    );
  }

  async searchRecordingsForInvestigation(query: string): Promise<RecordingInvestigationSearchResponse> {
    return firstValueFrom(
      this.http.get<RecordingInvestigationSearchResponse>(this.url('/recordings/investigation-search'), {
        headers: this.authHeaders(),
        params: { q: query }
      })
    );
  }

  async getRecordingTranscript(recordingId: string): Promise<RecordingTranscriptResponse> {
    return firstValueFrom(
      this.http.get<RecordingTranscriptResponse>(this.url(`/recordings/${recordingId}/transcript`), {
        headers: this.authHeaders()
      })
    );
  }

  async generateRecordingTranscript(recordingId: string, engine: string | null): Promise<RecordingTranscriptResponse> {
    return firstValueFrom(
      this.http.post<RecordingTranscriptResponse>(
        this.url(`/recordings/${recordingId}/transcript/generate`),
        { engine },
        {
          headers: this.authHeaders()
        }
      )
    );
  }

  async getRecordingTranscriptSubtitles(recordingId: string): Promise<string> {
    return firstValueFrom(
      this.http.get(this.url(`/recordings/${recordingId}/transcript/subtitles.vtt`), {
        headers: this.authHeaders(),
        responseType: 'text'
      })
    );
  }

  async createJoinToken(
    sessionId: string,
    participantName: string
  ): Promise<LiveKitTokenResponse> {
    return firstValueFrom(
      this.http.post<LiveKitTokenResponse>(
        this.url(`/sessions/${sessionId}/join-token`),
        {
          participantName,
          participantRole: 'OPERATOR'
        },
        {
          headers: this.authHeaders()
        }
      )
    );
  }

  async endSession(sessionId: string): Promise<SessionResponse> {
    return firstValueFrom(
      this.http.post<SessionResponse>(
        this.url(`/sessions/${sessionId}/end`),
        {},
        {
          headers: this.authHeaders()
        }
      )
    );
  }

  explainError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const message =
        typeof error.error === 'string'
          ? error.error
          : (error.error?.message as string | undefined);

      return message || error.message || `HTTP ${error.status}`;
    }

    if (error instanceof Error) {
      return error.message;
    }

    return 'Unexpected request failure';
  }

  private async getCurrentUser(): Promise<CurrentUserResponse> {
    return firstValueFrom(
      this.http.get<CurrentUserResponse>(this.url('/auth/me'), {
        headers: this.authHeaders()
      })
    );
  }

  private authHeaders(): HttpHeaders {
    const token = this.accessToken();
    if (!token) {
      return new HttpHeaders();
    }

    return new HttpHeaders({
      Authorization: `Bearer ${token}`
    });
  }

  private clearSession(): void {
    this.accessToken.set(null);
    this.currentUser.set(null);
    localStorage.removeItem(ACCESS_TOKEN_KEY);
  }

  private url(path: string): string {
    return `${this.apiBase()}${path}`;
  }
}
