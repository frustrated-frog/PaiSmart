interface LegacyRememberedLogin {
  userName?: string;
  password?: string;
}

export interface RememberedLogin {
  userName: string;
}

export function sanitizeRememberedLogin(value?: LegacyRememberedLogin | null): RememberedLogin | null {
  const userName = value?.userName?.trim();
  return userName ? { userName } : null;
}
