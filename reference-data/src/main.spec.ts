describe('main bootstrap', () => {
  const originalEnv = process.env;
  let listen: jest.Mock;
  let create: jest.Mock;
  let setup: jest.Mock;

  function runMain(env: Record<string, string | undefined>) {
    process.env = { ...originalEnv, ...env };
    Object.keys(env).forEach(
      (k) => env[k] === undefined && delete process.env[k],
    );
    listen = jest.fn().mockResolvedValue(undefined);
    create = jest.fn().mockResolvedValue({ listen });
    setup = jest.fn();
    jest.isolateModules(() => {
      jest.doMock('@nestjs/core', () => ({ NestFactory: { create } }));
      jest.doMock('@nestjs/swagger', () => {
        const actual = jest.requireActual('@nestjs/swagger');
        return {
          ...actual,
          SwaggerModule: {
            createDocument: jest.fn().mockReturnValue({ openapi: '3.0.0' }),
            setup,
          },
        };
      });
      require('./main');
    });
    return new Promise((resolve) => setImmediate(resolve));
  }

  afterEach(() => {
    process.env = originalEnv;
    jest.resetModules();
    jest.dontMock('@nestjs/core');
    jest.dontMock('@nestjs/swagger');
  });

  it('listens on 18085 / 0.0.0.0 when no env overrides are set', async () => {
    await runMain({
      REFERENCE_DATA_SERVICE_PORT: undefined,
      REFERENCE_DATA_HOSTNAME: undefined,
    });
    expect(listen).toHaveBeenCalledWith(18085, '0.0.0.0');
  });

  it('uses REFERENCE_DATA_SERVICE_PORT and REFERENCE_DATA_HOSTNAME when provided', async () => {
    await runMain({
      REFERENCE_DATA_SERVICE_PORT: '9999',
      REFERENCE_DATA_HOSTNAME: '127.0.0.1',
    });
    expect(listen).toHaveBeenCalledWith(9999, '127.0.0.1');
  });

  it('falls back to the default port when REFERENCE_DATA_SERVICE_PORT is not numeric', async () => {
    await runMain({
      REFERENCE_DATA_SERVICE_PORT: 'abc',
      REFERENCE_DATA_HOSTNAME: undefined,
    });
    expect(listen).toHaveBeenCalledWith(18085, '0.0.0.0');
  });

  it('creates the app with CORS enabled and mounts Swagger docs at /api', async () => {
    await runMain({});
    expect(create).toHaveBeenCalledWith(expect.anything(), { cors: true });
    expect(setup).toHaveBeenCalledWith('api', expect.anything(), {
      openapi: '3.0.0',
    });
  });
});
