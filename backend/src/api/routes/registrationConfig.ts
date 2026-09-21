/**
 * Form configuration endpoint (specification § 4.2, US-003, AC-003-08).
 *
 * The frontend has no hard-coded option list and no hard-coded validation rule table:
 * both come from here, so changing the conference programme or a field rule is a backend
 * concern only.
 */
import { Router } from 'express';

import type { FormTokenService } from '../../application/formTokenService.js';
import type { OptionCatalogue } from '../../config/optionsConfig.js';
import { ValidationError } from '../../domain/errors.js';
import { FIXED_FIELDS, isRegistrationVariant, REGISTRATION_VARIANTS } from '../../domain/registration.js';
import { FIELD_RULES } from '../../domain/schema/registrationSchema.js';

export interface RegistrationConfigDependencies {
  readonly catalogue: OptionCatalogue;
  readonly formTokens: FormTokenService;
  readonly formTokenTtlSeconds: number;
  readonly conferenceName: string;
}

export function registrationConfigRouter(deps: RegistrationConfigDependencies): Router {
  const router = Router();

  router.get('/registration-config', (req, res) => {
    const variant = req.query.variant;
    if (!isRegistrationVariant(variant)) {
      throw new ValidationError([
        {
          field: 'variant',
          code: 'invalid_variant',
          message: `Registration variant must be one of: ${REGISTRATION_VARIANTS.join(', ')}.`,
        },
      ]);
    }

    res.json({
      conferenceName: deps.conferenceName,
      variant,
      fields: FIXED_FIELDS[variant],
      fieldRules: FIELD_RULES[variant],
      consents: [
        {
          id: 'privacy',
          required: true,
          // Returned unchecked; the frontend renders it unchecked (AC-G-08).
          text: deps.catalogue.privacyConsentText,
        },
      ],
      optionGroups: deps.catalogue.activeGroupsFor(variant),
      formToken: deps.formTokens.issue(variant),
      formTokenTtlSeconds: deps.formTokenTtlSeconds,
    });
  });

  return router;
}
