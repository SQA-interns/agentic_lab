const express = require('express');
const router = express.Router();
const registrationRepository = require('../db/registrationRepository');
const exportService = require('../services/exportService');

/**
 * GET /api/admin/registrations
 * Returns all registrations.
 */
router.get('/registrations', (req, res, next) => {
  try {
    const list = registrationRepository.findAll();
    res.json({
      success: true,
      total: list.length,
      data: list
    });
  } catch (err) {
    next(err);
  }
});

/**
 * GET /api/admin/export/excel
 * Generates and downloads the Excel file.
 */
router.get('/export/excel', async (req, res, next) => {
  try {
    const buffer = await exportService.generateRegistrationsExcelBuffer();
    const dateStr = new Date().toISOString().split('T')[0];
    const filename = `registracije_konferenca_${dateStr}.xlsx`;

    res.setHeader('Content-Type', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
    res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
    res.setHeader('Content-Length', buffer.length);
    res.send(buffer);
  } catch (err) {
    next(err);
  }
});

module.exports = router;
