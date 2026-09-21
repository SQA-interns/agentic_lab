/**
 * Anti-bot honeypot middleware.
 * Verifies that hidden honeypot fields ('honeypot', 'website_hp') are completely empty.
 */
function antiBotHoneypot(req, res, next) {
  if (req.body) {
    const honeypot = req.body.honeypot || req.body.website_hp;
    if (typeof honeypot === 'string' && honeypot.trim().length > 0) {
      return res.status(400).json({
        success: false,
        error: 'Automated submission rejected.',
        details: [{ field: 'honeypot', message: 'Honeypot field must remain empty.' }]
      });
    }
  }
  next();
}

module.exports = antiBotHoneypot;
