const express = require('express');
const router = express.Router();
const optionsService = require('../services/optionsService');

router.get('/conference-options', (req, res, next) => {
  try {
    const activeOptions = optionsService.getActiveOptions();
    res.json({
      success: true,
      data: activeOptions
    });
  } catch (err) {
    next(err);
  }
});

module.exports = router;
