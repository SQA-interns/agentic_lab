/**
 * Trims leading and trailing whitespace while preserving internal spacing and Unicode characters.
 * @param {string} val 
 * @returns {string}
 */
function trimString(val) {
  if (typeof val !== 'string') return '';
  return val.trim();
}

/**
 * Sanitizes input text by removing potentially harmful HTML tags and scripts.
 * Preserves normal text, punctuation, and Unicode/Slovenian characters.
 * @param {string} val 
 * @returns {string}
 */
function sanitizeText(val) {
  if (typeof val !== 'string') return '';
  const trimmed = val.trim();
  // Strip script tags and HTML elements, keep text
  return trimmed
    .replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, '')
    .replace(/<[^>]+>/g, '');
}

/**
 * Validates standard email address format using RFC 5322 compliant pattern.
 * @param {string} email 
 * @returns {boolean}
 */
function isValidEmail(email) {
  if (typeof email !== 'string') return false;
  const trimmed = email.trim();
  // Comprehensive RFC 5322 regex matching standard email structure
  const emailRegex = /^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$/;
  return emailRegex.test(trimmed);
}

module.exports = {
  trimString,
  sanitizeText,
  isValidEmail
};
