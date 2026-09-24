import { useCallback, useEffect, useState } from 'react';
import { fetchFormToken, fetchOptions } from '../api/client';
import type { ConferenceOption, RegistrationType } from '../types';

interface FormBootstrap {
  options: ConferenceOption[] | null;
  token: string | null;
  loadError: boolean;
  refreshToken: () => void;
}

/** Loads the active options for the variant and an anti-automation form token. */
export function useFormBootstrap(type: RegistrationType): FormBootstrap {
  const [options, setOptions] = useState<ConferenceOption[] | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [loadError, setLoadError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    Promise.all([fetchOptions(type), fetchFormToken()])
      .then(([loadedOptions, loadedToken]) => {
        if (!cancelled) {
          setOptions(loadedOptions);
          setToken(loadedToken);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setLoadError(true);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [type]);

  const refreshToken = useCallback(() => {
    fetchFormToken()
      .then(setToken)
      .catch(() => {
        setToken(null);
      });
  }, []);

  return { options, token, loadError, refreshToken };
}
