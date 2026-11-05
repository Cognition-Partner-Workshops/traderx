import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import * as request from 'supertest';
import { AppModule } from './app.module';
import { corsOptions, DEFAULT_ALLOWED_ORIGINS, parseAllowedOrigins } from './cors';

const DISALLOWED_ORIGIN = 'https://evil.example.com';

async function createApp(env: NodeJS.ProcessEnv): Promise<INestApplication> {
    const moduleRef = await Test.createTestingModule({ imports: [AppModule] }).compile();
    const app = moduleRef.createNestApplication({ cors: corsOptions(env) });
    await app.init();
    return app;
}

describe('CORS allow-list', () => {
    describe('with default origins', () => {
        let app: INestApplication;
        beforeAll(async () => {
            app = await createApp({});
        });
        afterAll(async () => app.close());

        it.each(['http://localhost:8080', 'http://localhost:18093', 'http://localhost:18094'])(
            'sends Access-Control-Allow-Origin to local UI origin %s',
            async (origin) => {
                await request(app.getHttpServer())
                    .get('/stocks')
                    .set('Origin', origin)
                    .expect(200)
                    .expect('Access-Control-Allow-Origin', origin);
                await request(app.getHttpServer())
                    .options('/stocks')
                    .set('Origin', origin)
                    .set('Access-Control-Request-Method', 'GET')
                    .expect(204)
                    .expect('Access-Control-Allow-Origin', origin);
            },
        );

        it('does not send Access-Control-Allow-Origin to a disallowed origin', async () => {
            const get = await request(app.getHttpServer()).get('/stocks').set('Origin', DISALLOWED_ORIGIN);
            const preflight = await request(app.getHttpServer())
                .options('/stocks')
                .set('Origin', DISALLOWED_ORIGIN)
                .set('Access-Control-Request-Method', 'GET');
            expect(get.headers['access-control-allow-origin']).toBeUndefined();
            expect(preflight.headers['access-control-allow-origin']).toBeUndefined();
        });
    });

    describe('with CORS_ALLOWED_ORIGINS set', () => {
        let app: INestApplication;
        beforeAll(async () => {
            app = await createApp({ CORS_ALLOWED_ORIGINS: 'https://traderx.example.com, *' });
        });
        afterAll(async () => app.close());

        it('allows only the configured origin and ignores "*"', async () => {
            await request(app.getHttpServer())
                .get('/stocks')
                .set('Origin', 'https://traderx.example.com')
                .expect('Access-Control-Allow-Origin', 'https://traderx.example.com');
            for (const origin of ['http://localhost:18093', DISALLOWED_ORIGIN]) {
                const res = await request(app.getHttpServer()).get('/stocks').set('Origin', origin);
                expect(res.headers['access-control-allow-origin']).toBeUndefined();
            }
        });
    });

    it('parses the allow-list, dropping blanks and wildcards', () => {
        expect(parseAllowedOrigins(undefined)).toEqual(DEFAULT_ALLOWED_ORIGINS.split(','));
        expect(parseAllowedOrigins(' * , ,https://a.example.com')).toEqual(['https://a.example.com']);
        expect(parseAllowedOrigins('')).toEqual([]);
    });
});
