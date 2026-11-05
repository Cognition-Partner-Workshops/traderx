import { INestApplication } from '@nestjs/common';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';

export const SWAGGER_PATH = 'api';

// Swagger UI/OpenAPI docs are off unless SWAGGER_ENABLED=true (e.g. for local development).
export function isSwaggerEnabled(
  env: NodeJS.ProcessEnv = process.env,
): boolean {
  return (env.SWAGGER_ENABLED || '').toLowerCase() === 'true';
}

export function setupSwagger(
  app: INestApplication,
  env: NodeJS.ProcessEnv = process.env,
): boolean {
  if (!isSwaggerEnabled(env)) {
    return false;
  }
  const config = new DocumentBuilder()
    .setTitle('Reference Data Example')
    .setDescription('The Reference Data API description')
    .setVersion('1.0')
    .build();
  const document = SwaggerModule.createDocument(app, config);
  SwaggerModule.setup(SWAGGER_PATH, app, document);
  return true;
}
