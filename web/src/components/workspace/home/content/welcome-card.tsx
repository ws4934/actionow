"use client";

import { Card } from "@heroui/react";
import { Sparkles } from "lucide-react";

interface WelcomeCardProps {
  userName: string;
}

// Get greeting based on time of day
function getGreeting(): string {
  const hour = new Date().getHours();
  if (hour < 6) return "夜深了";
  if (hour < 12) return "早上好";
  if (hour < 14) return "中午好";
  if (hour < 18) return "下午好";
  return "晚上好";
}

// Random motivational messages
const MOTIVATIONAL_MESSAGES = [
  "今天是个创作的好日子！",
  "灵感正在向你招手 ✨",
  "准备好开始新的创作了吗？",
  "让我们一起创造精彩故事！",
  "你的创意值得被看见！",
];

export function WelcomeCard({ userName }: WelcomeCardProps) {
  const greeting = getGreeting();
  const message = MOTIVATIONAL_MESSAGES[Math.floor(Math.random() * MOTIVATIONAL_MESSAGES.length)];

  return (
    <Card variant="tertiary" className="relative overflow-hidden p-6">
      {/* Background decoration */}
      <div className="absolute -right-8 -top-8 size-32 rounded-full bg-accent/5" />
      <div className="absolute -bottom-4 -right-4 size-20 rounded-full bg-accent/10" />

      <div className="relative">
        <div className="flex items-center gap-2">
          <Sparkles className="size-5 text-accent" />
          <span className="text-sm text-muted">{greeting}</span>
        </div>
        <h1 className="mt-2 text-2xl font-bold">
          欢迎回来, {userName}
        </h1>
        <p className="mt-2 text-muted">
          {message}
        </p>
      </div>
    </Card>
  );
}
