const fs = require('node:fs');
const config = require('../../config');

class OptionsService {
  constructor(optionsFilePath = config.conferenceOptionsPath) {
    this.optionsFilePath = optionsFilePath;
    this.optionsCache = null;
  }

  /**
   * Loads options from configuration file.
   */
  loadOptions() {
    try {
      const raw = fs.readFileSync(this.optionsFilePath, 'utf-8');
      this.optionsCache = JSON.parse(raw);
      return this.optionsCache;
    } catch (err) {
      throw new Error(`Failed to load conference options from ${this.optionsFilePath}: ${err.message}`);
    }
  }

  /**
   * Gets all options (including active status).
   */
  getAllOptions() {
    return this.loadOptions();
  }

  /**
   * Gets only active options categorized for client form presentation.
   */
  getActiveOptions() {
    const all = this.loadOptions();
    const active = {};
    for (const [category, items] of Object.entries(all)) {
      if (Array.isArray(items)) {
        active[category] = items.filter(item => item.active === true);
      }
    }
    return active;
  }

  /**
   * Flattens all options into a Map for fast ID lookup.
   * @returns {Map<string, { id: string, name: string, category: string, active: boolean }>}
   */
  getOptionsMap() {
    const all = this.loadOptions();
    const map = new Map();
    for (const [category, items] of Object.entries(all)) {
      if (Array.isArray(items)) {
        for (const item of items) {
          map.set(item.id, {
            ...item,
            category
          });
        }
      }
    }
    return map;
  }

  /**
   * Validates a list of submitted option IDs.
   * @param {string[]} selectedOptionIds 
   * @returns {{ valid: boolean, errors: string[], resolvedOptions: Array<{id: string, name: string, category: string}> }}
   */
  validateSelectedOptions(selectedOptionIds) {
    if (!selectedOptionIds) {
      return { valid: true, errors: [], resolvedOptions: [] };
    }

    if (!Array.isArray(selectedOptionIds)) {
      return {
        valid: false,
        errors: ['selectedOptions must be an array of option identifiers.'],
        resolvedOptions: []
      };
    }

    const map = this.getOptionsMap();
    const errors = [];
    const resolvedOptions = [];

    for (const id of selectedOptionIds) {
      if (typeof id !== 'string') {
        errors.push(`Invalid option ID format: ${JSON.stringify(id)}`);
        continue;
      }
      const option = map.get(id);
      if (!option) {
        errors.push(`Selected option '${id}' does not exist.`);
      } else if (!option.active) {
        errors.push(`Selected option '${id}' (${option.name}) is inactive or no longer available.`);
      } else {
        resolvedOptions.push({
          id: option.id,
          name: option.name,
          category: option.category
        });
      }
    }

    return {
      valid: errors.length === 0,
      errors,
      resolvedOptions
    };
  }
}

module.exports = new OptionsService();
