/**
 * Registration use case (specification § 6).
 *
 * Orchestrates the whole accepted-registration path: anti-automation, validation,
 * atomic persistence of the database row and the JSON backup, and — after the response
 * has been handed back — the two emails.
 */
import type { OptionCatalogue } from '../config/optionsConfig.js';
import {
  PersistenceError,
  ValidationError,
  type FieldError,
} from '../domain/errors.js';
import { generateReference } from '../domain/reference.js';
import type { Registration, RegistrationVariant, SelectedOption } from '../domain/registration.js';
import { validateRegistrationFields } from '../domain/schema/registrationSchema.js';
import type { JsonBackupStore } from '../infrastructure/backup/jsonBackupStore.js';
import type { RegistrationRepository } from '../infrastructure/db/registrationRepository.js';
import type { Logger } from '../infrastructure/logging/logger.js';
import {
  composeOrganizerNotification,
  composeParticipantConfirmation,
} from '../infrastructure/mail/templates.js';
import type { FormTokenService } from './formTokenService.js';
import type { MailPort } from './ports.js';

const MAX_REFERENCE_ATTEMPTS = 5;

export interface RegistrationServiceDependencies {
  readonly repository: RegistrationRepository;
  readonly backupStore: JsonBackupStore;
  readonly catalogue: OptionCatalogue;
  readonly formTokens: FormTokenService;
  readonly mail: MailPort;
  readonly organizerRecipients: readonly string[];
  readonly logger: Logger;
  readonly now?: () => Date;
}

export interface RegistrationResult {
  readonly registration: Registration;
  /** Resolves when both emails have been attempted; failures are logged, never thrown. */
  readonly emailsDispatched: Promise<void>;
}

export class RegistrationService {
  private readonly deps: RegistrationServiceDependencies;
  private readonly now: () => Date;

  constructor(dependencies: RegistrationServiceDependencies) {
    this.deps = dependencies;
    this.now = dependencies.now ?? (() => new Date());
  }

  /**
   * Process one registration submission.
   *
   * @throws AntiAutomationError when an anti-automation control rejects the submission.
   * @throws ValidationError when any field or option rule is violated; nothing is stored
   *   and no email is produced (AC-001-09, AC-002-09).
   * @throws PersistenceError when the registration could not be stored durably; neither
   *   the database row nor the JSON file remains (AC-005-04).
   */
  register(body: unknown): RegistrationResult {
    const rawBody = (typeof body === 'object' && body !== null ? body : {}) as Record<string, unknown>;

    // 1. Anti-automation runs before anything is parsed or stored, so scripted traffic
    //    never reaches validation or the database (specification § 6 step 1).
    this.deps.formTokens.checkHoneypot(rawBody.website);

    // 2. Field validation.
    const validation = validateRegistrationFields(body);
    if (!validation.ok) {
      throw new ValidationError(validation.errors);
    }
    const input = validation.value;

    this.deps.formTokens.consume(input.formToken, input.variant);

    // 3. Option validation and resolution against the live configuration.
    const { options, errors } = this.resolveOptions(input.selectedOptionIds, input.variant);
    if (errors.length > 0) {
      throw new ValidationError(errors);
    }

    const timestamp = this.now().toISOString();
    const registration: Registration = {
      reference: this.generateUniqueReference(),
      variant: input.variant,
      participant: {
        firstName: input.firstName,
        lastName: input.lastName,
        email: input.email,
        organization: input.organization,
        studyInstitution: input.studyInstitution,
        studyProgramme: input.studyProgramme,
        studentId: input.studentId,
      },
      selectedOptions: options,
      privacyConsent: true,
      privacyConsentAt: timestamp,
      createdAt: timestamp,
    };

    // 4. Persist the database row and the JSON backup as one unit.
    this.persist(registration);

    this.deps.logger.info(
      { reference: registration.reference, variant: registration.variant, optionCount: options.length },
      'registration stored',
    );

    // 5/6. The caller responds now; emails are dispatched independently of the response.
    return { registration, emailsDispatched: this.dispatchEmails(registration) };
  }

  private resolveOptions(
    optionIds: readonly string[],
    variant: RegistrationVariant,
  ): { options: SelectedOption[]; errors: FieldError[] } {
    const options: SelectedOption[] = [];
    const errors: FieldError[] = [];

    for (const optionId of optionIds) {
      const resolution = this.deps.catalogue.resolve(optionId, variant);
      if (resolution.ok) {
        options.push(resolution.option);
        continue;
      }
      errors.push({
        field: 'selectedOptionIds',
        code:
          resolution.reason === 'unknown'
            ? 'unknown_option'
            : resolution.reason === 'inactive'
              ? 'inactive_option'
              : 'option_not_available',
        message:
          resolution.reason === 'unknown'
            ? `Unknown conference option: "${optionId}".`
            : resolution.reason === 'inactive'
              ? `The conference option "${optionId}" is no longer available.`
              : `The conference option "${optionId}" is not available for this registration form.`,
      });
    }

    return { options, errors };
  }

  private generateUniqueReference(): string {
    for (let attempt = 0; attempt < MAX_REFERENCE_ATTEMPTS; attempt += 1) {
      const reference = generateReference(this.now());
      if (!this.deps.repository.existsByReference(reference)) {
        return reference;
      }
    }
    throw new PersistenceError('A unique registration reference could not be generated.');
  }

  /**
   * Write the database row and the JSON backup atomically.
   *
   * The backup write happens inside the database transaction: if the file cannot be
   * written the insert is rolled back, and if the commit fails the file is removed. The
   * caller may therefore treat a returned registration as fully durable (AC-005-04).
   */
  private persist(registration: Registration): void {
    const document = this.deps.backupStore.buildDocument(
      registration,
      this.deps.catalogue.conferenceName,
    );
    const fileName = this.deps.backupStore.fileNameFor(registration.reference);
    let fileWritten = false;

    try {
      this.deps.repository.insertWithinTransaction(registration, fileName, () => {
        this.deps.backupStore.write(document);
        fileWritten = true;
      });
    } catch (cause) {
      if (fileWritten) {
        try {
          this.deps.backupStore.remove(registration.reference);
        } catch (removalCause) {
          this.deps.logger.error(
            { reference: registration.reference, err: removalCause },
            'failed to remove the backup file of a rolled-back registration',
          );
        }
      }
      this.deps.logger.error(
        { reference: registration.reference, err: cause },
        'registration could not be stored; no partial data remains',
      );
      throw new PersistenceError('The registration could not be stored.', { cause });
    }
  }

  /**
   * Send the participant confirmation and the organizer notification.
   *
   * Each send is independent and each failure is swallowed after logging: the
   * registration is already durable, and a mail outage must not lose it or tell the
   * participant it failed (AC-005-05, AC-006-04, AC-007-05).
   */
  private async dispatchEmails(registration: Registration): Promise<void> {
    const conferenceName = this.deps.catalogue.conferenceName;

    try {
      const message = composeParticipantConfirmation(registration, conferenceName);
      await this.deps.mail.send({
        to: [registration.participant.email],
        subject: message.subject,
        text: message.text,
        html: message.html,
      });
      this.deps.logger.info({ reference: registration.reference }, 'participant confirmation sent');
    } catch (cause) {
      this.deps.logger.error(
        { reference: registration.reference, err: cause },
        'participant confirmation email failed; the registration remains stored',
      );
    }

    try {
      const message = composeOrganizerNotification(registration, conferenceName);
      // Read the stored file back so the attachment is byte-identical to the backup on
      // persistent storage (AC-007-03).
      const attachment = this.deps.backupStore.readRaw(registration.reference);
      await this.deps.mail.send({
        to: [...this.deps.organizerRecipients],
        subject: message.subject,
        text: message.text,
        html: message.html,
        replyTo: registration.participant.email,
        attachments: [
          {
            filename: this.deps.backupStore.fileNameFor(registration.reference),
            content: attachment,
            contentType: 'application/json',
          },
        ],
      });
      this.deps.logger.info({ reference: registration.reference }, 'organizer notification sent');
    } catch (cause) {
      this.deps.logger.error(
        { reference: registration.reference, err: cause },
        'organizer notification email failed; the registration remains stored',
      );
    }
  }
}
