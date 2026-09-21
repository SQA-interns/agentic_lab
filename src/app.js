const express = require('express');
const path = require('node:path');
const securityHeaders = require('./middleware/securityHeaders');
const errorHandler = require('./middleware/errorHandler');
const optionsRoutes = require('./routes/optionsRoutes');
const registrationRoutes = require('./routes/registrationRoutes');
const adminRoutes = require('./routes/adminRoutes');

function createApp() {
  const app = express();

  // Trust reverse proxy for correct client IP detection in container/proxy environments
  app.set('trust proxy', true);

  // Security headers
  app.use(securityHeaders);

  // JSON body parser with size limit for security
  app.use(express.json({ limit: '250kb' }));
  app.use(express.urlencoded({ extended: false, limit: '250kb' }));

  // Static frontend files
  const publicDir = path.join(__dirname, '..', 'public');
  app.use(express.static(publicDir));

  // Health check
  app.get('/api/health', (req, res) => {
    res.json({
      status: 'ok',
      uptime: process.uptime(),
      timestamp: new Date().toISOString()
    });
  });

  // REST API routes
  app.use('/api', optionsRoutes);
  app.use('/api', registrationRoutes);
  app.use('/api/admin', adminRoutes);

  // Centralized error handler
  app.use(errorHandler);

  return app;
}

module.exports = createApp;
