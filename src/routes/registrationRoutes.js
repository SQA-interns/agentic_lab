const express = require('express');
const router = express.Router();
const registrationService = require('../services/registrationService');
const antiBotHoneypot = require('../middleware/antiBot');
const { defaultLimiter } = require('../middleware/rateLimiter');

router.post('/register', defaultLimiter.middleware(), antiBotHoneypot, async (req, res, next) => {
  try {
    const meta = {
      ipAddress: req.ip || req.connection.remoteAddress || 'unknown',
      userAgent: req.get('User-Agent') || 'unknown'
    };

    const result = await registrationService.processRegistration(req.body, meta);

    return res.status(result.statusCode).json(result);
  } catch (err) {
    next(err);
  }
});

module.exports = router;
