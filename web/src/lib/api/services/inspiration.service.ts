/**
 * Inspiration Service
 *
 * Real API implementation for inspiration sessions and records.
 * Generation is submitted via dedicated endpoint; the backend handles
 * record creation, AI dispatch, and asset binding.
 */

import { api } from "../client";
import type {
  InspirationSessionDTO,
  InspirationRecordDTO,
  FreeGenerationRequestDTO,
  FreeGenerationResponseDTO,
  InspirationSessionsPageDTO,
  InspirationRecordsPageDTO,
  CreateInspirationSessionRequestDTO,
} from "@/lib/api/dto/inspiration.dto";

const BASE = "/api/inspiration";

// ============================================================================
// Session APIs
// ============================================================================

function getSessions(params?: {
  page?: number;
  size?: number;
  status?: string;
}) {
  return api.get<InspirationSessionsPageDTO>(`${BASE}/sessions`, {
    params: {
      pageNum: params?.page,
      pageSize: params?.size,
      status: params?.status,
    },
  });
}

function getSession(sessionId: string) {
  return api.get<InspirationSessionDTO>(`${BASE}/sessions/${sessionId}`);
}

function createSession(data?: CreateInspirationSessionRequestDTO) {
  return api.post<InspirationSessionDTO>(`${BASE}/sessions`, data ?? {});
}

function updateSessionTitle(sessionId: string, title: string) {
  return api.patch<InspirationSessionDTO>(`${BASE}/sessions/${sessionId}`, { title });
}

function deleteSession(sessionId: string) {
  return api.delete<null>(`${BASE}/sessions/${sessionId}`);
}

function archiveSession(sessionId: string) {
  return api.post<InspirationSessionDTO>(`${BASE}/sessions/${sessionId}/archive`);
}

// ============================================================================
// Record APIs
// ============================================================================

function getRecords(
  sessionId: string,
  params?: { page?: number; size?: number }
) {
  return api.get<InspirationRecordsPageDTO>(
    `${BASE}/sessions/${sessionId}/records`,
    {
      params: {
        pageNum: params?.page,
        pageSize: params?.size,
      },
    }
  );
}

function deleteRecord(sessionId: string, recordId: string) {
  return api.delete<null>(
    `${BASE}/sessions/${sessionId}/records/${recordId}`
  );
}

// ============================================================================
// Generation
// ============================================================================

function submitGeneration(data: FreeGenerationRequestDTO) {
  return api.post<FreeGenerationResponseDTO>(`${BASE}/generate`, data);
}

// ============================================================================
// Export
// ============================================================================

export const inspirationService = {
  // Sessions
  getSessions,
  getSession,
  createSession,
  updateSessionTitle,
  deleteSession,
  archiveSession,
  // Records
  getRecords,
  deleteRecord,
  // Generation
  submitGeneration,
};
