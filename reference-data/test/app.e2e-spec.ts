import { Test, TestingModule } from '@nestjs/testing';
import { INestApplication } from '@nestjs/common';
import request from 'supertest';
import { AppModule } from './../src/app.module';

describe('Reference data API (e2e)', () => {
  let app: INestApplication;

  beforeAll(async () => {
    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();

    app = moduleFixture.createNestApplication();
    await app.init();
  });

  afterAll(async () => {
    await app.close();
  });

  it('/stocks (GET)', () => {
    return request(app.getHttpServer())
      .get('/stocks')
      .expect(200)
      .expect(({ body }) => {
        expect(Array.isArray(body)).toBe(true);
        expect(body).toHaveLength(505);
        expect(body[0]).toEqual({ ticker: 'MMM', companyName: '3M' });
      });
  });

  it('/stocks/MSFT (GET)', () => {
    return request(app.getHttpServer())
      .get('/stocks/MSFT')
      .expect(200)
      .expect({ ticker: 'MSFT', companyName: 'Microsoft' });
  });

  it('/stocks/BADTICKER (GET)', () => {
    return request(app.getHttpServer()).get('/stocks/BADTICKER').expect(404);
  });

  it('/health (GET)', () => {
    return request(app.getHttpServer()).get('/health').expect(200);
  });
});
