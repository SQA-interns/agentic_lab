/**
 * Health endpoint (specification § 4.1). Used by the container healthcheck.
 */
import { Router } from 'express';

export function healthRouter(): Router {
  const router = Router();

  router.get('/health', (_req, res) => {
    res.json({ status: 'ok', uptimeSeconds: Math.floor(process.uptime()) });
  });

  return router;
}
