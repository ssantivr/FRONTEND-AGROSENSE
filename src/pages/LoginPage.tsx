import { useState, type FormEvent } from 'react';
import type { DataSource } from '../data/dataSource';
import { DEMO_EMAIL, DEMO_PASSWORD } from '../data/mockData';
import type { SessionUser } from '../types';
import { errorMessage } from '../useAsync';

interface Props {
  dataSource: DataSource;
  demoMode: boolean;
  onLogin: (user: SessionUser) => void;
}

export function LoginPage({ dataSource, demoMode, onLogin }: Props) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!email.trim() || !password) {
      setError('Ingresa tu correo y tu contraseña.');
      return;
    }
    await signIn(email.trim(), password);
  }

  async function signIn(loginEmail: string, loginPassword: string) {
    setSubmitting(true);
    setError(null);
    try {
      onLogin(await dataSource.login(loginEmail, loginPassword));
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="login">
      <form className="card login-card" onSubmit={handleSubmit} noValidate>
        <h1>AgroSense</h1>
        <p className="muted">Monitoreo de cultivos y riego inteligente.</p>

        <label htmlFor="email">Correo electrónico</label>
        <input
          id="email"
          type="email"
          autoComplete="username"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
        />

        <label htmlFor="password">Contraseña</label>
        <input
          id="password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />

        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}

        <button type="submit" className="button" disabled={submitting}>
          {submitting ? 'Ingresando…' : 'Iniciar sesión'}
        </button>

        {demoMode && (
          <>
            <button
              type="button"
              className="button secondary"
              disabled={submitting}
              onClick={() => signIn(DEMO_EMAIL, DEMO_PASSWORD)}
            >
              Entrar con la cuenta de demostración
            </button>
            <p className="muted demo-hint">
              Modo demostración, con datos de ejemplo: aún no hay cuentas reales. También puedes escribir{' '}
              <code>{DEMO_EMAIL}</code> y la contraseña <code>{DEMO_PASSWORD}</code>.
            </p>
          </>
        )}
      </form>
    </main>
  );
}
