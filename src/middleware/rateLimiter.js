const config = require('../../config');

class MemoryRateLimiter {
  constructor(windowMs = config.rateLimit.windowMs, maxRequests = config.rateLimit.maxRequests) {
    this.windowMs = windowMs;
    this.maxRequests = maxRequests;
    this.requests = new Map();

    // Clean up stale entries every 2 minutes
    this.cleanupTimer = setInterval(() => {
      this.cleanup();
    }, 2 * 60 * 1000);
    if (this.cleanupTimer.unref) {
      this.cleanupTimer.unref();
    }
  }

  cleanup() {
    const now = Date.now();
    for (const [key, timestamps] of this.requests.entries()) {
      const valid = timestamps.filter(t => now - t < this.windowMs);
      if (valid.length === 0) {
        this.requests.delete(key);
      } else {
        this.requests.set(key, valid);
      }
    }
  }

  reset() {
    this.requests.clear();
  }

  middleware() {
    return (req, res, next) => {
      // In test mode or when disabled, allow bypass if requested
      if (config.nodeEnv === 'test' && req.headers['x-bypass-rate-limit']) {
        return next();
      }

      const ip = req.ip || req.connection.remoteAddress || 'unknown';
      const now = Date.now();
      const userRequests = this.requests.get(ip) || [];

      // Filter timestamps within window
      const recentRequests = userRequests.filter(t => now - t < this.windowMs);

      if (recentRequests.length >= this.maxRequests) {
        const oldest = recentRequests[0];
        const retryAfterSeconds = Math.ceil((this.windowMs - (now - oldest)) / 1000);
        res.setHeader('Retry-After', retryAfterSeconds.toString());
        return res.status(429).json({
          success: false,
          error: 'Too many registration attempts from this IP. Please try again later.',
          retryAfter: retryAfterSeconds
        });
      }

      recentRequests.push(now);
      this.requests.set(ip, recentRequests);
      next();
    };
  }
}

const defaultLimiter = new MemoryRateLimiter();

module.exports = {
  MemoryRateLimiter,
  defaultLimiter
};
