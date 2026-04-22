"use client";

/**
 * Script Panel Component
 * Main panel for viewing and editing script details
 */

import { useState, useEffect, useCallback } from "react";
import { Card, Tabs } from "@heroui/react";
import { useTranslations } from "next-intl";

import type { ScriptPanelProps, TabKey } from "./types";
import { TABS, TAB_I18N_KEYS } from "./constants";
import { getScriptActiveTab, setScriptActiveTab } from "@/lib/stores/preferences-store";
import { useCollaboration } from "./hooks/use-collaboration";
import { useStoryboardEpisode } from "./hooks/use-storyboard-episode-store";
import { OnlineUsers } from "./components";

// Import tabs - these will be lazy loaded in the future
import { ScriptDetailsTab } from "./tabs/script-details-tab";
import { EpisodeTab } from "./tabs/episode-tab";
import { StoryboardTab } from "./tabs/storyboard-tab";
import { CharacterTab } from "./tabs/character-tab";
import { SceneTab } from "./tabs/scene-tab";
import { PropTab } from "./tabs/prop-tab";
import { AssetTab } from "./tabs/asset-tab";
import { EntityTab } from "./tabs/entity-tab";

export function ScriptPanel({ scriptId }: ScriptPanelProps) {
  const [activeTab, setActiveTab] = useState<TabKey>(() => getScriptActiveTab(scriptId));
  const t = useTranslations("workspace.studio");

  // Collaboration hook
  const { users, connected } = useCollaboration(scriptId, activeTab);
  const storyboardEpisode = useStoryboardEpisode(scriptId);

  // Update active tab when scriptId changes
  useEffect(() => {
    setActiveTab(getScriptActiveTab(scriptId));
  }, [scriptId]);

  // Handle tab change with storage
  const handleTabChange = useCallback((key: TabKey) => {
    setActiveTab(key);
    setScriptActiveTab(scriptId, key);
  }, [scriptId]);

  // Render tab content based on active tab
  const renderTabContent = () => {
    switch (activeTab) {
      case "details":
        return <ScriptDetailsTab scriptId={scriptId} />;
      case "episodes":
        return <EpisodeTab scriptId={scriptId} />;
      case "storyboards":
        return <StoryboardTab scriptId={scriptId} />;
      case "characters":
        return <CharacterTab scriptId={scriptId} />;
      case "scenes":
        return <SceneTab scriptId={scriptId} />;
      case "props":
        return <PropTab scriptId={scriptId} />;
      case "assets":
        return <AssetTab scriptId={scriptId} />;
      default:
        return <EntityTab key={activeTab} tabKey={activeTab} scriptId={scriptId} />;
    }
  };

  return (
    <Card className="flex h-full flex-col overflow-hidden">
      {/* Header */}
      <div className="flex shrink-0 items-center justify-between gap-2 overflow-hidden">
        {/* Left: Tabs */}
        <Tabs
          selectedKey={activeTab}
          onSelectionChange={(key) => handleTabChange(key as TabKey)}
          className="min-w-0 flex-1"
        >
          <Tabs.ListContainer className="overflow-x-auto scrollbar-hide">
            <Tabs.List aria-label="Script tabs">
              {TABS.map((tab) => {
                const baseLabel = TAB_I18N_KEYS[tab.key] ? t(TAB_I18N_KEYS[tab.key]) : tab.label;
                const label =
                  tab.key === "storyboards" && storyboardEpisode
                    ? `${baseLabel} · ${storyboardEpisode.title}`
                    : baseLabel;
                return (
                  <Tabs.Tab
                    key={tab.key}
                    id={tab.key}
                    className="gap-1.5 whitespace-nowrap px-2.5 text-xs"
                  >
                    {tab.icon}
                    {label}
                    <Tabs.Indicator />
                  </Tabs.Tab>
                );
              })}
            </Tabs.List>
          </Tabs.ListContainer>
        </Tabs>

        {/* Right: Online Users */}
        {connected && users.length > 0 && (
          <OnlineUsers users={users} />
        )}
      </div>

      {/* Content */}
      <div className="min-h-0 flex-1">
        {renderTabContent()}
      </div>
    </Card>
  );
}

// Re-export types and components for external use
export * from "./types";
export * from "./constants";
export * from "./utils";
export * from "./common";
export * from "./tabs";
export * from "./components";
export * from "./hooks";

export default ScriptPanel;
