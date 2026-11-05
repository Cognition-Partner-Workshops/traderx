import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';
import { setupSwagger } from './swagger';

const port = parseInt(process.env.REFERENCE_DATA_SERVICE_PORT, 10) || 18085;
const hostname = process.env.REFERENCE_DATA_HOSTNAME || '0.0.0.0';

async function bootstrap() {
    const app = await NestFactory.create(AppModule, { cors: true });

    setupSwagger(app);

    await app.listen(port, hostname);
}
bootstrap();
