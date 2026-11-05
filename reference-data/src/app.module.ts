import { Module } from '@nestjs/common';
import { StocksModule } from './stocks/stocks.module';
import HealthModule from './health/health.module';
import { AuthModule } from './auth/auth.module';

@Module({
    imports: [AuthModule, StocksModule, HealthModule]
})
export class AppModule {}