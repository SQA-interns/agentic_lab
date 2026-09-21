/**
 * Conference options configuration schema (specification § 3.4, US-003).
 *
 * The configuration file is the only place where workshops, events, meals and other
 * activities are defined. It is schema-validated at startup so an invalid programme is
 * a startup failure rather than a runtime surprise (AC-003-06).
 */
import { z } from 'zod';

import { OPTION_GROUPS, REGISTRATION_VARIANTS } from '../registration.js';

const identifier = z
  .string()
  .trim()
  .min(1)
  .max(64)
  .regex(/^[a-z0-9][a-z0-9-]*$/u, 'must be lower-case letters, digits and hyphens');

export const conferenceOptionSchema = z
  .object({
    id: identifier,
    displayName: z.string().trim().min(1).max(200),
    active: z.boolean(),
    /** Which registration variants may select this option; defaults to both. */
    availableTo: z
      .array(z.enum(REGISTRATION_VARIANTS))
      .nonempty()
      .optional()
      .default([...REGISTRATION_VARIANTS]),
    description: z.string().trim().max(500).optional(),
  })
  .strict();

export const optionGroupSchema = z
  .object({
    id: z.enum(OPTION_GROUPS),
    displayName: z.string().trim().min(1).max(200),
    options: z.array(conferenceOptionSchema),
  })
  .strict();

export const conferenceOptionsConfigSchema = z
  .object({
    conferenceName: z.string().trim().min(1).max(200),
    privacyConsentText: z.string().trim().min(1).max(1000),
    groups: z
      .array(optionGroupSchema)
      // All four groups named in FORM_SCHEMA.md must be present, even when empty, so the
      // form structure is stable while the programme changes (AC-003-02).
      .length(OPTION_GROUPS.length)
      .superRefine((groups, ctx) => {
        const groupIds = groups.map((group) => group.id);
        for (const required of OPTION_GROUPS) {
          if (!groupIds.includes(required)) {
            ctx.addIssue({ code: 'custom', message: `missing option group "${required}"` });
          }
        }
        const seen = new Set<string>();
        for (const group of groups) {
          for (const option of group.options) {
            if (seen.has(option.id)) {
              ctx.addIssue({
                code: 'custom',
                message: `duplicate option identifier "${option.id}"`,
              });
            }
            seen.add(option.id);
          }
        }
      }),
  })
  .strict();

export type ConferenceOption = z.infer<typeof conferenceOptionSchema>;
export type OptionGroup = z.infer<typeof optionGroupSchema>;
export type ConferenceOptionsConfig = z.infer<typeof conferenceOptionsConfigSchema>;
