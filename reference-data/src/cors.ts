import { CorsOptions } from '@nestjs/common/interfaces/external/cors-options.interface';

export const DEFAULT_ALLOWED_ORIGINS = 'http://localhost:8080,http://localhost:18093,http://localhost:18094';

// Cross-origin access is limited to the comma-separated origins in CORS_ALLOWED_ORIGINS.
// A "*" entry is ignored so the service can never be opened to every origin.
export function parseAllowedOrigins(origins: string | undefined): string[] {
    return (origins ?? DEFAULT_ALLOWED_ORIGINS)
        .split(',')
        .map((origin) => origin.trim())
        .filter((origin) => origin !== '' && origin !== '*');
}

export function corsOptions(env: NodeJS.ProcessEnv = process.env): CorsOptions {
    return { origin: parseAllowedOrigins(env.CORS_ALLOWED_ORIGINS), methods: ['GET', 'HEAD', 'OPTIONS'] };
}
