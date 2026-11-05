import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import * as request from 'supertest';
import { AppModule } from './app.module';
import { isSwaggerEnabled, setupSwagger } from './swagger';

describe('swagger exposure', () => {
  let app: INestApplication;

  async function start(env: NodeJS.ProcessEnv): Promise<void> {
    const moduleRef = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();
    app = moduleRef.createNestApplication();
    setupSwagger(app, env);
    await app.init();
  }

  afterEach(async () => {
    await app?.close();
  });

  it('is disabled when SWAGGER_ENABLED is unset', async () => {
    expect(isSwaggerEnabled({})).toBe(false);
    await start({});
    await request(app.getHttpServer()).get('/api').expect(404);
    await request(app.getHttpServer()).get('/api-json').expect(404);
  });

  it('is disabled for any value other than "true"', () => {
    expect(isSwaggerEnabled({ SWAGGER_ENABLED: 'false' })).toBe(false);
    expect(isSwaggerEnabled({ SWAGGER_ENABLED: '1' })).toBe(false);
  });

  it('is served at /api when SWAGGER_ENABLED=true', async () => {
    await start({ SWAGGER_ENABLED: 'true' });
    await request(app.getHttpServer()).get('/api-json').expect(200);
    const res = await request(app.getHttpServer()).get('/api/');
    expect(res.status).toBe(200);
  });
});
