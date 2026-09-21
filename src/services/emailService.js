const nodemailer = require('nodemailer');
const config = require('../../config');

class EmailService {
  constructor() {
    this.sentEmails = []; // In-memory tracking for verification/testing
    this.transporter = this._createTransporter();
  }

  _createTransporter() {
    if (config.smtp && config.smtp.host && config.nodeEnv !== 'test') {
      return nodemailer.createTransport({
        host: config.smtp.host,
        port: config.smtp.port,
        secure: config.smtp.port === 465,
        auth: config.smtp.user ? {
          user: config.smtp.user,
          pass: config.smtp.pass
        } : undefined
      });
    }

    // Fallback test transport
    return {
      sendMail: async (mailOptions) => {
        this.sentEmails.push({
          ...mailOptions,
          sentAt: new Date().toISOString()
        });
        return {
          messageId: `mock_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`,
          response: 'Mock 250 Message accepted'
        };
      }
    };
  }

  /**
   * Dispatches confirmation email to participant.
   * @param {object} registration 
   * @param {Array<{id: string, name: string, category: string}>} resolvedOptions 
   */
  async sendParticipantConfirmation(registration, resolvedOptions = []) {
    const participantName = `${registration.firstName} ${registration.lastName}`;
    const subject = `Potrditev prijave na konferenco / Conference Registration Confirmation - ${registration.uuid}`;

    const optionsListHtml = resolvedOptions.length > 0
      ? `<ul>${resolvedOptions.map(o => `<li><strong>[${o.category}]</strong> ${o.name}</li>`).join('')}</ul>`
      : '<p><em>Ni izbranih posebnih aktivnosti.</em></p>';

    const optionsListText = resolvedOptions.length > 0
      ? resolvedOptions.map(o => `- [${o.category}] ${o.name}`).join('\n')
      : 'Ni izbranih posebnih aktivnosti.';

    const participantTypeLabel = registration.registrationType === 'student' ? 'Študent' : 'Zunanji udeleženec';
    const institutionDetails = registration.registrationType === 'student'
      ? `Študijska ustanova: ${registration.studyInstitution || '-'}\nŠtudijski program: ${registration.studyProgramme || '-'}\nVpisna številka: ${registration.studentId || '-'}`
      : `Organizacija / Ustanova: ${registration.organization || '-'}`;

    const textBody = `Spoštovani ${participantName},

Hvala za vašo prijavo na konferenco.
Vaša prijava je bila uspešno zabeležena v našem sistemu.

Podrobnosti prijave:
------------------------------------------
ID prijave: ${registration.uuid}
Tip udeleženca: ${participantTypeLabel}
Ime in priimek: ${participantName}
E-pošta: ${registration.email}
${institutionDetails}
Datum in čas prijave: ${registration.createdAt}

Izbrane konferenčne aktivnosti:
${optionsListText}

V primeru vprašanj se lahko obrnete na organizacijski odbor: ${config.organizerEmail}.

Lep pozdrav,
Organizacijski odbor konference
`;

    const htmlBody = `
      <div style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto;">
        <h2 style="color: #1e3a8a;">Potrditev prijave na konferenco</h2>
        <p>Spoštovani <strong>${participantName}</strong>,</p>
        <p>vaša prijava na konferenco je bila uspešno zabeležena.</p>
        <div style="background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 16px; margin: 16px 0;">
          <h3 style="margin-top: 0; color: #1e293b;">Podrobnosti prijave</h3>
          <p><strong>Referenčna številka (ID):</strong> <code>${registration.uuid}</code></p>
          <p><strong>Tip prijave:</strong> ${participantTypeLabel}</p>
          <p><strong>Ime in priimek:</strong> ${participantName}</p>
          <p><strong>E-pošta:</strong> ${registration.email}</p>
          ${registration.registrationType === 'student' 
            ? `<p><strong>Ustanova:</strong> ${registration.studyInstitution || '-'}</p>
               <p><strong>Program:</strong> ${registration.studyProgramme || '-'}</p>
               <p><strong>Vpisna številka:</strong> ${registration.studentId || '-'}</p>`
            : `<p><strong>Organizacija:</strong> ${registration.organization || '-'}</p>`
          }
          <p><strong>Čas prijave:</strong> ${registration.createdAt}</p>
        </div>
        <h3>Izbrane aktivnosti</h3>
        ${optionsListHtml}
        <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 24px 0;" />
        <p style="font-size: 0.9em; color: #64748b;">
          Vprašanja glede prijave ali programa lahko naslovite na <a href="mailto:${config.organizerEmail}">${config.organizerEmail}</a>.
        </p>
      </div>
    `;

    try {
      await this.transporter.sendMail({
        from: config.smtp.from,
        to: registration.email,
        subject,
        text: textBody,
        html: htmlBody
      });
      return true;
    } catch (err) {
      console.error(`[EmailService] Failed to send participant confirmation to ${registration.email}:`, err.message);
      return false;
    }
  }

  /**
   * Dispatches notification email to conference organizers with JSON attachment.
   * @param {object} registration 
   * @param {Array<{id: string, name: string, category: string}>} resolvedOptions 
   * @param {string} jsonString 
   */
  async sendOrganizerNotification(registration, resolvedOptions = [], jsonString = '') {
    const participantName = `${registration.firstName} ${registration.lastName}`;
    const subject = `Nova prijava na konferenco: ${participantName} (${registration.registrationType})`;

    const textBody = `Organizacijski odbor,

Prejeta je bila nova prijava na konferenco:

ID: ${registration.uuid}
Tip: ${registration.registrationType}
Ime in priimek: ${participantName}
E-pošta: ${registration.email}
Čas: ${registration.createdAt}

V prilogi sporočila je celotna JSON datoteka prijave (${registration.uuid}.json).
`;

    const htmlBody = `
      <div style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
        <h3>Prejeta nova prijava na konferenco</h3>
        <p><strong>ID:</strong> ${registration.uuid}</p>
        <p><strong>Ime in priimek:</strong> ${participantName}</p>
        <p><strong>Tip:</strong> ${registration.registrationType}</p>
        <p><strong>E-pošta:</strong> ${registration.email}</p>
        <p>Podatki so shranjeni v relacijski bazi in priloženi kot JSON datoteka.</p>
      </div>
    `;

    const attachmentFilename = `registration_${registration.uuid}.json`;
    const attachments = [{
      filename: attachmentFilename,
      content: jsonString || JSON.stringify(registration, null, 2),
      contentType: 'application/json'
    }];

    try {
      await this.transporter.sendMail({
        from: config.smtp.from,
        to: config.organizerEmail,
        subject,
        text: textBody,
        html: htmlBody,
        attachments
      });
      return true;
    } catch (err) {
      console.error(`[EmailService] Failed to send organizer notification to ${config.organizerEmail}:`, err.message);
      return false;
    }
  }

  /**
   * Clears sent emails tracking (useful in tests).
   */
  clearSentEmails() {
    this.sentEmails = [];
  }
}

module.exports = new EmailService();
