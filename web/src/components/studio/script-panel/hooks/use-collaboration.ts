/**
 * useCollaboration Hook
 * Integrates WebSocket collaboration with the script panel
 */

import { useEffect, useCallback, useMemo, useRef } from "react";
import {
  useWebSocketContext,
  useWebSocketMessage,
} from "@/lib/websocket";
import type {
  CollabTab,
  ScriptCollaborationData,
  UserJoinedData,
  UserLeftData,
  UserLocationChangedData,
  EntityCollaborationData,
  EditingLockedData,
  EditingUnlockedData,
} from "@/lib/websocket/types";
import {
  collaborationSelectors,
  useCollaborationActions,
  useCollaborationState,
} from "@/lib/stores/collaboration-store";
import { getUserId } from "@/lib/stores/auth-store";

// Map local tab keys to WebSocket tab types
const TAB_MAP: Record<string, CollabTab> = {
  details: "DETAIL",
  episodes: "EPISODES",
  storyboards: "STORYBOARDS",
  characters: "CHARACTERS",
  scenes: "SCENES",
  props: "PROPS",
  assets: "PROPS", // Assets uses PROPS for now as it's not in the API
};

export function useCollaboration(scriptId: string, currentTab: string) {
  const ws = useWebSocketContext();
  const store = useCollaborationState();
  const {
    setCurrentScript,
    setCurrentTab,
    setUsers,
    addUser,
    removeUser,
    updateUser,
    setEntityCollaboration,
    cleanStaleEditors,
    reset,
  } = useCollaborationActions();
  const currentUserId = getUserId();
  const prevStatusRef = useRef(ws.status);

  // Convert tab to WebSocket format
  const wsTab = TAB_MAP[currentTab] || "DETAIL";

  // Enter script on mount, leave on unmount
  useEffect(() => {
    if (!scriptId) return;

    setCurrentScript(scriptId);
    ws.enterScript(scriptId, wsTab);

    return () => {
      ws.leaveScript();
      reset();
    };
  }, [scriptId]); // eslint-disable-line react-hooks/exhaustive-deps

  // Switch tab when currentTab changes
  useEffect(() => {
    if (!scriptId) return;

    setCurrentTab(wsTab);
    ws.switchTab(wsTab);
  }, [wsTab]); // eslint-disable-line react-hooks/exhaustive-deps

  // Handle WebSocket reconnection - re-enter script to get fresh state
  useEffect(() => {
    const prevStatus = prevStatusRef.current;
    prevStatusRef.current = ws.status;

    // If we just reconnected (was not connected, now connected)
    if (prevStatus !== "connected" && ws.status === "connected" && scriptId) {
      ws.enterScript(scriptId, wsTab);
    }
  }, [ws.status, scriptId, wsTab]); // eslint-disable-line react-hooks/exhaustive-deps

  // Periodic cleanup of stale editors (every 2 minutes)
  useEffect(() => {
    const interval = setInterval(() => {
      cleanStaleEditors(10 * 60 * 1000); // 10 minutes
    }, 2 * 60 * 1000);

    return () => clearInterval(interval);
  }, [cleanStaleEditors]);

  // Handle WebSocket messages
  useWebSocketMessage(
    (message) => {
      switch (message.type) {
        case "SCRIPT_COLLABORATION": {
          const data = message.data as ScriptCollaborationData;
          if (data.scriptId === scriptId) {
            setUsers(data.users, data.tabUserCounts);
          }
          break;
        }

        case "USER_JOINED": {
          const data = message.data as UserJoinedData;
          if (data.scriptId === scriptId && data.user.userId !== currentUserId) {
            addUser(data.user);
          }
          break;
        }

        case "USER_LEFT": {
          const data = message.data as UserLeftData;
          if (data.scriptId === scriptId && data.userId !== currentUserId) {
            removeUser(data.userId);
          }
          break;
        }

        case "USER_LOCATION_CHANGED": {
          const data = message.data as UserLocationChangedData;
          if (data.scriptId === scriptId) {
            updateUser(data.user);
          }
          break;
        }

        case "ENTITY_COLLABORATION": {
          const data = message.data as EntityCollaborationData;
          if (data.scriptId === scriptId) {
            setEntityCollaboration(
              data.entityType,
              data.entityId,
              data.viewers,
              data.editor
            );
          }
          break;
        }

        case "EDITING_LOCKED": {
          const data = message.data as EditingLockedData;
          // Update entity state with the new editor
          setEntityCollaboration(
            data.entityType,
            data.entityId,
            [], // viewers will be updated separately
            {
              userId: data.lockedBy.userId,
              nickname: data.lockedBy.nickname,
              avatar: data.lockedBy.avatar,
            }
          );
          break;
        }

        case "EDITING_UNLOCKED": {
          const data = message.data as EditingUnlockedData;
          // Entity is now available for editing
          setEntityCollaboration(data.entityType, data.entityId, [], null);
          break;
        }
      }
    },
    [scriptId, currentUserId]
  );

  // Get users in current tab
  const currentTabUsers = useMemo(() => {
    return store.users.filter((u) => u.tab === wsTab && u.userId !== currentUserId);
  }, [store.users, wsTab, currentUserId]);

  // Get all users except current user
  const otherUsers = useMemo(() => {
    return store.users.filter((u) => u.userId !== currentUserId);
  }, [store.users, currentUserId]);

  // Focus entity
  const focusEntity = useCallback(
    (entityType: string, entityId: string) => {
      ws.focusEntity(entityType.toUpperCase(), entityId);
    },
    [ws]
  );

  // Blur entity
  const blurEntity = useCallback(
    (entityType: string, entityId: string) => {
      ws.blurEntity(entityType.toUpperCase(), entityId);
    },
    [ws]
  );

  // Start editing
  const startEditing = useCallback(
    (entityType: string, entityId: string) => {
      ws.startEditing(entityType.toUpperCase(), entityId);
    },
    [ws]
  );

  // Stop editing
  const stopEditing = useCallback(
    (entityType: string, entityId: string) => {
      ws.stopEditing(entityType.toUpperCase(), entityId);
    },
    [ws]
  );

  // Get entity collaborators
  const getEntityCollaborators = useCallback(
    (entityType: string, entityId: string) => {
      const state = collaborationSelectors.getEntityState(
        store,
        entityType.toUpperCase(),
        entityId
      );
      return {
        viewers: state?.viewers.filter((v) => v.userId !== currentUserId) || [],
        editor: state?.editor || null,
        lockedAt: state?.lockedAt || null,
        isLockedByOther: state?.editor
          ? state.editor.userId !== currentUserId
          : false,
      };
    },
    [store, currentUserId]
  );

  // Refresh entity state by re-focusing
  const refreshEntityState = useCallback(
    (entityType: string, entityId: string) => {
      ws.focusEntity(entityType.toUpperCase(), entityId);
    },
    [ws]
  );

  // Check if entity is locked by others
  const isEntityLocked = useCallback(
    (entityType: string, entityId: string) => {
      return collaborationSelectors.isEntityLocked(
        store,
        entityType.toUpperCase(),
        entityId,
        currentUserId || ""
      );
    },
    [store, currentUserId]
  );

  return {
    // Connection state
    connected: ws.connected,
    status: ws.status,

    // Users
    users: otherUsers,
    currentTabUsers,
    tabUserCounts: store.tabUserCounts,

    // Entity methods
    focusEntity,
    blurEntity,
    startEditing,
    stopEditing,
    getEntityCollaborators,
    isEntityLocked,
    refreshEntityState,
  };
}

export default useCollaboration;
