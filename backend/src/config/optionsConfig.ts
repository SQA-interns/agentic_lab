/**
 * Conference options catalogue (specification § 3.4, US-003).
 *
 * Loads and validates the configuration file once at startup and exposes lookup helpers
 * used by the registration-config endpoint and by option validation. Registrations are
 * never served against an invalid configuration (AC-003-06).
 */
import { readFileSync } from 'node:fs';

import { ConfigurationError } from '../domain/errors.js';
import type { OptionGroupId, RegistrationVariant } from '../domain/registration.js';
import {
  conferenceOptionsConfigSchema,
  type ConferenceOption,
  type ConferenceOptionsConfig,
} from '../domain/schema/optionsConfigSchema.js';

export interface ResolvedOption {
  readonly optionId: string;
  readonly group: OptionGroupId;
  readonly displayName: string;
}

export type OptionResolution =
  | { readonly ok: true; readonly option: ResolvedOption }
  | { readonly ok: false; readonly reason: 'unknown' | 'inactive' | 'not_available_for_variant' };

export class OptionCatalogue {
  private readonly config: ConferenceOptionsConfig;
  private readonly byId: ReadonlyMap<string, { option: ConferenceOption; group: OptionGroupId }>;

  constructor(config: ConferenceOptionsConfig) {
    this.config = config;
    const index = new Map<string, { option: ConferenceOption; group: OptionGroupId }>();
    for (const group of config.groups) {
      for (const option of group.options) {
        index.set(option.id, { option, group: group.id });
      }
    }
    this.byId = index;
  }

  get conferenceName(): string {
    return this.config.conferenceName;
  }

  get privacyConsentText(): string {
    return this.config.privacyConsentText;
  }

  /** Option groups with only the options a given variant may currently select. */
  activeGroupsFor(variant: RegistrationVariant): ReadonlyArray<{
    id: OptionGroupId;
    displayName: string;
    options: ReadonlyArray<{ id: string; displayName: string; description?: string }>;
  }> {
    return this.config.groups.map((group) => ({
      id: group.id,
      displayName: group.displayName,
      options: group.options
        .filter((option) => option.active && option.availableTo.includes(variant))
        .map((option) => ({
          id: option.id,
          displayName: option.displayName,
          ...(option.description === undefined ? {} : { description: option.description }),
        })),
    }));
  }

  /**
   * Resolve one submitted identifier for a variant.
   *
   * Unknown, inactive and variant-restricted identifiers are all rejected; the caller
   * turns the reason into a field error naming the identifier (AC-003-04, AC-003-05,
   * AC-002-08).
   */
  resolve(optionId: string, variant: RegistrationVariant): OptionResolution {
    const entry = this.byId.get(optionId);
    if (entry === undefined) {
      return { ok: false, reason: 'unknown' };
    }
    if (!entry.option.active) {
      return { ok: false, reason: 'inactive' };
    }
    if (!entry.option.availableTo.includes(variant)) {
      return { ok: false, reason: 'not_available_for_variant' };
    }
    return {
      ok: true,
      option: {
        optionId: entry.option.id,
        group: entry.group,
        displayName: entry.option.displayName,
      },
    };
  }

  /**
   * Display name for an identifier regardless of its current state, used by the export
   * so registrations stored before a change remain readable (AC-003-07).
   */
  displayNameOf(optionId: string): string | null {
    return this.byId.get(optionId)?.option.displayName ?? null;
  }
}

export function parseOptionsConfig(raw: unknown, sourceLabel: string): OptionCatalogue {
  const parsed = conferenceOptionsConfigSchema.safeParse(raw);
  if (!parsed.success) {
    const details = parsed.error.issues
      .map((issue) => `  ${issue.path.join('.') || '(root)'}: ${issue.message}`)
      .join('\n');
    throw new ConfigurationError(`Invalid conference options configuration (${sourceLabel}):\n${details}`);
  }
  return new OptionCatalogue(parsed.data);
}

export function loadOptionsConfig(filePath: string): OptionCatalogue {
  let contents: string;
  try {
    contents = readFileSync(filePath, 'utf8');
  } catch {
    throw new ConfigurationError(`Conference options configuration file cannot be read: ${filePath}`);
  }

  let raw: unknown;
  try {
    raw = JSON.parse(contents);
  } catch {
    throw new ConfigurationError(`Conference options configuration file is not valid JSON: ${filePath}`);
  }

  return parseOptionsConfig(raw, filePath);
}
