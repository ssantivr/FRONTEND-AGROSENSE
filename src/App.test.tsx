import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from './App';
import { createMockDataSource } from './data/dataSource';
import { DEMO_EMAIL, DEMO_PASSWORD } from './data/mockData';

function renderApp() {
  render(<App dataSource={createMockDataSource()} demoMode />);
  return userEvent.setup();
}

async function signIn(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Correo electrónico'), DEMO_EMAIL);
  await user.type(screen.getByLabelText('Contraseña'), DEMO_PASSWORD);
  await user.click(screen.getByRole('button', { name: 'Iniciar sesión' }));
}

describe('App', () => {
  it('requires both credentials before calling the data source', async () => {
    const user = renderApp();
    await user.click(screen.getByRole('button', { name: 'Iniciar sesión' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Ingresa tu correo y tu contraseña.');
  });

  it('rejects wrong credentials', async () => {
    const user = renderApp();
    await user.type(screen.getByLabelText('Correo electrónico'), DEMO_EMAIL);
    await user.type(screen.getByLabelText('Contraseña'), 'wrong');
    await user.click(screen.getByRole('button', { name: 'Iniciar sesión' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Correo o contraseña incorrectos.');
  });

  it('signs in with the demo account button', async () => {
    const user = renderApp();
    await user.click(screen.getByRole('button', { name: 'Entrar con la cuenta de demostración' }));
    expect(await screen.findByRole('heading', { name: 'Panel' })).toBeInTheDocument();
  });

  it('shows the dashboard after signing in and returns to login on sign out', async () => {
    const user = renderApp();
    await signIn(user);

    expect(await screen.findByRole('heading', { name: 'Panel' })).toBeInTheDocument();
    const indicators = await screen.findByRole('region', { name: 'Indicadores' });
    expect(within(indicators).getByText('Alertas sin atender').nextSibling).toHaveTextContent('3');
    expect(await screen.findByRole('img', { name: /Humedad del suelo — Café/ })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Cerrar sesión' }));
    expect(screen.getByRole('button', { name: 'Iniciar sesión' })).toBeInTheDocument();
  });

  it('acknowledges an alert and removes it from the open list', async () => {
    const user = renderApp();
    await signIn(user);
    await user.click(await screen.findByRole('button', { name: 'Alertas' }));

    const buttons = await screen.findAllByRole('button', { name: 'Marcar como atendida' });
    expect(buttons).toHaveLength(3);
    await user.click(buttons[0]);

    expect(await screen.findAllByRole('button', { name: 'Marcar como atendida' })).toHaveLength(2);
  });

  it('validates the duration and registers a manual irrigation', async () => {
    const user = renderApp();
    await signIn(user);
    await user.click(await screen.findByRole('button', { name: 'Riego' }));

    const duration = await screen.findByLabelText('Duración (minutos)');
    await user.clear(duration);
    await user.type(duration, '0');
    await user.click(screen.getByRole('button', { name: 'Iniciar riego' }));
    expect(screen.getByRole('alert')).toHaveTextContent('entre 1 y 240 minutos');

    await user.clear(duration);
    await user.type(duration, '15');
    await user.click(screen.getByRole('button', { name: 'Iniciar riego' }));
    expect(await screen.findByText('En curso')).toBeInTheDocument();
  });
});
