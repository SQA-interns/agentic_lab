/**
 * Registration endpoint (specification § 4.3, US-001, US-002, US-004).
 */
import { Router } from 'express';

import type { RegistrationService } from '../../application/registrationService.js';
import { MalformedRequestError } from '../../domain/errors.js';
import type { Logger } from '../../infrastructure/logging/logger.js';

export interface RegistrationRouterDependencies {
  readonly service: RegistrationService;
  readonly logger: Logger;
}

export function registrationsRouter(deps: RegistrationRouterDependencies): Router {
  const router = Router();

  router.post('/registrations', (req, res) => {
    if (typeof req.body !== 'object' || req.body === null || Array.isArray(req.body)) {
      throw new MalformedRequestError();
    }

    const { registration, emailsDispatched } = deps.service.register(req.body);

    // Storage is already durable at this point, so the 201 is a truthful promise and the
    // only trigger for the frontend confirmation (AC-004-01, AC-005-04).
    res.status(201).json({
      reference: registration.reference,
      variant: registration.variant,
      email: registration.participant.email,
      createdAt: registration.createdAt,
      selectedOptions: registration.selectedOptions,
      confirmationEmailQueuedTo: registration.participant.email,
    });

    // Emails are dispatched after the response; a failure is logged inside the service
    // and must never turn an accepted registration into an error (AC-005-05).
    void emailsDispatched.catch((error: unknown) => {
      deps.logger.error({ reference: registration.reference, err: error }, 'email dispatch failed');
    });
  });

  return router;
}
