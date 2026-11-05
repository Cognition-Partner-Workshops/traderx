import { Test, TestingModule } from '@nestjs/testing';
import { INestApplication } from '@nestjs/common';
import * as request from 'supertest';
import { AppModule } from './app.module';

describe('Reference data HTTP API (AppModule)', () => {
    let app: INestApplication;

    beforeAll(async () => {
        const moduleFixture: TestingModule = await Test.createTestingModule({
            imports: [AppModule]
        }).compile();

        app = moduleFixture.createNestApplication();
        await app.init();
    });

    afterAll(async () => {
        await app.close();
    });

    describe('GET /stocks', () => {
        it('returns 200 with every S&P 500 stock as {ticker, companyName}', async () => {
            const res = await request(app.getHttpServer())
                .get('/stocks')
                .expect(200)
                .expect('Content-Type', /application\/json/);

            expect(res.body).toHaveLength(505);
            expect(res.body[0]).toEqual({ ticker: 'MMM', companyName: '3M' });
            expect(res.body[504]).toEqual({ ticker: 'ZTS', companyName: 'Zoetis' });
            res.body.forEach((stock) => {
                expect(Object.keys(stock).sort()).toEqual(['companyName', 'ticker']);
            });
        });

        it('returns unique tickers so lookups by ticker are unambiguous', async () => {
            const res = await request(app.getHttpServer()).get('/stocks').expect(200);
            const tickers = res.body.map((s) => s.ticker);
            expect(new Set(tickers).size).toBe(tickers.length);
        });
    });

    describe('GET /stocks/:ticker', () => {
        it('returns 200 with the matching stock for a known ticker', async () => {
            const res = await request(app.getHttpServer()).get('/stocks/MSFT').expect(200);
            expect(res.body).toEqual({ ticker: 'MSFT', companyName: 'Microsoft' });
        });

        it('resolves tickers containing a dot (e.g. BRK.B)', async () => {
            const res = await request(app.getHttpServer()).get('/stocks/BRK.B').expect(200);
            expect(res.body.ticker).toBe('BRK.B');
            expect(res.body.companyName).toMatch(/Berkshire Hathaway/);
        });

        it('returns 404 with a NestJS error body for an unknown ticker', async () => {
            const res = await request(app.getHttpServer())
                .get('/stocks/BADTICKER')
                .expect(404);
            expect(res.body).toEqual({
                statusCode: 404,
                message: 'Stock ticker "BADTICKER" not found.',
                error: 'Not Found'
            });
        });

        it('treats tickers as case-sensitive (lower-case lookup is 404)', async () => {
            const res = await request(app.getHttpServer()).get('/stocks/msft').expect(404);
            expect(res.body.message).toBe('Stock ticker "msft" not found.');
        });

        it('does not match on a ticker prefix', async () => {
            await request(app.getHttpServer()).get('/stocks/MSF').expect(404);
        });
    });

    describe('GET /health', () => {
        it('returns 200 with an ok terminus status', async () => {
            const res = await request(app.getHttpServer()).get('/health').expect(200);
            expect(res.body).toEqual({ status: 'ok', info: {}, error: {}, details: {} });
        });
    });

    it('returns 404 for routes the service does not expose', async () => {
        await request(app.getHttpServer()).get('/').expect(404);
        await request(app.getHttpServer()).post('/stocks').expect(404);
    });
});
