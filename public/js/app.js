// Common Frontend Application Logic

document.addEventListener('DOMContentLoaded', () => {
  initRegistrationPage();
});

async function initRegistrationPage() {
  const form = document.getElementById('registrationForm');
  if (!form) return;

  const registrationType = form.dataset.registrationType || 'external';

  // 1. Fetch configurable conference options
  await loadConferenceOptions();

  // 2. Setup form submission handler
  form.addEventListener('submit', (e) => handleFormSubmit(e, form, registrationType));

  // 3. Setup blur whitespace trimmer on text fields
  form.querySelectorAll('input[type="text"], input[type="email"]').forEach(input => {
    input.addEventListener('blur', () => {
      input.value = input.value.trim();
    });
  });
}

/**
 * Loads conference options from the backend and builds category checklists.
 */
async function loadConferenceOptions() {
  const container = document.getElementById('configurableOptionsContainer');
  if (!container) return;

  try {
    const res = await fetch('/api/conference-options');
    if (!res.ok) throw new Error('Napaka pri nalaganju konferenčnih aktivnosti.');
    const json = await res.json();
    const data = json.data || {};

    const categoryTitles = {
      workshops: 'Delavnice (Workshops)',
      events: 'Dogodki in družabni program',
      meals: 'Izbira prehrane',
      other: 'Druge opcijske aktivnosti'
    };

    let html = '';

    for (const [categoryKey, title] of Object.entries(categoryTitles)) {
      const items = data[categoryKey] || [];
      if (items.length === 0) continue;

      html += `
        <div class="options-category">
          <div class="options-category-title">${title}</div>
          <div class="options-list">
      `;

      items.forEach(item => {
        html += `
          <label class="checkbox-label">
            <input type="checkbox" name="selectedOptions" value="${escapeHtml(item.id)}">
            <span>${escapeHtml(item.name)}</span>
          </label>
        `;
      });

      html += `
          </div>
        </div>
      `;
    }

    container.innerHTML = html || '<p class="text-muted">Trenutno ni na voljo posebnih izbirnih aktivnosti.</p>';
  } catch (err) {
    console.error('Error loading conference options:', err);
    container.innerHTML = '<p class="field-error" style="display:block;">Možnosti programa trenutno niso dosegljive.</p>';
  }
}

/**
 * Handles form submission with client-side validation and backend POST.
 */
async function handleFormSubmit(e, form, registrationType) {
  e.preventDefault();
  hideAlerts();
  clearFieldErrors();

  const submitBtn = document.getElementById('submitBtn');
  const alertBox = document.getElementById('errorAlert');

  // Collect and trim values
  const payload = {
    registrationType,
    firstName: form.elements['firstName']?.value.trim() || '',
    lastName: form.elements['lastName']?.value.trim() || '',
    email: form.elements['email']?.value.trim() || '',
    privacyConsent: form.elements['privacyConsent']?.checked || false,
    honeypot: form.elements['website_hp']?.value || '',
    selectedOptions: []
  };

  // Collect selected checkboxes
  const optionCheckboxes = form.querySelectorAll('input[name="selectedOptions"]:checked');
  optionCheckboxes.forEach(cb => payload.selectedOptions.push(cb.value));

  if (registrationType === 'external') {
    payload.organization = form.elements['organization']?.value.trim() || '';
  } else if (registrationType === 'student') {
    payload.studyInstitution = form.elements['studyInstitution']?.value.trim() || '';
    payload.studyProgramme = form.elements['studyProgramme']?.value.trim() || '';
    payload.studentId = form.elements['studentId']?.value.trim() || '';
  }

  // Client-side validation
  let hasClientErrors = false;

  if (!payload.firstName) {
    showFieldError('firstName', 'Vnesite ime.');
    hasClientErrors = true;
  }
  if (!payload.lastName) {
    showFieldError('lastName', 'Vnesite priimek.');
    hasClientErrors = true;
  }
  if (!payload.email) {
    showFieldError('email', 'Vnesite e-poštni naslov.');
    hasClientErrors = true;
  } else if (!isValidEmail(payload.email)) {
    showFieldError('email', 'Vnesite veljaven e-poštni naslov.');
    hasClientErrors = true;
  }

  if (registrationType === 'external' && !payload.organization) {
    showFieldError('organization', 'Vnesite organizacijo ali ustanovo.');
    hasClientErrors = true;
  }

  if (registrationType === 'student') {
    if (!payload.studyInstitution) {
      showFieldError('studyInstitution', 'Vnesite študijsko ustanovo.');
      hasClientErrors = true;
    }
    if (!payload.studyProgramme) {
      showFieldError('studyProgramme', 'Vnesite študijski program.');
      hasClientErrors = true;
    }
    if (!payload.studentId) {
      showFieldError('studentId', 'Vnesite vpisno številko.');
      hasClientErrors = true;
    }
  }

  if (!payload.privacyConsent) {
    showFieldError('privacyConsent', 'Za nadaljevanje je obvezno potrditi soglasje za obdelavo podatkov.');
    hasClientErrors = true;
  }

  if (hasClientErrors) {
    showAlert('errorAlert', 'Prosimo, preverite označena polja in odpravite napake.');
    return;
  }

  // Disable submit button and show loading state
  if (submitBtn) {
    submitBtn.disabled = true;
    submitBtn.textContent = 'Oddajanje prijave...';
  }

  try {
    const response = await fetch('/api/register', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(payload)
    });

    const result = await response.json();

    if (response.status === 201 && result.success) {
      // Successful registration: Show confirmation
      displayConfirmation(result.data);
    } else {
      // Backend rejected
      const errorMsg = result.error || 'Prijava ni bila uspešna.';
      const detailsMsg = (result.details && Array.isArray(result.details))
        ? result.details.map(d => d.message).join('<br>')
        : '';
      showAlert('errorAlert', `${errorMsg}${detailsMsg ? '<br>' + detailsMsg : ''}`);

      if (result.details && Array.isArray(result.details)) {
        result.details.forEach(item => {
          if (item.field) showFieldError(item.field, item.message);
        });
      }
    }
  } catch (err) {
    console.error('Submission network error:', err);
    showAlert('errorAlert', 'Prišlo je do omrežne napake. Prosimo, poskusite znova kasneje.');
  } finally {
    if (submitBtn) {
      submitBtn.disabled = false;
      submitBtn.textContent = 'Oddaj prijavo';
    }
  }
}

/**
 * Renders the confirmation card with registration data.
 */
function displayConfirmation(data) {
  const formCard = document.getElementById('formCard');
  const confirmCard = document.getElementById('confirmationCard');
  if (!confirmCard) return;

  if (formCard) formCard.style.display = 'none';
  confirmCard.classList.add('active');

  const regIdEl = document.getElementById('confRegId');
  const nameEl = document.getElementById('confName');
  const emailEl = document.getElementById('confEmail');
  const orgEl = document.getElementById('confOrg');
  const optionsEl = document.getElementById('confOptions');

  if (regIdEl) regIdEl.textContent = data.registrationId;
  if (nameEl) nameEl.textContent = `${data.firstName} ${data.lastName}`;
  if (emailEl) emailEl.textContent = data.email;

  if (orgEl) {
    const institution = data.registrationType === 'student'
      ? `${data.studyInstitution} (${data.studyProgramme}, ${data.studentId})`
      : data.organization;
    orgEl.textContent = institution || '-';
  }

  if (optionsEl) {
    if (data.selectedOptions && data.selectedOptions.length > 0) {
      optionsEl.innerHTML = data.selectedOptions
        .map(o => `<li>${escapeHtml(o.name || o.id)}</li>`)
        .join('');
    } else {
      optionsEl.innerHTML = '<li><em>Brez dodatnih izbir</em></li>';
    }
  }

  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function showFieldError(fieldName, message) {
  const group = document.querySelector(`[data-field-group="${fieldName}"]`);
  if (group) {
    group.classList.add('input-has-error');
    const errText = group.querySelector('.field-error');
    if (errText) errText.textContent = message;
  }
}

function clearFieldErrors() {
  document.querySelectorAll('.input-has-error').forEach(el => {
    el.classList.remove('input-has-error');
  });
}

function showAlert(id, message) {
  const el = document.getElementById(id);
  if (el) {
    el.innerHTML = message;
    el.style.display = 'block';
  }
}

function hideAlerts() {
  document.querySelectorAll('.alert').forEach(el => {
    el.style.display = 'none';
  });
}

function isValidEmail(email) {
  const regex = /^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$/;
  return regex.test(email);
}

function escapeHtml(str) {
  if (typeof str !== 'string') return '';
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}
