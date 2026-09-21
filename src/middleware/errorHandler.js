/**
 * Centralized error handler middleware.
 */
function errorHandler(err, req, res, next) {
  console.error('[Error Handler]', err);

  if (err.type === 'entity.too.large') {
    return res.status(413).json({
      success: false,
      error: 'Payload too large. Maximum allowed size is 250KB.'
    });
  }

  const statusCode = err.statusCode || 500;
  const message = err.message || 'Internal server error';

  res.status(statusCode).json({
    success: false,
    error: message
  });
}

module.exports = errorHandler;
