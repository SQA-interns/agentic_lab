const ExcelJS = require('exceljs');
const registrationRepository = require('../db/registrationRepository');
const optionsService = require('./optionsService');

class ExportService {
  /**
   * Generates Excel workbook buffer containing all registrations.
   * @returns {Promise<Buffer>}
   */
  async generateRegistrationsExcelBuffer() {
    const registrations = registrationRepository.findAll();
    const optionsMap = optionsService.getOptionsMap();

    const workbook = new ExcelJS.Workbook();
    workbook.creator = 'Konferenca Sistem';
    workbook.created = new Date();

    const sheet = workbook.addWorksheet('Prijave', {
      views: [{ state: 'frozen', xSplit: 0, ySplit: 1 }]
    });

    sheet.columns = [
      { header: 'ID prijave', key: 'uuid', width: 38 },
      { header: 'Datum in čas prijave', key: 'createdAt', width: 22 },
      { header: 'Tip udeleženca', key: 'typeLabel', width: 18 },
      { header: 'Ime', key: 'firstName', width: 16 },
      { header: 'Priimek', key: 'lastName', width: 18 },
      { header: 'E-pošta', key: 'email', width: 28 },
      { header: 'Organizacija / Ustanova', key: 'institution', width: 32 },
      { header: 'Študijski program', key: 'studyProgramme', width: 28 },
      { header: 'Vpisna številka', key: 'studentId', width: 16 },
      { header: 'Izbrane delavnice', key: 'workshops', width: 35 },
      { header: 'Izbrani dogodki', key: 'events', width: 35 },
      { header: 'Izbrana prehrana', key: 'meals', width: 25 },
      { header: 'Druge aktivnosti', key: 'other', width: 30 },
      { header: 'Soglasje GDPR', key: 'privacyConsent', width: 14 }
    ];

    // Style the header row
    const headerRow = sheet.getRow(1);
    headerRow.font = { bold: true, color: { argb: 'FFFFFFFF' } };
    headerRow.fill = {
      type: 'pattern',
      pattern: 'solid',
      fgColor: { argb: 'FF1E3A8A' }
    };
    headerRow.alignment = { vertical: 'middle', horizontal: 'center' };
    headerRow.height = 26;

    for (const reg of registrations) {
      const typeLabel = reg.registrationType === 'student' ? 'Študent' : 'Zunanji udeleženec';
      const institution = reg.registrationType === 'student' ? (reg.studyInstitution || '') : (reg.organization || '');

      // Group options by category
      const workshops = [];
      const events = [];
      const meals = [];
      const other = [];

      if (Array.isArray(reg.selectedOptions)) {
        for (const optId of reg.selectedOptions) {
          const item = optionsMap.get(optId);
          const optName = item ? item.name : optId;
          const category = item ? item.category : 'other';

          if (category === 'workshops') workshops.push(optName);
          else if (category === 'events') events.push(optName);
          else if (category === 'meals') meals.push(optName);
          else other.push(optName);
        }
      }

      sheet.addRow({
        uuid: reg.uuid,
        createdAt: reg.createdAt,
        typeLabel,
        firstName: reg.firstName,
        lastName: reg.lastName,
        email: reg.email,
        institution,
        studyProgramme: reg.studyProgramme || '-',
        studentId: reg.studentId || '-',
        workshops: workshops.join('; ') || '-',
        events: events.join('; ') || '-',
        meals: meals.join('; ') || '-',
        other: other.join('; ') || '-',
        privacyConsent: reg.privacyConsent ? 'DA' : 'NE'
      });
    }

    return await workbook.xlsx.writeBuffer();
  }
}

module.exports = new ExportService();
