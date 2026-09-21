/**
 * Excel export use case (specification § 8, US-008).
 */
import type { RegistrationRepository } from '../infrastructure/db/registrationRepository.js';
import { buildRegistrationsWorkbook } from '../infrastructure/excel/excelExporter.js';

export interface ExportResult {
  readonly fileName: string;
  readonly content: Buffer;
  readonly registrationCount: number;
}

export class ExportService {
  private readonly repository: RegistrationRepository;
  private readonly now: () => Date;

  constructor(repository: RegistrationRepository, now: () => Date = () => new Date()) {
    this.repository = repository;
    this.now = now;
  }

  /**
   * Build the workbook from the current contents of the database, so every registration
   * accepted before the request is included (AC-008-04).
   */
  async buildExport(): Promise<ExportResult> {
    const registrations = this.repository.findAll();
    const content = await buildRegistrationsWorkbook(registrations);
    return {
      fileName: `registrations-${timestampSuffix(this.now())}.xlsx`,
      content,
      registrationCount: registrations.length,
    };
  }
}

function timestampSuffix(now: Date): string {
  return now.toISOString().replace(/[-:]/gu, '').replace(/\..*$/u, '').replace('T', '-');
}
