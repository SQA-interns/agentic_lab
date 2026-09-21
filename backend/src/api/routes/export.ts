/**
 * Excel export endpoint (specification § 4.4, US-008).
 */
import { Router } from 'express';

import type { ExportService } from '../../application/exportService.js';
import type { AppConfig } from '../../config/env.js';
import type { Logger } from '../../infrastructure/logging/logger.js';
import { basicAuth } from '../middleware/basicAuth.js';
import { exportRateLimiter } from '../middleware/rateLimit.js';

const XLSX_CONTENT_TYPE =
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

export interface ExportRouterDependencies {
  readonly service: ExportService;
  readonly config: AppConfig;
  readonly logger: Logger;
}

export function exportRouter(deps: ExportRouterDependencies): Router {
  const router = Router();

  router.get(
    '/export/registrations.xlsx',
    exportRateLimiter(),
    basicAuth(deps.config.export.username, deps.config.export.password, 'Conference export'),
    async (_req, res) => {
      const result = await deps.service.buildExport();

      deps.logger.info({ registrationCount: result.registrationCount }, 'registration export built');

      res.setHeader('Content-Type', XLSX_CONTENT_TYPE);
      res.setHeader('Content-Disposition', `attachment; filename="${result.fileName}"`);
      res.setHeader('Content-Length', result.content.length);
      // The export contains personal data; no intermediary may retain a copy.
      res.setHeader('Cache-Control', 'no-store');
      res.status(200).send(result.content);
    },
  );

  return router;
}
