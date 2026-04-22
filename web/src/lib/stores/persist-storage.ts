import { createJSONStorage, type StateStorage } from "zustand/middleware";

const noopStorage: StateStorage = {
  getItem: () => null,
  setItem: () => undefined,
  removeItem: () => undefined,
};

export function createPersistStorage<T>() {
  return createJSONStorage<T>(() =>
    typeof window !== "undefined" ? localStorage : noopStorage
  );
}

