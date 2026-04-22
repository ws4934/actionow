"use client";

import { useEffect, useState } from "react";
import { skillService } from "@/lib/api/services/skill.service";

export type JsonSchema = Record<string, unknown>;

const cache = new Map<string, Promise<JsonSchema | null>>();
const resolved = new Map<string, JsonSchema | null>();

/** Preload a schema into the resolved cache (used by demo pages / tests). */
export function seedSchema(name: string, schema: JsonSchema | null): void {
  resolved.set(name, schema);
  cache.set(name, Promise.resolve(schema));
}

export function getSchema(name: string): Promise<JsonSchema | null> {
  const hit = cache.get(name);
  if (hit) return hit;
  const p = skillService
    .getOutputSchema(name)
    .then((s) => {
      const value = (s as JsonSchema | null) ?? null;
      resolved.set(name, value);
      return value;
    })
    .catch(() => {
      // Don't cache rejections — allow retry next time
      cache.delete(name);
      resolved.set(name, null);
      return null;
    });
  cache.set(name, p);
  return p;
}

export function useSchema(name: string | undefined | null): {
  schema: JsonSchema | null;
  isLoading: boolean;
} {
  const initial = name ? resolved.get(name) ?? null : null;
  const [schema, setSchema] = useState<JsonSchema | null>(initial);
  const [isLoading, setIsLoading] = useState<boolean>(!!name && !resolved.has(name));

  useEffect(() => {
    if (!name) {
      setSchema(null);
      setIsLoading(false);
      return;
    }
    if (resolved.has(name)) {
      setSchema(resolved.get(name) ?? null);
      setIsLoading(false);
      return;
    }
    let cancelled = false;
    setIsLoading(true);
    getSchema(name).then((s) => {
      if (!cancelled) {
        setSchema(s);
        setIsLoading(false);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [name]);

  return { schema, isLoading };
}
